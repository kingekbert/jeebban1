package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.ui.navigation.NavGraph
import com.example.ui.theme.FinTrackTheme

class MainActivity : ComponentActivity() {
    private val navigateToWidgetScanState = mutableStateOf(false)
    private val navigateToScanSmsState = mutableStateOf(false)
    private val sharedSmsTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        val app = application as FinTrackApp

        setContent {
            val themeModeState = remember {
                mutableStateOf(app.appModule.settingsRepository.themeMode)
            }
            val darkTheme = when (themeModeState.value) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            FinTrackTheme(darkTheme = darkTheme) {
                NavGraph(
                    app = app,
                    initialSharedSmsText = sharedSmsTextState.value,
                    navigateToScanSms = navigateToScanSmsState.value,
                    navigateToWidgetScan = navigateToWidgetScanState.value,
                    onThemeChanged = { newMode ->
                        themeModeState.value = newMode
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        val autoScan = intent?.getBooleanExtra("extra_auto_scan", false) == true ||
                intent?.getStringExtra("navigate_to") == "widget_scan"
        val scanSms = intent?.getStringExtra("navigate_to") == "scan_sms"
        val sharedText = intent?.getStringExtra("shared_sms_text")

        navigateToWidgetScanState.value = autoScan
        navigateToScanSmsState.value = scanSms
        sharedSmsTextState.value = sharedText
    }
}
