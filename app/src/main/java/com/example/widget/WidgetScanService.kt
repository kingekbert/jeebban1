package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.example.FinTrackApp
import com.example.R
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.AmountFormatter
import com.example.util.CategoryGuesser
import com.example.util.DateFormatter
import com.example.util.toPersianDigits
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class WidgetScanService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetScanViewsFactory(applicationContext)
    }
}

class WidgetScanViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<ScannedSmsItem> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()

    override fun onCreate() {
        // Init
    }

    override fun onDataSetChanged() {
        items = WidgetDataStore.getItems(context)
        try {
            val app = context.applicationContext as? FinTrackApp
            val appModule = app?.appModule ?: com.example.di.AppModule(context.applicationContext)
            runBlocking {
                categories = appModule.categoryRepository.allCategories.firstOrNull() ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        items = emptyList()
        categories = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in items.indices) return null
        val item = items[position]
        val parsed = item.parsedSms

        val isIncome = parsed.transactionType == TransactionType.INCOME
        val typeText = if (isIncome) "واریز" else "برداشت"
        val amountColorRes = if (isIncome) R.color.widget_income_color else R.color.widget_expense_color

        val bankPart = parsed.bankName?.ifBlank { "بانک" } ?: "بانک"
        val accountPart = if (!parsed.accountIdentifier.isNullOrBlank()) {
            " (${parsed.accountIdentifier.toPersianDigits()})"
        } else ""
        val bankFullText = "$bankPart$accountPart"

        val amountFullText = AmountFormatter.formatAmountWithCurrency(parsed.amount ?: 0L)
        val dateText = DateFormatter.formatShort(item.date)
        val noteText = parsed.rawDescription?.ifBlank { item.rawText } ?: item.rawText

        val fullTextForCategory = "$noteText $bankPart $accountPart"
        val matchingCategories = categories.filter {
            if (isIncome) it.type == com.example.data.local.entity.CategoryType.INCOME || it.type == com.example.data.local.entity.CategoryType.BOTH
            else it.type == com.example.data.local.entity.CategoryType.EXPENSE || it.type == com.example.data.local.entity.CategoryType.BOTH
        }
        val guessedCategory = CategoryGuesser.guessCategory(fullTextForCategory, matchingCategories)
        val categoryBtnLabel = if (guessedCategory != null) {
            "دسته: ${guessedCategory.name} 🏷️"
        } else {
            "انتخاب دسته 🏷️"
        }

        val views = RemoteViews(context.packageName, R.layout.widget_sms_item)

        views.setTextViewText(R.id.tv_item_bank, bankFullText)
        views.setTextViewText(R.id.tv_item_type, typeText)
        views.setTextColor(R.id.tv_item_type, ContextCompat.getColor(context, amountColorRes))

        views.setTextViewText(R.id.tv_item_amount, amountFullText)
        views.setTextColor(R.id.tv_item_amount, ContextCompat.getColor(context, amountColorRes))

        views.setTextViewText(R.id.tv_item_date, dateText)
        views.setTextViewText(R.id.tv_item_note, noteText)
        views.setTextViewText(R.id.btn_item_category, categoryBtnLabel)

        // 1. Category Picker FillInIntent (triggers floating Category Picker dialog directly)
        val pickCategoryIntent = Intent().apply {
            putExtra("widget_action", "PICK_CATEGORY")
            putExtra("extra_sms_hash", item.smsHash)
            putExtra("extra_amount", parsed.amount ?: 0L)
            putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
            putExtra("extra_date", parsed.date ?: item.date)
            putExtra("extra_bank_name", parsed.bankName ?: "")
            putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
            putExtra("extra_note", noteText)
        }
        views.setOnClickFillInIntent(R.id.btn_item_category, pickCategoryIntent)
        views.setOnClickFillInIntent(R.id.widget_item_container, pickCategoryIntent)

        // 2. Quick Register FillInIntent (saves with suggested category without opening any dialog)
        val quickRegisterIntent = Intent().apply {
            putExtra("widget_action", "QUICK_REGISTER")
            putExtra("extra_sms_hash", item.smsHash)
            putExtra("extra_amount", parsed.amount ?: 0L)
            putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
            putExtra("extra_date", parsed.date ?: item.date)
            putExtra("extra_bank_name", parsed.bankName ?: "")
            putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
            putExtra("extra_note", noteText)
            if (guessedCategory != null) {
                putExtra("extra_category_id", guessedCategory.id)
            }
        }
        views.setOnClickFillInIntent(R.id.btn_item_register, quickRegisterIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
