package com.example.domain.usecase

import com.example.data.local.dao.GroupExpenseDao
import com.example.data.local.dao.RawCategorySummary
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.GroupExpenseEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.GroupExpenseWithShares
import com.example.data.local.relation.TransactionWithDetails
import com.example.data.repository.DuplicateSmsTransactionException
import com.example.data.repository.GroupExpenseRepository
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicGroupExpenseTest {

    /**
     * In-memory transactional store simulating Room's `database.withTransaction` across both
     * `TransactionDao` and `GroupExpenseDao`. All writes inside a transaction target staging
     * buffers and are committed atomically ONLY if the block completes without throwing; any
     * thrown exception rolls back transactions, group expenses, and shares together.
     */
    private class TransactionalFakeStore(
        private val onTransactionInsert: (TransactionEntity, Long) -> Long = { _, seq -> seq },
        private val onGroupExpenseInsert: (GroupExpenseEntity, Long) -> Long = { _, seq -> seq },
        private val onSharesInsert: (List<GroupExpenseShareEntity>, Long) -> List<Long> = { shares, startSeq ->
            shares.indices.map { startSeq + it }
        }
    ) {
        val committedTransactions = mutableListOf<TransactionEntity>()
        val committedGroupExpenses = mutableListOf<GroupExpenseEntity>()
        val committedShares = mutableListOf<GroupExpenseShareEntity>()

        var transactionCount = 0
            private set
        var rollbackCount = 0
            private set
        var shareInsertAttempts = 0
            private set

        private var stagedTransactions: MutableList<TransactionEntity>? = null
        private var stagedGroupExpenses: MutableList<GroupExpenseEntity>? = null
        private var stagedShares: MutableList<GroupExpenseShareEntity>? = null

        private var txSeq = 0L
        private var geSeq = 0L
        private var shareSeq = 0L

        suspend fun runInTransaction(block: suspend () -> Any?): Any? {
            transactionCount++
            val txStage = committedTransactions.toMutableList()
            val geStage = committedGroupExpenses.toMutableList()
            val shStage = committedShares.toMutableList()
            val prevTxSeq = txSeq
            val prevGeSeq = geSeq
            val prevShareSeq = shareSeq

            stagedTransactions = txStage
            stagedGroupExpenses = geStage
            stagedShares = shStage

            return try {
                val result = block()
                committedTransactions.clear()
                committedTransactions.addAll(txStage)
                committedGroupExpenses.clear()
                committedGroupExpenses.addAll(geStage)
                committedShares.clear()
                committedShares.addAll(shStage)
                result
            } catch (t: Throwable) {
                rollbackCount++
                txSeq = prevTxSeq
                geSeq = prevGeSeq
                shareSeq = prevShareSeq
                throw t
            } finally {
                stagedTransactions = null
                stagedGroupExpenses = null
                stagedShares = null
            }
        }

        val transactionDao: TransactionDao = object : TransactionDao {
            override suspend fun insert(transaction: TransactionEntity): Long {
                txSeq++
                val id = onTransactionInsert(transaction, txSeq)
                val target = stagedTransactions ?: committedTransactions
                target.add(transaction.copy(id = id))
                return id
            }

            override suspend fun update(transaction: TransactionEntity) = Unit
            override suspend fun delete(transaction: TransactionEntity) = Unit
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun updateBookmark(id: Long, isBookmarked: Boolean) = Unit
            override fun getAll(): Flow<List<TransactionEntity>> = emptyFlow()
            override fun getAllWithDetails(): Flow<List<TransactionWithDetails>> = emptyFlow()
            override fun getByIdWithDetails(id: Long): Flow<TransactionWithDetails?> = emptyFlow()
            override fun getById(id: Long): Flow<TransactionEntity?> = emptyFlow()
            override suspend fun getByIdSync(id: Long): TransactionEntity? = (stagedTransactions ?: committedTransactions).find { it.id == id }
            override suspend fun getLinkedTransferFees(parentTransferId: Long): List<TransactionEntity> = emptyList()
            override fun getRecent(limit: Int): Flow<List<TransactionWithDetails>> = emptyFlow()
            override fun getByAccount(accountId: Long): Flow<List<TransactionWithDetails>> = emptyFlow()
            override fun getByDateRange(start: Long, end: Long): Flow<List<TransactionWithDetails>> = emptyFlow()
            override fun getTodayIncome(todayStart: Long, todayEnd: Long): Flow<Long?> = emptyFlow()
            override fun getTodayExpense(todayStart: Long, todayEnd: Long): Flow<Long?> = emptyFlow()
            override fun getCategorySummaryRaw(start: Long, end: Long, type: TransactionType): Flow<List<RawCategorySummary>> = emptyFlow()
            override fun getTotalIncome(start: Long, end: Long): Flow<Long?> = emptyFlow()
            override fun getTotalExpense(start: Long, end: Long): Flow<Long?> = emptyFlow()
            override suspend fun deleteAll() = Unit
            override fun getAllSmsHashes(): Flow<List<String>> = emptyFlow()
            override suspend fun getAllSmsHashesList(): List<String> =
                (stagedTransactions ?: committedTransactions).mapNotNull { it.smsHash }.filter { it.isNotBlank() }

            override suspend fun getAllWithDetailsList(): List<TransactionWithDetails> = emptyList()
            override suspend fun findMatchingTransactions(amount: Long, dayStart: Long, dayEnd: Long): List<TransactionWithDetails> = emptyList()
            override suspend fun countByRecurringAndDate(recurringId: Long, executedForDate: Long): Int = 0
            override suspend fun getTransactionsForAccountUpToDate(accountId: Long, date: Long): List<TransactionEntity> = emptyList()
        }

        val groupExpenseDao: GroupExpenseDao = object : GroupExpenseDao {
            override suspend fun insertGroupExpense(groupExpense: GroupExpenseEntity): Long {
                geSeq++
                val id = onGroupExpenseInsert(groupExpense, geSeq)
                val target = stagedGroupExpenses ?: committedGroupExpenses
                target.add(groupExpense.copy(id = id))
                return id
            }

            override suspend fun insertShares(shares: List<GroupExpenseShareEntity>): List<Long> {
                shareInsertAttempts++
                val startSeq = shareSeq + 1L
                val ids = onSharesInsert(shares, startSeq)
                shareSeq += shares.size
                val target = stagedShares ?: committedShares
                shares.forEachIndexed { idx, share ->
                    val id = ids.getOrElse(idx) { -1L }
                    target.add(share.copy(id = id))
                }
                return ids
            }

            override suspend fun updateShare(share: GroupExpenseShareEntity) = Unit
            override suspend fun deleteGroupExpense(groupExpense: GroupExpenseEntity) = Unit
            override suspend fun deleteByTransactionId(transactionId: Long) {
                val geTarget = stagedGroupExpenses ?: committedGroupExpenses
                val removedIds = geTarget.filter { it.transactionId == transactionId }.map { it.id }.toSet()
                geTarget.removeAll { it.transactionId == transactionId }
                if (removedIds.isNotEmpty()) {
                    (stagedShares ?: committedShares).removeAll { it.groupExpenseId in removedIds }
                }
            }

            override fun getAllWithShares(): Flow<List<GroupExpenseWithShares>> = emptyFlow()
            override suspend fun getAllWithSharesList(): List<GroupExpenseWithShares> = emptyList()
            override suspend fun getByTransactionId(transactionId: Long): GroupExpenseWithShares? = null
            override fun observeByTransactionId(transactionId: Long): Flow<GroupExpenseWithShares?> = emptyFlow()
            override suspend fun getById(id: Long): GroupExpenseWithShares? = null
            override suspend fun getShareById(shareId: Long): GroupExpenseShareEntity? = null
            override suspend fun getSharesSettledByTransaction(transactionId: Long): List<GroupExpenseShareEntity> = emptyList()
            override suspend fun getOpenSharesByAmount(amount: Long): List<GroupExpenseShareEntity> = emptyList()
            override suspend fun getAllOpenShares(): List<GroupExpenseShareEntity> = emptyList()
            override fun getRecentPersonNames(): Flow<List<String>> = emptyFlow()
            override suspend fun getAllEntities(): List<GroupExpenseEntity> = committedGroupExpenses.toList()
            override suspend fun getAllShares(): List<GroupExpenseShareEntity> = committedShares.toList()
            override suspend fun deleteAllShares() = Unit
            override suspend fun deleteAllGroupExpenses() = Unit
        }
    }

    private fun buildUseCase(store: TransactionalFakeStore): AddTransactionUseCase {
        val txRepo = TransactionRepository(transactionDao = store.transactionDao)
        val groupRepo = GroupExpenseRepository(
            groupExpenseDao = store.groupExpenseDao,
            transactionDao = store.transactionDao,
            transactionRunner = { block -> store.runInTransaction(block) }
        )
        return AddTransactionUseCase(
            transactionRepository = txRepo,
            groupExpenseRepository = groupRepo
        )
    }

    private fun sampleExpense(
        amount: Long = 900_000L,
        accountId: Long? = 1L,
        smsHash: String? = null
    ) = TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = amount,
        accountId = accountId,
        categoryId = 3L,
        date = 1_710_000_000_000L,
        note = "شام با دوستان",
        smsHash = smsHash
    )

    @Test
    fun `group expense and shares succeed atomically inside a single database transaction`() = runTest {
        val store = TransactionalFakeStore()
        val useCase = buildUseCase(store)

        val result = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 900_000L),
            shareMode = GroupExpenseShareMode.EQUAL,
            shares = listOf(" علی " to 300_000L, "سارا" to 300_000L)
        )

        assertTrue(result.isSuccess)
        val inserted = result.getOrThrow()
        assertEquals(1L, inserted.transactionId)
        assertEquals(1L, inserted.groupExpenseId)
        assertEquals(listOf(1L, 2L), inserted.shareIds)

        // Exactly ONE atomic DB transaction with zero rollbacks
        assertEquals(1, store.transactionCount)
        assertEquals(0, store.rollbackCount)
        assertEquals(1, store.committedTransactions.size)
        assertEquals(1, store.committedGroupExpenses.size)
        assertEquals(2, store.committedShares.size)

        // Verify relational integrity and trimmed names
        assertEquals(1L, store.committedGroupExpenses.first().transactionId)
        assertEquals(GroupExpenseShareMode.EQUAL, store.committedGroupExpenses.first().shareMode)
        assertEquals("علی", store.committedShares[0].personName)
        assertEquals(300_000L, store.committedShares[0].amount)
        assertEquals(1L, store.committedShares[0].groupExpenseId)
        assertEquals("سارا", store.committedShares[1].personName)
        assertEquals(300_000L, store.committedShares[1].amount)
    }

    @Test
    fun `failure while inserting shares rolls back main transaction and group expense leaving zero orphan records`() = runTest {
        val store = TransactionalFakeStore(
            onSharesInsert = { _, _ ->
                throw RuntimeException("SQLite disk I/O error while inserting shares")
            }
        )
        val useCase = buildUseCase(store)

        val result = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 600_000L),
            shareMode = GroupExpenseShareMode.CUSTOM,
            shares = listOf("رضا" to 200_000L, "مریم" to 150_000L)
        )

        assertTrue(result.isFailure)
        assertEquals("SQLite disk I/O error while inserting shares", result.exceptionOrNull()?.message)
        assertEquals(1, store.transactionCount)
        assertEquals(1, store.rollbackCount)
        assertEquals(1, store.shareInsertAttempts)

        // Crucial atomicity guarantee: ZERO transactions, ZERO group expenses, ZERO orphan shares
        assertTrue(store.committedTransactions.isEmpty())
        assertTrue(store.committedGroupExpenses.isEmpty())
        assertTrue(store.committedShares.isEmpty())
    }

    @Test
    fun `failure while inserting group expense header rolls back main transaction and never inserts shares`() = runTest {
        val store = TransactionalFakeStore(
            onGroupExpenseInsert = { _, _ ->
                throw RuntimeException("Constraint failure on group_expenses")
            }
        )
        val useCase = buildUseCase(store)

        val result = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(),
            shareMode = GroupExpenseShareMode.EQUAL,
            shares = listOf("امیر" to 450_000L)
        )

        assertTrue(result.isFailure)
        assertEquals("Constraint failure on group_expenses", result.exceptionOrNull()?.message)
        assertEquals(1, store.transactionCount)
        assertEquals(1, store.rollbackCount)
        assertEquals(0, store.shareInsertAttempts)
        assertTrue(store.committedTransactions.isEmpty())
        assertTrue(store.committedGroupExpenses.isEmpty())
        assertTrue(store.committedShares.isEmpty())
    }

    @Test
    fun `invalid or partial share row ids trigger full rollback`() = runTest {
        val store = TransactionalFakeStore(
            onSharesInsert = { _, _ -> listOf(1L, -1L) }
        )
        val useCase = buildUseCase(store)

        val result = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(),
            shareMode = GroupExpenseShareMode.EQUAL,
            shares = listOf("علی" to 300_000L, "سارا" to 300_000L)
        )

        assertTrue(result.isFailure)
        assertEquals(1, store.rollbackCount)
        assertTrue(store.committedTransactions.isEmpty())
        assertTrue(store.committedGroupExpenses.isEmpty())
        assertTrue(store.committedShares.isEmpty())
    }

    @Test
    fun `validation rejects invalid shares before touching database`() = runTest {
        val store = TransactionalFakeStore()
        val useCase = buildUseCase(store)

        val emptyShares = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 500_000L),
            shareMode = GroupExpenseShareMode.EQUAL,
            shares = emptyList()
        )
        assertTrue(emptyShares.isFailure)

        val blankName = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 500_000L),
            shareMode = GroupExpenseShareMode.CUSTOM,
            shares = listOf("   " to 100_000L)
        )
        assertTrue(blankName.isFailure)

        val negativeShare = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 500_000L),
            shareMode = GroupExpenseShareMode.CUSTOM,
            shares = listOf("علی" to -10_000L)
        )
        assertTrue(negativeShare.isFailure)

        val sumExceedsTotal = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 500_000L),
            shareMode = GroupExpenseShareMode.CUSTOM,
            shares = listOf("علی" to 300_000L, "سارا" to 300_000L)
        )
        assertTrue(sumExceedsTotal.isFailure)

        // None of the invalid attempts should ever open a DB transaction
        assertEquals(0, store.transactionCount)
        assertTrue(store.committedTransactions.isEmpty())
        assertTrue(store.committedGroupExpenses.isEmpty())
        assertTrue(store.committedShares.isEmpty())
    }

    @Test
    fun `duplicate sms hash on group expense rolls back cleanly without creating second expense or shares`() = runTest {
        val store = TransactionalFakeStore()
        val useCase = buildUseCase(store)

        val first = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 400_000L, smsHash = "sms_group_hash_1"),
            shareMode = GroupExpenseShareMode.EQUAL,
            shares = listOf("مهدی" to 200_000L)
        )
        assertTrue(first.isSuccess)
        assertEquals(1, store.committedTransactions.size)
        assertEquals(1, store.committedGroupExpenses.size)
        assertEquals(1, store.committedShares.size)

        val duplicate = useCase.addGroupExpenseWithShares(
            expenseTransaction = sampleExpense(amount = 400_000L, smsHash = "sms_group_hash_1"),
            shareMode = GroupExpenseShareMode.EQUAL,
            shares = listOf("مهدی" to 200_000L)
        )
        assertTrue(duplicate.isFailure)
        assertTrue(duplicate.exceptionOrNull() is DuplicateSmsTransactionException)
        assertEquals(1, store.rollbackCount)
        assertEquals(1, store.committedTransactions.size)
        assertEquals(1, store.committedGroupExpenses.size)
        assertEquals(1, store.committedShares.size)
    }
}
