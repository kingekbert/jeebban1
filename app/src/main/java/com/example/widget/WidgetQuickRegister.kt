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
import com.example.util.AmountFormatter
import com.example.util.CategoryGuesser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

object WidgetQuickRegister {

    suspend fun registerFromIntent(context: Context, intent: Intent) = withContext(Dispatchers.IO) {
        val smsHash = intent.getStringExtra("extra_sms_hash") ?: return@withContext
        val amount = intent.getLongExtra("extra_amount", 0L)
        val typeStr = intent.getStringExtra("extra_type") ?: TransactionType.EXPENSE.name
        val txType = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.EXPENSE }
        val date = intent.getLongExtra("extra_date", System.currentTimeMillis())
        val bankName = intent.getStringExtra("extra_bank_name") ?: ""
        val accountIdent = intent.getStringExtra("extra_account_ident") ?: ""
        val note = intent.getStringExtra("extra_note") ?: ""

        try {
            val app = context.applicationContext as? FinTrackApp
            val appModule = app?.appModule ?: com.example.di.AppModule(context.applicationContext)

            // 1. Resolve Accounts
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

            // 2. Resolve Category with intelligent guessing
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
            val guessedCategory = CategoryGuesser.guessCategory(fullText, filteredCats)
            val selectedCategory = explicitCategory ?: guessedCategory ?: filteredCats.firstOrNull()
            val categoryId = selectedCategory?.id

            // 3. Create and Save Transaction
            val transaction = TransactionEntity(
                type = txType,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                note = note.ifBlank { null },
                smsHash = smsHash
            )

            val result = appModule.addTransactionUseCase(transaction)

            if (result.isSuccess) {
                // Remove from widget cache
                val remaining = WidgetDataStore.removeItem(context, smsHash)
                WidgetStatusHelper.saveScanResult(context, remaining.size)
                WidgetStatusHelper.updateAllWidgets(context)

                val catName = selectedCategory?.name?.let { "($it)" } ?: ""
                val amountFormatted = AmountFormatter.formatAmountWithCurrency(amount)

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "تراکنش $amountFormatted $catName ثبت شد ✅",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "خطا در ثبت تراکنش: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "خطا در ثبت تراکنش: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
