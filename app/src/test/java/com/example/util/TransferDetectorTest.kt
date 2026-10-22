package com.example.util

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDetectorTest {

    private val melliAccount = AccountEntity(
        id = 1L,
        name = "بانک ملی",
        type = AccountType.BANK,
        initialBalance = 100_000_000L,
        color = 0,
        icon = "account_balance",
        cardNumber = "6037991234504007",
        shabaNumber = "IR120170000000000000004007"
    )

    private val mehrAccount = AccountEntity(
        id = 2L,
        name = "بانک مهر ایران",
        type = AccountType.BANK,
        initialBalance = 50_000_000L,
        color = 0,
        icon = "account_balance",
        cardNumber = "6063731122334455",
        shabaNumber = "IR8206000000420970020082269001"
    )

    @Test
    fun testPayaTransferWithExactAmountAndLongTimeDifference() {
        // User scenario 2:
        // 600,000,000 Rials transfer from Melli at 12:34
        // 600,000,000 Rials deposit at Mehr at 19:06 (6 hours and 32 minutes later)
        val withdrawalDate = 1729933440000L // 12:34
        val depositDate = withdrawalDate + (6 * 3600 + 32 * 60) * 1000L // 19:06 (6h 32m later)

        val withdrawalSms = ScannedSmsItem(
            id = "w1",
            parsedSms = ParsedSms(
                bankName = "بانك ملي ايران",
                transactionType = TransactionType.EXPENSE,
                amount = 600_000_000L,
                date = withdrawalDate,
                accountIdentifier = "04007",
                rawDescription = "پایا"
            ),
            rawText = "بانك ملي ايران\nپايا:600,000,000-\nحساب:04007\nمانده:64,075,403\n0727-12:34",
            smsHash = "hash_w1",
            date = withdrawalDate
        )

        val depositSms = ScannedSmsItem(
            id = "d1",
            parsedSms = ParsedSms(
                bankName = null,
                transactionType = TransactionType.INCOME,
                amount = 600_000_000L,
                date = depositDate,
                accountIdentifier = "\u202A420970020082269001\u202C", // with unicode bidirectional marks
                rawDescription = "واریز"
            ),
            rawText = "\u202A420970020082269001\u202C\n600,000,000+\n1405/7/27-19:06\nمانده:950,429,422",
            smsHash = "hash_d1",
            date = depositDate
        )

        val accounts = listOf(melliAccount, mehrAccount)
        val transfers = TransferDetector.findTransfers(listOf(withdrawalSms, depositSms), accounts)

        assertEquals(1, transfers.size)
        val transfer = transfers.first()
        assertEquals(600_000_000L, transfer.transferAmount)
        assertEquals(0L, transfer.feeAmount)
        assertNotNull(transfer.sourceAccount)
        assertEquals(1L, transfer.sourceAccount?.id)
        assertNotNull(transfer.destAccount)
        assertEquals(2L, transfer.destAccount?.id)
    }

    @Test
    fun testTransferWithFeeDeducted() {
        // User scenario 1:
        // 20,000,450 Rials withdrawal from Melli at 22:54
        // 20,000,000 Rials deposit at Mehr at 23:06 (12 minutes later, 450 Rials bank fee)
        val withdrawalDate = 1729625040000L
        val depositDate = withdrawalDate + 12 * 60 * 1000L

        val withdrawalSms = ScannedSmsItem(
            id = "w2",
            parsedSms = ParsedSms(
                bankName = "بانك ملي ايران",
                transactionType = TransactionType.EXPENSE,
                amount = 20_000_450L,
                date = withdrawalDate,
                accountIdentifier = "04007",
                rawDescription = "انتقال"
            ),
            rawText = "بانك ملي ايران\nانتقال:20,000,450-\nحساب:04007\nمانده:664,075,403\n0722-22:54",
            smsHash = "hash_w2",
            date = withdrawalDate
        )

        val depositSms = ScannedSmsItem(
            id = "d2",
            parsedSms = ParsedSms(
                bankName = null,
                transactionType = TransactionType.INCOME,
                amount = 20_000_000L,
                date = depositDate,
                accountIdentifier = "\u202A420970020082269001\u202C",
                rawDescription = "واریز"
            ),
            rawText = "\u202A420970020082269001\u202C\n20,000,000+\n1405/7/22-23:06\nمانده:950,429,422",
            smsHash = "hash_d2",
            date = depositDate
        )

        val accounts = listOf(melliAccount, mehrAccount)
        val transfers = TransferDetector.findTransfers(listOf(withdrawalSms, depositSms), accounts)

        assertEquals(1, transfers.size)
        val transfer = transfers.first()
        assertEquals(20_000_000L, transfer.transferAmount)
        assertEquals(450L, transfer.feeAmount)
        assertEquals(1L, transfer.sourceAccount?.id)
        assertEquals(2L, transfer.destAccount?.id)
    }

    @Test
    fun testDigitSanitization() {
        val persianRaw = "‪۴۲۰۹۷۰۰۲۰۰۸۲۲۶۹۰۰۱‬"
        val sanitized = TransferDetector.sanitizeDigits(persianRaw)
        assertEquals("420970020082269001", sanitized)
    }
}
