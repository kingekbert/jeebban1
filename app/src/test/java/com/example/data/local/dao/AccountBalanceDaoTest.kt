package com.example.data.local.dao

import androidx.room.Room
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountBalanceDaoTest {
    private lateinit var database: FinTrackDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            FinTrackDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun balanceIncludesIncomeExpensesAndBothTransferDirections() = runBlocking {
        val accountA = database.accountDao().insert(account("A", 1_000L))
        val accountB = database.accountDao().insert(account("B", 500L))

        database.transactionDao().insert(transaction(TransactionType.INCOME, 200L, accountA, date = 10L))
        database.transactionDao().insert(transaction(TransactionType.EXPENSE, 50L, accountA, date = 20L))
        database.transactionDao().insert(transaction(TransactionType.TRANSFER, 300L, accountA, accountB, date = 30L))
        database.transactionDao().insert(transaction(TransactionType.TRANSFER, 100L, accountB, accountA, date = 40L))

        val balances = database.accountDao().getAllWithBalance().first().associate {
            it.account.id to it.currentBalance
        }

        assertEquals(950L, balances[accountA])
        assertEquals(700L, balances[accountB])
        // Transfers are balance-neutral across all accounts.
        assertEquals(1_650L, balances.values.sum())
    }

    @Test
    fun historicalBalanceOnlyIncludesTransactionsUpToRequestedTimestamp() = runBlocking {
        val accountA = database.accountDao().insert(account("A", 1_000L))
        val accountB = database.accountDao().insert(account("B", 500L))

        database.transactionDao().insert(transaction(TransactionType.INCOME, 200L, accountA, date = 10L))
        database.transactionDao().insert(transaction(TransactionType.EXPENSE, 50L, accountA, date = 20L))
        database.transactionDao().insert(transaction(TransactionType.TRANSFER, 300L, accountA, accountB, date = 30L))
        database.transactionDao().insert(transaction(TransactionType.TRANSFER, 100L, accountB, accountA, date = 40L))

        assertEquals(1_150L, database.accountDao().calculateAppBalanceAtDate(accountA, 25L))
        assertEquals(850L, database.accountDao().calculateAppBalanceAtDate(accountA, 35L))
        assertEquals(950L, database.accountDao().calculateAppBalanceAtDate(accountA, 45L))
        assertEquals(500L, database.accountDao().calculateAppBalanceAtDate(accountB, 25L))
        assertEquals(800L, database.accountDao().calculateAppBalanceAtDate(accountB, 35L))
        assertEquals(700L, database.accountDao().calculateAppBalanceAtDate(accountB, 45L))
    }

    @Test
    fun transferCountIsIncludedForBothSourceAndDestinationAccounts() = runBlocking {
        val accountA = database.accountDao().insert(account("A", 0L))
        val accountB = database.accountDao().insert(account("B", 0L))
        database.transactionDao().insert(transaction(TransactionType.TRANSFER, 250L, accountA, accountB, date = 1L))

        val summaries = database.accountDao().getAllWithBalance().first().associateBy { it.account.id }
        assertEquals(1, summaries[accountA]?.transactionCount)
        assertEquals(1, summaries[accountB]?.transactionCount)
    }

    private fun account(name: String, initialBalance: Long) = AccountEntity(
        name = name,
        type = AccountType.BANK,
        initialBalance = initialBalance,
        color = 0,
        icon = "account_balance"
    )

    private fun transaction(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        toAccountId: Long? = null,
        date: Long
    ) = TransactionEntity(
        type = type,
        amount = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        date = date
    )
}
