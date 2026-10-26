package com.example.util

import com.example.data.local.entity.AccountBalanceSnapshotEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceReconciliationTest {

    private val testAccount = AccountEntity(
        id = 10L,
        name = "بانک سامان",
        type = AccountType.BANK,
        initialBalance = 1_000_000L,
        color = 0,
        icon = "account_balance"
    )

    private fun createDummySmsItem(id: String, amount: Long, type: TransactionType): ScannedSmsItem {
        return ScannedSmsItem(
            id = id,
            parsedSms = ParsedSms(
                amount = amount,
                transactionType = type,
                date = System.currentTimeMillis(),
                bankName = "سامان",
                accountIdentifier = "1234",
                rawDescription = "برداشت"
            ),
            rawText = "برداشت $amount ریال",
            smsHash = id,
            date = System.currentTimeMillis()
        )
    }

    @Test
    fun testCalculateAppBalanceAndDiff() {
        val initialBalance = 1_000_000L
        val accountId = 10L

        val transactions = listOf(
            TransactionEntity(
                id = 1L,
                amount = 500_000L,
                type = TransactionType.INCOME,
                date = 1000L,
                categoryId = 1L,
                accountId = accountId
            ),
            TransactionEntity(
                id = 2L,
                amount = 200_000L,
                type = TransactionType.EXPENSE,
                date = 2000L,
                categoryId = 2L,
                accountId = accountId
            ),
            TransactionEntity(
                id = 3L,
                amount = 100_000L,
                type = TransactionType.TRANSFER,
                date = 3000L,
                categoryId = 3L,
                accountId = accountId,
                toAccountId = 20L
            ),
            TransactionEntity(
                id = 4L,
                amount = 300_000L,
                type = TransactionType.TRANSFER,
                date = 4000L,
                categoryId = 3L,
                accountId = 20L,
                toAccountId = accountId
            )
        )

        // Expected: 1,000,000 + 500,000 - 200,000 - 100,000 + 300,000 = 1,500,000
        val appBalance = BalanceReconciliationEngine.calculateAppBalance(initialBalance, accountId, transactions)
        assertEquals(1_500_000L, appBalance)

        // When bank balance is same as app balance: diff = 0
        assertEquals(0L, BalanceReconciliationEngine.calculateDiff(1_500_000L, appBalance))

        // When bank balance is higher by 300,000: diff = +300,000
        assertEquals(300_000L, BalanceReconciliationEngine.calculateDiff(1_800_000L, appBalance))

        // When bank balance is lower by 200,000: diff = -200,000
        assertEquals(-200_000L, BalanceReconciliationEngine.calculateDiff(1_300_000L, appBalance))
    }

    @Test
    fun testSubsetSumSingleCandidateMatch() {
        // App balance is 1,500,000, Bank balance is 1,000,000 -> diff = -500,000 (missing expense)
        val targetDiff = -500_000L
        val candidates = listOf(
            createDummySmsItem("sms1", 100_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms2", 500_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms3", 300_000L, TransactionType.EXPENSE)
        )

        val result = BalanceReconciliationEngine.findSubsetSumCandidates(candidates, targetDiff)
        assertNotNull(result)
        assertEquals(1, result?.size)
        assertEquals("sms2", result?.first()?.id)
    }

    @Test
    fun testSubsetSumTwoCandidatesMatch() {
        // diff = -750,000. Combination: 500,000 + 250,000
        val targetDiff = -750_000L
        val candidates = listOf(
            createDummySmsItem("sms1", 500_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms2", 900_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms3", 250_000L, TransactionType.EXPENSE)
        )

        val result = BalanceReconciliationEngine.findSubsetSumCandidates(candidates, targetDiff)
        assertNotNull(result)
        assertEquals(2, result?.size)
        val ids = result?.map { it.id }?.toSet()
        assertTrue(ids?.contains("sms1") == true)
        assertTrue(ids?.contains("sms3") == true)
    }

    @Test
    fun testSubsetSumThreeCandidatesMatch() {
        // diff = -600,000. Combination: 100,000 + 200,000 + 300,000
        val targetDiff = -600_000L
        val candidates = listOf(
            createDummySmsItem("sms1", 100_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms2", 800_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms3", 200_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms4", 300_000L, TransactionType.EXPENSE)
        )

        val result = BalanceReconciliationEngine.findSubsetSumCandidates(candidates, targetDiff)
        assertNotNull(result)
        assertEquals(3, result?.size)
        val ids = result?.map { it.id }?.toSet()
        assertTrue(ids?.contains("sms1") == true)
        assertTrue(ids?.contains("sms3") == true)
        assertTrue(ids?.contains("sms4") == true)
    }

    @Test
    fun testSubsetSumNoCandidateMatch() {
        // diff = -999,000. No combination should sum up to this
        val targetDiff = -999_000L
        val candidates = listOf(
            createDummySmsItem("sms1", 100_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms2", 200_000L, TransactionType.EXPENSE),
            createDummySmsItem("sms3", 300_000L, TransactionType.EXPENSE)
        )

        val result = BalanceReconciliationEngine.findSubsetSumCandidates(candidates, targetDiff)
        assertNull(result)
    }

    @Test
    fun testBankFeeDetection() {
        // Small diff <= 100,000 Rials is detected as bank fee
        assertTrue(BalanceReconciliationEngine.isBankFee(diff = -50_000L, bankBalance = 5_000_000L))
        assertTrue(BalanceReconciliationEngine.isBankFee(diff = -100_000L, bankBalance = 5_000_000L))

        // Diff within 2% of bank balance: 150,000 on 10,000,000 (1.5%) -> true
        assertTrue(BalanceReconciliationEngine.isBankFee(diff = -150_000L, bankBalance = 10_000_000L))

        // Diff exceeding 2% and exceeding 100,000: 500,000 on 1,000,000 (50%) -> false
        assertFalse(BalanceReconciliationEngine.isBankFee(diff = -500_000L, bankBalance = 1_000_000L))

        // Positive diff is never a bank fee (fee is always a reduction)
        assertFalse(BalanceReconciliationEngine.isBankFee(diff = 50_000L, bankBalance = 5_000_000L))
        assertFalse(BalanceReconciliationEngine.isBankFee(diff = 0L, bankBalance = 5_000_000L))
    }

    @Test
    fun testConstantDiffFromEarliest() {
        // Identical non-zero diff across all snapshots in history
        val constantHistory = listOf(-2_000_000L, -2_000_000L, -2_000_000L)
        assertTrue(BalanceReconciliationEngine.isConstantDiffFromEarliest(constantHistory))

        // Non-constant diff
        val changingHistory = listOf(-1_000_000L, -2_000_000L, -2_000_000L)
        assertFalse(BalanceReconciliationEngine.isConstantDiffFromEarliest(changingHistory))

        // Started with 0 diff (was synced), then diverged later -> not initial balance error
        val divergedHistory = listOf(0L, -2_000_000L)
        assertFalse(BalanceReconciliationEngine.isConstantDiffFromEarliest(divergedHistory))

        // Empty history
        assertFalse(BalanceReconciliationEngine.isConstantDiffFromEarliest(emptyList()))
    }

    @Test
    fun testDiagnoseCausePriority() {
        val candidates = listOf(
            createDummySmsItem("sms1", 450_000L, TransactionType.EXPENSE)
        )

        // Case 1: Candidate match takes highest priority
        val cause1 = BalanceReconciliationEngine.diagnoseCause(
            diff = -450_000L,
            bankBalance = 5_000_000L,
            currentInitialBalance = 1_000_000L,
            candidates = candidates,
            historyDiffsChronological = listOf(-450_000L, -450_000L)
        )
        assertTrue(cause1 is ReconciliationCause.UnregisteredTransactions)

        // Case 2: Bank fee priority when no candidate matches and diff is small
        val cause2 = BalanceReconciliationEngine.diagnoseCause(
            diff = -50_000L,
            bankBalance = 5_000_000L,
            currentInitialBalance = 1_000_000L,
            candidates = emptyList(),
            historyDiffsChronological = listOf(-50_000L, -50_000L)
        )
        assertTrue(cause2 is ReconciliationCause.BankFee)
        assertEquals(50_000L, (cause2 as ReconciliationCause.BankFee).feeAmount)

        // Case 3: Wrong initial balance priority when not fee, but constant diff from earliest snapshot
        val cause3 = BalanceReconciliationEngine.diagnoseCause(
            diff = -5_000_000L,
            bankBalance = 2_000_000L,
            currentInitialBalance = 7_000_000L,
            candidates = emptyList(),
            historyDiffsChronological = listOf(-5_000_000L, -5_000_000L)
        )
        assertTrue(cause3 is ReconciliationCause.WrongInitialBalance)
        val wrongInitial = cause3 as ReconciliationCause.WrongInitialBalance
        assertEquals(-5_000_000L, wrongInitial.diff)
        assertEquals(7_000_000L, wrongInitial.currentInitialBalance)
        assertEquals(2_000_000L, wrongInitial.proposedInitialBalance)

        // Case 4: Manual adjustment fallback
        val cause4 = BalanceReconciliationEngine.diagnoseCause(
            diff = -5_000_000L,
            bankBalance = 2_000_000L,
            currentInitialBalance = 7_000_000L,
            candidates = emptyList(),
            historyDiffsChronological = listOf(0L, -5_000_000L) // Diverged later, so not constant from earliest
        )
        assertTrue(cause4 is ReconciliationCause.ManualAdjustment)
        assertEquals(-5_000_000L, (cause4 as ReconciliationCause.ManualAdjustment).diff)

        // Case 5: When diff is 0, cause is null
        val causeZero = BalanceReconciliationEngine.diagnoseCause(
            diff = 0L,
            bankBalance = 5_000_000L,
            currentInitialBalance = 5_000_000L,
            candidates = emptyList(),
            historyDiffsChronological = listOf(0L)
        )
        assertNull(causeZero)
    }
}
