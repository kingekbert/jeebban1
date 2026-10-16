package com.example.ui.screens.scan_sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.AccountRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.GroupExpenseRepository
import com.example.domain.usecase.AddTransactionUseCase
import com.example.ui.screens.add_transaction.GroupShareInput
import com.example.util.AmountFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchedDong(
    val shareId: Long,
    val personName: String,
    val amount: Long,
    val parentNote: String,
    val isExact: Boolean = true,
    val diffPercent: Double = 0.0
)

data class PreConfirmUiState(
    val amount: Long = 0L,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val bankName: String? = null,
    val accountIdent: String? = null,
    val smsHash: String = "",
    val note: String = "",
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val errorMessage: String? = null,
    val isSavedSuccess: Boolean = false,
    // Group Expense (Dong)
    val isGroupExpenseEnabled: Boolean = false,
    val groupShareMode: GroupExpenseShareMode = GroupExpenseShareMode.EQUAL,
    val groupShares: List<GroupShareInput> = emptyList(),
    val recentPersonNames: List<String> = emptyList(),
    // Dong Settlement for Income
    val matchedDongs: List<MatchedDong> = emptyList(),
    val selectedMatchedDongId: Long? = null,
    val settleDongEnabled: Boolean = true
)

class PreConfirmViewModel(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val groupExpenseRepository: GroupExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreConfirmUiState())
    val uiState: StateFlow<PreConfirmUiState> = _uiState.asStateFlow()

    fun initFromItem(item: ScannedSmsItem?) {
        val targetItem = item ?: ScanSmsDataHolder.selectedItem ?: return
        val finalAmount = targetItem.parsedSms.amount ?: 0L
        val type = targetItem.parsedSms.transactionType ?: TransactionType.EXPENSE
        val rawDate = targetItem.parsedSms.date ?: targetItem.date
        val finalDate = if (rawDate > 0L) rawDate else System.currentTimeMillis()
        val finalBankName = targetItem.parsedSms.bankName
        val finalAccountIdent = targetItem.parsedSms.accountIdentifier
        val finalSmsHash = targetItem.smsHash
        val finalNote = targetItem.parsedSms.rawDescription ?: ""

        _uiState.update {
            it.copy(
                amount = finalAmount,
                transactionType = type,
                dateTimestamp = finalDate,
                bankName = finalBankName?.ifBlank { null },
                accountIdent = finalAccountIdent?.ifBlank { null },
                smsHash = finalSmsHash,
                note = finalNote,
                isGroupExpenseEnabled = false,
                groupShares = emptyList()
            )
        }

        if (type == TransactionType.INCOME && finalAmount > 0L) {
            checkForMatchingDongs(finalAmount)
        }

        viewModelScope.launch {
            try {
                val allAccs = try { accountRepository.allAccounts.first() } catch (e: Exception) { emptyList() }
                var matchedAccount: AccountEntity? = null

                if (!finalAccountIdent.isNullOrBlank()) {
                    matchedAccount = allAccs.find { acc ->
                        (acc.cardNumber != null && acc.cardNumber.contains(finalAccountIdent)) ||
                        (acc.shabaNumber != null && acc.shabaNumber.contains(finalAccountIdent)) ||
                        acc.name.contains(finalAccountIdent)
                    }
                }

                if (matchedAccount == null && !finalBankName.isNullOrBlank()) {
                    matchedAccount = allAccs.find { acc ->
                        acc.name.contains(finalBankName, ignoreCase = true) ||
                        (finalBankName.contains("ملی") && acc.name.contains("ملی")) ||
                        (finalBankName.contains("رسالت") && acc.name.contains("رسالت")) ||
                        (finalBankName.contains("تجارت") && acc.name.contains("تجارت")) ||
                        (finalBankName.contains("ملت") && acc.name.contains("ملت")) ||
                        (finalBankName.contains("دی") && acc.name.contains("دی"))
                    }
                }

                val defaultAccId = matchedAccount?.id ?: allAccs.firstOrNull()?.id

                val allCats = try { categoryRepository.allCategories.first() } catch (e: Exception) { emptyList() }
                val filteredCats = allCats.filter {
                    when (type) {
                        TransactionType.EXPENSE -> it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH
                        TransactionType.INCOME -> it.type == CategoryType.INCOME || it.type == CategoryType.BOTH
                        else -> true
                    }
                }
                val recentNames = try { groupExpenseRepository.recentPersonNames.first() } catch (e: Exception) { emptyList() }

                val fullText = "$finalNote ${targetItem.rawText}"
                val guessedCatId = guessCategoryId(fullText, filteredCats)
                val selectedCatId = guessedCatId ?: filteredCats.firstOrNull()?.id

                _uiState.update {
                    it.copy(
                        accounts = allAccs,
                        selectedAccountId = defaultAccId,
                        categories = filteredCats,
                        selectedCategoryId = selectedCatId,
                        recentPersonNames = recentNames
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "خطا در بارگذاری حساب‌ها و دسته‌بندی‌ها") }
            }
        }
    }

    private fun checkForMatchingDongs(amount: Long) {
        viewModelScope.launch {
            try {
                val matches = groupExpenseRepository.findMatchingOpenShares(amount)
                val mapped = matches.map { result ->
                    MatchedDong(
                        shareId = result.share.id,
                        personName = result.share.personName,
                        amount = result.share.amount,
                        parentNote = result.parentNote,
                        isExact = result.isExact,
                        diffPercent = result.diffPercent
                    )
                }
                _uiState.update {
                    it.copy(
                        matchedDongs = mapped,
                        selectedMatchedDongId = mapped.firstOrNull()?.shareId,
                        settleDongEnabled = mapped.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                // Ignore matching error gracefully
            }
        }
    }

    fun setSelectedAccount(accountId: Long) {
        _uiState.update { it.copy(selectedAccountId = accountId) }
    }

    fun setSelectedCategory(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun setTransactionType(type: TransactionType) {
        viewModelScope.launch {
            val allCats = categoryRepository.allCategories.first()
            val filteredCats = allCats.filter {
                when (type) {
                    TransactionType.EXPENSE -> it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH
                    TransactionType.INCOME -> it.type == CategoryType.INCOME || it.type == CategoryType.BOTH
                    else -> true
                }
            }
            _uiState.update {
                it.copy(
                    transactionType = type,
                    categories = filteredCats,
                    selectedCategoryId = if (filteredCats.any { c -> c.id == it.selectedCategoryId }) {
                        it.selectedCategoryId
                    } else {
                        filteredCats.firstOrNull()?.id
                    },
                    isGroupExpenseEnabled = if (type == TransactionType.EXPENSE) it.isGroupExpenseEnabled else false
                )
            }

            if (type == TransactionType.INCOME && _uiState.value.amount > 0L) {
                checkForMatchingDongs(_uiState.value.amount)
            } else {
                _uiState.update { it.copy(matchedDongs = emptyList(), selectedMatchedDongId = null) }
            }
        }
    }

    fun setDateTimestamp(timestamp: Long) {
        _uiState.update { it.copy(dateTimestamp = timestamp) }
    }

    // --- Group Expense (Dong) Functions ---
    fun setGroupExpenseEnabled(enabled: Boolean) {
        _uiState.update { current ->
            val updatedShares = if (enabled && current.groupShareMode == GroupExpenseShareMode.EQUAL && current.groupShares.isNotEmpty() && current.amount > 0) {
                recalculateEqualShares(current.groupShares, current.amount)
            } else current.groupShares

            current.copy(
                isGroupExpenseEnabled = enabled,
                groupShares = updatedShares
            )
        }
    }

    fun setGroupShareMode(mode: GroupExpenseShareMode) {
        _uiState.update { current ->
            val updatedShares = if (mode == GroupExpenseShareMode.EQUAL && current.groupShares.isNotEmpty() && current.amount > 0) {
                recalculateEqualShares(current.groupShares, current.amount)
            } else current.groupShares

            current.copy(
                groupShareMode = mode,
                groupShares = updatedShares,
                errorMessage = null
            )
        }
    }

    fun addGroupPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        _uiState.update { current ->
            if (current.groupShares.any { it.name.equals(trimmed, ignoreCase = true) }) {
                return@update current
            }
            val newList = current.groupShares + GroupShareInput(name = trimmed)
            val updated = if (current.groupShareMode == GroupExpenseShareMode.EQUAL && current.amount > 0) {
                recalculateEqualShares(newList, current.amount)
            } else newList

            current.copy(groupShares = updated, errorMessage = null)
        }
    }

    fun removeGroupPerson(id: String) {
        _uiState.update { current ->
            val newList = current.groupShares.filterNot { it.id == id }
            val updated = if (current.groupShareMode == GroupExpenseShareMode.EQUAL && current.amount > 0 && newList.isNotEmpty()) {
                recalculateEqualShares(newList, current.amount)
            } else newList

            current.copy(groupShares = updated, errorMessage = null)
        }
    }

    fun updateGroupPersonAmount(id: String, amount: Long) {
        _uiState.update { current ->
            val newList = current.groupShares.map {
                if (it.id == id) it.copy(amount = amount) else it
            }
            current.copy(groupShares = newList, errorMessage = null)
        }
    }

    private fun recalculateEqualShares(shares: List<GroupShareInput>, totalAmount: Long): List<GroupShareInput> {
        if (shares.isEmpty()) return shares
        // User themselves is part of the consumers, so total people = friends + 1 (user)
        val count = shares.size + 1
        val baseShare = totalAmount / count
        val remainder = totalAmount % count

        return shares.mapIndexed { index, item ->
            // First friend receives remainder to strictly ensure sum equals exact Rial
            val amount = if (index == 0) baseShare + remainder else baseShare
            item.copy(amount = amount)
        }
    }

    // --- Matched Dong Settlement Functions ---
    fun setSettleDongEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settleDongEnabled = enabled) }
    }

    fun selectMatchedDong(shareId: Long) {
        _uiState.update { it.copy(selectedMatchedDongId = shareId) }
    }

    fun saveTransaction() {
        val currentState = _uiState.value
        val amt = currentState.amount
        val accId = currentState.selectedAccountId
        val catId = currentState.selectedCategoryId

        if (amt <= 0L) {
            _uiState.update { it.copy(errorMessage = "مبلغ معتبر نیست") }
            return
        }
        if (accId == null) {
            _uiState.update { it.copy(errorMessage = "لطفاً حساب را انتخاب کنید") }
            return
        }
        if (catId == null) {
            _uiState.update { it.copy(errorMessage = "لطفاً دسته را انتخاب کنید") }
            return
        }

        // Validate Group Expense
        if (currentState.transactionType == TransactionType.EXPENSE && currentState.isGroupExpenseEnabled) {
            if (currentState.groupShares.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "لطفاً حداقل نام یک دوست را برای هزینه مشترک وارد کنید") }
                return
            }
            if (currentState.groupShares.any { it.name.isBlank() }) {
                _uiState.update { it.copy(errorMessage = "نام تمام افراد در هزینه مشترک باید مشخص باشد") }
                return
            }
            if (currentState.groupShareMode == GroupExpenseShareMode.CUSTOM) {
                val sumFriendsShares = currentState.groupShares.sumOf { it.amount }
                if (sumFriendsShares > currentState.amount) {
                    val diff = sumFriendsShares - currentState.amount
                    val diffText = AmountFormatter.formatAmountWithCurrency(diff)
                    _uiState.update { it.copy(errorMessage = "مجموع سهم دوستان بیشتر از کل مبلغ هزینه است ($diffText مازاد)") }
                    return
                }
            }
        }

        viewModelScope.launch {
            try {
                val transaction = TransactionEntity(
                    type = currentState.transactionType,
                    amount = amt,
                    accountId = accId,
                    categoryId = catId,
                    date = currentState.dateTimestamp,
                    note = currentState.note.ifBlank { null },
                    smsHash = currentState.smsHash.ifBlank { null }
                )

                // If Income and Settle Dong is enabled with matched share, settle atomically
                if (currentState.transactionType == TransactionType.INCOME && currentState.settleDongEnabled && currentState.selectedMatchedDongId != null) {
                    groupExpenseRepository.settleShareWithIncomeTransaction(
                        shareId = currentState.selectedMatchedDongId,
                        incomeTransaction = transaction
                    )
                    _uiState.update { it.copy(isSavedSuccess = true) }
                    return@launch
                }

                // If Group Expense is enabled for EXPENSE
                if (currentState.transactionType == TransactionType.EXPENSE && currentState.isGroupExpenseEnabled && currentState.groupShares.isNotEmpty()) {
                    val finalShares = if (currentState.groupShareMode == GroupExpenseShareMode.EQUAL) {
                        recalculateEqualShares(currentState.groupShares, currentState.amount)
                    } else currentState.groupShares

                    groupExpenseRepository.saveExpenseWithShares(
                        expenseTransaction = transaction,
                        shareMode = currentState.groupShareMode,
                        shares = finalShares.map { it.name to it.amount }
                    )
                    _uiState.update { it.copy(isSavedSuccess = true) }
                    return@launch
                }

                // Standard transaction registration
                val res = addTransactionUseCase(transaction)
                if (res.isSuccess) {
                    _uiState.update { it.copy(isSavedSuccess = true) }
                } else {
                    _uiState.update {
                        it.copy(errorMessage = res.exceptionOrNull()?.message ?: "خطا در ثبت تراکنش")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "خطا در ذخیره‌سازی تراکنش")
                }
            }
        }
    }

    fun quickSaveTransaction() {
        val currentState = _uiState.value
        if (currentState.selectedCategoryId == null && currentState.categories.isNotEmpty()) {
            val guessed = guessCategoryId("${currentState.note} ${currentState.bankName}", currentState.categories)
            val finalCatId = guessed ?: currentState.categories.first().id
            _uiState.update { it.copy(selectedCategoryId = finalCatId) }
        }
        if (currentState.selectedAccountId == null && currentState.accounts.isNotEmpty()) {
            _uiState.update { it.copy(selectedAccountId = currentState.accounts.first().id) }
        }
        saveTransaction()
    }

    private fun guessCategoryId(
        text: String,
        categories: List<CategoryEntity>
    ): Long? {
        if (text.isBlank() || categories.isEmpty()) return null
        val cleanText = text.replace(Regex("[\\u200c\\s]+"), " ").trim()

        // 1. Direct match with user categories
        for (cat in categories) {
            val cleanCat = cat.name.replace(Regex("[\\u200c\\s]+"), " ").trim()
            if (cleanCat.isNotBlank() && cleanText.contains(cleanCat, ignoreCase = true)) {
                return cat.id
            }
        }

        // 2. Common category synonyms mapping for Iranian bank SMS
        val synonyms = mapOf(
            listOf("خوراک", "غذا", "سوپرمارکت", "رستوران") to listOf(
                "غذا", "رستوران", "کافه", "قهوه", "نان", "سوپر", "هایپر", "میوه",
                "اسنپ فود", "اسنپفود", "فست فود", "خوراک", "پروتئین", "قصابی", "قنادی", "شیرینی"
            ),
            listOf("حمل و نقل", "تاکسی", "خودرو", "بنزین") to listOf(
                "اسنپ", "تپسی", "بنزین", "پمپ بنزین", "جایگاه", "گازوئیل", "کرایه", "تاکسی",
                "مترو", "اتوبوس", "حمل", "نقل", "عوارض", "پارکینگ", "تعمیرگاه"
            ),
            listOf("قبوض", "خانه", "ارتباطات") to listOf(
                "قبض", "برق", "گاز", "آب", "مخابرات", "همراه اول", "ایرانسل", "رایتل",
                "اینترنت", "شارژ", "همراه", "آسیاتک", "شاتل"
            ),
            listOf("سلامت", "درمان", "پزشکی") to listOf(
                "دارو", "درمان", "پزشک", "دکتر", "بیمارستان", "درمانگاه", "کلینیک",
                "آزمایشگاه", "دندانپزشک", "داروخانه", "ویزیت", "فیزیوتراپی"
            ),
            listOf("پوشاک", "لباس") to listOf(
                "پوشاک", "لباس", "کفش", "شلوار", "پیراهن", "کت", "بوتیک", "کیف"
            ),
            listOf("تفریح", "ورزش") to listOf(
                "سینما", "تئاتر", "کتاب", "بازی", "ورزش", "باشگاه", "استخر", "تفریح", "شهربازی"
            ),
            listOf("خرید", "عمومی") to listOf(
                "دیجی کالا", "دیجیکالا", "خرید", "پوز", "شاپرک", "فروشگاه"
            ),
            listOf("حقوق", "درآمد") to listOf(
                "حقوق", "پاداش", "دستمزد", "عیدی", "مساعده", "واریز حقوق"
            )
        )

        for ((targetCatNames, keywords) in synonyms) {
            for (kw in keywords) {
                if (cleanText.contains(kw, ignoreCase = true)) {
                    val matched = categories.find { cat ->
                        targetCatNames.any { target -> cat.name.contains(target, ignoreCase = true) }
                    }
                    if (matched != null) return matched.id
                }
            }
        }

        return null
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository,
        private val addTransactionUseCase: AddTransactionUseCase,
        private val groupExpenseRepository: GroupExpenseRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PreConfirmViewModel(
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                addTransactionUseCase = addTransactionUseCase,
                groupExpenseRepository = groupExpenseRepository
            ) as T
        }
    }
}
