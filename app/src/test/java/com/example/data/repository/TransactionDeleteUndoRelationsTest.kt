package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
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
        assertNull(database.groupExpenseDao().getShareById(externallySettledShareId)?.settledTransactionId)

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
    }
}
