package com.example.util

import com.example.data.local.entity.SmsPatternEntity
import com.example.data.local.entity.TransactionType

data class ParsedSms(
    val bankName: String? = null,
    val transactionType: TransactionType? = null,   // EXPENSE or INCOME
    val amount: Long? = null,                       // amount in Rials (integer)
    val date: Long? = null,                         // timestamp
    val accountIdentifier: String? = null,          // account number or last 4 digits of card
    val rawDescription: String? = null,             // transaction type (قبض، حقوق، برداشت و...)
    val balance: Long? = null                       // remaining balance in Rials
)

object SmsParser {

    /**
     * Parses an SMS message solely using learned patterns stored in the database.
     * If no patterns exist or no pattern matches, returns ParsedSms with null values.
     */
    fun parse(
        smsText: String,
        patterns: List<SmsPatternEntity> = emptyList()
    ): ParsedSms {
        val cleanedText = cleanSharedText(smsText)
        if (cleanedText.isBlank() || patterns.isEmpty()) {
            return ParsedSms(null, null, null, null, null, null)
        }

        val matched = PatternMatcher.match(cleanedText, patterns)
        return matched ?: ParsedSms(null, null, null, null, null, null)
    }

    fun cleanSharedText(text: String): String {
        return PatternExtractor.cleanSharedText(text)
    }
}
