package com.example.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.FinTrackApp
import com.example.MainActivity
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.JalaliDate
import com.example.util.SmsHashUtil
import com.example.util.SmsParser
import com.example.util.toPersianDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetBackgroundScanner {

    private fun isOtpSms(text: String): Boolean {
        val otpKeywords = listOf(
            "رمز پویا", "رمز یکبار", "کد تایید", "کد فعالسازی", "رمز اینترنتی",
            "رمز دوم", "کد ورود", "کد احراز", "کد صحت", "کد ثبت نام", "کد تائید"
        )
        return otpKeywords.any { text.contains(it, ignoreCase = true) }
    }

    suspend fun performBackgroundScan(context: Context) = withContext(Dispatchers.IO) {
        // 1. Check permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "برای اسکن، لطفاً ابتدا مجوز خواندن پیامک را در برنامه فعال کنید.",
                    Toast.LENGTH_LONG
                ).show()
            }
            // Open MainActivity to let user grant permission
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_auto_scan", true)
                putExtra("navigate_to", "widget_scan")
            }
            context.startActivity(intent)
            return@withContext
        }

        // Show "در حال اسکن..." temporarily on widget
        WidgetStatusHelper.setTemporaryStatus(context, "در حال اسکن هوشمند پیامک‌ها...")

        try {
            val app = context.applicationContext as? FinTrackApp
            val appModule = app?.appModule ?: com.example.di.AppModule(context.applicationContext)

            val existingTransactions = appModule.transactionRepository.getAllTransactionsWithDetailsList()
            val allAccounts = appModule.accountRepository.getAllAccountsList()
            val allSenders = try {
                appModule.accountSmsSenderRepository.getAllSendersList()
            } catch (e: Exception) {
                emptyList()
            }
            val accountMap = allAccounts.associateBy { it.id }

            val patterns = try {
                appModule.smsPatternRepository.getActivePatternsList()
            } catch (e: Exception) {
                emptyList()
            }

            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val days = 7
            val startTime = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)

            val (selection, selectionArgs) = com.example.util.SmsAccountMatcher.buildSmsQuerySelection(
                startTime, allAccounts, allSenders
            )

            val scannedUnregistered = mutableListOf<ScannedSmsItem>()

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

                    if (body.isNullOrBlank()) continue
                    if (isOtpSms(body)) continue

                    val (parsed, senderAccount) = com.example.util.SmsAccountMatcher.parseSmsWithSenderPriority(
                        body = body,
                        smsSender = sender,
                        allAccounts = allAccounts,
                        allSenders = allSenders,
                        patterns = patterns
                    )
                    val amt = parsed.amount
                    val smsDate = parsed.date ?: date
                    val txType = parsed.transactionType

                    if (amt != null && amt > 0L && txType != null) {
                        val accountIdent = parsed.accountIdentifier?.trim() ?: ""
                        val bankName = parsed.bankName?.trim() ?: ""

                        val effectiveAccountIdent = if (accountIdent.isNotBlank()) accountIdent else (senderAccount?.cardNumber?.takeLast(4) ?: "")
                        val effectiveBankName = if (bankName.isNotBlank()) bankName else (senderAccount?.name ?: "")
                        val effectiveParsed = parsed.copy(
                            bankName = if (parsed.bankName.isNullOrBlank()) senderAccount?.name else parsed.bankName,
                            accountIdentifier = if (parsed.accountIdentifier.isNullOrBlank()) (senderAccount?.cardNumber?.takeLast(4) ?: "") else parsed.accountIdentifier
                        )

                        val hash = SmsHashUtil.calculateHash(sender, amt, smsDate, body)
                        val legacyHash = "${amt}_${smsDate}_${effectiveAccountIdent}"
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

                            if (senderAccount != null && (tx.accountId == senderAccount.id || tx.toAccountId == senderAccount.id)) {
                                return@any true
                            }

                            if (effectiveAccountIdent.isNotBlank()) {
                                val srcAccount = tx.accountId?.let { accountMap[it] }
                                val destAccount = tx.toAccountId?.let { accountMap[it] }

                                val matchesAccount = { accCard: String?, accShaba: String?, accName: String? ->
                                    val matchesCard = accCard != null && (accCard.contains(effectiveAccountIdent) || effectiveAccountIdent.contains(accCard.takeLast(4)))
                                    val matchesShaba = accShaba != null && accShaba.contains(effectiveAccountIdent)
                                    val matchesName = accName != null && (accName.contains(effectiveAccountIdent) || effectiveAccountIdent.contains(accName))
                                    val matchesBank = effectiveBankName.isNotBlank() && accName != null && (accName.contains(effectiveBankName) || effectiveBankName.contains(accName))
                                    matchesCard || matchesShaba || matchesName || matchesBank
                                }

                                val matchesSrc = matchesAccount(
                                    srcAccount?.cardNumber ?: txItem.accountCardNumber,
                                    srcAccount?.shabaNumber,
                                    srcAccount?.name ?: txItem.accountName
                                )
                                val matchesDest = (tx.type == TransactionType.TRANSFER && destAccount != null && matchesAccount(
                                    destAccount.cardNumber,
                                    destAccount.shabaNumber,
                                    destAccount.name
                                ))

                                matchesSrc || matchesDest
                            } else true
                        }

                        if (!isRegistered && scannedUnregistered.none { it.smsHash == hash }) {
                            scannedUnregistered.add(
                                ScannedSmsItem(
                                    id = hash,
                                    parsedSms = effectiveParsed,
                                    rawText = body,
                                    smsHash = hash,
                                    date = smsDate,
                                    isRecognized = true,
                                    isRegistered = false,
                                    sender = sender
                                )
                            )
                        }
                    }
                }
            }

            // Run TransferDetector to filter out any SMS that are part of an already registered transfer
            val detectedTransfers = try {
                com.example.util.TransferDetector.findTransfers(scannedUnregistered, allAccounts, existingTransactions)
            } catch (e: Exception) {
                emptyList()
            }
            val registeredTransferHashes = detectedTransfers.filter { it.isRegistered }
                .flatMap { listOf(it.withdrawalItem.smsHash, it.depositItem.smsHash) }
                .toSet()
            if (registeredTransferHashes.isNotEmpty()) {
                scannedUnregistered.removeAll { registeredTransferHashes.contains(it.smsHash) }
            }

            // Save to WidgetDataStore
            WidgetDataStore.saveItems(context, scannedUnregistered)
            WidgetStatusHelper.saveScanResult(context, scannedUnregistered.size)
            WidgetStatusHelper.updateAllWidgets(context)

            Handler(Looper.getMainLooper()).post {
                if (scannedUnregistered.isEmpty()) {
                    Toast.makeText(
                        context,
                        "همه پیامک‌های ۷ روز گذشته قبلاً ثبت شده‌اند ✅",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val count = scannedUnregistered.size.toString().toPersianDigits()
                    Toast.makeText(
                        context,
                        "$count پیامک جدید شناسایی شد و در ویجت قرار گرفت.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            WidgetStatusHelper.updateAllWidgets(context)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "خطا در اسکن پیامک‌ها: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
