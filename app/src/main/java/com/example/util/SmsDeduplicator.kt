package com.example.util

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.TransactionWithDetails
import kotlin.math.abs

/**
 * Single source of truth for SMS transaction deduplication across ScanSmsViewModel,
 * WidgetBackgroundScanner, and TransferDetector.
 */
object SmsDeduplicator {

    data class SmsCandidate(
        val transactionType: TransactionType,
        val amount: Long,
        val date: Long,
        val primaryHash: String,
        val legacyHashes: Set<String> = emptySet(),
        val matchedAccountId: Long? = null,
        val accountIdent: String = "",
        val bankName: String = ""
    )

    /**
     * Checks whether [candidate] is already registered in [existingTransactions].
     *
     * Rules:
     * 1. Exact primary hash match (including composite transfer hashes `wHash|dHash`) always matches.
     * 2. Legacy hash match (v1/simple) matches ONLY when transaction type is compatible,
     *    preventing cross-type collisions (e.g. same-amount deposit vs withdrawal).
     * 3. Heuristic fallback (amount + same Jalali day + transaction type + account) applies ONLY
     *    to manual transactions (`smsHash.isNullOrBlank()`). Existing SMS-backed transactions with a
     *    different `smsHash` represent distinct real SMS events and NEVER suppress another SMS.
     * 4. Matched manual transaction IDs are recorded in [consumedManualTxIds] (1-to-1 matching)
     *    so a single manual transaction cannot hide multiple independent SMS purchases of the same amount.
     */
    fun isSmsRegistered(
        candidate: SmsCandidate,
        existingTransactions: List<TransactionWithDetails>,
        accountsById: Map<Long, AccountEntity>,
        consumedManualTxIds: MutableSet<Long> = mutableSetOf()
    ): Boolean {
        val primaryTokens = SmsHashUtil.extractHashTokens(candidate.primaryHash)
        val legacyTokens = candidate.legacyHashes
            .flatMap { SmsHashUtil.extractHashTokens(it) }
            .toSet()

        // Pass 1: Exact hash match against SMS-backed transactions
        for (txItem in existingTransactions) {
            val tx = txItem.transaction
            if (tx.smsHash.isNullOrBlank() || SmsHashUtil.isFeeHash(tx.smsHash)) continue
            val storedTokens = SmsHashUtil.extractHashTokens(tx.smsHash)
            if (storedTokens.isEmpty()) continue

            // Canonical v2 hash already embeds transactionType + accountIdentifier + sender + amount + date + text
            if (primaryTokens.any { it in storedTokens }) {
                return true
            }

            // Legacy hashes lacked transactionType; enforce type compatibility before matching
            if (legacyTokens.isNotEmpty() && legacyTokens.any { it in storedTokens }) {
                if (isTypeAndSideCompatible(tx, candidate, accountsById, txItem)) {
                    return true
                }
            }
        }

        // Pass 2: Manual transaction heuristic fallback (1-to-1, type-aware, account-aware, same Jalali day)
        val smsJalali = JalaliDate.fromTimestamp(candidate.date)
        var bestManualMatchId: Long? = null
        var minTimeDelta = Long.MAX_VALUE

        for (txItem in existingTransactions) {
            val tx = txItem.transaction
            // Never use amount+day heuristic against another SMS-backed transaction
            if (!tx.smsHash.isNullOrBlank()) continue
            if (tx.id in consumedManualTxIds) continue
            if (tx.amount != candidate.amount) continue

            val txJalali = JalaliDate.fromTimestamp(tx.date)
            val sameDay = txJalali.year == smsJalali.year &&
                txJalali.month == smsJalali.month &&
                txJalali.day == smsJalali.day
            if (!sameDay) continue

            if (!isTypeAndSideCompatible(tx, candidate, accountsById, txItem)) continue

            val delta = abs(tx.date - candidate.date)
            if (delta < minTimeDelta) {
                minTimeDelta = delta
                bestManualMatchId = tx.id
            }
        }

        if (bestManualMatchId != null) {
            consumedManualTxIds.add(bestManualMatchId)
            return true
        }

        return false
    }

    /**
     * Verifies both transaction direction compatibility (EXPENSE vs INCOME, or the matching leg
     * of a TRANSFER) and account matching.
     */
    internal fun isTypeAndSideCompatible(
        tx: TransactionEntity,
        candidate: SmsCandidate,
        accountsById: Map<Long, AccountEntity>,
        txItem: TransactionWithDetails
    ): Boolean {
        return when (tx.type) {
            TransactionType.EXPENSE -> {
                candidate.transactionType == TransactionType.EXPENSE &&
                    matchesAccount(
                        accountId = tx.accountId,
                        fallbackCard = txItem.accountCardNumber,
                        fallbackName = txItem.accountName,
                        candidate = candidate,
                        accountsById = accountsById
                    )
            }
            TransactionType.INCOME -> {
                candidate.transactionType == TransactionType.INCOME &&
                    matchesAccount(
                        accountId = tx.accountId,
                        fallbackCard = txItem.accountCardNumber,
                        fallbackName = txItem.accountName,
                        candidate = candidate,
                        accountsById = accountsById
                    )
            }
            TransactionType.TRANSFER -> {
                // Withdrawal SMS corresponds strictly to the source account (accountId);
                // Deposit SMS corresponds strictly to the destination account (toAccountId).
                when (candidate.transactionType) {
                    TransactionType.EXPENSE -> matchesAccount(
                        accountId = tx.accountId,
                        fallbackCard = txItem.accountCardNumber,
                        fallbackName = txItem.accountName,
                        candidate = candidate,
                        accountsById = accountsById
                    )
                    TransactionType.INCOME -> matchesAccount(
                        accountId = tx.toAccountId,
                        fallbackCard = null,
                        fallbackName = txItem.toAccountName,
                        candidate = candidate,
                        accountsById = accountsById
                    )
                    TransactionType.TRANSFER -> true
                }
            }
        }
    }

    private fun matchesAccount(
        accountId: Long?,
        fallbackCard: String?,
        fallbackName: String?,
        candidate: SmsCandidate,
        accountsById: Map<Long, AccountEntity>
    ): Boolean {
        if (candidate.matchedAccountId != null) {
            return accountId == candidate.matchedAccountId
        }

        val ident = candidate.accountIdent.trim()
        val bankName = candidate.bankName.trim()
        if (ident.isBlank() && bankName.isBlank()) {
            return true
        }

        val account = accountId?.let { accountsById[it] }
        val card = account?.cardNumber ?: fallbackCard
        val shaba = account?.shabaNumber
        val name = account?.name ?: fallbackName

        val cleanIdent = TransferDetector.sanitizeDigits(ident)
        val cleanCard = TransferDetector.sanitizeDigits(card)
        val cleanShaba = TransferDetector.sanitizeDigits(shaba)

        val matchesCard = cleanIdent.isNotBlank() && cleanCard.isNotBlank() && (
            cleanCard.contains(cleanIdent) ||
                (cleanCard.length >= 4 && cleanIdent.contains(cleanCard.takeLast(4)))
            )
        val matchesShaba = cleanIdent.isNotBlank() && cleanShaba.isNotBlank() && (
            cleanShaba.contains(cleanIdent) || cleanIdent.contains(cleanShaba)
            )
        val matchesIdentInName = ident.isNotBlank() && name != null && (
            name.contains(ident, ignoreCase = true) || ident.contains(name, ignoreCase = true)
            )
        val matchesBank = bankName.isNotBlank() && name != null && (
            name.contains(bankName, ignoreCase = true) || bankName.contains(name, ignoreCase = true)
            )

        return matchesCard || matchesShaba || matchesIdentInName || matchesBank
    }
}
