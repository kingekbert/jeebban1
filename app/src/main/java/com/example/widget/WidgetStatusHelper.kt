package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.FinTrackApp
import com.example.MainActivity
import com.example.R
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionType
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.AmountFormatter
import com.example.util.BankUtils
import com.example.util.DateFormatter
import com.example.util.JalaliDate
import com.example.util.toPersianDigits
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

object WidgetStatusHelper {
    private const val PREFS_NAME = "jeebban_widget_prefs"
    private const val KEY_LAST_SCAN_TIME = "last_scan_time"
    private const val KEY_LAST_NEW_COUNT = "last_new_count"
    private const val KEY_CATEGORY_PAGE = "widget_category_page"
    private const val KEY_CURRENT_TX_INDEX = "widget_current_tx_index"

    fun getCurrentTransactionIndex(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_CURRENT_TX_INDEX, 0)
        } catch (e: Exception) {
            0
        }
    }

    fun setCurrentTransactionIndex(context: Context, index: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_CURRENT_TX_INDEX, maxOf(0, index)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun nextTransaction(context: Context) {
        val items = WidgetDataStore.getItems(context)
        if (items.isEmpty()) return
        val current = getCurrentTransactionIndex(context)
        val next = (current + 1) % items.size
        setCurrentTransactionIndex(context, next)
        updateAllWidgets(context)
    }

    fun prevTransaction(context: Context) {
        val items = WidgetDataStore.getItems(context)
        if (items.isEmpty()) return
        val current = getCurrentTransactionIndex(context)
        val prev = if (current - 1 < 0) items.size - 1 else current - 1
        setCurrentTransactionIndex(context, prev)
        updateAllWidgets(context)
    }

    fun getCategoryPage(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_CATEGORY_PAGE, 0)
        } catch (e: Exception) {
            0
        }
    }

    fun toggleCategoryPage(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getInt(KEY_CATEGORY_PAGE, 0)
            val next = if (current == 0) 1 else 0
            prefs.edit().putInt(KEY_CATEGORY_PAGE, next).apply()
            updateAllWidgets(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveScanResult(context: Context, newSmsCount: Int, timestamp: Long = System.currentTimeMillis()) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_SCAN_TIME, timestamp)
                .putInt(KEY_LAST_NEW_COUNT, newSmsCount)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastScanTimestamp(context: Context): Long {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getLong(KEY_LAST_SCAN_TIME, 0L)
        } catch (e: Exception) {
            0L
        }
    }

    fun getLastNewSmsCount(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(KEY_LAST_NEW_COUNT, 0)
        } catch (e: Exception) {
            0
        }
    }

    fun formatWidgetStatusText(
        lastScanTimestamp: Long,
        newSmsCount: Int,
        currentTimestamp: Long = System.currentTimeMillis()
    ): String {
        if (lastScanTimestamp <= 0L) {
            return "هنوز اسکن نشده"
        }

        val elapsedMs = maxOf(0L, currentTimestamp - lastScanTimestamp)
        val elapsedMinutes = elapsedMs / (60 * 1000)

        val timeStr = when {
            elapsedMinutes < 1 -> "الان"
            elapsedMinutes < 60 -> "${elapsedMinutes.toPersianDigits()} دقیقه پیش"
            elapsedMinutes < 1440 -> "${(elapsedMinutes / 60).toPersianDigits()} ساعت پیش"
            else -> "${(elapsedMinutes / 1440).toPersianDigits()} روز پیش"
        }

        val countStr = if (newSmsCount > 0) {
            "${newSmsCount.toPersianDigits()} پیامک جدید"
        } else {
            "بدون پیامک جدید"
        }

        return "آخرین اسکن: $timeStr — $countStr"
    }

    fun formatScanSummary(newSmsCount: Int, elapsedMinutes: Long): String {
        val countStr = if (newSmsCount > 0) {
            "${newSmsCount.toPersianDigits()} پیامک جدید"
        } else {
            "بدون پیامک جدید"
        }

        val timeStr = when {
            elapsedMinutes <= 0 -> "الان"
            elapsedMinutes < 60 -> "${elapsedMinutes.toPersianDigits()} دقیقه پیش"
            elapsedMinutes < 1440 -> "${(elapsedMinutes / 60).toPersianDigits()} ساعت پیش"
            else -> "${(elapsedMinutes / 1440).toPersianDigits()} روز پیش"
        }

        return "$countStr — $timeStr"
    }

    fun formatSmsDateTime(timestamp: Long): String {
        return try {
            val validTs = if (timestamp > 0L) timestamp else System.currentTimeMillis()
            val jalali = JalaliDate.fromTimestamp(validTs)
            val zonedDateTime = java.time.Instant.ofEpochMilli(validTs).atZone(java.time.ZoneId.systemDefault())
            val hour = zonedDateTime.hour.toString().padStart(2, '0').toPersianDigits()
            val minute = zonedDateTime.minute.toString().padStart(2, '0').toPersianDigits()
            val monthNames = arrayOf(
                "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
            )
            val monthStr = if (jalali.month in 1..12) monthNames[jalali.month] else ""
            val dayStr = jalali.day.toString().toPersianDigits()
            "$dayStr $monthStr — ساعت $hour:$minute"
        } catch (e: Exception) {
            DateFormatter.formatShort(timestamp)
        }
    }

    fun filterUnregisteredSms(items: List<ScannedSmsItem>): List<ScannedSmsItem> {
        return items.filter { !it.isRegistered }
    }

    fun setTemporaryStatus(context: Context, status: String) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SmsScanWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val isDark = isDarkThemeActive(context)
                val titleColor = if (isDark) 0xFF81C784.toInt() else 0xFF1B5E20.toInt()
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_sms_scan)
                    views.setTextViewText(R.id.widget_tv_status, status)
                    views.setViewVisibility(R.id.widget_card_flipper, View.GONE)
                    views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                    views.setTextViewText(R.id.widget_tv_empty_title, "در حال جستجو و پردازش پیامک‌های ۷ روز گذشته...")
                    views.setTextColor(R.id.widget_tv_empty_title, titleColor)
                    views.setTextViewText(R.id.widget_tv_empty_sub, "لطفاً چند لحظه صبر کنید...")
                    appWidgetManager.partiallyUpdateAppWidget(id, views)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SmsScanWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isDarkThemeActive(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(com.example.util.Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val mode = prefs.getString(com.example.util.Constants.KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
            when (mode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> {
                    val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val lastTime = getLastScanTimestamp(context)
        val items = WidgetDataStore.getItems(context)
        val newCount = items.size
        saveScanResult(context, newCount)
        val statusText = formatWidgetStatusText(lastTime, newCount)
        val isDark = isDarkThemeActive(context)

        // Fetch categories from DB
        val app = context.applicationContext as? FinTrackApp
        val appModule = app?.appModule ?: com.example.di.AppModule(context.applicationContext)
        val allCategories = runBlocking {
            try {
                appModule.categoryRepository.allCategories.firstOrNull() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        val titleColor = if (isDark) 0xFF81C784.toInt() else 0xFF1B5E20.toInt()
        val subtitleColor = if (isDark) 0xFFA0A0A0.toInt() else 0xFF424843.toInt()
        val statusColor = if (isDark) 0xFFB0B0B0.toInt() else 0xFF556B5C.toInt()
        val emptyNormalColor = if (isDark) 0xFF81C784.toInt() else 0xFF1B5E20.toInt()
        val emptySuccessColor = if (isDark) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
        val containerBgRes = if (isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light

        val catPage = getCategoryPage(context)
        val catButtonIds = listOf(
            R.id.btn_cat_1, R.id.btn_cat_2, R.id.btn_cat_3, R.id.btn_cat_4
        )

        val catBtnBg = if (isDark) R.drawable.widget_cat_btn_bg_dark else R.drawable.widget_cat_btn_bg_light
        val catBtnTextColor = if (isDark) 0xFF81C784.toInt() else 0xFF1B5E20.toInt()
        val moreBtnBg = if (isDark) R.drawable.widget_cat_more_btn_bg_dark else R.drawable.widget_cat_more_btn_bg_light
        val moreBtnTextColor = if (isDark) 0xFFE0E0E0.toInt() else 0xFF424843.toInt()
        val itemBgRes = if (isDark) R.drawable.widget_item_bg_dark else R.drawable.widget_item_bg_light

        val currentIndex = if (items.isNotEmpty()) {
            getCurrentTransactionIndex(context).coerceIn(0, items.size - 1)
        } else 0
        val currentItem = items.getOrNull(currentIndex)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_sms_scan)

            // Outer Container background
            views.setInt(R.id.widget_container, "setBackgroundResource", containerBgRes)

            // 1. Header click opens MainActivity
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                100,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, appPendingIntent)

            // 2. Scan Button: 100% Background Scan via Broadcast
            val scanIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                action = SmsScanWidgetProvider.ACTION_TRIGGER_SCAN
            }
            val scanPendingIntent = PendingIntent.getBroadcast(
                context,
                200,
                scanIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_scan, scanPendingIntent)

            // 3. Status text & Title & Subtitle with theme-adaptive colors
            views.setTextViewText(R.id.widget_tv_status, statusText)
            views.setTextColor(R.id.widget_tv_status, statusColor)
            views.setTextViewText(R.id.widget_tv_title, context.getString(R.string.app_name))
            views.setTextColor(R.id.widget_tv_title, titleColor)
            views.setTextColor(R.id.widget_tv_subtitle, subtitleColor)

            // 4. Single Transaction Card or Empty State
            if (currentItem != null) {
                views.setViewVisibility(R.id.widget_card_flipper, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                views.setInt(R.id.widget_card_single_transaction, "setBackgroundResource", itemBgRes)

                val parsed = currentItem.parsedSms
                val isIncome = (parsed.transactionType == TransactionType.INCOME)

                // Bank Logo
                views.setImageViewResource(R.id.iv_widget_bank_logo, BankUtils.getBankLogo(parsed.bankName))

                // Colored Amount (Green for Income, Red for Expense)
                val amountFormatted = AmountFormatter.formatAmountWithCurrency(parsed.amount ?: 0L)
                val amountPrefix = if (isIncome) "+ " else "- "
                views.setTextViewText(R.id.tv_widget_tx_amount, "$amountPrefix$amountFormatted")
                val amountColor = if (isIncome) {
                    if (isDark) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
                } else {
                    if (isDark) 0xFFEF5350.toInt() else 0xFFD32F2F.toInt()
                }
                views.setTextColor(R.id.tv_widget_tx_amount, amountColor)

                // Date & Time
                val dateTimeStr = formatSmsDateTime(parsed.date ?: currentItem.date)
                views.setTextViewText(R.id.tv_widget_tx_datetime, dateTimeStr)
                views.setTextColor(R.id.tv_widget_tx_datetime, statusColor)

                // Counter Badge & Navigation
                if (items.size > 1) {
                    views.setViewVisibility(R.id.widget_nav_container, View.VISIBLE)
                    views.setViewVisibility(R.id.btn_widget_prev_tx, View.VISIBLE)
                    views.setViewVisibility(R.id.btn_widget_next_tx, View.VISIBLE)
                    views.setViewVisibility(R.id.tv_widget_tx_counter, View.VISIBLE)
                    views.setTextViewText(R.id.tv_widget_tx_counter, "${(currentIndex + 1).toPersianDigits()} از ${items.size.toPersianDigits()}")
                    views.setTextColor(R.id.tv_widget_tx_counter, titleColor)
                    views.setTextColor(R.id.btn_widget_prev_tx, titleColor)
                    views.setTextColor(R.id.btn_widget_next_tx, titleColor)

                    val prevIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                        action = SmsScanWidgetProvider.ACTION_PREV_TX
                    }
                    val prevPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (appWidgetId * 10000) + 300,
                        prevIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_prev_tx, prevPendingIntent)

                    val nextIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                        action = SmsScanWidgetProvider.ACTION_NEXT_TX
                    }
                    val nextPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (appWidgetId * 10000) + 301,
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_next_tx, nextPendingIntent)
                } else {
                    views.setViewVisibility(R.id.btn_widget_prev_tx, View.GONE)
                    views.setViewVisibility(R.id.btn_widget_next_tx, View.GONE)
                    if (items.size == 1) {
                        views.setViewVisibility(R.id.widget_nav_container, View.VISIBLE)
                        views.setViewVisibility(R.id.tv_widget_tx_counter, View.VISIBLE)
                        views.setTextViewText(R.id.tv_widget_tx_counter, "۱ از ۱")
                        views.setTextColor(R.id.tv_widget_tx_counter, titleColor)
                    } else {
                        views.setViewVisibility(R.id.widget_nav_container, View.GONE)
                    }
                }

                // Note / Raw Description
                val noteText = parsed.rawDescription?.ifBlank { currentItem.rawText } ?: currentItem.rawText
                views.setTextViewText(R.id.tv_widget_tx_note, noteText)
                views.setTextColor(R.id.tv_widget_tx_note, subtitleColor)

                // Category Buttons (Horizontal Row with comfortable sizing & optional pagination)
                val matchingCats = allCategories.filter {
                    if (isIncome) it.type == CategoryType.INCOME || it.type == CategoryType.BOTH
                    else it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH
                }
                val starred = matchingCats.filter { it.isFavorite }
                val nonStarred = matchingCats.filter { !it.isFavorite }
                val allDisplayCats = starred + nonStarred

                val pageSize = 4
                val totalCats = allDisplayCats.size
                val hasMultiplePages = totalCats > pageSize
                val effectivePage = if (hasMultiplePages && catPage == 1) 1 else 0
                val pageCats = if (effectivePage == 1) {
                    allDisplayCats.drop(pageSize).take(pageSize).ifEmpty { allDisplayCats.take(pageSize) }
                } else {
                    allDisplayCats.take(pageSize)
                }

                for (i in catButtonIds.indices) {
                    val btnId = catButtonIds[i]
                    if (i < pageCats.size) {
                        val cat = pageCats[i]
                        views.setViewVisibility(btnId, View.VISIBLE)
                        views.setTextViewText(btnId, cat.name)
                        views.setInt(btnId, "setBackgroundResource", catBtnBg)
                        views.setTextColor(btnId, catBtnTextColor)

                        val regIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                            action = SmsScanWidgetProvider.ACTION_REGISTER_ITEM
                            putExtra("widget_action", "REGISTER_CATEGORY")
                            putExtra("extra_sms_hash", currentItem.smsHash)
                            putExtra("extra_amount", parsed.amount ?: 0L)
                            putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
                            putExtra("extra_date", parsed.date ?: currentItem.date)
                            putExtra("extra_bank_name", parsed.bankName ?: "")
                            putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
                            putExtra("extra_note", noteText)
                            putExtra("extra_category_id", cat.id)
                            putExtra("extra_category_name", cat.name)
                        }
                        val regPi = PendingIntent.getBroadcast(
                            context,
                            (appWidgetId * 10000) + (currentIndex * 10) + i,
                            regIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(btnId, regPi)
                    } else {
                        views.setViewVisibility(btnId, View.GONE)
                    }
                }

                // Pagination button (◀ / ▶) if more than 4 categories
                if (hasMultiplePages) {
                    views.setViewVisibility(R.id.btn_cat_page, View.VISIBLE)
                    views.setInt(R.id.btn_cat_page, "setBackgroundResource", moreBtnBg)
                    views.setTextColor(R.id.btn_cat_page, moreBtnTextColor)
                    views.setTextViewText(R.id.btn_cat_page, if (effectivePage == 0) "◀" else "▶")

                    val switchPageIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                        action = SmsScanWidgetProvider.ACTION_SWITCH_CATEGORY_PAGE
                    }
                    val switchPagePi = PendingIntent.getBroadcast(
                        context,
                        (appWidgetId * 10000) + 98,
                        switchPageIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_cat_page, switchPagePi)
                } else {
                    views.setViewVisibility(R.id.btn_cat_page, View.GONE)
                }

                // "سایر..." button
                views.setViewVisibility(R.id.btn_cat_more, View.VISIBLE)
                views.setInt(R.id.btn_cat_more, "setBackgroundResource", moreBtnBg)
                views.setTextColor(R.id.btn_cat_more, moreBtnTextColor)

                val pickerIntent = Intent(context, WidgetCategoryPickerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("widget_action", "PICK_CATEGORY")
                    putExtra("extra_sms_hash", currentItem.smsHash)
                    putExtra("extra_amount", parsed.amount ?: 0L)
                    putExtra("extra_type", (parsed.transactionType ?: TransactionType.EXPENSE).name)
                    putExtra("extra_date", parsed.date ?: currentItem.date)
                    putExtra("extra_bank_name", parsed.bankName ?: "")
                    putExtra("extra_account_ident", parsed.accountIdentifier ?: "")
                    putExtra("extra_note", noteText)
                }
                val pickerPi = PendingIntent.getActivity(
                    context,
                    (appWidgetId * 10000) + (currentIndex * 10) + 9,
                    pickerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_cat_more, pickerPi)
                views.setOnClickPendingIntent(R.id.widget_card_single_transaction, pickerPi)

            } else {
                views.setViewVisibility(R.id.widget_card_flipper, View.GONE)
                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)

                if (lastTime <= 0L) {
                    views.setTextViewText(R.id.widget_tv_empty_title, "پیامک‌های بانکی ۷ روز اخیر")
                    views.setTextColor(R.id.widget_tv_empty_title, emptyNormalColor)
                    views.setTextViewText(R.id.widget_tv_empty_sub, "برای یافتن پیامک‌های ثبت‌نشده، دکمه «اسکن پیامک‌ها» را لمس کنید.")
                    views.setTextColor(R.id.widget_tv_empty_sub, statusColor)
                } else {
                    views.setTextViewText(R.id.widget_tv_empty_title, "همه پیامک‌های ۷ روز گذشته ثبت شده‌اند ✅")
                    views.setTextColor(R.id.widget_tv_empty_title, emptySuccessColor)
                    views.setTextViewText(R.id.widget_tv_empty_sub, "هیچ تراکنش ثبت‌نشده‌ای در ۷ روز اخیر یافت نشد.")
                    views.setTextColor(R.id.widget_tv_empty_sub, statusColor)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
