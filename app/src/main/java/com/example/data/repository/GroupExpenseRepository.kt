package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.GroupExpenseEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.GroupExpenseWithShares
import kotlinx.coroutines.flow.Flow

class GroupExpenseRepository(
    private val database: FinTrackDatabase
) {
    private val groupExpenseDao = database.groupExpenseDao()
    private val transactionDao = database.transactionDao()

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
        return database.withTransaction {
            val txId = transactionDao.insert(incomeTransaction)
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

    suspend fun saveExpenseWithShares(
        expenseTransaction: TransactionEntity,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>
    ): Long {
        return database.withTransaction {
            val txId = transactionDao.insert(expenseTransaction)
            saveGroupExpense(txId, shareMode, shares)
            txId
        }
    }

    suspend fun saveGroupExpense(
        transactionId: Long,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>
    ) {
        if (shares.isEmpty()) return

        database.withTransaction {
            // Remove existing if any (in case of edit)
            groupExpenseDao.deleteByTransactionId(transactionId)

            val groupExpenseId = groupExpenseDao.insertGroupExpense(
                GroupExpenseEntity(
                    transactionId = transactionId,
                    shareMode = shareMode,
                    createdAt = System.currentTimeMillis()
                )
            )

            val shareEntities = shares.map { (name, amount) ->
                GroupExpenseShareEntity(
                    groupExpenseId = groupExpenseId,
                    personName = name.trim(),
                    amount = amount,
                    isSettled = false,
                    settledTransactionId = null
                )
            }
            groupExpenseDao.insertShares(shareEntities)
        }
    }

    suspend fun settleShare(
        shareId: Long,
        incomeAccountId: Long? = null,
        categoryId: Long? = null,
        customNote: String? = null
    ) {
        database.withTransaction {
            val share = groupExpenseDao.getShareById(shareId) ?: return@withTransaction
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
        database.withTransaction {
            val share = groupExpenseDao.getShareById(shareId) ?: return@withTransaction
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
        database.withTransaction {
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
        database.withTransaction {
            val share = groupExpenseDao.getShareById(shareId) ?: return@withTransaction
            if (settleAmount >= share.amount) {
                settleShare(shareId, incomeAccountId, categoryId, customNote)
                return@withTransaction
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
        database.withTransaction {
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
