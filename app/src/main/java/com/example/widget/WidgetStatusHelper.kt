package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.toPersianDigits

object WidgetStatusHelper {
    private const val PREFS_NAME = "jeebban_widget_prefs"
    private const val KEY_LAST_SCAN_TIME = "last_scan_time"
    private const val KEY_LAST_NEW_COUNT = "last_new_count"

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

    fun filterUnregisteredSms(items: List<ScannedSmsItem>): List<ScannedSmsItem> {
        return items.filter { !it.isRegistered }
    }

    fun setTemporaryStatus(context: Context, status: String) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SmsScanWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_sms_scan)
                    views.setTextViewText(R.id.widget_tv_status, status)
                    // While scanning, empty view shows scanning indicator instead of false success
                    views.setTextViewText(R.id.widget_tv_empty_title, "در حال جستجو و پردازش پیامک‌های ۷ روز گذشته...")
                    views.setTextColor(R.id.widget_tv_empty_title, ContextCompat.getColor(context, R.color.widget_title_color))
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

    private const val KEY_WIDGET_THEME_OVERRIDE = "widget_theme_override"

    fun isDarkThemeActive(context: Context): Boolean {
        return try {
            val widgetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val widgetOverride = widgetPrefs.getString(KEY_WIDGET_THEME_OVERRIDE, null)
            if (widgetOverride == "DARK") return true
            if (widgetOverride == "LIGHT") return false

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

    fun toggleWidgetTheme(context: Context) {
        try {
            val currentIsDark = isDarkThemeActive(context)
            val newIsDark = !currentIsDark
            val newMode = if (newIsDark) "DARK" else "LIGHT"

            val widgetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            widgetPrefs.edit().putString(KEY_WIDGET_THEME_OVERRIDE, newMode).apply()

            // Also keep app theme setting in sync
            val appPrefs = context.getSharedPreferences(com.example.util.Constants.PREFS_NAME, Context.MODE_PRIVATE)
            appPrefs.edit().putString(com.example.util.Constants.KEY_THEME_MODE, newMode).apply()

            updateAllWidgets(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val lastTime = getLastScanTimestamp(context)
        val newCount = getLastNewSmsCount(context)
        val statusText = formatWidgetStatusText(lastTime, newCount)
        val isDark = isDarkThemeActive(context)

        val titleColor = if (isDark) 0xFF81C784.toInt() else 0xFF1B5E20.toInt()
        val subtitleColor = if (isDark) 0xFFA0A0A0.toInt() else 0xFF424843.toInt()
        val statusColor = if (isDark) 0xFFB0B0B0.toInt() else 0xFF556B5C.toInt()
        val emptyNormalColor = if (isDark) 0xFF81C784.toInt() else 0xFF1B5E20.toInt()
        val emptySuccessColor = if (isDark) 0xFF66BB6A.toInt() else 0xFF2E7D32.toInt()
        val themeIconRes = if (isDark) R.drawable.ic_widget_sun else R.drawable.ic_widget_moon
        val containerBgRes = if (isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_sms_scan)

            // Outer Container background (ensures instant dark/light switch on home screen)
            views.setInt(R.id.widget_container, "setBackgroundResource", containerBgRes)

            // 1. Header click opens MainActivity (optional)
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

            // 1.1 Theme Toggle Button (Moon/Sun icon)
            val themeIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                action = SmsScanWidgetProvider.ACTION_TOGGLE_THEME
            }
            val themePendingIntent = PendingIntent.getBroadcast(
                context,
                250,
                themeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setImageViewResource(R.id.widget_btn_theme_toggle, themeIconRes)
            views.setOnClickPendingIntent(R.id.widget_btn_theme_toggle, themePendingIntent)

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

            // 4. Empty View text: precise and contextual (never shows false success during initial or scanning)
            if (lastTime <= 0L) {
                views.setTextViewText(R.id.widget_tv_empty_title, "پیامک‌های بانکی ۷ روز اخیر")
                views.setTextColor(R.id.widget_tv_empty_title, emptyNormalColor)
                views.setTextViewText(R.id.widget_tv_empty_sub, "برای یافتن پیامک‌های ثبت‌نشده، دکمه «اسکن پیامک‌ها» را لمس کنید.")
                views.setTextColor(R.id.widget_tv_empty_sub, statusColor)
            } else if (newCount == 0) {
                views.setTextViewText(R.id.widget_tv_empty_title, "همه پیامک‌های ۷ روز گذشته ثبت شده‌اند ✅")
                views.setTextColor(R.id.widget_tv_empty_title, emptySuccessColor)
                views.setTextViewText(R.id.widget_tv_empty_sub, "هیچ تراکنش ثبت‌نشده‌ای در ۷ روز اخیر یافت نشد.")
                views.setTextColor(R.id.widget_tv_empty_sub, statusColor)
            }

            // 5. Remote Collection Adapter for ListView
            val serviceIntent = Intent(context, WidgetScanService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

            // 6. PendingIntent template for items in ListView (handles both category pick & quick register)
            val itemClickIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                action = SmsScanWidgetProvider.ACTION_ITEM_CLICK
            }
            val itemPendingIntent = PendingIntent.getBroadcast(
                context,
                300,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, itemPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        }
    }
}
