package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtType
import com.example.data.local.entity.GroupExpenseEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.SavingsEntryType
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.SavingsGoalEntryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransactionDeleteUndoRelationsTest {
    private val database: FinTrackDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        FinTrackDatabase::class.java
    ).allowMainThreadQueries().build()
    private val repository = TransactionRepository(database.transactionDao(), database)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `undo restores savings entries group expense shares and settlement links`() = runTest {
        val accountId = database.accountDao().insert(
            AccountEntity(name = "Test", type = AccountType.CASH, initialBalance = 0, color = 0, icon = "wallet")
        )
        val target = database.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 500L, accountId = accountId, date = 100L, note = "group meal")
        )
        database.debtDao().insert(
            DebtEntity(personName = "Friend", type = DebtType.OWED_TO_ME, amount = 500L, transactionId = target, createdAt = 100L)
        )
        val settlement = database.transactionDao().insert(
            TransactionEntity(type = TransactionType.INCOME, amount = 200L, accountId = accountId, date = 200L, note = "settlement")
        )
        val goalId = database.savingsGoalDao().insert(
            SavingsGoalEntity(title = "Goal", targetAmount = 1_000L)
        )
        database.savingsGoalDao().insertEntry(
            SavingsGoalEntryEntity(goalId = goalId, transactionId = target, amount = 500L, type = SavingsEntryType.DEPOSIT, date = 100L)
        )

        val groupId = database.groupExpenseDao().insertGroupExpense(
            GroupExpenseEntity(transactionId = target, shareMode = GroupExpenseShareMode.CUSTOM, createdAt = 100L)
        )
        database.groupExpenseDao().insertShares(
            listOf(GroupExpenseShareEntity(groupExpenseId = groupId, personName = "Friend", amount = 200L, isSettled = true, settledTransactionId = settlement))
        )

        val otherGroupId = database.groupExpenseDao().insertGroupExpense(
            GroupExpenseEntity(transactionId = settlement, shareMode = GroupExpenseShareMode.EQUAL, createdAt = 200L)
        )
        val externallySettledShareId = database.groupExpenseDao().insertShares(
            listOf(GroupExpenseShareEntity(groupExpenseId = otherGroupId, personName = "Other", amount = 100L, isSettled = true, settledTransactionId = target))
        ).single()

        val deletedSnapshot = repository.deleteTransactionWithUndoSnapshot(
            database.transactionDao().getById(target).first()!!
        )

        assertNull(database.transactionDao().getById(target).first())
        assertNull(database.groupExpenseDao().getByTransactionId(target))
        assertEquals(0, database.savingsGoalDao().getEntriesLinkedToTransaction(target).size)
        assertEquals(0, database.debtDao().getByTransactionId(target).size)
        val deletedExternalShare = database.groupExpenseDao().getShareById(externallySettledShareId)
        assertNull(deletedExternalShare?.settledTransactionId)
        assertEquals(false, deletedExternalShare?.isSettled)

        val restoredId = repository.restoreDeletedTransaction(deletedSnapshot)
        assertEquals(target, restoredId)
        assertNotNull(database.transactionDao().getById(restoredId).first())
        assertEquals(1, database.savingsGoalDao().getEntriesLinkedToTransaction(restoredId).size)

        val restoredGroup = database.groupExpenseDao().getByTransactionId(restoredId)
        assertNotNull(restoredGroup)
        assertEquals(1, restoredGroup!!.shares.size)
        assertEquals(settlement, restoredGroup.shares.single().settledTransactionId)

        val restoredExternalShare = database.groupExpenseDao().getShareById(externallySettledShareId)
        assertNotNull(restoredExternalShare)
        assertEquals(restoredId, restoredExternalShare!!.settledTransactionId)
        assertEquals(true, restoredExternalShare.isSettled)
    }

    @Test
    fun `undo restores linked fee transaction with original ids and parent reference`() = runTest {
        val accountId = database.accountDao().insert(
            AccountEntity(name = "Test", type = AccountType.CASH, initialBalance = 0, color = 0, icon = "wallet")
        )
        val transferId = database.transactionDao().insert(
            TransactionEntity(type = TransactionType.TRANSFER, amount = 10_000L, accountId = accountId, toAccountId = accountId + 1, date = 100L)
        )
        val feeId = database.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100L, accountId = accountId, date = 100L, note = "fee", transferParentId = transferId)
        )

        val snapshot = repository.deleteTransactionWithUndoSnapshot(database.transactionDao().getById(transferId).first()!!)
        assertNull(database.transactionDao().getById(transferId).first())
        assertNull(database.transactionDao().getById(feeId).first())

        val restoredTransferId = repository.restoreDeletedTransaction(snapshot)
        assertEquals(transferId, restoredTransferId)
        val restoredFee = database.transactionDao().getById(feeId).first()!!
        assertEquals(feeId, restoredFee.id)
        assertEquals(restoredTransferId, restoredFee.transferParentId)
        assertEquals(100L, restoredFee.amount)
    }

    @Test
    fun `editing linked transaction preserves sms and recurring metadata and syncs savings entry`() = runTest {
        val accountId = database.accountDao().insert(
            AccountEntity(name = "Test", type = AccountType.CASH, initialBalance = 0, color = 0, icon = "wallet")
        )
        val txId = database.transactionDao().insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = 100L,
                accountId = accountId,
                date = 100L,
                smsHash = "unique-sms-hash",
                recurringId = 77L,
                executedForDate = 88L,
                createdAt = 99L
            )
        )
        val goalId = database.savingsGoalDao().insert(SavingsGoalEntity(title = "Goal", targetAmount = 1000L))
        val entryId = database.savingsGoalDao().insertEntry(
            SavingsGoalEntryEntity(goalId = goalId, transactionId = txId, amount = 100L, type = SavingsEntryType.DEPOSIT, date = 100L)
        )

        repository.updateTransaction(
            TransactionEntity(
                id = txId,
                type = TransactionType.EXPENSE,
                amount = 250L,
                accountId = accountId,
                date = 200L,
                note = "edited"
            )
        )

        val saved = database.transactionDao().getById(txId).first()!!
        assertEquals("unique-sms-hash", saved.smsHash)
        assertEquals(77L, saved.recurringId)
        assertEquals(88L, saved.executedForDate)
        assertEquals(99L, saved.createdAt)
        val entry = database.savingsGoalDao().getEntriesLinkedToTransaction(txId).single()
        assertEquals(entryId, entry.id)
        assertEquals(250L, entry.amount)
        assertEquals(200L, entry.date)
    }

    @Test
    fun `editing linked debt amount and date follows transaction`() = runTest {
        val accountId = database.accountDao().insert(
            AccountEntity(name = "Test", type = AccountType.CASH, initialBalance = 0, color = 0, icon = "wallet")
        )
        val txId = database.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 150L, accountId = accountId, date = 300L)
        )
        val debtId = database.debtDao().insert(
            DebtEntity(personName = "Friend", type = DebtType.OWED_TO_ME, amount = 150L, transactionId = txId, createdAt = 300L)
        )
        repository.updateTransaction(
            TransactionEntity(id = txId, type = TransactionType.EXPENSE, amount = 400L, accountId = accountId, date = 450L)
        )
        val debt = database.debtDao().getById(debtId).first()!!
        assertEquals(400L, debt.amount)
        assertEquals(450L, debt.createdAt)
    }

    @Test
    fun `editing linked savings transaction to income is rejected`() = runTest {
        val accountId = database.accountDao().insert(
            AccountEntity(name = "Test", type = AccountType.CASH, initialBalance = 0, color = 0, icon = "wallet")
        )
        val txId = database.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 100L, accountId = accountId, date = 100L)
        )
        val goalId = database.savingsGoalDao().insert(SavingsGoalEntity(title = "Goal", targetAmount = 1000L))
        database.savingsGoalDao().insertEntry(
            SavingsGoalEntryEntity(goalId = goalId, transactionId = txId, amount = 100L, type = SavingsEntryType.DEPOSIT, date = 100L)
        )

        try {
            repository.updateTransaction(
                TransactionEntity(id = txId, type = TransactionType.INCOME, amount = 100L, accountId = accountId, date = 100L)
            )
            throw AssertionError("Expected invalid linked transaction type to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("نوع تراکنش مرتبط با پس‌انداز را نمی‌توان تغییر داد", expected.message)
        }
        assertEquals(TransactionType.EXPENSE, database.transactionDao().getById(txId).first()!!.type)
    }
}
