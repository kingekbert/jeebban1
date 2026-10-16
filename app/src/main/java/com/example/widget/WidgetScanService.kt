package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.AmountFormatter
import com.example.util.DateFormatter
import com.example.util.toPersianDigits

class WidgetScanService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetScanViewsFactory(applicationContext)
    }
}

class WidgetScanViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<ScannedSmsItem> = emptyList()

    override fun onCreate() {
        // Init
    }

    override fun onDataSetChanged() {
        items = WidgetDataStore.getItems(context)
    }

    override fun onDestroy() {
        items = emptyList()
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

        val views = RemoteViews(context.packageName, R.layout.widget_sms_item)

        views.setTextViewText(R.id.tv_item_bank, bankFullText)
        views.setTextViewText(R.id.tv_item_type, typeText)
        views.setTextColor(R.id.tv_item_type, ContextCompat.getColor(context, amountColorRes))

        views.setTextViewText(R.id.tv_item_amount, amountFullText)
        views.setTextColor(R.id.tv_item_amount, ContextCompat.getColor(context, amountColorRes))

        views.setTextViewText(R.id.tv_item_date, dateText)
        views.setTextViewText(R.id.tv_item_note, noteText)

        // FillInIntent triggered on tap of "ثبت ⚡" or row
        val fillInIntent = Intent().apply {
            putExtra("extra_sms_hash", item.smsHash)
            putExtra("extra_amount", parsed.amount ?: 0L)
            putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
            putExtra("extra_date", parsed.date ?: item.date)
            putExtra("extra_bank_name", parsed.bankName ?: "")
            putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
            putExtra("extra_note", noteText)
        }

        views.setOnClickFillInIntent(R.id.btn_item_register, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
