package com.example.widget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.FinTrackApp
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.DuplicateSmsTransactionException
import com.example.util.CategoryGuesser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

object WidgetQuickRegister {

    suspend fun registerFromIntent(context: Context, intent: Intent) = withContext(Dispatchers.IO) {
        val smsHash = intent.getStringExtra("extra_sms_hash")
        if (smsHash.isNullOrBlank()) {
            showErrorToast(context)
            return@withContext
        }

        // Treat the widget payload only as a lookup key. Financial fields must come
        // from the app-owned scanned-item cache, never from broadcast extras.
        val sourceItem = WidgetDataStore.getItems(context).firstOrNull { it.smsHash == smsHash }
        if (sourceItem == null) {
            showErrorToast(context)
            return@withContext
        }
        val parsed = sourceItem.parsedSms
        val amount = parsed.amount ?: 0L
        if (amount <= 0L) {
            showErrorToast(context)
            return@withContext
        }
        val txType = parsed.transactionType ?: TransactionType.EXPENSE
        val date = parsed.date ?: sourceItem.date
        val bankName = parsed.bankName ?: ""
        val accountIdent = parsed.accountIdentifier ?: ""
        val note = parsed.rawDescription?.ifBlank { sourceItem.rawText } ?: sourceItem.rawText

        try {
            val app = context.applicationContext as? FinTrackApp
            val appModule = app?.appModule ?: com.example.di.AppModule(context.applicationContext)

            // 0. Guard against duplicate registration across app/widget paths
            if (appModule.transactionRepository.isSmsHashRegistered(smsHash)) {
                removeDuplicateAndNotify(context, smsHash)
                return@withContext
            }

            // 1. Resolve Account
            val allAccounts = appModule.accountRepository.getAllAccountsList()
            var matchedAccount = allAccounts.find { acc ->
                (accountIdent.isNotBlank() && (
                    (acc.cardNumber != null && acc.cardNumber.contains(accountIdent)) ||
                    (acc.shabaNumber != null && acc.shabaNumber.contains(accountIdent)) ||
                    acc.name.contains(accountIdent)
                ))
            }
            if (matchedAccount == null && bankName.isNotBlank()) {
                matchedAccount = allAccounts.find { acc ->
                    acc.name.contains(bankName, ignoreCase = true) ||
                    (bankName.contains("ملی") && acc.name.contains("ملی")) ||
                    (bankName.contains("رسالت") && acc.name.contains("رسالت")) ||
                    (bankName.contains("تجارت") && acc.name.contains("تجارت")) ||
                    (bankName.contains("ملت") && acc.name.contains("ملت")) ||
                    (bankName.contains("سپه") && acc.name.contains("سپه")) ||
                    (bankName.contains("صادرات") && acc.name.contains("صادرات"))
                }
            }
            val accountId = matchedAccount?.id ?: allAccounts.firstOrNull()?.id

            // 2. Resolve Category
            val allCategories = appModule.categoryRepository.allCategories.firstOrNull() ?: emptyList()
            val filteredCats = allCategories.filter {
                when (txType) {
                    TransactionType.EXPENSE -> it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH
                    TransactionType.INCOME -> it.type == CategoryType.INCOME || it.type == CategoryType.BOTH
                    else -> true
                }
            }

            val explicitCatId = intent.getLongExtra("extra_category_id", -1L)
            val explicitCategory = if (explicitCatId > 0) filteredCats.find { it.id == explicitCatId } else null

            val fullText = "$note $bankName $accountIdent"
            val selectedCategory = explicitCategory ?: CategoryGuesser.resolveCategory(fullText, filteredCats, context, txType)
            val categoryId = selectedCategory?.id

            // Save last used category for future predictions (gradual learning)
            if (categoryId != null) {
                CategoryGuesser.saveLastUsedCategory(context, txType, categoryId)
            }

            // 3. Build TransactionEntity
            val transaction = TransactionEntity(
                type = txType,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                note = note.ifBlank { null },
                smsHash = smsHash
            )

            // 4. Check for open Dong settlement if this is an Income SMS
            var isSavedSuccess = false
            var isDuplicate = false
            if (txType == TransactionType.INCOME) {
                val matchingShares = try {
                    appModule.groupExpenseRepository.findMatchingOpenShares(amount)
                } catch (e: Exception) {
                    emptyList()
                }
                val bestMatch = matchingShares.firstOrNull()
                if (bestMatch != null) {
                    try {
                        appModule.groupExpenseRepository.settleShareWithIncomeTransaction(
                            shareId = bestMatch.share.id,
                            incomeTransaction = transaction
                        )
                        isSavedSuccess = true
                    } catch (e: DuplicateSmsTransactionException) {
                        isDuplicate = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (isDuplicate) {
                removeDuplicateAndNotify(context, smsHash)
                return@withContext
            }

            if (!isSavedSuccess) {
                val result = appModule.addTransactionUseCase(transaction)
                if (result.exceptionOrNull() is DuplicateSmsTransactionException) {
                    removeDuplicateAndNotify(context, smsHash)
                    return@withContext
                }
                isSavedSuccess = result.isSuccess
            }

            if (isSavedSuccess) {
                val currentItem = WidgetDataStore.getItems(context).find { it.smsHash == smsHash }
                val extractedBalance = currentItem?.parsedSms?.balance
                if (accountId != null && extractedBalance != null && extractedBalance > 0L) {
                    try {
                        appModule.reconciliationRepository.recordSnapshot(
                            accountId = accountId,
                            smsDate = date,
                            balance = extractedBalance,
                            smsHash = smsHash
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val remaining = WidgetDataStore.removeItem(context, smsHash)
                val curIdx = WidgetStatusHelper.getCurrentTransactionIndex(context)
                if (curIdx >= remaining.size) {
                    WidgetStatusHelper.setCurrentTransactionIndex(context, maxOf(0, remaining.size - 1))
                }
                WidgetStatusHelper.saveScanResult(context, remaining.size)
                WidgetStatusHelper.updateAllWidgets(context)

                val categoryName = selectedCategory?.name ?: intent.getStringExtra("extra_category_name") ?: "بدون دسته‌بندی"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "ثبت شد — $categoryName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                showErrorToast(context)
            }
        } catch (e: DuplicateSmsTransactionException) {
            removeDuplicateAndNotify(context, smsHash)
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast(context)
        }
    }

    private fun removeDuplicateAndNotify(context: Context, smsHash: String) {
        val remaining = WidgetDataStore.removeItem(context, smsHash)
        val curIdx = WidgetStatusHelper.getCurrentTransactionIndex(context)
        if (curIdx >= remaining.size) {
            WidgetStatusHelper.setCurrentTransactionIndex(context, maxOf(0, remaining.size - 1))
        }
        WidgetStatusHelper.saveScanResult(context, remaining.size)
        WidgetStatusHelper.updateAllWidgets(context)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "این پیامک قبلاً ثبت شده است",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showErrorToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "خطا در ثبت — اپ را باز کنید",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
