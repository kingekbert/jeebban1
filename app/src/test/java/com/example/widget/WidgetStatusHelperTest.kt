package com.example.widget

import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.ParsedSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStatusHelperTest {

    private fun createDummyItem(id: String, isRegistered: Boolean): ScannedSmsItem {
        return ScannedSmsItem(
            id = id,
            parsedSms = ParsedSms(
                bankName = "ملت",
                transactionType = TransactionType.EXPENSE,
                amount = 100_000L,
                date = System.currentTimeMillis(),
                accountIdentifier = "1234",
                rawDescription = "برداشت"
            ),
            rawText = "برداشت ۱۰۰۰۰۰",
            smsHash = id,
            date = System.currentTimeMillis(),
            isRecognized = true,
            isRegistered = isRegistered
        )
    }

    @Test
    fun testFilterUnregisteredSms_mixed() {
        val items = listOf(
            createDummyItem("hash_1", isRegistered = false),
            createDummyItem("hash_2", isRegistered = true),
            createDummyItem("hash_3", isRegistered = false),
            createDummyItem("hash_4", isRegistered = true)
        )

        val filtered = WidgetStatusHelper.filterUnregisteredSms(items)

        assertEquals(2, filtered.size)
        assertEquals("hash_1", filtered[0].id)
        assertEquals("hash_3", filtered[1].id)
        assertTrue(filtered.all { !it.isRegistered })
    }

    @Test
    fun testFilterUnregisteredSms_allRegistered() {
        val items = listOf(
            createDummyItem("hash_1", isRegistered = true),
            createDummyItem("hash_2", isRegistered = true)
        )

        val filtered = WidgetStatusHelper.filterUnregisteredSms(items)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testFilterUnregisteredSms_allUnregistered() {
        val items = listOf(
            createDummyItem("hash_1", isRegistered = false),
            createDummyItem("hash_2", isRegistered = false)
        )

        val filtered = WidgetStatusHelper.filterUnregisteredSms(items)
        assertEquals(2, filtered.size)
    }

    @Test
    fun testFilterUnregisteredSms_empty() {
        val filtered = WidgetStatusHelper.filterUnregisteredSms(emptyList())
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testFormatWidgetStatusText_neverScanned() {
        val status0 = WidgetStatusHelper.formatWidgetStatusText(0L, 0)
        assertEquals("هنوز اسکن نشده", status0)

        val statusNeg = WidgetStatusHelper.formatWidgetStatusText(-1L, 5)
        assertEquals("هنوز اسکن نشده", statusNeg)
    }

    @Test
    fun testFormatWidgetStatusText_justNowWithNewItems() {
        val now = 1_700_000_000_000L
        val lastScan = now - 20_000L // 20 seconds ago (< 1 minute)

        val status = WidgetStatusHelper.formatWidgetStatusText(
            lastScanTimestamp = lastScan,
            newSmsCount = 3,
            currentTimestamp = now
        )

        assertEquals("آخرین اسکن: الان — ۳ پیامک جدید", status)
    }

    @Test
    fun testFormatWidgetStatusText_justNowNoNewItems() {
        val now = 1_700_000_000_000L
        val lastScan = now - 10_000L

        val status = WidgetStatusHelper.formatWidgetStatusText(
            lastScanTimestamp = lastScan,
            newSmsCount = 0,
            currentTimestamp = now
        )

        assertEquals("آخرین اسکن: الان — بدون پیامک جدید", status)
    }

    @Test
    fun testFormatWidgetStatusText_minutesAgo() {
        val now = 1_700_000_000_000L
        val lastScan = now - (15 * 60 * 1000L) // 15 minutes ago

        val status = WidgetStatusHelper.formatWidgetStatusText(
            lastScanTimestamp = lastScan,
            newSmsCount = 5,
            currentTimestamp = now
        )

        assertEquals("آخرین اسکن: ۱۵ دقیقه پیش — ۵ پیامک جدید", status)
    }

    @Test
    fun testFormatWidgetStatusText_hoursAgo() {
        val now = 1_700_000_000_000L
        val lastScan = now - (2 * 60 * 60 * 1000L) // 2 hours ago

        val status = WidgetStatusHelper.formatWidgetStatusText(
            lastScanTimestamp = lastScan,
            newSmsCount = 3,
            currentTimestamp = now
        )

        assertEquals("آخرین اسکن: ۲ ساعت پیش — ۳ پیامک جدید", status)
    }

    @Test
    fun testFormatWidgetStatusText_daysAgo() {
        val now = 1_700_000_000_000L
        val lastScan = now - (24 * 60 * 60 * 1000L) // 1 day ago

        val status = WidgetStatusHelper.formatWidgetStatusText(
            lastScanTimestamp = lastScan,
            newSmsCount = 0,
            currentTimestamp = now
        )

        assertEquals("آخرین اسکن: ۱ روز پیش — بدون پیامک جدید", status)
    }

    @Test
    fun testFormatScanSummary() {
        // "X پیامک جدید — Y دقیقه پیش"
        val summary1 = WidgetStatusHelper.formatScanSummary(newSmsCount = 3, elapsedMinutes = 20)
        assertEquals("۳ پیامک جدید — ۲۰ دقیقه پیش", summary1)

        val summaryJustNow = WidgetStatusHelper.formatScanSummary(newSmsCount = 1, elapsedMinutes = 0)
        assertEquals("۱ پیامک جدید — الان", summaryJustNow)

        val summaryHours = WidgetStatusHelper.formatScanSummary(newSmsCount = 4, elapsedMinutes = 120)
        assertEquals("۴ پیامک جدید — ۲ ساعت پیش", summaryHours)

        val summaryNoNew = WidgetStatusHelper.formatScanSummary(newSmsCount = 0, elapsedMinutes = 35)
        assertEquals("بدون پیامک جدید — ۳۵ دقیقه پیش", summaryNoNew)
    }
}
