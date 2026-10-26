package com.example.util

import com.example.data.local.entity.AccountBalanceSnapshotEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import kotlin.math.abs

sealed class ReconciliationCause {
    /**
     * One or more unregistered transactions explain the discrepancy.
     */
    data class UnregisteredTransactions(
        val candidateItems: List<ScannedSmsItem>
    ) : ReconciliationCause()

    /**
     * Small deduction likely due to unrecorded bank fee (e.g. SMS alert fee, card maintenance).
     */
    data class BankFee(
        val feeAmount: Long
    ) : ReconciliationCause()

    /**
     * Initial balance was entered incorrectly and discrepancy was present from the first snapshot.
     */
    data class WrongInitialBalance(
        val diff: Long,
        val currentInitialBalance: Long,
        val proposedInitialBalance: Long
    ) : ReconciliationCause()

    /**
     * Fallback: manual adjustment transaction.
     */
    data class ManualAdjustment(
        val diff: Long
    ) : ReconciliationCause()
}

data class ReconciliationHistoryItem(
    val snapshot: AccountBalanceSnapshotEntity,
    val appBalance: Long,
    val diff: Long
)

data class ReconciliationDiagnosis(
    val account: AccountEntity,
    val latestSnapshot: AccountBalanceSnapshotEntity?,
    val appBalance: Long,
    val bankBalance: Long,
    val diff: Long,
    val isSynced: Boolean,
    val cause: ReconciliationCause?,
    val history: List<ReconciliationHistoryItem>
)

object BalanceReconciliationEngine {

    const val DEFAULT_BANK_FEE_MAX_AMOUNT = 100_000L // 100,000 Rials (~10,000 Tomans)
    const val DEFAULT_BANK_FEE_PERCENT = 0.02 // 2% of bank balance

    /**
     * Calculates the calculated app balance at a specific point in time (smsDate).
     * appBalance = initialBalance ± sum of transactions for this account up to timestamp.
     */
    fun calculateAppBalance(
        initialBalance: Long,
        accountId: Long,
        transactionsUpToDate: List<TransactionEntity>
    ): Long {
        var balance = initialBalance
        for (tx in transactionsUpToDate) {
            if (tx.accountId == accountId) {
                when (tx.type) {
                    TransactionType.EXPENSE -> balance -= tx.amount
                    TransactionType.INCOME -> balance += tx.amount
                    TransactionType.TRANSFER -> balance -= tx.amount
                }
            } else if (tx.toAccountId == accountId && tx.type == TransactionType.TRANSFER) {
                balance += tx.amount
            }
        }
        return balance
    }

    /**
     * diff = bankBalance - appBalance
     * > 0: bank has more money (missing income in app)
     * < 0: app has more money (missing expense in app)
     */
    fun calculateDiff(bankBalance: Long, appBalance: Long): Long {
        return bankBalance - appBalance
    }

    /**
     * Computes the signed impact of a candidate SMS on the account balance.
     * Income adds to app balance, Expense subtracts from app balance.
     */
    fun getCandidateSignedEffect(item: ScannedSmsItem): Long {
        val amount = item.parsedSms.amount ?: 0L
        return when (item.parsedSms.transactionType) {
            TransactionType.INCOME -> amount
            TransactionType.EXPENSE -> -amount
            else -> -amount // Default assumption is expense for bank transactions
        }
    }

    /**
     * Bounded subset-sum search: Finds a combination of up to 3 candidate SMS
     * whose net signed impact equals targetDiff.
     * Returns the smallest matching subset (size 1 first, then 2, then 3).
     */
    fun findSubsetSumCandidates(
        candidates: List<ScannedSmsItem>,
        targetDiff: Long,
        maxSubsetSize: Int = 3
    ): List<ScannedSmsItem>? {
        if (targetDiff == 0L || candidates.isEmpty()) return null

        // 1. Single candidate match (size 1)
        for (candidate in candidates) {
            if (getCandidateSignedEffect(candidate) == targetDiff) {
                return listOf(candidate)
            }
        }

        if (maxSubsetSize < 2) return null

        // 2. Pair match (size 2)
        val n = candidates.size
        for (i in 0 until n) {
            val effI = getCandidateSignedEffect(candidates[i])
            for (j in i + 1 until n) {
                val effJ = getCandidateSignedEffect(candidates[j])
                if (effI + effJ == targetDiff) {
                    return listOf(candidates[i], candidates[j])
                }
            }
        }

        if (maxSubsetSize < 3) return null

        // 3. Triplet match (size 3)
        // Cap pool size to prevent excessive combinations if candidate list is large
        val boundedPool = if (n > 30) candidates.take(30) else candidates
        val boundedSize = boundedPool.size
        for (i in 0 until boundedSize) {
            val effI = getCandidateSignedEffect(boundedPool[i])
            for (j in i + 1 until boundedSize) {
                val effJ = getCandidateSignedEffect(boundedPool[j])
                for (k in j + 1 until boundedSize) {
                    val effK = getCandidateSignedEffect(boundedPool[k])
                    if (effI + effJ + effK == targetDiff) {
                        return listOf(boundedPool[i], boundedPool[j], boundedPool[k])
                    }
                }
            }
        }

        return null
    }

    /**
     * Bank fee detection:
     * App balance is higher than bank balance (diff < 0), no candidate matched,
     * and |diff| is below threshold (e.g. <= 100,000 Rials or < 2% of bank balance).
     */
    fun isBankFee(
        diff: Long,
        bankBalance: Long,
        maxFeeAmount: Long = DEFAULT_BANK_FEE_MAX_AMOUNT,
        maxFeePercent: Double = DEFAULT_BANK_FEE_PERCENT
    ): Boolean {
        if (diff >= 0) return false
        val absDiff = abs(diff)
        if (absDiff <= maxFeeAmount) return true
        if (bankBalance > 0 && absDiff.toDouble() <= (bankBalance * maxFeePercent)) return true
        return false
    }

    /**
     * Checks if discrepancy has been constant since the earliest recorded snapshot.
     * Chronological order of diffs: [diff_1, diff_2, ...].
     * Returns true if all recorded diffs are equal to the latest non-zero diff.
     */
    fun isConstantDiffFromEarliest(historyDiffs: List<Long>): Boolean {
        if (historyDiffs.isEmpty()) return false
        val firstDiff = historyDiffs.first()
        if (firstDiff == 0L) return false
        return historyDiffs.all { it == firstDiff }
    }

    /**
     * Diagnoses root cause according to the strict priority order in specifications:
     * 1. Unregistered transactions (subset-sum up to 3 candidates)
     * 2. Bank fee (small diff < 0)
     * 3. Wrong initial balance (constant diff since earliest snapshot)
     * 4. Manual adjustment fallback
     */
    fun diagnoseCause(
        diff: Long,
        bankBalance: Long,
        currentInitialBalance: Long,
        candidates: List<ScannedSmsItem>,
        historyDiffsChronological: List<Long>
    ): ReconciliationCause? {
        if (diff == 0L) return null

        // 1. Unregistered transactions
        val matchedSubset = findSubsetSumCandidates(candidates, diff, maxSubsetSize = 3)
        if (!matchedSubset.isNullOrEmpty()) {
            return ReconciliationCause.UnregisteredTransactions(matchedSubset)
        }

        // 2. Bank fee
        if (isBankFee(diff, bankBalance)) {
            return ReconciliationCause.BankFee(abs(diff))
        }

        // 3. Wrong initial balance
        if (isConstantDiffFromEarliest(historyDiffsChronological)) {
            return ReconciliationCause.WrongInitialBalance(
                diff = diff,
                currentInitialBalance = currentInitialBalance,
                proposedInitialBalance = currentInitialBalance + diff
            )
        }

        // 4. Manual adjustment
        return ReconciliationCause.ManualAdjustment(diff)
    }
}
