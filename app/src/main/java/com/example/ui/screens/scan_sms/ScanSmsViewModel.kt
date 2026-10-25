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
    val sender: String = ""
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
    private val accountSmsSenderRepository: com.example.data.repository.AccountSmsSenderRepository? = null
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

                                // Calculate SHA-256 hash (sender + amount + smsDate + normalized text)
                                val hash = com.example.util.SmsHashUtil.calculateHash(sender, amt, smsDate, body)
                                val legacyHash = "${amt}_${smsDate}_${effectiveAccountIdent}"

                                val smsJalali = JalaliDate.fromTimestamp(smsDate)

                                // Accurate check for registered transaction based on:
                                // 1. Exact hash
                                // 2. Amount match
                                // 3. Same day Jalali
                                // 4. Account matching (Sender account prioritized)
                                val isRegistered = existingTransactions.any { txItem ->
                                    val tx = txItem.transaction

                                    // Exact smsHash match (supporting both new SHA-256 and legacy hashes)
                                    if (!tx.smsHash.isNullOrBlank() && (tx.smsHash == hash || tx.smsHash == legacyHash)) {
                                        return@any true
                                    }

                                    // 1. Amount match
                                    if (tx.amount != amt) return@any false

                                    // 2. Same day in Jalali calendar
                                    val txJalali = JalaliDate.fromTimestamp(tx.date)
                                    val sameDay = txJalali.year == smsJalali.year &&
                                            txJalali.month == smsJalali.month &&
                                            txJalali.day == smsJalali.day
                                    if (!sameDay) return@any false

                                    // 3. Account matching: If sender matches a registered account, check that account directly
                                    if (senderAccount != null && tx.accountId == senderAccount.id) {
                                        return@any true
                                    }

                                    if (effectiveAccountIdent.isNotBlank()) {
                                        val account = tx.accountId?.let { accountMap[it] }
                                        val card = account?.cardNumber ?: txItem.accountCardNumber
                                        val shaba = account?.shabaNumber
                                        val name = account?.name ?: txItem.accountName

                                        val matchesCard = card != null && (card.contains(effectiveAccountIdent) || effectiveAccountIdent.contains(card.takeLast(4)))
                                        val matchesShaba = shaba != null && shaba.contains(effectiveAccountIdent)
                                        val matchesName = name != null && (name.contains(effectiveAccountIdent) || effectiveAccountIdent.contains(name))
                                        val matchesBank = effectiveBankName.isNotBlank() && name != null && (name.contains(effectiveBankName) || effectiveBankName.contains(name))

                                        matchesCard || matchesShaba || matchesName || matchesBank
                                    } else {
                                        true
                                    }
                                }

                                // Avoid duplicate within current scan list
                                if (scannedResult.any { it.smsHash == hash }) continue

                                scannedResult.add(
                                    ScannedSmsItem(
                                        id = hash,
                                        parsedSms = effectiveParsed,
                                        rawText = body,
                                        smsHash = hash,
                                        date = smsDate,
                                        isRecognized = true,
                                        isRegistered = isRegistered,
                                        sender = sender
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

                    Pair(finalItems, transfers)
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
        val targetHashes = smsHash.split("|").filter { it.isNotBlank() }.toSet()
        _uiState.update { state ->
            val updated = state.scannedList.map { item ->
                if (item.smsHash == smsHash || targetHashes.contains(item.smsHash) || smsHash.contains(item.smsHash)) {
                    item.copy(isRegistered = true)
                } else item
            }
            val updatedTransfers = state.detectedTransfers.map { tr ->
                if (targetHashes.contains(tr.withdrawalItem.smsHash) ||
                    targetHashes.contains(tr.depositItem.smsHash) ||
                    tr.id == smsHash ||
                    smsHash.contains(tr.withdrawalItem.smsHash) ||
                    smsHash.contains(tr.depositItem.smsHash)
                ) {
                    tr.copy(isRegistered = true)
                } else tr
            }
            state.copy(scannedList = updated, detectedTransfers = updatedTransfers)
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

                val updatedList = _uiState.value.scannedList.map { item ->
                    if (item.isRegistered) item
                    else {
                        val amt = item.parsedSms.amount ?: 0L
                        val smsDate = item.parsedSms.date ?: item.date
                        val accountIdent = item.parsedSms.accountIdentifier?.trim() ?: ""
                        val bankName = item.parsedSms.bankName?.trim() ?: ""
                        val hash = item.smsHash
                        val legacyHash = "${amt}_${smsDate}_${accountIdent}"
                        val smsJalali = JalaliDate.fromTimestamp(smsDate)

                        val isRegistered = existingTransactions.any { txItem ->
                            val tx = txItem.transaction
                            if (!tx.smsHash.isNullOrBlank()) {
                                if (tx.smsHash == hash || tx.smsHash == legacyHash || tx.smsHash.contains(hash)) {
                                    return@any true
                                }
                                val parts = tx.smsHash.split("|")
                                if (parts.any { it == hash }) return@any true
                            }
                            if (tx.amount != amt) return@any false
                            val txJalali = JalaliDate.fromTimestamp(tx.date)
                            val sameDay = txJalali.year == smsJalali.year &&
                                    txJalali.month == smsJalali.month &&
                                    txJalali.day == smsJalali.day
                            if (!sameDay) return@any false
                            if (accountIdent.isNotBlank()) {
                                val account = tx.accountId?.let { accountMap[it] }
                                val card = account?.cardNumber ?: txItem.accountCardNumber
                                val shaba = account?.shabaNumber
                                val name = account?.name ?: txItem.accountName
                                val matchesCard = card != null && (card.contains(accountIdent) || accountIdent.contains(card.takeLast(4)))
                                val matchesShaba = shaba != null && shaba.contains(accountIdent)
                                val matchesName = name != null && (name.contains(accountIdent) || accountIdent.contains(name))
                                val matchesBank = bankName.isNotBlank() && name != null && (name.contains(bankName) || bankName.contains(name))
                                matchesCard || matchesShaba || matchesName || matchesBank
                            } else true
                        }
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
                    state.copy(scannedList = finalList, detectedTransfers = transfers)
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

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val smsPatternRepository: SmsPatternRepository,
        private val accountSmsSenderRepository: com.example.data.repository.AccountSmsSenderRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanSmsViewModel(transactionRepository, accountRepository, smsPatternRepository, accountSmsSenderRepository) as T
        }
    }
}
