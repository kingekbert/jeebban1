package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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

    fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val lastTime = getLastScanTimestamp(context)
        val newCount = getLastNewSmsCount(context)
        val statusText = formatWidgetStatusText(lastTime, newCount)

        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("extra_auto_scan", true)
                putExtra("navigate_to", "widget_scan")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_sms_scan).apply {
                setOnClickPendingIntent(R.id.widget_btn_scan, pendingIntent)
                setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                setTextViewText(R.id.widget_tv_status, statusText)
                setTextViewText(R.id.widget_tv_title, context.getString(R.string.app_name))
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
