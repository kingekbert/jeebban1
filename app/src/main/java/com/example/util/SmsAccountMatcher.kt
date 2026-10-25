package com.example.util

import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountSmsSenderEntity
import com.example.data.local.entity.SmsPatternEntity
import com.example.data.repository.AccountSmsSenderRepository

object SmsAccountMatcher {

    /**
     * Finds the AccountEntity linked to a given SMS sender address.
     */
    fun findAccountForSender(
        smsSender: String,
        allAccounts: List<AccountEntity>,
        allSenders: List<AccountSmsSenderEntity>
    ): AccountEntity? {
        if (smsSender.isBlank() || allSenders.isEmpty()) return null

        val matchedSender = allSenders.firstOrNull { senderEntity ->
            AccountSmsSenderRepository.matchesSender(smsSender, senderEntity.address)
        } ?: return null

        return allAccounts.firstOrNull { it.id == matchedSender.accountId }
    }

    /**
     * Determines whether an SmsPatternEntity belongs to a specific AccountEntity.
     */
    fun isPatternForAccount(pattern: SmsPatternEntity, account: AccountEntity): Boolean {
        val ident = pattern.accountIdentifier.trim()
        val bank = pattern.bankName?.trim() ?: ""

        val card = account.cardNumber?.trim()
        val shaba = account.shabaNumber?.trim()
        val accName = account.name.trim()

        if (card != null && card.isNotBlank()) {
            if (ident.isNotBlank() && (card.contains(ident) || ident.contains(card.takeLast(4)))) return true
        }
        if (shaba != null && shaba.isNotBlank()) {
            if (ident.isNotBlank() && shaba.contains(ident)) return true
        }
        if (accName.isNotBlank()) {
            if (ident.isNotBlank() && (accName.contains(ident) || ident.contains(accName))) return true
            if (bank.isNotBlank() && (accName.contains(bank) || bank.contains(accName))) return true
        }

        return false
    }

    /**
     * Parses an SMS text with sender priority.
     * 1. If the SMS sender is bound to Account A, first attempt parsing using patterns
     *    associated with Account A for maximum accuracy.
     * 2. If Account A's patterns do not match (or no Account A pattern exists), fallback to
     *    all other patterns (protecting against user errors in sender registration).
     * 3. If the SMS sender is not registered to any account, parses using all active patterns.
     */
    fun parseSmsWithSenderPriority(
        body: String,
        smsSender: String,
        allAccounts: List<AccountEntity>,
        allSenders: List<AccountSmsSenderEntity>,
        patterns: List<SmsPatternEntity>
    ): Pair<ParsedSms, AccountEntity?> {
        val matchedAccount = findAccountForSender(smsSender, allAccounts, allSenders)

        if (matchedAccount != null) {
            val accountPatterns = patterns.filter { isPatternForAccount(it, matchedAccount) }
            val otherPatterns = patterns.filterNot { isPatternForAccount(it, matchedAccount) }

            // 1. Try account's own patterns first
            if (accountPatterns.isNotEmpty()) {
                val parsed = SmsParser.parse(body, accountPatterns)
                if (parsed.amount != null && parsed.amount > 0L && parsed.transactionType != null) {
                    return Pair(parsed, matchedAccount)
                }
            }

            // 2. Fallback to other patterns if account patterns didn't match
            if (otherPatterns.isNotEmpty()) {
                val parsedFallback = SmsParser.parse(body, otherPatterns)
                if (parsedFallback.amount != null && parsedFallback.amount > 0L && parsedFallback.transactionType != null) {
                    return Pair(parsedFallback, matchedAccount)
                }
            }
        }

        // 3. Sender is not bound or no account patterns matched
        val parsedDefault = SmsParser.parse(body, patterns)
        return Pair(parsedDefault, matchedAccount)
    }

    /**
     * Builds SQLite query selection and arguments for querying SMS inbox.
     * If all accounts have registered senders, restricts query to `address IN (...)` for faster scans.
     * If any account has no registered senders, scans the full inbox for the given time range.
     */
    fun buildSmsQuerySelection(
        startTime: Long,
        allAccounts: List<AccountEntity>,
        allSenders: List<AccountSmsSenderEntity>
    ): Pair<String, Array<String>> {
        val hasAccounts = allAccounts.isNotEmpty()
        val accountsWithoutSenders = allAccounts.filter { acc ->
            allSenders.none { it.accountId == acc.id }
        }

        val allRegisteredAddresses = allSenders.map { it.address.trim() }.filter { it.isNotBlank() }.distinct()

        if (hasAccounts && accountsWithoutSenders.isEmpty() && allRegisteredAddresses.isNotEmpty()) {
            val placeholders = allRegisteredAddresses.joinToString(",") { "?" }
            val selection = "date >= ? AND address IN ($placeholders)"
            val args = arrayOf(startTime.toString()) + allRegisteredAddresses.toTypedArray()
            return Pair(selection, args)
        }

        return Pair("date >= ?", arrayOf(startTime.toString()))
    }
}
