package com.example.util

import com.example.data.local.entity.TransactionType
import java.security.MessageDigest

object SmsHashUtil {

    private const val COMPOSITE_DELIMITER = "|"

    /**
     * Computes a canonical SHA-256 hash for a bank SMS transaction.
     * Includes transactionType (EXPENSE/INCOME/TRANSFER) and normalized account identifier
     * alongside sender, amount, timestamp, and normalized body so same-amount deposits and
     * withdrawals on the same account/day never collide.
     */
    fun calculateHash(
        sender: String?,
        amount: Long?,
        date: Long,
        text: String,
        transactionType: TransactionType? = null,
        accountIdentifier: String? = null
    ): String {
        if (transactionType == null && accountIdentifier.isNullOrBlank()) {
            return calculateLegacyV1Hash(sender, amount, date, text)
        }
        val normalizedType = transactionType?.name ?: ""
        val normalizedAccount = normalizeAccountIdentifier(accountIdentifier)
        val normalizedSender = (sender ?: "").trim().lowercase()
        val normalizedAmount = amount?.toString() ?: "0"
        val normalizedText = normalizeText(text)
        val raw = "v2|$normalizedType|$normalizedAccount|$normalizedSender|$normalizedAmount|$date|$normalizedText"
        return sha256Hex(raw)
    }

    /**
     * Previous SHA-256 hash format (without transactionType/accountIdentifier),
     * preserved for backward-compatible matching of already-persisted records.
     */
    fun calculateLegacyV1Hash(sender: String?, amount: Long?, date: Long, text: String): String {
        val normalizedSender = (sender ?: "").trim()
        val normalizedAmount = amount?.toString() ?: "0"
        val normalizedText = normalizeText(text)
        val raw = "$normalizedSender|$normalizedAmount|$date|$normalizedText"
        return sha256Hex(raw)
    }

    fun calculateLegacySimpleHash(amount: Long?, date: Long, accountIdentifier: String?): String {
        val normalizedAmount = amount?.toString() ?: "0"
        val normalizedIdent = (accountIdentifier ?: "").trim()
        return "${normalizedAmount}_${date}_${normalizedIdent}"
    }

    fun isFeeHash(smsHash: String?): Boolean {
        if (smsHash.isNullOrBlank()) return false
        val trimmed = smsHash.trim()
        return trimmed.endsWith("_fee") || trimmed.startsWith("fee:")
    }

    /**
     * Extracts exact hash tokens from a stored or incoming smsHash.
     * Supports single hashes ("h1") and composite two-SMS transfer hashes ("h1|h2").
     * Fee hashes are excluded so a fee record never interferes with primary SMS deduplication.
     */
    fun extractHashTokens(smsHash: String?): Set<String> {
        if (smsHash.isNullOrBlank() || isFeeHash(smsHash)) return emptySet()
        val trimmed = smsHash.trim()
        val result = linkedSetOf(trimmed)
        if (trimmed.contains(COMPOSITE_DELIMITER)) {
            trimmed.split(COMPOSITE_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotBlank() && !isFeeHash(it) }
                .forEach { result.add(it) }
        }
        return result
    }

    /**
     * Checks whether an incoming smsHash (single or composite) conflicts with any already-stored
     * non-fee smsHash in the database using exact token comparison.
     */
    fun hasHashConflict(incomingSmsHash: String?, storedSmsHashes: Collection<String>): Boolean {
        val incomingTokens = extractHashTokens(incomingSmsHash)
        if (incomingTokens.isEmpty()) return false
        for (stored in storedSmsHashes) {
            val storedTokens = extractHashTokens(stored)
            if (storedTokens.isNotEmpty() && incomingTokens.any { it in storedTokens }) {
                return true
            }
        }
        return false
    }

    private fun normalizeAccountIdentifier(accountIdentifier: String?): String {
        if (accountIdentifier.isNullOrBlank()) return ""
        val digits = TransferDetector.sanitizeDigits(accountIdentifier)
        return if (digits.isNotBlank()) digits else accountIdentifier.trim().lowercase()
    }

    private fun normalizeText(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun sha256Hex(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
