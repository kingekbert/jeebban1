package com.example.util

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.TransactionWithDetails
import com.example.ui.screens.scan_sms.ScannedSmsItem
import java.util.UUID

data class DetectedTransfer(
    val id: String = UUID.randomUUID().toString(),
    val withdrawalItem: ScannedSmsItem,
    val depositItem: ScannedSmsItem,
    val sourceAccount: AccountEntity?,
    val destAccount: AccountEntity?,
    val transferAmount: Long,
    val feeAmount: Long = 0L,
    val isRegistered: Boolean = false
)

object TransferDetector {

    /**
     * Sanitizes digit strings by converting Persian/Arabic digits to English digits
     * and stripping directional formatting marks (like \u202A, \u202C) and whitespace.
     */
    fun sanitizeDigits(input: String?): String {
        if (input == null) return ""
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val sb = StringBuilder()
        for (ch in input) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch in persianDigits -> sb.append((ch - '۰' + '0'.code).toChar())
                ch in arabicDigits -> sb.append((ch - '٠' + '0'.code).toChar())
            }
        }
        return sb.toString()
    }

    /**
     * Identifies pairs of withdrawal and deposit SMS messages representing transfers
     * between the user's own accounts (Card-to-Card, Paya, Satna, etc.).
     *
     * Matching conditions:
     * 1. Withdrawal has transactionType == EXPENSE and Deposit has transactionType == INCOME.
     * 2. Amounts:
     *    - Exact: withdrawalAmount == depositAmount (e.g. Paya where fee is deducted separately or not printed).
     *    - With Fee: withdrawalAmount > depositAmount, where fee = withdrawalAmount - depositAmount
     *      is a realistic bank fee (<= 500,000 Rials / 50,000 Tomans or <= 2% of transfer).
     * 3. Time difference:
     *    - Deposit date is within [-30 minutes, +72 hours] of withdrawal date (covers instant transfers,
     *      as well as Paya clearing cycles with > 2 hours delay or weekend/holiday schedules).
     * 4. Account distinctness:
     *    - If both accounts are identified, sourceAccount.id != destAccount.id.
     */
    fun findTransfers(
        items: List<ScannedSmsItem>,
        accounts: List<AccountEntity>,
        existingTransactions: List<TransactionWithDetails> = emptyList()
    ): List<DetectedTransfer> {
        val withdrawals = items.filter {
            it.parsedSms.transactionType == TransactionType.EXPENSE && (it.parsedSms.amount ?: 0L) > 0L
        }
        val deposits = items.filter {
            it.parsedSms.transactionType == TransactionType.INCOME && (it.parsedSms.amount ?: 0L) > 0L
        }

        val pairedDeposits = mutableSetOf<String>()
        val results = mutableListOf<DetectedTransfer>()

        for (w in withdrawals) {
            val wAmt = w.parsedSms.amount ?: continue
            val wDate = if ((w.parsedSms.date ?: 0L) > 0L) w.parsedSms.date!! else w.date
            val srcAcc = matchAccount(w, accounts)

            var bestDeposit: ScannedSmsItem? = null
            var bestDestAcc: AccountEntity? = null
            var bestFee = 0L
            var minScore = Long.MAX_VALUE

            for (d in deposits) {
                if (pairedDeposits.contains(d.id)) continue
                val dAmt = d.parsedSms.amount ?: continue
                val dDate = if ((d.parsedSms.date ?: 0L) > 0L) d.parsedSms.date!! else d.date
                val destAcc = matchAccount(d, accounts)

                // Source and destination accounts cannot be identical
                if (srcAcc != null && destAcc != null && srcAcc.id == destAcc.id) {
                    continue
                }

                // Amount check: exact match or difference within realistic bank fee
                val isExact = (wAmt == dAmt)
                val diff = wAmt - dAmt
                val maxAllowedFee = maxOf(500_000L, (dAmt * 0.02).toLong())
                val isWithFee = (!isExact && diff > 0L && diff <= maxAllowedFee)

                if (!isExact && !isWithFee) continue

                // Time window: -30 minutes to +72 hours (covers Paya clearing cycle delays > 2h up to 3 days)
                val timeDiff = dDate - wDate
                val minTime = -30 * 60 * 1000L
                val maxTime = 72 * 3600 * 1000L
                if (timeDiff < minTime || timeDiff > maxTime) continue

                // Prioritize candidate with accounts identified and closest timestamp
                val score = Math.abs(timeDiff) + (if (srcAcc != null && destAcc != null) 0L else 10_000_000L)
                if (score < minScore) {
                    minScore = score
                    bestDeposit = d
                    bestDestAcc = destAcc
                    bestFee = if (isExact) 0L else diff
                }
            }

            if (bestDeposit != null) {
                pairedDeposits.add(bestDeposit.id)

                // Infer missing account if user has exactly 2 accounts
                val finalSrcAcc = srcAcc ?: if (accounts.size == 2 && bestDestAcc != null) {
                    accounts.find { it.id != bestDestAcc.id }
                } else null

                val finalDestAcc = bestDestAcc ?: if (accounts.size == 2 && finalSrcAcc != null) {
                    accounts.find { it.id != finalSrcAcc.id }
                } else null

                // Check registration status from both items or existing transactions with composite/matching hashes
                val isRegistered = (w.isRegistered && bestDeposit.isRegistered) ||
                        existingTransactions.any { txItem ->
                            val tx = txItem.transaction
                            val txHash = tx.smsHash ?: ""
                            txHash.isNotBlank() && (
                                txHash == "${w.smsHash}|${bestDeposit.smsHash}" ||
                                (txHash.contains(w.smsHash) && txHash.contains(bestDeposit.smsHash))
                            )
                        }

                results.add(
                    DetectedTransfer(
                        id = "${w.smsHash}_${bestDeposit.smsHash}",
                        withdrawalItem = w,
                        depositItem = bestDeposit,
                        sourceAccount = finalSrcAcc,
                        destAccount = finalDestAcc,
                        transferAmount = bestDeposit.parsedSms.amount ?: wAmt,
                        feeAmount = bestFee,
                        isRegistered = isRegistered
                    )
                )
            }
        }

        return results
    }

    fun matchAccount(item: ScannedSmsItem, accounts: List<AccountEntity>): AccountEntity? {
        val rawIdent = item.parsedSms.accountIdentifier?.trim() ?: ""
        val cleanIdent = sanitizeDigits(rawIdent)
        val bankName = item.parsedSms.bankName?.trim() ?: ""

        if (cleanIdent.isNotBlank()) {
            val found = accounts.find { acc ->
                val cardClean = sanitizeDigits(acc.cardNumber)
                val shabaClean = sanitizeDigits(acc.shabaNumber)
                val nameClean = sanitizeDigits(acc.name)

                (cardClean.isNotBlank() && (cardClean.contains(cleanIdent) || (cleanIdent.length >= 4 && cardClean.endsWith(cleanIdent.takeLast(4))))) ||
                (shabaClean.isNotBlank() && (shabaClean.contains(cleanIdent) || cleanIdent.contains(shabaClean))) ||
                (cleanIdent.length >= 4 && nameClean.contains(cleanIdent)) ||
                (rawIdent.isNotBlank() && acc.name.contains(rawIdent))
            }
            if (found != null) return found
        }

        if (bankName.isNotBlank()) {
            val normalizedKey = BankUtils.getNormalizedBankKey(bankName)
            val found = accounts.find { acc ->
                acc.name.contains(bankName, ignoreCase = true) ||
                (normalizedKey != null && BankUtils.getNormalizedBankKey(acc.name) == normalizedKey)
            }
            if (found != null) return found
        }

        // Check if raw SMS mentions any account name/bank
        for (acc in accounts) {
            val accKey = BankUtils.getNormalizedBankKey(acc.name)
            if (accKey != null && BankUtils.getNormalizedBankKey(item.rawText) == accKey) {
                return acc
            }
            if (item.rawText.contains(acc.name, ignoreCase = true)) {
                return acc
            }
        }

        return null
    }
}

