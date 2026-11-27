package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.FinTrackApp
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives widget-originated actions through explicit PendingIntents.
 * This receiver is deliberately non-exported so other apps cannot forge widget actions.
 */
class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SmsScanWidgetProvider.ACTION_TRIGGER_SCAN -> {
                if (isAppLocked(context)) {
                    openAppForUnlock(context)
                    return
                }
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetBackgroundScanner.performBackgroundScan(context)
                    } finally {
                        result.finish()
                    }
                }
            }
            SmsScanWidgetProvider.ACTION_NEXT_TX -> WidgetStatusHelper.nextTransaction(context)
            SmsScanWidgetProvider.ACTION_PREV_TX -> WidgetStatusHelper.prevTransaction(context)
            SmsScanWidgetProvider.ACTION_SWITCH_CATEGORY_PAGE -> WidgetStatusHelper.toggleCategoryPage(context)
            SmsScanWidgetProvider.ACTION_REGISTER_ITEM,
            SmsScanWidgetProvider.ACTION_ITEM_CLICK -> {
                if (isAppLocked(context)) {
                    openAppForUnlock(context)
                    return
                }
                if (intent.getStringExtra("widget_action") == "PICK_CATEGORY") {
                    val picker = Intent(context, WidgetCategoryPickerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtras(intent)
                    }
                    context.startActivity(picker)
                } else {
                    val result = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            WidgetQuickRegister.registerFromIntent(context, intent)
                        } finally {
                            result.finish()
                        }
                    }
                }
            }
        }
    }

    private fun isAppLocked(context: Context): Boolean {
        val app = context.applicationContext as? FinTrackApp ?: return false
        val lock = app.appModule.appLockRepository
        lock.onAppForegrounded()
        return lock.lockEnabled.value && lock.locked.value
    }

    private fun openAppForUnlock(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        Toast.makeText(context, "برای ثبت تراکنش، ابتدا قفل برنامه را باز کنید", Toast.LENGTH_SHORT).show()
    }
}
