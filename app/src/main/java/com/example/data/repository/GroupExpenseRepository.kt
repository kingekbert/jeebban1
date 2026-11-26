package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FinTrackDatabase
import com.example.data.local.dao.GroupExpenseDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.GroupExpenseEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.GroupExpenseWithShares
import com.example.util.SmsHashUtil
import kotlinx.coroutines.flow.Flow

data class GroupExpenseInsertResult(
    val transactionId: Long,
    val groupExpenseId: Long,
    val shareIds: List<Long>
)

class GroupExpenseRepository private constructor(
    private val groupExpenseDao: GroupExpenseDao,
    private val transactionDao: TransactionDao,
    private val database: FinTrackDatabase?,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)?
) {
    constructor(database: FinTrackDatabase) : this(
        groupExpenseDao = database.groupExpenseDao(),
        transactionDao = database.transactionDao(),
        database = database,
        transactionRunner = null
    )

    internal constructor(
        groupExpenseDao: GroupExpenseDao,
        transactionDao: TransactionDao,
        transactionRunner: suspend (suspend () -> Any?) -> Any?
    ) : this(
        groupExpenseDao = groupExpenseDao,
        transactionDao = transactionDao,
        database = null,
        transactionRunner = transactionRunner
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runAtomic(block: suspend () -> T): T {
        return when {
            transactionRunner != null -> transactionRunner.invoke { block() } as T
            database != null -> database.withTransaction { block() }
            else -> throw IllegalStateException("تراکنش اتمیک پایگاه داده پیکربندی نشده است")
        }
    }

    val allGroupExpenses: Flow<List<GroupExpenseWithShares>> = groupExpenseDao.getAllWithShares()
    val recentPersonNames: Flow<List<String>> = groupExpenseDao.getRecentPersonNames()

    suspend fun getByTransactionId(transactionId: Long): GroupExpenseWithShares? {
        return groupExpenseDao.getByTransactionId(transactionId)
    }

    fun observeByTransactionId(transactionId: Long): Flow<GroupExpenseWithShares?> {
        return groupExpenseDao.observeByTransactionId(transactionId)
    }

data class MatchedShareResult(
    val share: GroupExpenseShareEntity,
    val parentNote: String,
    val isExact: Boolean,
    val diffPercent: Double
)

    suspend fun findMatchingOpenShares(amount: Long): List<MatchedShareResult> {
        val openShares = groupExpenseDao.getAllOpenShares()
        if (openShares.isEmpty() || amount <= 0) return emptyList()

        val results = mutableListOf<MatchedShareResult>()
        for (share in openShares) {
            val isExact = share.amount == amount
            val diff = kotlin.math.abs(share.amount - amount)
            val diffRatio = diff.toDouble() / share.amount
            if (isExact) {
                val parentExpense = groupExpenseDao.getById(share.groupExpenseId)
                val parentNote = parentExpense?.transaction?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
                results.add(MatchedShareResult(share, parentNote, isExact = true, diffPercent = 0.0))
            } else if (diffRatio < 0.10) {
                val parentExpense = groupExpenseDao.getById(share.groupExpenseId)
                val parentNote = parentExpense?.transaction?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
                results.add(MatchedShareResult(share, parentNote, isExact = false, diffPercent = diffRatio * 100))
            }
        }
        return results.sortedWith(compareBy({ !it.isExact }, { it.diffPercent }))
    }

    suspend fun settleShareWithTransactionId(shareId: Long, transactionId: Long) {
        val share = groupExpenseDao.getShareById(shareId) ?: return
        groupExpenseDao.updateShare(
            share.copy(
                isSettled = true,
                settledTransactionId = transactionId
            )
        )
    }

    suspend fun settleShareWithIncomeTransaction(
        shareId: Long,
        incomeTransaction: TransactionEntity
    ): Long {
        return runAtomic {
            val smsHash = incomeTransaction.smsHash
            if (!smsHash.isNullOrBlank() && !SmsHashUtil.isFeeHash(smsHash)) {
                if (SmsHashUtil.hasHashConflict(smsHash, transactionDao.getAllSmsHashesList())) {
                    throw DuplicateSmsTransactionException()
                }
            }
            val txId = transactionDao.insert(incomeTransaction)
            if (txId <= 0L) {
                throw IllegalStateException("خطا در ثبت تراکنش تسویه دنگ")
            }
            val share = groupExpenseDao.getShareById(shareId)
            if (share != null) {
                groupExpenseDao.updateShare(
                    share.copy(
                        isSettled = true,
                        settledTransactionId = txId
                    )
                )
            }
            txId
        }
    }

    /**
     * Atomically persists the main expense transaction, its [GroupExpenseEntity], and all
     * associated [GroupExpenseShareEntity] records inside a single Room database transaction.
     * If any step fails or returns an invalid row ID, the entire transaction is rolled back.
     */
    suspend fun saveExpenseWithSharesAtomic(
        expenseTransaction: TransactionEntity,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>
    ): GroupExpenseInsertResult {
        if (expenseTransaction.type != TransactionType.EXPENSE) {
            throw IllegalArgumentException("نوع تراکنش هزینه مشترک باید هزینه باشد")
        }
        if (expenseTransaction.amount <= 0L) {
            throw IllegalArgumentException("مبلغ هزینه مشترک باید بزرگتر از صفر باشد")
        }
        if (expenseTransaction.accountId == null) {
            throw IllegalArgumentException("حساب انتخاب نشده است")
        }
        if (shares.isEmpty()) {
            throw IllegalArgumentException("حداقل یک سهم برای هزینه مشترک لازم است")
        }
        if (shares.any { it.first.isBlank() }) {
            throw IllegalArgumentException("نام تمام افراد در هزینه مشترک باید مشخص باشد")
        }
        if (shares.any { it.second < 0L }) {
            throw IllegalArgumentException("مبلغ سهم افراد نمی‌تواند منفی باشد")
        }
        if (shares.sumOf { it.second } > expenseTransaction.amount) {
            throw IllegalArgumentException("مجموع سهم دوستان نمی‌تواند بیشتر از مبلغ کل هزینه باشد")
        }

        return runAtomic {
            val smsHash = expenseTransaction.smsHash
            if (!smsHash.isNullOrBlank() && !SmsHashUtil.isFeeHash(smsHash)) {
                if (SmsHashUtil.hasHashConflict(smsHash, transactionDao.getAllSmsHashesList())) {
                    throw DuplicateSmsTransactionException()
                }
            }

            val txId = transactionDao.insert(expenseTransaction)
            if (txId <= 0L) {
                throw IllegalStateException("خطا در ثبت تراکنش اصلی هزینه مشترک")
            }

            val (groupExpenseId, shareIds) = insertGroupExpenseAndSharesInternal(
                transactionId = txId,
                shareMode = shareMode,
                shares = shares
            )

            GroupExpenseInsertResult(
                transactionId = txId,
                groupExpenseId = groupExpenseId,
                shareIds = shareIds
            )
        }
    }

    suspend fun saveExpenseWithShares(
        expenseTransaction: TransactionEntity,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>
    ): Long = saveExpenseWithSharesAtomic(
        expenseTransaction = expenseTransaction,
        shareMode = shareMode,
        shares = shares
    ).transactionId

    suspend fun saveGroupExpense(
        transactionId: Long,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>
    ) {
        if (shares.isEmpty()) return

        runAtomic {
            insertGroupExpenseAndSharesInternal(
                transactionId = transactionId,
                shareMode = shareMode,
                shares = shares
            )
        }
    }

    private suspend fun insertGroupExpenseAndSharesInternal(
        transactionId: Long,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>
    ): Pair<Long, List<Long>> {
        // Remove existing if any (in case of edit)
        groupExpenseDao.deleteByTransactionId(transactionId)

        val groupExpenseId = groupExpenseDao.insertGroupExpense(
            GroupExpenseEntity(
                transactionId = transactionId,
                shareMode = shareMode,
                createdAt = System.currentTimeMillis()
            )
        )
        if (groupExpenseId <= 0L) {
            throw IllegalStateException("خطا در ثبت اطلاعات هزینه مشترک")
        }

        val shareEntities = shares.map { (name, amount) ->
            GroupExpenseShareEntity(
                groupExpenseId = groupExpenseId,
                personName = name.trim(),
                amount = amount,
                isSettled = false,
                settledTransactionId = null
            )
        }

        val insertedShareIds = groupExpenseDao.insertShares(shareEntities)
        if (insertedShareIds.size != shareEntities.size || insertedShareIds.any { it <= 0L }) {
            throw IllegalStateException("خطا در ثبت سهم‌های هزینه مشترک")
        }

        return groupExpenseId to insertedShareIds
    }

    suspend fun settleShare(
        shareId: Long,
        incomeAccountId: Long? = null,
        categoryId: Long? = null,
        customNote: String? = null
    ) {
        runAtomic {
            val share = groupExpenseDao.getShareById(shareId) ?: return@runAtomic
            var createdIncomeId: Long? = null

            if (incomeAccountId != null) {
                val now = System.currentTimeMillis()
                val note = customNote ?: "تسویه دنگ ${share.personName}"
                val incomeTransaction = TransactionEntity(
                    amount = share.amount,
                    type = TransactionType.INCOME,
                    accountId = incomeAccountId,
                    categoryId = categoryId,
                    date = now,
                    note = note
                )
                createdIncomeId = transactionDao.insert(incomeTransaction)
            }

            groupExpenseDao.updateShare(
                share.copy(
                    isSettled = true,
                    settledTransactionId = createdIncomeId
                )
            )
        }
    }

    suspend fun unsettleShare(shareId: Long) {
        runAtomic {
            val share = groupExpenseDao.getShareById(shareId) ?: return@runAtomic
            val incomeTxId = share.settledTransactionId
            if (incomeTxId != null) {
                transactionDao.deleteById(incomeTxId)
            }

            groupExpenseDao.updateShare(
                share.copy(
                    isSettled = false,
                    settledTransactionId = null
                )
            )
        }
    }

    suspend fun settleMultipleShares(
        shareIds: List<Long>,
        totalAmount: Long,
        incomeAccountId: Long? = null,
        categoryId: Long? = null,
        customNote: String? = null
    ) {
        if (shareIds.isEmpty()) return
        runAtomic {
            var createdIncomeId: Long? = null
            if (incomeAccountId != null && totalAmount > 0) {
                val now = System.currentTimeMillis()
                val note = customNote ?: "تسویه دنگ‌های مشترک"
                val incomeTransaction = TransactionEntity(
                    amount = totalAmount,
                    type = TransactionType.INCOME,
                    accountId = incomeAccountId,
                    categoryId = categoryId,
                    date = now,
                    note = note
                )
                createdIncomeId = transactionDao.insert(incomeTransaction)
            }

            for (shareId in shareIds) {
                val share = groupExpenseDao.getShareById(shareId) ?: continue
                groupExpenseDao.updateShare(
                    share.copy(
                        isSettled = true,
                        settledTransactionId = createdIncomeId
                    )
                )
            }
        }
    }

    suspend fun settlePartialShare(
        shareId: Long,
        settleAmount: Long,
        incomeAccountId: Long? = null,
        categoryId: Long? = null,
        customNote: String? = null
    ) {
        runAtomic {
            val share = groupExpenseDao.getShareById(shareId) ?: return@runAtomic
            if (settleAmount >= share.amount) {
                settleShare(shareId, incomeAccountId, categoryId, customNote)
                return@runAtomic
            }

            var createdIncomeId: Long? = null
            if (incomeAccountId != null && settleAmount > 0) {
                val now = System.currentTimeMillis()
                val note = customNote ?: "تسویه جزئی دنگ ${share.personName}"
                val incomeTransaction = TransactionEntity(
                    amount = settleAmount,
                    type = TransactionType.INCOME,
                    accountId = incomeAccountId,
                    categoryId = categoryId,
                    date = now,
                    note = note
                )
                createdIncomeId = transactionDao.insert(incomeTransaction)
            }

            val remainingAmount = share.amount - settleAmount
            groupExpenseDao.updateShare(
                share.copy(amount = remainingAmount)
            )

            val settledShare = GroupExpenseShareEntity(
                groupExpenseId = share.groupExpenseId,
                personName = share.personName,
                amount = settleAmount,
                isSettled = true,
                settledTransactionId = createdIncomeId
            )
            groupExpenseDao.insertShares(listOf(settledShare))
        }
    }

    suspend fun settlePartialPersonDebt(
        personName: String,
        amountToSettle: Long,
        incomeAccountId: Long? = null,
        categoryId: Long? = null,
        customNote: String? = null
    ) {
        runAtomic {
            val allExpenses = groupExpenseDao.getAllWithSharesList()
            val openSharesWithDate = allExpenses.flatMap { exp ->
                val date = exp.transaction?.date ?: exp.groupExpense.createdAt
                exp.shares.filter { !it.isSettled && it.personName.equals(personName, ignoreCase = true) }
                    .map { it to date }
            }.sortedBy { it.second }

            var remainingToSettle = amountToSettle
            var totalActuallySettled = 0L
            val fullySettledShareIds = mutableListOf<Long>()
            val partiallySettledShares = mutableListOf<Pair<GroupExpenseShareEntity, Long>>()

            for ((share, _) in openSharesWithDate) {
                if (remainingToSettle <= 0) break
                if (remainingToSettle >= share.amount) {
                    fullySettledShareIds.add(share.id)
                    remainingToSettle -= share.amount
                    totalActuallySettled += share.amount
                } else {
                    partiallySettledShares.add(share to remainingToSettle)
                    totalActuallySettled += remainingToSettle
                    remainingToSettle = 0
                }
            }

            var createdIncomeId: Long? = null
            if (incomeAccountId != null && totalActuallySettled > 0) {
                val now = System.currentTimeMillis()
                val note = customNote ?: "تسویه دنگ $personName"
                val incomeTransaction = TransactionEntity(
                    amount = totalActuallySettled,
                    type = TransactionType.INCOME,
                    accountId = incomeAccountId,
                    categoryId = categoryId,
                    date = now,
                    note = note
                )
                createdIncomeId = transactionDao.insert(incomeTransaction)
            }

            for (shareId in fullySettledShareIds) {
                val share = groupExpenseDao.getShareById(shareId) ?: continue
                groupExpenseDao.updateShare(
                    share.copy(
                        isSettled = true,
                        settledTransactionId = createdIncomeId
                    )
                )
            }

            for ((share, paidAmount) in partiallySettledShares) {
                val remainingAmount = share.amount - paidAmount
                groupExpenseDao.updateShare(
                    share.copy(amount = remainingAmount)
                )
                val settledShare = GroupExpenseShareEntity(
                    groupExpenseId = share.groupExpenseId,
                    personName = share.personName,
                    amount = paidAmount,
                    isSettled = true,
                    settledTransactionId = createdIncomeId
                )
                groupExpenseDao.insertShares(listOf(settledShare))
            }
        }
    }

    suspend fun deleteGroupExpenseByTransaction(transactionId: Long) {
        groupExpenseDao.deleteByTransactionId(transactionId)
    }
}
