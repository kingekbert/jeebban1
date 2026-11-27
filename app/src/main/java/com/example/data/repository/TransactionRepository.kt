package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FinTrackDatabase
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.SavingsGoalEntryEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.relation.GroupExpenseWithShares
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.TransactionWithDetails
import com.example.domain.model.CategorySummary
import com.example.util.SmsHashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DuplicateSmsTransactionException(
    message: String = "این پیامک قبلاً ثبت شده است"
) : IllegalStateException(message)

data class DeletedTransactionSnapshot(
    val transaction: TransactionEntity,
    val groupExpense: GroupExpenseWithShares?,
    val linkedSavingsEntries: List<SavingsGoalEntryEntity>,
    val sharesSettledByTransaction: List<GroupExpenseShareEntity>
)

data class TransferInsertResult(
    val transferId: Long,
    val feeTransactionId: Long? = null
)

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val database: FinTrackDatabase? = null,
    private val transactionRunner: (suspend (suspend () -> TransferInsertResult) -> TransferInsertResult)? = null
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAll()
    val allTransactionsWithDetails: Flow<List<TransactionWithDetails>> = transactionDao.getAllWithDetails()

    fun getTransactionById(id: Long): Flow<TransactionEntity?> =
        transactionDao.getById(id)

    fun getTransactionByIdWithDetails(id: Long): Flow<TransactionWithDetails?> =
        transactionDao.getByIdWithDetails(id)

    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionWithDetails>> =
        transactionDao.getRecent(limit)

    fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionWithDetails>> =
        transactionDao.getByAccount(accountId)

    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<TransactionWithDetails>> =
        transactionDao.getByDateRange(start, end)

    fun getTodayIncome(todayStart: Long, todayEnd: Long): Flow<Long> =
        transactionDao.getTodayIncome(todayStart, todayEnd).map { it ?: 0L }

    fun getTodayExpense(todayStart: Long, todayEnd: Long): Flow<Long> =
        transactionDao.getTodayExpense(todayStart, todayEnd).map { it ?: 0L }

    fun getTotalIncome(start: Long, end: Long): Flow<Long> =
        transactionDao.getTotalIncome(start, end).map { it ?: 0L }

    fun getTotalExpense(start: Long, end: Long): Flow<Long> =
        transactionDao.getTotalExpense(start, end).map { it ?: 0L }

    fun getCategorySummary(start: Long, end: Long, type: TransactionType): Flow<List<CategorySummary>> {
        val totalFlow = if (type == TransactionType.EXPENSE) {
            getTotalExpense(start, end)
        } else {
            getTotalIncome(start, end)
        }
        val rawFlow = transactionDao.getCategorySummaryRaw(start, end, type)

        return combine(rawFlow, totalFlow) { rawList, total ->
            rawList.map { raw ->
                val percentage = if (total > 0) {
                    (raw.totalAmount.toFloat() / total.toFloat()) * 100f
                } else {
                    0f
                }
                CategorySummary(
                    category = raw.category,
                    totalAmount = raw.totalAmount,
                    transactionCount = raw.transactionCount,
                    percentage = percentage
                )
            }
        }
    }

    suspend fun isSmsHashRegistered(smsHash: String?): Boolean = withContext(Dispatchers.IO) {
        if (smsHash.isNullOrBlank() || SmsHashUtil.isFeeHash(smsHash)) return@withContext false
        SmsHashUtil.hasHashConflict(smsHash, transactionDao.getAllSmsHashesList())
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long = withContext(Dispatchers.IO) {
        val hash = transaction.smsHash
        val shouldGuardHash = !hash.isNullOrBlank() && !SmsHashUtil.isFeeHash(hash)
        if (!shouldGuardHash) {
            return@withContext transactionDao.insert(transaction)
        }

        val checkAndInsert: suspend () -> Long = {
            val existingHashes = transactionDao.getAllSmsHashesList()
            if (SmsHashUtil.hasHashConflict(hash, existingHashes)) {
                throw DuplicateSmsTransactionException()
            }
            transactionDao.insert(transaction)
        }

        if (database != null) {
            database.withTransaction { checkAndInsert() }
        } else {
            checkAndInsert()
        }
    }

    /**
     * Atomically persists a transfer transaction and its optional bank fee transaction inside
     * a single Room database transaction. If either insert fails, the entire transaction rolls back.
     */
    suspend fun insertTransferWithFee(
        transferTransaction: TransactionEntity,
        feeTransaction: TransactionEntity? = null
    ): TransferInsertResult = withContext(Dispatchers.IO) {
        val runAtomic: suspend (suspend () -> TransferInsertResult) -> TransferInsertResult = { block ->
            when {
                transactionRunner != null -> transactionRunner.invoke(block)
                database != null -> database.withTransaction { block() }
                else -> throw IllegalStateException("تراکنش اتمیک پایگاه داده پیکربندی نشده است")
            }
        }

        runAtomic {
            val transferHash = transferTransaction.smsHash
            if (!transferHash.isNullOrBlank() && !SmsHashUtil.isFeeHash(transferHash)) {
                val existingHashes = transactionDao.getAllSmsHashesList()
                if (SmsHashUtil.hasHashConflict(transferHash, existingHashes)) {
                    throw DuplicateSmsTransactionException()
                }
            }

            val transferId = transactionDao.insert(transferTransaction)
            if (transferId <= 0L) {
                throw IllegalStateException("خطا در ثبت تراکنش انتقال")
            }

            val feeId = if (feeTransaction != null) {
                val insertedFeeId = transactionDao.insert(feeTransaction)
                if (insertedFeeId <= 0L) {
                    throw IllegalStateException("خطا در ثبت تراکنش کارمزد")
                }
                insertedFeeId
            } else {
                null
            }

            TransferInsertResult(
                transferId = transferId,
                feeTransactionId = feeId
            )
        }
    }

    suspend fun insert(transaction: TransactionEntity): Long = insertTransaction(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val db = database ?: run {
            transactionDao.update(transaction)
            return@withContext
        }
        db.withTransaction {
            val original = transactionDao.getByIdSync(transaction.id)
                ?: throw IllegalStateException("تراکنش برای ویرایش یافت نشد")
            val linkedSavings = db.savingsGoalDao().getEntriesLinkedToTransaction(transaction.id)
            val groupExpense = db.groupExpenseDao().getByTransactionId(transaction.id)

            if (linkedSavings.isNotEmpty()) {
                if (linkedSavings.size != 1) {
                    throw IllegalStateException("این تراکنش به چند رکورد پس‌انداز متصل است و ویرایش آن امن نیست")
                }
                val expectedType = when (linkedSavings.single().type) {
                    com.example.data.local.entity.SavingsEntryType.DEPOSIT -> TransactionType.EXPENSE
                    com.example.data.local.entity.SavingsEntryType.WITHDRAW -> TransactionType.INCOME
                }
                if (transaction.type != expectedType) {
                    throw IllegalArgumentException("نوع تراکنش مرتبط با پس‌انداز را نمی‌توان تغییر داد")
                }
            }

            if (groupExpense != null) {
                if (transaction.type != TransactionType.EXPENSE) {
                    throw IllegalArgumentException("تراکنش هزینه مشترک باید از نوع هزینه باقی بماند")
                }
                val totalShares = groupExpense.shares.sumOf { it.amount }
                if (transaction.amount < totalShares) {
                    throw IllegalArgumentException("مبلغ تراکنش از مجموع سهم‌های ثبت‌شده کمتر است")
                }
            }

            // Editing visible fields must not detach SMS identity or recurring history.
            val preserved = transaction.copy(
                smsHash = original.smsHash,
                recurringId = original.recurringId,
                executedForDate = original.executedForDate,
                createdAt = original.createdAt
            )
            transactionDao.update(preserved)

            linkedSavings.singleOrNull()?.let { entry ->
                db.savingsGoalDao().updateEntry(entry.copy(amount = transaction.amount, date = transaction.date))
            }
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.delete(transaction)
    }

    /**
     * Captures child records that Room cascades/nullifies when the transaction is deleted.
     * This snapshot is used by the transaction-list undo action.
     */
    suspend fun deleteTransactionWithUndoSnapshot(transaction: TransactionEntity): DeletedTransactionSnapshot =
        withContext(Dispatchers.IO) {
            val db = database ?: throw IllegalStateException("Database required for reversible transaction deletion")
            db.withTransaction {
                val groupExpense = db.groupExpenseDao().getByTransactionId(transaction.id)
                val savingsEntries = db.savingsGoalDao().getEntriesLinkedToTransaction(transaction.id)
                val settledShares = db.groupExpenseDao().getSharesSettledByTransaction(transaction.id)
                settledShares.forEach { share ->
                    db.groupExpenseDao().updateShare(share.copy(isSettled = false, settledTransactionId = null))
                }
                transactionDao.deleteById(transaction.id)
                DeletedTransactionSnapshot(transaction, groupExpense, savingsEntries, settledShares)
            }
        }

    suspend fun restoreDeletedTransaction(snapshot: DeletedTransactionSnapshot): Long =
        withContext(Dispatchers.IO) {
            val db = database ?: throw IllegalStateException("Database required for transaction restore")
            db.withTransaction {
                val restoredId = transactionDao.insert(snapshot.transaction)
                if (restoredId <= 0L) throw IllegalStateException("Failed to restore transaction")
                snapshot.groupExpense?.let { saved ->
                    val newGroupId = db.groupExpenseDao().insertGroupExpense(
                        saved.groupExpense.copy(id = 0L, transactionId = restoredId)
                    )
                    if (newGroupId <= 0L) throw IllegalStateException("Failed to restore group expense")
                    val restoredShares = saved.shares.map { it.copy(id = 0L, groupExpenseId = newGroupId) }
                    if (restoredShares.isNotEmpty()) {
                        val ids = db.groupExpenseDao().insertShares(restoredShares)
                        if (ids.size != restoredShares.size || ids.any { it <= 0L }) {
                            throw IllegalStateException("Failed to restore group expense shares")
                        }
                    }
                }
                if (snapshot.linkedSavingsEntries.isNotEmpty()) {
                    val restoredEntries = snapshot.linkedSavingsEntries.map {
                        it.copy(id = 0L, transactionId = restoredId)
                    }
                    restoredEntries.forEach { entry ->
                        val id = db.savingsGoalDao().insertEntry(entry)
                        if (id <= 0L) throw IllegalStateException("Failed to restore savings entry")
                    }
                }
                snapshot.sharesSettledByTransaction.forEach { share ->
                    db.groupExpenseDao().updateShare(share.copy(settledTransactionId = restoredId))
                }
                restoredId
            }
        }

    suspend fun updateBookmark(id: Long, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        transactionDao.updateBookmark(id, isBookmarked)
    }

    val allSmsHashes: Flow<List<String>> = transactionDao.getAllSmsHashes()

    suspend fun getAllSmsHashesList(): List<String> = withContext(Dispatchers.IO) {
        transactionDao.getAllSmsHashesList()
    }

    suspend fun getAllTransactionsWithDetailsList(): List<TransactionWithDetails> = withContext(Dispatchers.IO) {
        transactionDao.getAllWithDetailsList()
    }

    suspend fun findMatchingTransactions(amount: Long, dayStart: Long, dayEnd: Long): List<TransactionWithDetails> = withContext(Dispatchers.IO) {
        transactionDao.findMatchingTransactions(amount, dayStart, dayEnd)
    }

    suspend fun countByRecurringAndDate(recurringId: Long, executedForDate: Long): Int = withContext(Dispatchers.IO) {
        transactionDao.countByRecurringAndDate(recurringId, executedForDate)
    }
}
