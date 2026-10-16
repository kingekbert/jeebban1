package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    fun setTemporaryStatus(context: Context, status: String) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SmsScanWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_sms_scan)
                    views.setTextViewText(R.id.widget_tv_status, status)
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

    fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val lastTime = getLastScanTimestamp(context)
        val newCount = getLastNewSmsCount(context)
        val statusText = formatWidgetStatusText(lastTime, newCount)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_sms_scan)

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

            // 2. Scan Button: 100% Background Scan via Broadcast (NO APP LAUNCH)
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

            // 3. Status text & Title
            views.setTextViewText(R.id.widget_tv_status, statusText)
            views.setTextViewText(R.id.widget_tv_title, context.getString(R.string.app_name))

            // 4. Remote Collection Adapter for ListView
            val serviceIntent = Intent(context, WidgetScanService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

            // 5. PendingIntent template for items in ListView (100% Background Quick Register)
            val itemRegisterIntent = Intent(context, SmsScanWidgetProvider::class.java).apply {
                action = SmsScanWidgetProvider.ACTION_REGISTER_ITEM
            }
            val itemPendingIntent = PendingIntent.getBroadcast(
                context,
                300,
                itemRegisterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, itemPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        }
    }
}
