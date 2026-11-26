package com.example.ui.screens.scan_sms

import com.example.data.local.entity.TransactionType
import com.example.util.DetectedTransfer
import com.example.util.ParsedSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSmsOrderingTest {

    private fun item(id: String, date: Long, isRegistered: Boolean): ScannedSmsItem {
        return ScannedSmsItem(
            id = id,
            parsedSms = ParsedSms(
                bankName = "مهر ایران",
                transactionType = TransactionType.EXPENSE,
                amount = 100_000L,
                date = date
            ),
            rawText = "test $id",
            smsHash = id,
            date = date,
            isRegistered = isRegistered
        )
    }

    @Test
    fun sortScannedItems_placesUnregisteredFirstAndPreservesNewestFirstWithinEachGroup() {
        val input = listOf(
            item("reg_newest", date = 5000L, isRegistered = true),
            item("unreg_older", date = 2000L, isRegistered = false),
            item("reg_middle", date = 4000L, isRegistered = true),
            item("unreg_newest", date = 3000L, isRegistered = false),
            item("reg_oldest", date = 1000L, isRegistered = true)
        )

        val sorted = ScanSmsViewModel.sortScannedItems(input)

        assertEquals(
            listOf("unreg_newest", "unreg_older", "reg_newest", "reg_middle", "reg_oldest"),
            sorted.map { it.id }
        )
        assertFalse(sorted[0].isRegistered)
        assertFalse(sorted[1].isRegistered)
        assertTrue(sorted[2].isRegistered)
        assertTrue(sorted[3].isRegistered)
        assertTrue(sorted[4].isRegistered)
    }

    @Test
    fun sortDetectedTransfers_placesUnregisteredTransfersFirst() {
        val tReg = DetectedTransfer(
            id = "t_reg",
            withdrawalItem = item("w1", 9000L, true),
            depositItem = item("d1", 9001L, true),
            sourceAccount = null,
            destAccount = null,
            transferAmount = 500_000L,
            feeAmount = 0L,
            isRegistered = true
        )
        val tUnregOld = DetectedTransfer(
            id = "t_unreg_old",
            withdrawalItem = item("w2", 3000L, false),
            depositItem = item("d2", 3001L, false),
            sourceAccount = null,
            destAccount = null,
            transferAmount = 500_000L,
            feeAmount = 0L,
            isRegistered = false
        )
        val tUnregNew = DetectedTransfer(
            id = "t_unreg_new",
            withdrawalItem = item("w3", 6000L, false),
            depositItem = item("d3", 6001L, false),
            sourceAccount = null,
            destAccount = null,
            transferAmount = 500_000L,
            feeAmount = 0L,
            isRegistered = false
        )

        val sorted = ScanSmsViewModel.sortDetectedTransfers(listOf(tReg, tUnregOld, tUnregNew))
        assertEquals(listOf("t_unreg_new", "t_unreg_old", "t_reg"), sorted.map { it.id })
    }
}
