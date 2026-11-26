package com.example.ui.screens.scan_sms

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AccountRepository
import com.example.data.repository.SmsPatternRepository
import com.example.data.repository.TransactionRepository
import com.example.util.JalaliDate
import com.example.util.ParsedSms
import com.example.util.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannedSmsItem(
    val id: String,
    val parsedSms: ParsedSms,
    val rawText: String,
    val smsHash: String,
    val date: Long,
    val isRecognized: Boolean = true,
    val isRegistered: Boolean = false,
    val sender: String = "",
    val balanceDiff: Long? = null,
    val matchedAccountId: Long? = null
)

data class ScanSmsUiState(
    val selectedDays: Int = 30,
    val isLoading: Boolean = false,
    val scannedList: List<ScannedSmsItem> = emptyList(),
    val detectedTransfers: List<com.example.util.DetectedTransfer> = emptyList(),
    val hasPermission: Boolean = false,
    val isScanCompleted: Boolean = false,
    val errorMessage: String? = null
)

class ScanSmsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val smsPatternRepository: SmsPatternRepository,
    private val accountSmsSenderRepository: com.example.data.repository.AccountSmsSenderRepository? = null,
    private val reconciliationRepository: com.example.data.repository.ReconciliationRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanSmsUiState())
    val uiState: StateFlow<ScanSmsUiState> = _uiState.asStateFlow()

    fun updatePermissionState(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
    }

    fun setSelectedDays(days: Int) {
        _uiState.update { it.copy(selectedDays = days) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun scanSms(context: Context) {
        val days = _uiState.value.selectedDays
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val existingTransactions = transactionRepository.getAllTransactionsWithDetailsList()
                    val allAccounts = accountRepository.getAllAccountsList()
                    val allSenders = try {
                        accountSmsSenderRepository?.getAllSendersList() ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val accountMap = allAccounts.associateBy { it.id }

                    val patterns = try {
                        smsPatternRepository.getActivePatternsList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val scannedResult = mutableListOf<ScannedSmsItem>()
                    val consumedManualTxIds = mutableSetOf<Long>()
                    val seenHashes = mutableSetOf<String>()

                    val uri = Uri.parse("content://sms/inbox")
                    val projection = arrayOf("_id", "address", "body", "date")
                    val startTime = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)

                    val (selection, selectionArgs) = com.example.util.SmsAccountMatcher.buildSmsQuerySelection(
                        startTime, allAccounts, allSenders
                    )

                    val cursor = context.contentResolver.query(
                        uri, projection, selection, selectionArgs, "date DESC"
                    )

                    cursor?.use { c ->
                        val addressIndex = c.getColumnIndex("address")
                        val bodyIndex = c.getColumnIndex("body")
                        val dateIndex = c.getColumnIndex("date")

                        while (c.moveToNext()) {
                            val sender = if (addressIndex != -1) c.getString(addressIndex) ?: "" else ""
                            val body = if (bodyIndex != -1) c.getString(bodyIndex) else ""
                            val date = if (dateIndex != -1) c.getLong(dateIndex) else System.currentTimeMillis()

                            if (body.isBlank()) continue

                            // 1. Filter out OTP / Second Passwords / Verification codes
                            if (isOtpSms(body)) continue

                            // 2. Parse SMS with sender priority (Account's patterns first, fallback to all patterns)
                            val (parsed, senderAccount) = com.example.util.SmsAccountMatcher.parseSmsWithSenderPriority(
                                body = body,
                                smsSender = sender,
                                allAccounts = allAccounts,
                                allSenders = allSenders,
                                patterns = patterns
                            )

                            // Strict check: only include SMS where amount, date, and transactionType are all non-null and amount > 0
                            val amt = parsed.amount
                            val smsDate = parsed.date ?: date
                            val txType = parsed.transactionType
                            val isStrictlyMatched = (amt != null && amt > 0L && txType != null)

                            if (isStrictlyMatched) {
                                val accountIdent = parsed.accountIdentifier?.trim() ?: ""
                                val bankName = parsed.bankName?.trim() ?: ""

                                val effectiveAccountIdent = if (accountIdent.isNotBlank()) accountIdent else (senderAccount?.cardNumber?.takeLast(4) ?: "")
                                val effectiveBankName = if (bankName.isNotBlank()) bankName else (senderAccount?.name ?: "")
                                val effectiveParsed = parsed.copy(
                                    bankName = if (parsed.bankName.isNullOrBlank()) senderAccount?.name else parsed.bankName,
                                    accountIdentifier = if (parsed.accountIdentifier.isNullOrBlank()) (senderAccount?.cardNumber?.takeLast(4) ?: "") else parsed.accountIdentifier
                                )

                                // Calculate canonical v2 SHA-256 hash including transactionType & accountIdentifier
                                val hash = com.example.util.SmsHashUtil.calculateHash(
                                    sender = sender,
                                    amount = amt,
                                    date = smsDate,
                                    text = body,
                                    transactionType = txType,
                                    accountIdentifier = effectiveAccountIdent
                                )

                                // Avoid duplicate within current scan list before consuming any manual match
                                if (!seenHashes.add(hash)) continue

                                val legacyHashes = setOf(
                                    com.example.util.SmsHashUtil.calculateLegacyV1Hash(sender, amt, smsDate, body),
                                    com.example.util.SmsHashUtil.calculateLegacySimpleHash(amt, smsDate, effectiveAccountIdent)
                                )

                                val isRegistered = com.example.util.SmsDeduplicator.isSmsRegistered(
                                    candidate = com.example.util.SmsDeduplicator.SmsCandidate(
                                        transactionType = txType,
                                        amount = amt,
                                        date = smsDate,
                                        primaryHash = hash,
                                        legacyHashes = legacyHashes,
                                        matchedAccountId = senderAccount?.id,
                                        accountIdent = effectiveAccountIdent,
                                        bankName = effectiveBankName
                                    ),
                                    existingTransactions = existingTransactions,
                                    accountsById = accountMap,
                                    consumedManualTxIds = consumedManualTxIds
                                )

                                var itemBalDiff: Long? = null
                                var itemMatchedAccId: Long? = null
                                if (effectiveParsed.balance != null) {
                                    val matchedAcc = senderAccount ?: if (effectiveAccountIdent.isNotBlank()) {
                                        allAccounts.find { acc ->
                                            (acc.cardNumber != null && (acc.cardNumber.contains(effectiveAccountIdent) || effectiveAccountIdent.contains(acc.cardNumber.takeLast(4)))) ||
                                            (acc.shabaNumber != null && acc.shabaNumber.contains(effectiveAccountIdent)) ||
                                            (acc.name.contains(effectiveAccountIdent) || effectiveAccountIdent.contains(acc.name))
                                        }
                                    } else if (effectiveBankName.isNotBlank()) {
                                        allAccounts.find { acc -> acc.name.contains(effectiveBankName) || effectiveBankName.contains(acc.name) }
                                    } else null

                                    if (matchedAcc != null) {
                                        itemMatchedAccId = matchedAcc.id
                                        val appBal = reconciliationRepository?.calculateAppBalanceAtDate(matchedAcc.id, smsDate)
                                        if (appBal != null) {
                                            itemBalDiff = effectiveParsed.balance - appBal
                                        }
                                    }
                                }

                                scannedResult.add(
                                    ScannedSmsItem(
                                        id = hash,
                                        parsedSms = effectiveParsed,
                                        rawText = body,
                                        smsHash = hash,
                                        date = smsDate,
                                        isRecognized = true,
                                        isRegistered = isRegistered,
                                        sender = sender,
                                        balanceDiff = itemBalDiff,
                                        matchedAccountId = itemMatchedAccId
                                    )
                                )
                            }
                        }
                    }
                    val transfers = com.example.util.TransferDetector.findTransfers(scannedResult, allAccounts, existingTransactions)
                    val registeredTransferHashes = transfers.filter { it.isRegistered }
                        .flatMap { listOf(it.withdrawalItem.smsHash, it.depositItem.smsHash) }
                        .toSet()

                    val finalItems = if (registeredTransferHashes.isNotEmpty()) {
                        scannedResult.map { item ->
                            if (registeredTransferHashes.contains(item.smsHash)) item.copy(isRegistered = true) else item
                        }
                    } else {
                        scannedResult
                    }

                    val sortedItems = sortScannedItems(finalItems)
                    val sortedTransfers = sortDetectedTransfers(transfers)

                    Pair(sortedItems, sortedTransfers)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scannedList = items.first,
                        detectedTransfers = items.second,
                        isScanCompleted = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "خطا در اسکن پیامک‌ها: ${e.message}",
                        isScanCompleted = true
                    )
                }
            }
        }
    }

    fun markItemAsRegistered(smsHash: String) {
        if (smsHash.isBlank()) return
        val targetTokens = com.example.util.SmsHashUtil.extractHashTokens(smsHash)
        if (targetTokens.isEmpty()) return
        _uiState.update { state ->
            val updated = state.scannedList.map { item ->
                val itemTokens = com.example.util.SmsHashUtil.extractHashTokens(item.smsHash)
                if (itemTokens.any { it in targetTokens }) {
                    item.copy(isRegistered = true)
                } else item
            }
            val updatedTransfers = state.detectedTransfers.map { tr ->
                val wTokens = com.example.util.SmsHashUtil.extractHashTokens(tr.withdrawalItem.smsHash)
                val dTokens = com.example.util.SmsHashUtil.extractHashTokens(tr.depositItem.smsHash)
                if (tr.id == smsHash ||
                    wTokens.any { it in targetTokens } ||
                    dTokens.any { it in targetTokens }
                ) {
                    tr.copy(isRegistered = true)
                } else tr
            }
            state.copy(
                scannedList = sortScannedItems(updated),
                detectedTransfers = sortDetectedTransfers(updatedTransfers)
            )
        }
    }

    fun refreshRegisteredStatus() {
        if (_uiState.value.scannedList.isEmpty()) return
        viewModelScope.launch {
            try {
                val existingTransactions = withContext(Dispatchers.IO) {
                    transactionRepository.getAllTransactionsWithDetailsList()
                }
                val allAccounts = withContext(Dispatchers.IO) {
                    accountRepository.getAllAccountsList()
                }
                val accountMap = allAccounts.associateBy { it.id }
                val consumedManualTxIds = mutableSetOf<Long>()

                val updatedList = _uiState.value.scannedList.map { item ->
                    if (item.isRegistered) item
                    else {
                        val amt = item.parsedSms.amount ?: 0L
                        val smsDate = item.parsedSms.date ?: item.date
                        val txType = item.parsedSms.transactionType ?: com.example.data.local.entity.TransactionType.EXPENSE
                        val accountIdent = item.parsedSms.accountIdentifier?.trim() ?: ""
                        val bankName = item.parsedSms.bankName?.trim() ?: ""
                        val legacyHashes = setOf(
                            com.example.util.SmsHashUtil.calculateLegacyV1Hash(item.sender, amt, smsDate, item.rawText),
                            com.example.util.SmsHashUtil.calculateLegacySimpleHash(amt, smsDate, accountIdent)
                        )

                        val isRegistered = com.example.util.SmsDeduplicator.isSmsRegistered(
                            candidate = com.example.util.SmsDeduplicator.SmsCandidate(
                                transactionType = txType,
                                amount = amt,
                                date = smsDate,
                                primaryHash = item.smsHash,
                                legacyHashes = legacyHashes,
                                matchedAccountId = item.matchedAccountId,
                                accountIdent = accountIdent,
                                bankName = bankName
                            ),
                            existingTransactions = existingTransactions,
                            accountsById = accountMap,
                            consumedManualTxIds = consumedManualTxIds
                        )
                        if (isRegistered) item.copy(isRegistered = true) else item
                    }
                }

                val transfers = withContext(Dispatchers.IO) {
                    com.example.util.TransferDetector.findTransfers(updatedList, allAccounts, existingTransactions)
                }
                val registeredTransferHashes = transfers.filter { it.isRegistered }
                    .flatMap { listOf(it.withdrawalItem.smsHash, it.depositItem.smsHash) }
                    .toSet()

                val finalList = if (registeredTransferHashes.isNotEmpty()) {
                    updatedList.map { item ->
                        if (registeredTransferHashes.contains(item.smsHash)) item.copy(isRegistered = true) else item
                    }
                } else {
                    updatedList
                }

                _uiState.update { state ->
                    state.copy(
                        scannedList = sortScannedItems(finalList),
                        detectedTransfers = sortDetectedTransfers(transfers)
                    )
                }
            } catch (e: Exception) {
                // Ignore refresh failure
            }
        }
    }

    private fun isPotentialBankSms(text: String): Boolean {
        val keywords = listOf(
            "واریز", "برداشت", "خرید", "انتقال", "مانده", "موجودی",
            "کارت", "حساب", "شاپرک", "شتاب", "پایا", "ساتنا",
            "بانک", "ملت", "ملی", "صادرات", "تجارت", "سپه", "رسالت",
            "کشاورزی", "مسکن", "سامان", "پاسارگاد", "پارسیان", "آینده", "شهر", "دی"
        )
        val matchCount = keywords.count { text.contains(it) }
        return matchCount >= 2
    }

    private fun isOtpSms(text: String): Boolean {
        val otpKeywords = listOf(
            "رمز پویا", "رمز یکبار", "کد تایید", "کد فعالسازی", "رمز اینترنتی",
            "رمز دوم", "کد ورود", "کد احراز", "کد صحت", "کد ثبت نام", "کد تائید"
        )
        return otpKeywords.any { text.contains(it, ignoreCase = true) }
    }

    companion object {
        fun sortScannedItems(items: List<ScannedSmsItem>): List<ScannedSmsItem> {
            return items.sortedWith(
                compareBy<ScannedSmsItem> { it.isRegistered }
                    .thenByDescending { it.date }
            )
        }

        fun sortDetectedTransfers(
            transfers: List<com.example.util.DetectedTransfer>
        ): List<com.example.util.DetectedTransfer> {
            return transfers.sortedWith(
                compareBy<com.example.util.DetectedTransfer> { it.isRegistered }
                    .thenByDescending { maxOf(it.withdrawalItem.date, it.depositItem.date) }
            )
        }
    }

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val smsPatternRepository: SmsPatternRepository,
        private val accountSmsSenderRepository: com.example.data.repository.AccountSmsSenderRepository? = null,
        private val reconciliationRepository: com.example.data.repository.ReconciliationRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanSmsViewModel(
                transactionRepository,
                accountRepository,
                smsPatternRepository,
                accountSmsSenderRepository,
                reconciliationRepository
            ) as T
        }
    }
}
