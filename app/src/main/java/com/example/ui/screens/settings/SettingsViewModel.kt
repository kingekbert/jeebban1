package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupManager
import com.example.data.local.entity.AccountEntity
import com.example.data.repository.AccountRepository
import com.example.data.repository.AppLockRepository
import com.example.data.repository.SettingsRepository
import com.example.util.Constants
import com.example.util.DateFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: String = "SYSTEM",
    val lastBackupTime: Long = 0L,
    val lastBackupTimeFormatted: String = "هرگز",
    val isBiometricEnabled: Boolean = false,
    val isSecureScreenEnabled: Boolean = false,
    val lockGraceMs: Long = Constants.DEFAULT_LOCK_GRACE_MS,
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val pendingRestoreJson: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
    private val backupManager: BackupManager,
    private val accountRepository: AccountRepository,
    private val onThemeChanged: (String) -> Unit = {}
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = settingsRepository.themeMode,
            lastBackupTime = settingsRepository.lastBackupTime,
            lastBackupTimeFormatted = DateFormatter.formatDateTime(settingsRepository.lastBackupTime),
            isBiometricEnabled = appLockRepository.lockEnabled.value,
            isSecureScreenEnabled = appLockRepository.secureScreenEnabled.value,
            lockGraceMs = appLockRepository.lockGraceMs.value
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        appLockRepository.setLockEnabled(enabled)
        _uiState.update {
            it.copy(
                isBiometricEnabled = enabled,
                userMessage = if (enabled) "قفل امنیتی برنامه فعال شد" else "قفل امنیتی برنامه غیرفعال شد",
                isErrorMessage = false
            )
        }
    }

    fun setLockGraceMs(graceMs: Long) {
        appLockRepository.setLockGraceMs(graceMs)
        _uiState.update {
            it.copy(
                lockGraceMs = graceMs,
                userMessage = "مهلت قفل مجدد ذخیره شد",
                isErrorMessage = false
            )
        }
    }

    fun setSecureScreenEnabled(enabled: Boolean) {
        appLockRepository.setSecureScreenEnabled(enabled)
        _uiState.update {
            it.copy(
                isSecureScreenEnabled = enabled,
                userMessage = if (enabled) "حفاظت صفحه در برنامه‌های اخیر فعال شد" else "حفاظت صفحه در برنامه‌های اخیر غیرفعال شد",
                isErrorMessage = false
            )
        }
    }

    fun showSecurityError(message: String) {
        _uiState.update {
            it.copy(
                userMessage = message,
                isErrorMessage = true
            )
        }
    }

    fun setThemeMode(mode: String) {
        settingsRepository.themeMode = mode
        onThemeChanged(mode)
        _uiState.update {
            it.copy(
                themeMode = mode,
                userMessage = "پوسته برنامه تغییر یافت"
            )
        }
    }

    suspend fun generateBackupJson(): String {
        return generatePlainBackupJson()
    }

    suspend fun generateEncryptedBackup(): ByteArray {
        val json = backupManager.createBackupJson()
        val encrypted = com.example.data.backup.BackupCrypto.encryptBackup(json)
        val now = System.currentTimeMillis()
        settingsRepository.lastBackupTime = now
        _uiState.update {
            it.copy(
                lastBackupTime = now,
                lastBackupTimeFormatted = DateFormatter.formatDateTime(now),
                userMessage = "نسخه پشتیبان امن (.ftb) با موفقیت ایجاد شد",
                isErrorMessage = false
            )
        }
        return encrypted
    }

    suspend fun generatePlainBackupJson(): String {
        val json = backupManager.createBackupJson()
        val now = System.currentTimeMillis()
        settingsRepository.lastBackupTime = now
        _uiState.update {
            it.copy(
                lastBackupTime = now,
                lastBackupTimeFormatted = DateFormatter.formatDateTime(now),
                userMessage = "نسخه پشتیبان قابل حمل (JSON) با موفقیت ایجاد شد",
                isErrorMessage = false
            )
        }
        return json
    }

    fun onBytesSelectedForRestore(bytes: ByteArray) {
        if (bytes.size > 25_000_000) {
            _uiState.update {
                it.copy(
                    userMessage = "حجم فایل انتخاب‌شده بیش از حد مجاز است.",
                    isErrorMessage = true
                )
            }
            return
        }

        when (val result = com.example.data.backup.BackupCrypto.decryptBackup(bytes)) {
            is com.example.data.backup.DecryptResult.Success -> {
                validateAndPrepareRestore(result.jsonString)
            }
            is com.example.data.backup.DecryptResult.LegacyPlainJson -> {
                validateAndPrepareRestore(result.jsonString)
            }
            is com.example.data.backup.DecryptResult.Error -> {
                _uiState.update {
                    it.copy(
                        userMessage = result.message,
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    fun onFileSelectedForRestore(jsonString: String) {
        onTextSelectedForRestore(jsonString)
    }

    fun onTextSelectedForRestore(rawText: String) {
        if (rawText.length > 25_000_000) {
            _uiState.update {
                it.copy(
                    userMessage = "حجم متن وارد‌شده بیش از حد مجاز است.",
                    isErrorMessage = true
                )
            }
            return
        }

        when (val result = com.example.data.backup.BackupCrypto.decryptFromString(rawText)) {
            is com.example.data.backup.DecryptResult.Success -> {
                validateAndPrepareRestore(result.jsonString)
            }
            is com.example.data.backup.DecryptResult.LegacyPlainJson -> {
                validateAndPrepareRestore(result.jsonString)
            }
            is com.example.data.backup.DecryptResult.Error -> {
                _uiState.update {
                    it.copy(
                        userMessage = result.message,
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    private fun validateAndPrepareRestore(jsonString: String) {
        when (val validation = backupManager.validateBackupDetailed(jsonString)) {
            is com.example.data.backup.BackupValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        userMessage = validation.reason,
                        isErrorMessage = true
                    )
                }
            }
            is com.example.data.backup.BackupValidationResult.Valid -> {
                _uiState.update {
                    it.copy(
                        pendingRestoreJson = jsonString,
                        showRestoreDialog = true
                    )
                }
            }
        }
    }

    fun confirmRestore() {
        val currentState = _uiState.value
        if (currentState.isLoading) return
        val jsonString = currentState.pendingRestoreJson ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showRestoreDialog = false) }

            try {
                // 1. Auto backup current state before restoring
                backupManager.createBackupJson()

                // 2. Perform validated atomic restoration
                when (val restoreResult = backupManager.restoreBackupDetailed(jsonString)) {
                    is com.example.data.backup.BackupRestoreResult.Success -> {
                        val now = System.currentTimeMillis()
                        settingsRepository.lastBackupTime = now
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastBackupTime = now,
                                lastBackupTimeFormatted = DateFormatter.formatDateTime(now),
                                pendingRestoreJson = null,
                                userMessage = "اطلاعات با موفقیت بازیابی شدند (پشتیبان‌گیری خودکار انجام شد)",
                                isErrorMessage = false
                            )
                        }
                    }
                    is com.example.data.backup.BackupRestoreResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pendingRestoreJson = null,
                                userMessage = restoreResult.reason,
                                isErrorMessage = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingRestoreJson = null,
                        userMessage = "خطایی رخ داد: ${e.localizedMessage}",
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    fun dismissRestoreDialog() {
        _uiState.update {
            it.copy(
                showRestoreDialog = false,
                pendingRestoreJson = null
            )
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val appLockRepository: AppLockRepository,
        private val backupManager: BackupManager,
        private val accountRepository: AccountRepository,
        private val onThemeChanged: (String) -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsRepository = settingsRepository,
                appLockRepository = appLockRepository,
                backupManager = backupManager,
                accountRepository = accountRepository,
                onThemeChanged = onThemeChanged
            ) as T
        }
    }
}
