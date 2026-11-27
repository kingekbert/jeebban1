package com.example.domain.usecase

import com.example.data.local.dao.RawCategorySummary
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.TransactionWithDetails
import com.example.data.repository.TransactionRepository
import com.example.data.repository.TransferInsertResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicTransferWithFeeTest {

    /**
     * In-memory transactional fake DAO that mirrors Room's `database.withTransaction` semantics:
     * writes during a transaction go to a staging buffer and are committed ONLY if the block
     * completes normally; any thrown exception rolls back all staged writes.
     */
    private class TransactionalFakeTransactionDao(
        private val onInsert: (TransactionEntity, Int) -> Long = { _, seq -> seq.toLong() }
    ) : TransactionDao {

        val committedTransactions = mutableListOf<TransactionEntity>()
        val attemptedInserts = mutableListOf<TransactionEntity>()
        var transactionCount = 0
            private set
        var rollbackCount = 0
            private set

        private var activeStaging: MutableList<TransactionEntity>? = null
        private var idSequence = 0

        suspend fun runInTransaction(block: suspend () -> TransferInsertResult): TransferInsertResult {
            transactionCount++
            val staging = committedTransactions.toMutableList()
            val previousIdSeq = idSequence
            activeStaging = staging
            return try {
                val result = block()
                committedTransactions.clear()
                committedTransactions.addAll(staging)
                result
            } catch (t: Throwable) {
                rollbackCount++
                idSequence = previousIdSeq
                throw t
            } finally {
                activeStaging = null
            }
        }

        override suspend fun insert(transaction: TransactionEntity): Long {
            attemptedInserts.add(transaction)
            idSequence++
            val assignedId = onInsert(transaction, idSequence)
            val saved = transaction.copy(id = assignedId)
            val target = activeStaging ?: committedTransactions
            target.add(saved)
            return assignedId
        }

        override suspend fun update(transaction: TransactionEntity) = Unit
        override suspend fun delete(transaction: TransactionEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun updateBookmark(id: Long, isBookmarked: Boolean) = Unit
        override fun getAll(): Flow<List<TransactionEntity>> = emptyFlow()
        override fun getAllWithDetails(): Flow<List<TransactionWithDetails>> = emptyFlow()
        override fun getByIdWithDetails(id: Long): Flow<TransactionWithDetails?> = emptyFlow()
        override fun getById(id: Long): Flow<TransactionEntity?> = emptyFlow()
        override suspend fun getByIdSync(id: Long): TransactionEntity? = (activeStaging ?: committedTransactions).find { it.id == id }
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
            (activeStaging ?: committedTransactions).mapNotNull { it.smsHash }.filter { it.isNotBlank() }
        override suspend fun getAllWithDetailsList(): List<TransactionWithDetails> = emptyList()
        override suspend fun findMatchingTransactions(amount: Long, dayStart: Long, dayEnd: Long): List<TransactionWithDetails> = emptyList()
        override suspend fun countByRecurringAndDate(recurringId: Long, executedForDate: Long): Int = 0
        override suspend fun getTransactionsForAccountUpToDate(accountId: Long, date: Long): List<TransactionEntity> = emptyList()
    }

    private fun buildUseCase(dao: TransactionalFakeTransactionDao): AddTransactionUseCase {
        val repository = TransactionRepository(
            transactionDao = dao,
            database = null,
            transactionRunner = { block -> dao.runInTransaction(block) }
        )
        return AddTransactionUseCase(transactionRepository = repository)
    }

    private fun sampleTransfer(
        amount: Long = 5_000_000L,
        fromAccountId: Long = 1L,
        toAccountId: Long = 2L
    ) = TransactionEntity(
        type = TransactionType.TRANSFER,
        amount = amount,
        accountId = fromAccountId,
        toAccountId = toAccountId,
        date = 1_700_000_000_000L,
        note = "انتقال بین حساب‌ها",
        smsHash = "hash_w|hash_d"
    )

    private fun sampleFee(
        amount: Long = 7_200L,
        fromAccountId: Long = 1L,
        categoryId: Long? = 10L
    ) = TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = amount,
        accountId = fromAccountId,
        categoryId = categoryId,
        date = 1_700_000_000_000L,
        note = "کارمزد انتقال بین حساب‌ها",
        smsHash = "hash_w|hash_d_fee"
    )

    @Test
    fun `transfer without fee succeeds atomically and inserts only transfer`() = runTest {
        val dao = TransactionalFakeTransactionDao()
        val useCase = buildUseCase(dao)

        val result = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(),
            feeTransaction = null
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(1L, data.transferId)
        assertNull(data.feeTransactionId)
        assertEquals(1, dao.transactionCount)
        assertEquals(0, dao.rollbackCount)
        assertEquals(1, dao.committedTransactions.size)
        assertEquals(TransactionType.TRANSFER, dao.committedTransactions.first().type)
    }

    @Test
    fun `transfer with fee succeeds atomically and inserts both records in one transaction`() = runTest {
        val dao = TransactionalFakeTransactionDao()
        val useCase = buildUseCase(dao)

        val result = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(amount = 10_000_000L),
            feeTransaction = sampleFee(amount = 12_000L)
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(1L, data.transferId)
        assertEquals(2L, data.feeTransactionId)
        assertEquals(1, dao.transactionCount)
        assertEquals(0, dao.rollbackCount)
        assertEquals(2, dao.committedTransactions.size)
        assertEquals(TransactionType.TRANSFER, dao.committedTransactions[0].type)
        assertEquals(10_000_000L, dao.committedTransactions[0].amount)
        assertEquals(TransactionType.EXPENSE, dao.committedTransactions[1].type)
        assertEquals(12_000L, dao.committedTransactions[1].amount)
    }

    @Test
    fun `failure while saving transfer rolls back and never attempts fee insert`() = runTest {
        val dao = TransactionalFakeTransactionDao { tx, _ ->
            if (tx.type == TransactionType.TRANSFER) {
                throw RuntimeException("DB disk full while inserting transfer")
            }
            99L
        }
        val useCase = buildUseCase(dao)

        val result = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(),
            feeTransaction = sampleFee()
        )

        assertTrue(result.isFailure)
        assertEquals("DB disk full while inserting transfer", result.exceptionOrNull()?.message)
        assertEquals(1, dao.transactionCount)
        assertEquals(1, dao.rollbackCount)
        // Fee insert must NEVER be attempted when transfer fails
        assertEquals(1, dao.attemptedInserts.size)
        assertEquals(TransactionType.TRANSFER, dao.attemptedInserts.first().type)
        // Zero records committed
        assertTrue(dao.committedTransactions.isEmpty())
    }

    @Test
    fun `failure while saving fee rolls back already inserted transfer leaving zero records`() = runTest {
        val dao = TransactionalFakeTransactionDao { tx, seq ->
            if (tx.type == TransactionType.EXPENSE) {
                throw RuntimeException("Foreign key error while inserting fee")
            }
            seq.toLong()
        }
        val useCase = buildUseCase(dao)

        val result = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(),
            feeTransaction = sampleFee()
        )

        assertTrue(result.isFailure)
        assertEquals("Foreign key error while inserting fee", result.exceptionOrNull()?.message)
        assertEquals(1, dao.transactionCount)
        assertEquals(1, dao.rollbackCount)
        assertEquals(2, dao.attemptedInserts.size)
        // Crucial integrity guarantee: transfer MUST be rolled back when fee fails
        assertTrue(dao.committedTransactions.isEmpty())
    }

    @Test
    fun `invalid row id returned on fee insert triggers rollback of transfer`() = runTest {
        val dao = TransactionalFakeTransactionDao { tx, seq ->
            if (tx.type == TransactionType.EXPENSE) -1L else seq.toLong()
        }
        val useCase = buildUseCase(dao)

        val result = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(),
            feeTransaction = sampleFee()
        )

        assertTrue(result.isFailure)
        assertEquals(1, dao.rollbackCount)
        assertTrue(dao.committedTransactions.isEmpty())
    }

    @Test
    fun `validation rejects same source and destination accounts without touching database`() = runTest {
        val dao = TransactionalFakeTransactionDao()
        val useCase = buildUseCase(dao)

        val result = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(fromAccountId = 1L, toAccountId = 1L),
            feeTransaction = sampleFee(fromAccountId = 1L)
        )

        assertTrue(result.isFailure)
        assertEquals(0, dao.transactionCount)
        assertTrue(dao.attemptedInserts.isEmpty())
        assertTrue(dao.committedTransactions.isEmpty())
    }

    @Test
    fun `validation rejects fee with mismatched account or non-positive amount before touching database`() = runTest {
        val dao = TransactionalFakeTransactionDao()
        val useCase = buildUseCase(dao)

        val mismatchResult = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(fromAccountId = 1L, toAccountId = 2L),
            feeTransaction = sampleFee(fromAccountId = 2L)
        )
        assertTrue(mismatchResult.isFailure)

        val zeroFeeResult = useCase.addTransferWithOptionalFee(
            transferTransaction = sampleTransfer(fromAccountId = 1L, toAccountId = 2L),
            feeTransaction = sampleFee(amount = 0L, fromAccountId = 1L)
        )
        assertTrue(zeroFeeResult.isFailure)

        assertEquals(0, dao.transactionCount)
        assertTrue(dao.attemptedInserts.isEmpty())
        assertTrue(dao.committedTransactions.isEmpty())
    }
}
