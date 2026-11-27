package com.example.domain.usecase

import androidx.room.Room
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddTransactionUseCaseTransferValidationTest {
    private lateinit var database: FinTrackDatabase
    private lateinit var useCase: AddTransactionUseCase
    private var sourceId: Long = 0L
    private var destinationId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            FinTrackDatabase::class.java
        ).allowMainThreadQueries().build()
        sourceId = database.accountDao().insert(account("Source"))
        destinationId = database.accountDao().insert(account("Destination"))
        useCase = AddTransactionUseCase(
            TransactionRepository(database.transactionDao(), database)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transferWithoutDestinationIsRejectedAndNotPersisted() = runBlocking {
        val result = useCase(transfer(amount = 100L, destination = null))
        assertFalse(result.isSuccess)
        assertEquals(0, database.transactionDao().getAll().first().size)
    }

    @Test
    fun transferToSameAccountIsRejectedAndNotPersisted() = runBlocking {
        val result = useCase(transfer(amount = 100L, destination = sourceId))
        assertFalse(result.isSuccess)
        assertEquals(0, database.transactionDao().getAll().first().size)
    }

    @Test
    fun zeroAndNegativeTransferAmountsAreRejectedAndNotPersisted() = runBlocking {
        assertFalse(useCase(transfer(amount = 0L)).isSuccess)
        assertFalse(useCase(transfer(amount = -1L)).isSuccess)
        assertEquals(0, database.transactionDao().getAll().first().size)
    }

    @Test
    fun validTransferIsPersisted() = runBlocking {
        val result = useCase(transfer(amount = 250L))
        assertTrue(result.isSuccess)
        assertEquals(1, database.transactionDao().getAll().first().size)
    }

    private fun account(name: String) = AccountEntity(
        name = name,
        type = AccountType.BANK,
        initialBalance = 0L,
        color = 0,
        icon = "account_balance"
    )

    private fun transfer(amount: Long, destination: Long? = destinationId) = TransactionEntity(
        type = TransactionType.TRANSFER,
        amount = amount,
        accountId = sourceId,
        toAccountId = destination,
        date = System.currentTimeMillis()
    )
}
