package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountBalanceSnapshotEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.BalanceReconciliationEngine
import com.example.util.ReconciliationDiagnosis
import com.example.util.ReconciliationHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ReconciliationRepository(private val database: FinTrackDatabase) {

    fun getSnapshotsForAccount(accountId: Long): Flow<List<AccountBalanceSnapshotEntity>> {
        return database.accountBalanceSnapshotDao().getByAccountId(accountId)
    }

    fun getLatestSnapshotFlow(accountId: Long): Flow<AccountBalanceSnapshotEntity?> {
        return database.accountBalanceSnapshotDao().getLatestSnapshotFlow(accountId)
    }

    suspend fun getLatestSnapshot(accountId: Long): AccountBalanceSnapshotEntity? = withContext(Dispatchers.IO) {
        database.accountBalanceSnapshotDao().getLatestSnapshot(accountId)
    }

    suspend fun calculateAppBalanceAtDate(accountId: Long, date: Long): Long? = withContext(Dispatchers.IO) {
        database.accountDao().calculateAppBalanceAtDate(accountId, date)
    }

    suspend fun recordSnapshot(
        accountId: Long,
        smsDate: Long,
        balance: Long,
        smsHash: String
    ): Long = withContext(Dispatchers.IO) {
        if (balance <= 0L || smsHash.isBlank()) return@withContext -1L
        // Avoid duplicate snapshot for same account and smsHash
        val existing = database.accountBalanceSnapshotDao().getByHash(accountId, smsHash)
        if (existing != null) {
            return@withContext existing.id
        }
        val entity = AccountBalanceSnapshotEntity(
            accountId = accountId,
            smsDate = smsDate,
            balance = balance,
            smsHash = smsHash
        )
        database.accountBalanceSnapshotDao().insert(entity)
    }

    suspend fun getReconciliationDiagnosis(
        accountId: Long,
        unregisteredCandidates: List<ScannedSmsItem>
    ): ReconciliationDiagnosis? = withContext(Dispatchers.IO) {
        val account = database.accountDao().getAccountById(accountId) ?: return@withContext null
        val snapshotsDesc = database.accountBalanceSnapshotDao().getListByAccountId(accountId)
        val latestSnapshot = snapshotsDesc.firstOrNull()

        if (latestSnapshot == null) {
            val currentAppBalance = database.accountDao().calculateAppBalanceAtDate(accountId, System.currentTimeMillis())
                ?: account.initialBalance
            return@withContext ReconciliationDiagnosis(
                account = account,
                latestSnapshot = null,
                appBalance = currentAppBalance,
                bankBalance = currentAppBalance,
                diff = 0L,
                isSynced = true,
                cause = null,
                history = emptyList()
            )
        }

        // Compute history items and app balances
        val historyItems = mutableListOf<ReconciliationHistoryItem>()
        for (snap in snapshotsDesc) {
            val appBalAtSnap = database.accountDao().calculateAppBalanceAtDate(accountId, snap.smsDate)
                ?: account.initialBalance
            val diffAtSnap = BalanceReconciliationEngine.calculateDiff(snap.balance, appBalAtSnap)
            historyItems.add(
                ReconciliationHistoryItem(
                    snapshot = snap,
                    appBalance = appBalAtSnap,
                    diff = diffAtSnap
                )
            )
        }

        val latestHistory = historyItems.first()
        val latestAppBalance = latestHistory.appBalance
        val latestBankBalance = latestSnapshot.balance
        val latestDiff = latestHistory.diff
        val isSynced = latestDiff == 0L

        // Filter candidate SMS relevant only to this account
        val accountCandidates = unregisteredCandidates.filter { candidate ->
            val p = candidate.parsedSms
            val matchesBank = !p.bankName.isNullOrBlank() && (
                account.name.contains(p.bankName, ignoreCase = true) ||
                p.bankName.contains(account.name, ignoreCase = true)
            )
            val matchesCard = !p.accountIdentifier.isNullOrBlank() && (
                account.cardNumber?.endsWith(p.accountIdentifier) == true ||
                account.shabaNumber?.contains(p.accountIdentifier) == true
            )
            (matchesBank || matchesCard) && candidate.date <= (latestSnapshot.smsDate + 3600_000L)
        }

        // Chronological diffs for checking constant initial discrepancy
        val chronologicalDiffs = historyItems.reversed().map { it.diff }

        val cause = if (!isSynced) {
            BalanceReconciliationEngine.diagnoseCause(
                diff = latestDiff,
                bankBalance = latestBankBalance,
                currentInitialBalance = account.initialBalance,
                candidates = accountCandidates,
                historyDiffsChronological = chronologicalDiffs
            )
        } else null

        ReconciliationDiagnosis(
            account = account,
            latestSnapshot = latestSnapshot,
            appBalance = latestAppBalance,
            bankBalance = latestBankBalance,
            diff = latestDiff,
            isSynced = isSynced,
            cause = cause,
            history = historyItems
        )
    }

    /**
     * Resolves discrepancy by registering candidate transactions atomically in database.withTransaction.
     */
    suspend fun fixUnregisteredTransactions(
        accountId: Long,
        items: List<ScannedSmsItem>
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false
        database.withTransaction {
            val allCats = database.categoryDao().getAll().first()
            val defaultIncomeCat = allCats.firstOrNull { it.type == CategoryType.INCOME }
            val defaultExpenseCat = allCats.firstOrNull { it.type == CategoryType.EXPENSE }

            for (item in items) {
                val p = item.parsedSms
                val type = p.transactionType ?: TransactionType.EXPENSE
                val categoryId = if (type == TransactionType.INCOME) {
                    defaultIncomeCat?.id ?: 1L
                } else {
                    defaultExpenseCat?.id ?: 2L
                }

                val tx = TransactionEntity(
                    type = type,
                    amount = p.amount ?: 0L,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = if (p.date != null && p.date > 0L) p.date else item.date,
                    note = p.rawDescription ?: "تطبیق مانده حساب",
                    smsHash = item.smsHash.ifBlank { null }
                )
                database.transactionDao().insert(tx)
            }
        }
        true
    }

    /**
     * Resolves bank fee discrepancy by inserting an Expense transaction under "کارمزد".
     * Automatically creates category "کارمزد" if it does not exist.
     */
    suspend fun fixBankFee(
        accountId: Long,
        feeAmount: Long,
        date: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (feeAmount <= 0L) return@withContext false
        database.withTransaction {
            val allCats = database.categoryDao().getAll().first()
            var feeCategory = allCats.find { it.name.contains("کارمزد") || it.name.contains("خدمات بانکی") }
            if (feeCategory == null) {
                val newCat = CategoryEntity(
                    name = "کارمزد",
                    type = CategoryType.EXPENSE,
                    icon = "Receipt",
                    color = 0xFF795548.toInt()
                )
                val newId = database.categoryDao().insert(newCat)
                feeCategory = newCat.copy(id = newId)
            }

            val tx = TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = feeAmount,
                accountId = accountId,
                categoryId = feeCategory.id,
                date = date,
                note = "کارمزد بانکی (تطبیق مانده)",
                smsHash = "reconcile_fee_${accountId}_$date"
            )
            database.transactionDao().insert(tx)
        }
        true
    }

    /**
     * Resolves incorrect initial balance by adjusting account.initialBalance by +diff.
     */
    suspend fun fixInitialBalance(
        accountId: Long,
        diff: Long
    ): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val account = database.accountDao().getAccountById(accountId) ?: return@withTransaction
            val newInitial = account.initialBalance + diff
            database.accountDao().updateInitialBalance(accountId, newInitial)
        }
        true
    }

    /**
     * Inserts a manual adjustment transaction chosen by user.
     */
    suspend fun fixManualAdjustment(
        accountId: Long,
        amount: Long,
        type: TransactionType,
        categoryId: Long,
        note: String,
        date: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0L) return@withContext false
        database.withTransaction {
            val tx = TransactionEntity(
                type = type,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                note = note.ifBlank { "تراکنش اصلاحی تطبیق مانده" },
                smsHash = "reconcile_adj_${accountId}_$date"
            )
            database.transactionDao().insert(tx)
        }
        true
    }
}
