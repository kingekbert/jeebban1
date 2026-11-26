package com.example.util

import com.example.data.local.entity.ExtractedField
import com.example.data.local.entity.FieldType
import com.example.data.local.entity.SmsPatternEntity
import com.example.data.local.entity.TransactionType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PatternMatcherDateTest {

    private val gson = Gson()

    private val smsSingleDigitHour = """
        420970020082269001
        149,910,000-
        1405/9/2-3:51
        مانده:758,874,872
    """.trimIndent()

    private val smsDoubleDigitHour = """
        420970020082269001
        2,000,000-
        1405/9/3-12:37
        مانده:756,874,872
    """.trimIndent()

    @Test
    fun extractParts_producesIdenticalTokenLayoutForSingleAndDoubleDigitHours() {
        val parts1 = PatternExtractor.extractParts(smsSingleDigitHour).map { it.text }
        val parts2 = PatternExtractor.extractParts(smsDoubleDigitHour).map { it.text }

        assertEquals(
            listOf("420970020082269001", "149,910,000", "-", "1405", "9", "2", "3", "51", "مانده", "758,874,872"),
            parts1
        )
        assertEquals(
            listOf("420970020082269001", "2,000,000", "-", "1405", "9", "3", "12", "37", "مانده", "756,874,872"),
            parts2
        )
    }

    @Test
    fun match_newPattern_parsesBothUserSmsCorrectly() {
        val fields = listOf(
            ExtractedField("420970020082269001", 0, FieldType.ACCOUNT_IDENTIFIER),
            ExtractedField("2,000,000", 1, FieldType.AMOUNT),
            ExtractedField("-", 1, FieldType.SIGN),
            ExtractedField("1405", 2, FieldType.DATE_YEAR),
            ExtractedField("9", 2, FieldType.DATE_MONTH),
            ExtractedField("3", 2, FieldType.DATE_DAY),
            ExtractedField("12", 2, FieldType.TIME_HOUR),
            ExtractedField("37", 2, FieldType.TIME_MINUTE),
            ExtractedField("مانده", 3, FieldType.IGNORE),
            ExtractedField("756,874,872", 3, FieldType.BALANCE)
        )
        val pattern = SmsPatternEntity(
            id = 1L,
            accountIdentifier = "420970020082269001",
            sampleSms = smsDoubleDigitHour,
            extractedFields = gson.toJson(fields),
            bankName = "مهر ایران"
        )

        val parsed1 = PatternMatcher.match(smsSingleDigitHour, listOf(pattern))
        assertNotNull(parsed1)
        assertEquals(149_910_000L, parsed1!!.amount)
        assertEquals(TransactionType.EXPENSE, parsed1.transactionType)
        assertEquals(758_874_872L, parsed1.balance)
        val date1 = JalaliDate.fromTimestamp(parsed1.date!!)
        assertEquals(JalaliDate(1405, 9, 2), date1)
        assertEquals(Pair(3, 51), DateFormatter.extractHourAndMinute(parsed1.date!!))

        val parsed2 = PatternMatcher.match(smsDoubleDigitHour, listOf(pattern))
        assertNotNull(parsed2)
        assertEquals(2_000_000L, parsed2!!.amount)
        assertEquals(TransactionType.EXPENSE, parsed2.transactionType)
        assertEquals(756_874_872L, parsed2.balance)
        val date2 = JalaliDate.fromTimestamp(parsed2.date!!)
        assertEquals(JalaliDate(1405, 9, 3), date2)
        assertEquals(Pair(12, 37), DateFormatter.extractHourAndMinute(parsed2.date!!))
    }

    @Test
    fun match_legacyBuggySavedPatternWithTimeBeforeDate_stillParsesBothSmsAccurately() {
        // Exact legacy saved field sequence produced before the fix when learning from `1405/9/3-12:37`
        val legacyFields = listOf(
            ExtractedField("420970020082269001", 0, FieldType.ACCOUNT_IDENTIFIER),
            ExtractedField("2,000,000", 1, FieldType.AMOUNT),
            ExtractedField("-", 1, FieldType.SIGN),
            ExtractedField("12", 2, FieldType.TIME_HOUR),
            ExtractedField("37", 2, FieldType.TIME_MINUTE),
            ExtractedField("1405", 2, FieldType.DATE_YEAR),
            ExtractedField("9", 2, FieldType.DATE_MONTH),
            ExtractedField("3", 2, FieldType.DATE_DAY),
            ExtractedField("-", 2, FieldType.IGNORE),
            ExtractedField("مانده", 3, FieldType.IGNORE),
            ExtractedField("756,874,872", 3, FieldType.BALANCE)
        )
        val legacyPattern = SmsPatternEntity(
            id = 2L,
            accountIdentifier = "420970020082269001",
            sampleSms = smsDoubleDigitHour,
            extractedFields = gson.toJson(legacyFields),
            bankName = "مهر ایران"
        )

        val parsed1 = PatternMatcher.match(smsSingleDigitHour, listOf(legacyPattern))
        assertNotNull(parsed1)
        assertEquals(JalaliDate(1405, 9, 2), JalaliDate.fromTimestamp(parsed1!!.date!!))
        assertEquals(Pair(3, 51), DateFormatter.extractHourAndMinute(parsed1.date!!))

        val parsed2 = PatternMatcher.match(smsDoubleDigitHour, listOf(legacyPattern))
        assertNotNull(parsed2)
        assertEquals(JalaliDate(1405, 9, 3), JalaliDate.fromTimestamp(parsed2!!.date!!))
        assertEquals(Pair(12, 37), DateFormatter.extractHourAndMinute(parsed2.date!!))
    }

    @Test
    fun match_legacySavedPatternWithHyphenSeparatorBetweenDateAndTime_stillParsesBothSmsAccurately() {
        val legacyFields = listOf(
            ExtractedField("420970020082269001", 0, FieldType.ACCOUNT_IDENTIFIER),
            ExtractedField("149,910,000", 1, FieldType.AMOUNT),
            ExtractedField("-", 1, FieldType.SIGN),
            ExtractedField("1405", 2, FieldType.DATE_YEAR),
            ExtractedField("9", 2, FieldType.DATE_MONTH),
            ExtractedField("2", 2, FieldType.DATE_DAY),
            ExtractedField("-", 2, FieldType.IGNORE),
            ExtractedField("3", 2, FieldType.TIME_HOUR),
            ExtractedField("51", 2, FieldType.TIME_MINUTE),
            ExtractedField("مانده", 3, FieldType.IGNORE),
            ExtractedField("758,874,872", 3, FieldType.BALANCE)
        )
        val legacyPattern = SmsPatternEntity(
            id = 3L,
            accountIdentifier = "420970020082269001",
            sampleSms = smsSingleDigitHour,
            extractedFields = gson.toJson(legacyFields),
            bankName = "مهر ایران"
        )

        val parsed1 = PatternMatcher.match(smsSingleDigitHour, listOf(legacyPattern))
        assertNotNull(parsed1)
        assertEquals(JalaliDate(1405, 9, 2), JalaliDate.fromTimestamp(parsed1!!.date!!))
        assertEquals(Pair(3, 51), DateFormatter.extractHourAndMinute(parsed1.date!!))

        val parsed2 = PatternMatcher.match(smsDoubleDigitHour, listOf(legacyPattern))
        assertNotNull(parsed2)
        assertEquals(JalaliDate(1405, 9, 3), JalaliDate.fromTimestamp(parsed2!!.date!!))
        assertEquals(Pair(12, 37), DateFormatter.extractHourAndMinute(parsed2.date!!))
    }

    @Test
    fun match_persianDigitsSingleDigitDateAndHour_parsesCorrectly() {
        val persianSms = """
            420970020082269001
            ۱۴۹,۹۱۰,۰۰۰-
            ۱۴۰۵/۹/۲-۳:۵۱
            مانده:۷۵۸,۸۷۴,۸۷۲
        """.trimIndent()

        val fields = listOf(
            ExtractedField("420970020082269001", 0, FieldType.ACCOUNT_IDENTIFIER),
            ExtractedField("2,000,000", 1, FieldType.AMOUNT),
            ExtractedField("-", 1, FieldType.SIGN),
            ExtractedField("1405", 2, FieldType.DATE_YEAR),
            ExtractedField("9", 2, FieldType.DATE_MONTH),
            ExtractedField("3", 2, FieldType.DATE_DAY),
            ExtractedField("12", 2, FieldType.TIME_HOUR),
            ExtractedField("37", 2, FieldType.TIME_MINUTE),
            ExtractedField("مانده", 3, FieldType.IGNORE),
            ExtractedField("756,874,872", 3, FieldType.BALANCE)
        )
        val pattern = SmsPatternEntity(
            id = 4L,
            accountIdentifier = "420970020082269001",
            sampleSms = smsDoubleDigitHour,
            extractedFields = gson.toJson(fields),
            bankName = "مهر ایران"
        )

        val parsed = PatternMatcher.match(persianSms, listOf(pattern))
        assertNotNull(parsed)
        assertEquals(JalaliDate(1405, 9, 2), JalaliDate.fromTimestamp(parsed!!.date!!))
        assertEquals(Pair(3, 51), DateFormatter.extractHourAndMinute(parsed.date!!))
    }

    @Test
    fun match_invalidDateTokens_rejectedInsteadOfProducingBogusDate() {
        val malformedFields = listOf(
            ExtractedField("420970020082269001", 0, FieldType.ACCOUNT_IDENTIFIER),
            ExtractedField("149,910,000", 1, FieldType.AMOUNT),
            ExtractedField("-", 1, FieldType.SIGN),
            ExtractedField("x", 2, FieldType.TIME_HOUR),
            ExtractedField("y", 2, FieldType.TIME_MINUTE),
            ExtractedField("z", 2, FieldType.DATE_YEAR),
            ExtractedField("w", 2, FieldType.DATE_MONTH),
            ExtractedField("q", 2, FieldType.DATE_DAY),
            ExtractedField("مانده", 3, FieldType.IGNORE),
            ExtractedField("758,874,872", 3, FieldType.BALANCE)
        )
        val pattern = SmsPatternEntity(
            id = 5L,
            accountIdentifier = "420970020082269001",
            sampleSms = smsSingleDigitHour,
            extractedFields = gson.toJson(malformedFields),
            bankName = "مهر ایران"
        )

        // Token at index 3 is "1405" which is invalid as TIME_HOUR -> must be rejected cleanly
        val parsed = PatternMatcher.match(smsSingleDigitHour, listOf(pattern))
        assertNull(parsed)
    }
}
