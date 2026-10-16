package com.example.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.example.FinTrackApp
import com.example.R
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.AmountFormatter
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

        // Filter categories for this transaction type:
        val matchingCategories = categories.filter {
            if (isIncome) it.type == CategoryType.INCOME || it.type == CategoryType.BOTH
            else it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH
        }

        // Starred categories (up to 4)
        val starredCategories = matchingCategories.filter { it.isFavorite }.take(4)

        val views = RemoteViews(context.packageName, R.layout.widget_sms_item)

        views.setTextViewText(R.id.tv_item_bank, bankFullText)
        views.setTextViewText(R.id.tv_item_type, typeText)
        views.setTextColor(R.id.tv_item_type, ContextCompat.getColor(context, amountColorRes))

        views.setTextViewText(R.id.tv_item_amount, amountFullText)
        views.setTextColor(R.id.tv_item_amount, ContextCompat.getColor(context, amountColorRes))

        views.setTextViewText(R.id.tv_item_date, dateText)
        views.setTextViewText(R.id.tv_item_note, noteText)

        // Helper function to create intent for clicking a category button
        fun createRegisterIntent(cat: CategoryEntity): Intent {
            return Intent().apply {
                putExtra("widget_action", "REGISTER_CATEGORY")
                putExtra("extra_sms_hash", item.smsHash)
                putExtra("extra_amount", parsed.amount ?: 0L)
                putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
                putExtra("extra_date", parsed.date ?: item.date)
                putExtra("extra_bank_name", parsed.bankName ?: "")
                putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
                putExtra("extra_note", noteText)
                putExtra("extra_category_id", cat.id)
                putExtra("extra_category_name", cat.name)
            }
        }

        // Configure Up to 4 Starred Category Buttons
        val favButtons = listOf(R.id.btn_fav_1, R.id.btn_fav_2, R.id.btn_fav_3, R.id.btn_fav_4)
        for (i in favButtons.indices) {
            val btnId = favButtons[i]
            if (i < starredCategories.size) {
                val cat = starredCategories[i]
                views.setViewVisibility(btnId, View.VISIBLE)
                views.setTextViewText(btnId, cat.name)
                views.setOnClickFillInIntent(btnId, createRegisterIntent(cat))
            } else {
                views.setViewVisibility(btnId, View.GONE)
            }
        }

        // Always show "سایر..." button
        views.setViewVisibility(R.id.btn_fav_more, View.VISIBLE)
        views.setTextViewText(R.id.btn_fav_more, "سایر...")

        val moreIntent = Intent().apply {
            putExtra("widget_action", "PICK_CATEGORY")
            putExtra("extra_sms_hash", item.smsHash)
            putExtra("extra_amount", parsed.amount ?: 0L)
            putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
            putExtra("extra_date", parsed.date ?: item.date)
            putExtra("extra_bank_name", parsed.bankName ?: "")
            putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
            putExtra("extra_note", noteText)
        }
        views.setOnClickFillInIntent(R.id.btn_fav_more, moreIntent)
        views.setOnClickFillInIntent(R.id.widget_item_container, moreIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
