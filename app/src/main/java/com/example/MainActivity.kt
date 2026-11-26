package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.navigation.NavGraph
import com.example.ui.screens.security.AppLockScreen
import com.example.ui.theme.FinTrackTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val navigateToWidgetScanState = mutableStateOf(false)
    private val navigateToScanSmsState = mutableStateOf(false)
    private val sharedSmsTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        val app = application as FinTrackApp
        val appLockRepository = app.appModule.appLockRepository

        // Apply FLAG_SECURE immediately before rendering first frame if security/lock is active
        updateSecureWindowFlag(appLockRepository.shouldApplySecureFlag())

        // Keep FLAG_SECURE reactively synchronized with lock and screen-security preferences
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    appLockRepository.lockEnabled,
                    appLockRepository.secureScreenEnabled,
                    appLockRepository.locked
                ) { lockEnabled, secureScreenEnabled, locked ->
                    lockEnabled || secureScreenEnabled || locked
                }.collect { shouldSecure ->
                    updateSecureWindowFlag(shouldSecure)
                }
            }
        }

        setContent {
            val themeModeState = remember {
                mutableStateOf(app.appModule.settingsRepository.themeMode)
            }
            val darkTheme = when (themeModeState.value) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            val isLocked by appLockRepository.locked.collectAsStateWithLifecycle()
            var hasUnlockedInSession by remember { mutableStateOf(!isLocked) }

            LaunchedEffect(isLocked) {
                if (!isLocked) {
                    hasUnlockedInSession = true
                }
            }

            FinTrackTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasUnlockedInSession) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isLocked) Modifier.clearAndSetSemantics {} else Modifier
                                )
                        ) {
                            NavGraph(
                                app = app,
                                initialSharedSmsText = sharedSmsTextState.value,
                                navigateToScanSms = navigateToScanSmsState.value,
                                navigateToWidgetScan = navigateToWidgetScanState.value,
                                onThemeChanged = { newMode ->
                                    themeModeState.value = newMode
                                    com.example.widget.WidgetStatusHelper.updateAllWidgets(this@MainActivity)
                                }
                            )
                        }
                    }

                    if (isLocked) {
                        AppLockScreen(
                            onUnlock = { appLockRepository.unlock() },
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(100f)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as FinTrackApp
        updateSecureWindowFlag(app.appModule.appLockRepository.shouldApplySecureFlag())
    }

    override fun onResume() {
        super.onResume()
        com.example.widget.WidgetStatusHelper.updateAllWidgets(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun updateSecureWindowFlag(shouldSecure: Boolean) {
        if (shouldSecure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
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
