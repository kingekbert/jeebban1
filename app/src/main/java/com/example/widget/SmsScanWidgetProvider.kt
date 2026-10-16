package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsScanWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TRIGGER_SCAN = "com.example.widget.ACTION_TRIGGER_SCAN"
        const val ACTION_ITEM_CLICK = "com.example.widget.ACTION_ITEM_CLICK"
        const val ACTION_REGISTER_ITEM = "com.example.widget.ACTION_REGISTER_ITEM"
        const val ACTION_TOGGLE_THEME = "com.example.widget.ACTION_TOGGLE_THEME"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_THEME -> {
                WidgetStatusHelper.toggleWidgetTheme(context)
            }
            ACTION_TRIGGER_SCAN -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetBackgroundScanner.performBackgroundScan(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_ITEM_CLICK, ACTION_REGISTER_ITEM -> {
                val actionType = intent.getStringExtra("widget_action")
                if (actionType == "PICK_CATEGORY") {
                    val pickerIntent = Intent(context, WidgetCategoryPickerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtras(intent)
                    }
                    context.startActivity(pickerIntent)
                } else {
                    // Default to background quick register
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            WidgetQuickRegister.registerFromIntent(context, intent)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            else -> {
                super.onReceive(context, intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetStatusHelper.updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetStatusHelper.updateAllWidgets(context)
    }
}
