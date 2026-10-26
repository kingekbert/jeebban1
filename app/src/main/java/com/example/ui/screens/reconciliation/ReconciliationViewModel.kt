package com.example.ui.screens.reconciliation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountBalanceSnapshotEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.AccountRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.ReconciliationRepository
import com.example.ui.screens.scan_sms.ScannedSmsItem
import com.example.util.ReconciliationCause
import com.example.util.ReconciliationHistoryItem
import com.example.widget.WidgetDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReconciliationUiState(
    val isLoading: Boolean = true,
    val account: AccountEntity? = null,
    val latestSnapshot: AccountBalanceSnapshotEntity? = null,
    val appBalance: Long = 0L,
    val bankBalance: Long = 0L,
    val diff: Long = 0L,
    val isSynced: Boolean = true,
    val cause: ReconciliationCause? = null,
    val history: List<ReconciliationHistoryItem> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val userMessage: String? = null,
    val isSuccessMessage: Boolean = true
)

class ReconciliationViewModel(
    val accountId: Long,
    private val accountRepository: AccountRepository,
    private val reconciliationRepository: ReconciliationRepository,
    private val categoryRepository: CategoryRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReconciliationUiState())
    val uiState: StateFlow<ReconciliationUiState> = _uiState.asStateFlow()

    init {
        loadDiagnosis()
    }

    fun loadDiagnosis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val account = accountRepository.getAccountById(accountId).first()
                val categories = try { categoryRepository.allCategories.first() } catch (e: Exception) { emptyList() }
                val candidates = try { WidgetDataStore.getItems(appContext) } catch (e: Exception) { emptyList() }

                val diag = reconciliationRepository.getReconciliationDiagnosis(accountId, candidates)
                if (diag != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            account = diag.account,
                            latestSnapshot = diag.latestSnapshot,
                            appBalance = diag.appBalance,
                            bankBalance = diag.bankBalance,
                            diff = diag.diff,
                            isSynced = diag.isSynced,
                            cause = diag.cause,
                            history = diag.history,
                            categories = categories
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            account = account,
                            categories = categories
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "خطا در بارگذاری اطلاعات تطبیق مانده: ${e.localizedMessage}",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    fun fixCandidate(item: ScannedSmsItem) {
        viewModelScope.launch {
            try {
                val ok = reconciliationRepository.fixUnregisteredTransactions(accountId, listOf(item))
                if (ok) {
                    WidgetDataStore.removeItem(appContext, item.smsHash)
                    _uiState.update {
                        it.copy(
                            userMessage = "تراکنش با موفقیت ثبت شد و مانده مجدداً محاسبه شد.",
                            isSuccessMessage = true
                        )
                    }
                    loadDiagnosis()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "خطا در ثبت تراکنش: ${e.localizedMessage}", isSuccessMessage = false)
                }
            }
        }
    }

    fun fixAllCandidates(items: List<ScannedSmsItem>) {
        viewModelScope.launch {
            try {
                val ok = reconciliationRepository.fixUnregisteredTransactions(accountId, items)
                if (ok) {
                    items.forEach { WidgetDataStore.removeItem(appContext, it.smsHash) }
                    _uiState.update {
                        it.copy(
                            userMessage = "${items.size} تراکنش کاندید ثبت شد و مانده همخوان گردید.",
                            isSuccessMessage = true
                        )
                    }
                    loadDiagnosis()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "خطا در ثبت تراکنش‌ها: ${e.localizedMessage}", isSuccessMessage = false)
                }
            }
        }
    }

    fun fixBankFee(feeAmount: Long, date: Long) {
        viewModelScope.launch {
            try {
                val ok = reconciliationRepository.fixBankFee(accountId, feeAmount, date)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            userMessage = "کارمزد بانکی ثبت شد و اختلاف مانده برطرف گردید.",
                            isSuccessMessage = true
                        )
                    }
                    loadDiagnosis()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "خطا در ثبت کارمزد: ${e.localizedMessage}", isSuccessMessage = false)
                }
            }
        }
    }

    fun fixInitialBalance(diff: Long) {
        viewModelScope.launch {
            try {
                val ok = reconciliationRepository.fixInitialBalance(accountId, diff)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            userMessage = "مانده اولیه حساب اصلاح شد و مانده همخوان گردید.",
                            isSuccessMessage = true
                        )
                    }
                    loadDiagnosis()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "خطا در اصلاح مانده اولیه: ${e.localizedMessage}", isSuccessMessage = false)
                }
            }
        }
    }

    fun fixManualAdjustment(
        amount: Long,
        type: TransactionType,
        categoryId: Long,
        note: String,
        date: Long
    ) {
        viewModelScope.launch {
            try {
                val ok = reconciliationRepository.fixManualAdjustment(accountId, amount, type, categoryId, note, date)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            userMessage = "تراکنش اصلاحی دستی ثبت شد و مانده به‌روزرسانی گردید.",
                            isSuccessMessage = true
                        )
                    }
                    loadDiagnosis()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(userMessage = "خطا در ثبت تراکنش اصلاحی: ${e.localizedMessage}", isSuccessMessage = false)
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    class Factory(
        private val accountId: Long,
        private val accountRepository: AccountRepository,
        private val reconciliationRepository: ReconciliationRepository,
        private val categoryRepository: CategoryRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReconciliationViewModel(
                accountId,
                accountRepository,
                reconciliationRepository,
                categoryRepository,
                appContext
            ) as T
        }
    }
}
