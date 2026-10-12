package com.example.ui.screens.pattern_learner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.ExtractedField
import com.example.data.local.entity.FieldType
import com.example.data.local.entity.SmsPatternEntity
import com.example.data.repository.AccountRepository
import com.example.data.repository.SmsPatternRepository
import com.example.util.PatternExtractor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmsPatternLearnerUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountId: Long? = null,
    val accountIdentifier: String = "",
    val sampleSms: String = "",
    val bankName: String = "",
    val extractedFields: List<ExtractedField> = emptyList(),
    val existingPatterns: List<SmsPatternEntity> = emptyList(),
    val editingPatternId: Long? = null,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

class SmsPatternLearnerViewModel(
    private val smsPatternRepository: SmsPatternRepository,
    private val accountRepository: AccountRepository,
    initialAccountId: Long? = null,
    initialSampleText: String? = null,
    initialPatternId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SmsPatternLearnerUiState(
            selectedAccountId = initialAccountId,
            sampleSms = initialSampleText ?: ""
        )
    )
    val uiState: StateFlow<SmsPatternLearnerUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        if (initialPatternId != null && initialPatternId > 0L) {
            viewModelScope.launch {
                val pattern = smsPatternRepository.getById(initialPatternId).firstOrNull()
                if (pattern != null) {
                    editPattern(pattern)
                }
            }
        } else if (!initialSampleText.isNullOrBlank()) {
            analyzeSms()
        }

        viewModelScope.launch {
            accountRepository.allAccounts.collect { accList ->
                _uiState.update { state ->
                    val selAcc = accList.find { it.id == state.selectedAccountId }
                    val autoIdent = if (state.accountIdentifier.isBlank()) (selAcc?.shabaNumber ?: selAcc?.cardNumber ?: "") else state.accountIdentifier
                    val autoBank = if (state.bankName.isBlank()) (selAcc?.name ?: "") else state.bankName
                    state.copy(
                        accounts = accList,
                        accountIdentifier = autoIdent,
                        bankName = autoBank
                    )
                }
            }
        }

        viewModelScope.launch {
            smsPatternRepository.allPatterns.collect { patterns ->
                _uiState.update { it.copy(existingPatterns = patterns) }
            }
        }
    }

    fun selectAccount(accountId: Long) {
        val acc = _uiState.value.accounts.find { it.id == accountId }
        val ident = acc?.shabaNumber?.takeIf { it.isNotBlank() } ?: acc?.cardNumber ?: ""
        val bank = acc?.name ?: ""
        _uiState.update {
            it.copy(
                selectedAccountId = accountId,
                accountIdentifier = if (it.accountIdentifier.isBlank()) ident else it.accountIdentifier,
                bankName = if (it.bankName.isBlank()) bank else it.bankName
            )
        }
    }

    fun setSampleSms(sms: String) {
        _uiState.update { it.copy(sampleSms = sms, errorMessage = null) }
    }

    fun setAccountIdentifier(identifier: String) {
        _uiState.update { it.copy(accountIdentifier = identifier) }
    }

    fun setBankName(name: String) {
        _uiState.update { it.copy(bankName = name) }
    }

    fun analyzeSms() {
        val text = _uiState.value.sampleSms.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(errorMessage = "لطفاً متن پیامک نمونه را وارد کنید.") }
            return
        }

        val parts = PatternExtractor.extractParts(text)
        if (parts.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "امکان استخراج بخش‌های پیامک وجود ندارد.") }
            return
        }

        _uiState.update {
            it.copy(
                extractedFields = parts,
                errorMessage = null
            )
        }
    }

    fun updateFieldType(index: Int, fieldType: FieldType) {
        val current = _uiState.value.extractedFields.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(fieldType = fieldType)
            _uiState.update { it.copy(extractedFields = current) }
        }
    }

    fun editPattern(pattern: SmsPatternEntity) {
        val typeToken = object : TypeToken<List<ExtractedField>>() {}.type
        var parsedFields: List<ExtractedField> = try {
            gson.fromJson(pattern.extractedFields, typeToken) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // If JSON fields empty or invalid, re-extract from sample text
        if (parsedFields.isEmpty() && pattern.sampleSms.isNotBlank()) {
            parsedFields = PatternExtractor.extractParts(pattern.sampleSms)
        }

        _uiState.update {
            it.copy(
                editingPatternId = pattern.id,
                sampleSms = pattern.sampleSms,
                accountIdentifier = pattern.accountIdentifier,
                bankName = pattern.bankName ?: "",
                extractedFields = parsedFields,
                errorMessage = null
            )
        }
    }

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingPatternId = null,
                sampleSms = "",
                extractedFields = emptyList(),
                errorMessage = null
            )
        }
    }

    fun savePattern() {
        val state = _uiState.value
        if (state.sampleSms.isBlank()) {
            _uiState.update { it.copy(errorMessage = "متن پیامک نمونه نباید خالی باشد.") }
            return
        }

        if (state.extractedFields.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "ابتدا روی دکمه «تحلیل» بزنید.") }
            return
        }

        val hasAmount = state.extractedFields.any { it.fieldType == FieldType.AMOUNT }
        if (!hasAmount) {
            _uiState.update { it.copy(errorMessage = "لطفاً بخش مربوط به «مبلغ تراکنش» را مشخص کنید.") }
            return
        }

        val jsonFields = gson.toJson(state.extractedFields)
        val entity = SmsPatternEntity(
            id = state.editingPatternId ?: 0L,
            accountIdentifier = state.accountIdentifier.trim(),
            sampleSms = state.sampleSms.trim(),
            extractedFields = jsonFields,
            bankName = state.bankName.trim().ifBlank { null },
            isActive = true
        )

        viewModelScope.launch {
            try {
                if (state.editingPatternId != null) {
                    smsPatternRepository.updatePattern(entity)
                } else {
                    smsPatternRepository.insertPattern(entity)
                }
                _uiState.update {
                    it.copy(
                        isSuccess = true,
                        editingPatternId = null,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "خطا در ذخیره الگو: ${e.localizedMessage}") }
            }
        }
    }

    fun deletePattern(id: Long) {
        viewModelScope.launch {
            smsPatternRepository.deleteById(id)
            if (_uiState.value.editingPatternId == id) {
                cancelEdit()
            }
        }
    }

    class Factory(
        private val smsPatternRepository: SmsPatternRepository,
        private val accountRepository: AccountRepository,
        private val accountId: Long? = null,
        private val initialSampleText: String? = null,
        private val initialPatternId: Long? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SmsPatternLearnerViewModel(smsPatternRepository, accountRepository, accountId, initialSampleText, initialPatternId) as T
        }
    }
}
