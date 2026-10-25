package com.example.util

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountSmsSenderEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.FieldType
import com.example.data.local.entity.SmsPatternEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.AccountSmsSenderRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSmsSenderMatchingTest {

    private val accountMelli = AccountEntity(
        id = 1L,
        name = "بانک ملی",
        type = AccountType.BANK,
        initialBalance = 10_000_000L,
        color = 0,
        icon = "account_balance",
        cardNumber = "6037991234504007",
        shabaNumber = "IR120170000000000000004007"
    )

    private val accountMehr = AccountEntity(
        id = 2L,
        name = "قرض‌الحسنه مهر ایران",
        type = AccountType.BANK,
        initialBalance = 5_000_000L,
        color = 0,
        icon = "account_balance",
        cardNumber = "6063731122334455",
        shabaNumber = "IR8206000000420970020082269001"
    )

    private val allAccounts = listOf(accountMelli, accountMehr)

    private val senders = listOf(
        AccountSmsSenderEntity(id = 1L, accountId = 1L, address = "V.MELLI"),
        AccountSmsSenderEntity(id = 2L, accountId = 1L, address = "+98700717"),
        AccountSmsSenderEntity(id = 3L, accountId = 2L, address = "B.QMEHRIRAN")
    )

    @Test
    fun testFindAccountForSenderDirect() {
        val acc1 = SmsAccountMatcher.findAccountForSender("V.MELLI", allAccounts, senders)
        assertNotNull(acc1)
        assertEquals(1L, acc1?.id)

        val acc2 = SmsAccountMatcher.findAccountForSender("b.qmehriran", allAccounts, senders)
        assertNotNull(acc2)
        assertEquals(2L, acc2?.id)

        val unknown = SmsAccountMatcher.findAccountForSender("UNKNOWN_BANK", allAccounts, senders)
        assertNull(unknown)
    }

    @Test
    fun testPhoneNumberPrefixMatching() {
        // Test matching with +98 vs 0098 vs 0 prefix
        assertTrue(AccountSmsSenderRepository.matchesSender("+98700717", "+98700717"))
        assertTrue(AccountSmsSenderRepository.matchesSender("0700717", "+98700717"))
        assertTrue(AccountSmsSenderRepository.matchesSender("98700717", "0700717"))
        assertTrue(AccountSmsSenderRepository.matchesSender("  B.QMEHRIRAN  ", "b.qmehriran"))
    }

    @Test
    fun testQuerySelectionWhenAllAccountsHaveSenders() {
        // Both accounts have senders
        val (selection, args) = SmsAccountMatcher.buildSmsQuerySelection(1000L, allAccounts, senders)
        assertTrue(selection.contains("address IN"))
        assertEquals("1000", args[0])
        assertTrue(args.contains("V.MELLI"))
        assertTrue(args.contains("+98700717"))
        assertTrue(args.contains("B.QMEHRIRAN"))
    }

    @Test
    fun testQuerySelectionFallbackWhenSomeAccountsHaveNoSenders() {
        // Account 3 has no senders registered
        val accountSepah = AccountEntity(
            id = 3L,
            name = "بانک سپه",
            type = AccountType.BANK,
            initialBalance = 1_000_000L,
            color = 0,
            icon = "account_balance"
        )
        val mixedAccounts = allAccounts + accountSepah

        val (selection, args) = SmsAccountMatcher.buildSmsQuerySelection(1000L, mixedAccounts, senders)
        // Must fallback to scanning entire inbox for the timeframe
        assertEquals("date >= ?", selection)
        assertEquals(1, args.size)
        assertEquals("1000", args[0])
    }

    @Test
    fun testQuerySelectionFallbackWhenNoAccountsHaveSenders() {
        val (selection, args) = SmsAccountMatcher.buildSmsQuerySelection(1000L, allAccounts, emptyList())
        assertEquals("date >= ?", selection)
        assertEquals(1, args.size)
        assertEquals("1000", args[0])
    }

    @Test
    fun testSenderMatchingPrioritizesAccountPatterns() {
        val sampleMelli = "بانك ملي\nبرداشت:500000\nحساب:4007\n1403 07 27"
        val tokensMelli = PatternExtractor.extractParts(sampleMelli).map { token ->
            when (token.text) {
                "برداشت" -> token.copy(fieldType = FieldType.TRANSACTION_TYPE)
                "500000" -> token.copy(fieldType = FieldType.AMOUNT)
                "4007" -> token.copy(fieldType = FieldType.ACCOUNT_IDENTIFIER)
                "1403" -> token.copy(fieldType = FieldType.DATE_YEAR)
                "07" -> token.copy(fieldType = FieldType.DATE_MONTH)
                "27" -> token.copy(fieldType = FieldType.DATE_DAY)
                else -> token
            }
        }
        val patternMelli = SmsPatternEntity(
            id = 10L,
            accountIdentifier = "4007",
            sampleSms = sampleMelli,
            extractedFields = com.google.gson.Gson().toJson(tokensMelli),
            bankName = "بانک ملی",
            isActive = true
        )

        val sampleMehr = "بانك مهر\nبرداشت:500000\nحساب:4455\n1403 07 27"
        val tokensMehr = PatternExtractor.extractParts(sampleMehr).map { token ->
            when (token.text) {
                "برداشت" -> token.copy(fieldType = FieldType.TRANSACTION_TYPE)
                "500000" -> token.copy(fieldType = FieldType.AMOUNT)
                "4455" -> token.copy(fieldType = FieldType.ACCOUNT_IDENTIFIER)
                "1403" -> token.copy(fieldType = FieldType.DATE_YEAR)
                "07" -> token.copy(fieldType = FieldType.DATE_MONTH)
                "27" -> token.copy(fieldType = FieldType.DATE_DAY)
                else -> token
            }
        }
        val patternMehr = SmsPatternEntity(
            id = 20L,
            accountIdentifier = "4455",
            sampleSms = sampleMehr,
            extractedFields = com.google.gson.Gson().toJson(tokensMehr),
            bankName = "قرض‌الحسنه مهر ایران",
            isActive = true
        )

        val patterns = listOf(patternMelli, patternMehr)

        val isMelliPattern = SmsAccountMatcher.isPatternForAccount(patternMelli, accountMelli)
        val isMehrPattern = SmsAccountMatcher.isPatternForAccount(patternMehr, accountMehr)
        assertTrue(isMelliPattern)
        assertTrue(isMehrPattern)

        // Parse SMS coming from B.QMEHRIRAN (Account 2 sender)
        val (parsed, matchedAcc) = SmsAccountMatcher.parseSmsWithSenderPriority(
            body = sampleMehr,
            smsSender = "B.QMEHRIRAN",
            allAccounts = allAccounts,
            allSenders = senders,
            patterns = patterns
        )

        assertNotNull(matchedAcc)
        assertEquals(2L, matchedAcc?.id)
        assertEquals(500000L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.transactionType)
    }

    @Test
    fun testSenderMatchingFallbackToOtherPatternsWhenAccountPatternDoesNotMatch() {
        val sampleMelli = "بانك ملي\nبرداشت:250000\nحساب:4007\n1403 07 27"
        val tokensMelli = PatternExtractor.extractParts(sampleMelli).map { token ->
            when (token.text) {
                "برداشت" -> token.copy(fieldType = FieldType.TRANSACTION_TYPE)
                "250000" -> token.copy(fieldType = FieldType.AMOUNT)
                "4007" -> token.copy(fieldType = FieldType.ACCOUNT_IDENTIFIER)
                "1403" -> token.copy(fieldType = FieldType.DATE_YEAR)
                "07" -> token.copy(fieldType = FieldType.DATE_MONTH)
                "27" -> token.copy(fieldType = FieldType.DATE_DAY)
                else -> token
            }
        }
        val patternMelli = SmsPatternEntity(
            id = 10L,
            accountIdentifier = "4007",
            sampleSms = sampleMelli,
            extractedFields = com.google.gson.Gson().toJson(tokensMelli),
            bankName = "بانک ملی",
            isActive = true
        )

        val (parsed, matchedAcc) = SmsAccountMatcher.parseSmsWithSenderPriority(
            body = sampleMelli,
            smsSender = "B.QMEHRIRAN",
            allAccounts = allAccounts,
            allSenders = senders,
            patterns = listOf(patternMelli)
        )

        // Result accepted despite sender being B.QMEHRIRAN
        assertEquals(250000L, parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.transactionType)
        assertEquals("4007", parsed.accountIdentifier)
    }
}
