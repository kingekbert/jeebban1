package com.example.ui.screens.group_expenses

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.relation.GroupExpenseWithShares
import com.example.data.repository.AccountRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.GroupExpenseRepository
import com.example.util.AmountFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettleMode {
    WITH_TRANSACTION,    // ① تسویه با تراکنش واریز
    WITHOUT_TRANSACTION, // ② تسویه بدون تراکنش
    PARTIAL              // ③ تسویه جزئی
}

data class PersonOpenDong(
    val shareId: Long,
    val groupExpenseId: Long,
    val amount: Long,
    val parentNote: String,
    val transactionDate: Long,
    val isSettled: Boolean = false,
    val shareEntity: GroupExpenseShareEntity,
    val expenseWithShares: GroupExpenseWithShares
)

data class PersonDebtSummary(
    val personName: String,
    val openDongsCount: Int,
    val totalDebt: Long,
    val openDongs: List<PersonOpenDong>
)

data class SettleTarget(
    val isForPerson: Boolean,
    val personName: String,
    val totalDebt: Long,
    val singleShare: GroupExpenseShareEntity? = null,
    val singleExpense: GroupExpenseWithShares? = null,
    val openDongs: List<PersonOpenDong> = emptyList()
)

data class GroupExpensesUiState(
    val groupExpenses: List<GroupExpenseWithShares> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val personSummaries: List<PersonDebtSummary> = emptyList(),
    val filterOnlyUnsettled: Boolean = true,
    val selectedTab: Int = 0, // 0: صورت‌حساب افراد, 1: تمام هزینه‌ها
    val totalPendingAmount: Long = 0L,
    val totalSettledAmount: Long = 0L,
    val pendingPeopleCount: Int = 0,
    val isLoading: Boolean = false,
    val userMessage: String? = null,

    // 3-Mode Settle Dialog state
    val settleTarget: SettleTarget? = null,
    val settleMode: SettleMode = SettleMode.WITH_TRANSACTION,
    val selectedIncomeAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val partialAmountInput: String = "",
    val createIncomeInPartial: Boolean = true,

    // Dialog state for un-settling
    val unsettlingShare: GroupExpenseShareEntity? = null
)

class GroupExpensesViewModel(
    private val groupExpenseRepository: GroupExpenseRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filterOnlyUnsettled = MutableStateFlow(true)
    private val _selectedTab = MutableStateFlow(0)
    private val _dialogState = MutableStateFlow(DialogState())
    private val _userMessage = MutableStateFlow<String?>(null)

    private data class DialogState(
        val settleTarget: SettleTarget? = null,
        val settleMode: SettleMode = SettleMode.WITH_TRANSACTION,
        val selectedIncomeAccountId: Long? = null,
        val selectedCategoryId: Long? = null,
        val partialAmountInput: String = "",
        val createIncomeInPartial: Boolean = true,
        val unsettlingShare: GroupExpenseShareEntity? = null
    )

    private val _filterTabAndMsg = combine(_filterOnlyUnsettled, _selectedTab, _userMessage) { onlyUnsettled, tab, msg ->
        Triple(onlyUnsettled, tab, msg)
    }

    val uiState: StateFlow<GroupExpensesUiState> = combine(
        groupExpenseRepository.allGroupExpenses,
        accountRepository.allAccounts,
        categoryRepository.allCategories,
        _filterTabAndMsg,
        _dialogState
    ) { expenses: List<GroupExpenseWithShares>, accounts: List<AccountEntity>, categories: List<CategoryEntity>, uiFilter: Triple<Boolean, Int, String?>, dialog: DialogState ->
        val (onlyUnsettled, tab, msg) = uiFilter
        val allSharesWithExpense = expenses.flatMap { exp ->
            val parentNote = exp.transaction?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
            val date = exp.transaction?.date ?: exp.groupExpense.createdAt
            exp.shares.map { share ->
                PersonOpenDong(
                    shareId = share.id,
                    groupExpenseId = exp.groupExpense.id,
                    amount = share.amount,
                    parentNote = parentNote,
                    transactionDate = date,
                    isSettled = share.isSettled,
                    shareEntity = share,
                    expenseWithShares = exp
                )
            }
        }

        val allShares = expenses.flatMap { it.shares }
        val pendingShares = allShares.filter { !it.isSettled }
        val settledShares = allShares.filter { it.isSettled }

        val totalPending = pendingShares.sumOf { it.amount }
        val totalSettled = settledShares.sumOf { it.amount }
        val pendingCount = pendingShares.size

        // Build person summaries: group open dangs by personName, sorted by debt descending
        val personSummaries = allSharesWithExpense
            .filter { !it.isSettled }
            .groupBy { it.shareEntity.personName }
            .map { (personName, dongs) ->
                val sortedDongs = dongs.sortedBy { it.transactionDate } // Oldest first
                PersonDebtSummary(
                    personName = personName,
                    openDongsCount = dongs.size,
                    totalDebt = dongs.sumOf { it.amount },
                    openDongs = sortedDongs
                )
            }
            .sortedByDescending { it.totalDebt }

        val filteredExpenses = if (onlyUnsettled) {
            expenses.filter { exp -> exp.shares.any { !it.isSettled } }
        } else {
            expenses
        }

        GroupExpensesUiState(
            groupExpenses = filteredExpenses,
            accounts = accounts,
            categories = categories,
            personSummaries = personSummaries,
            filterOnlyUnsettled = onlyUnsettled,
            selectedTab = tab,
            totalPendingAmount = totalPending,
            totalSettledAmount = totalSettled,
            pendingPeopleCount = pendingCount,
            userMessage = msg,
            settleTarget = dialog.settleTarget,
            settleMode = dialog.settleMode,
            selectedIncomeAccountId = dialog.selectedIncomeAccountId ?: accounts.firstOrNull()?.id,
            selectedCategoryId = dialog.selectedCategoryId,
            partialAmountInput = dialog.partialAmountInput,
            createIncomeInPartial = dialog.createIncomeInPartial,
            unsettlingShare = dialog.unsettlingShare
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GroupExpensesUiState()
    )

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setFilterOnlyUnsettled(onlyUnsettled: Boolean) {
        _filterOnlyUnsettled.value = onlyUnsettled
    }

    // Open settle dialog for a single share
    fun openSettleDialogForShare(share: GroupExpenseShareEntity, expense: GroupExpenseWithShares) {
        val parentNote = expense.transaction?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
        val date = expense.transaction?.date ?: expense.groupExpense.createdAt
        val singleDong = PersonOpenDong(
            shareId = share.id,
            groupExpenseId = expense.groupExpense.id,
            amount = share.amount,
            parentNote = parentNote,
            transactionDate = date,
            isSettled = share.isSettled,
            shareEntity = share,
            expenseWithShares = expense
        )

        _dialogState.update {
            it.copy(
                settleTarget = SettleTarget(
                    isForPerson = false,
                    personName = share.personName,
                    totalDebt = share.amount,
                    singleShare = share,
                    singleExpense = expense,
                    openDongs = listOf(singleDong)
                ),
                settleMode = SettleMode.WITH_TRANSACTION,
                partialAmountInput = share.amount.toString(),
                createIncomeInPartial = true
            )
        }
    }

    // Open settle dialog for an entire person (all open dangs)
    fun openSettleDialogForPerson(person: PersonDebtSummary) {
        _dialogState.update {
            it.copy(
                settleTarget = SettleTarget(
                    isForPerson = true,
                    personName = person.personName,
                    totalDebt = person.totalDebt,
                    singleShare = null,
                    singleExpense = null,
                    openDongs = person.openDongs
                ),
                settleMode = SettleMode.WITH_TRANSACTION,
                partialAmountInput = person.totalDebt.toString(),
                createIncomeInPartial = true
            )
        }
    }

    fun closeSettleDialog() {
        _dialogState.update {
            it.copy(
                settleTarget = null,
                partialAmountInput = ""
            )
        }
    }

    fun setSettleMode(mode: SettleMode) {
        _dialogState.update { it.copy(settleMode = mode) }
    }

    fun setSelectedIncomeAccountId(accountId: Long) {
        _dialogState.update { it.copy(selectedIncomeAccountId = accountId) }
    }

    fun setSelectedCategoryId(categoryId: Long?) {
        _dialogState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun setPartialAmountInput(input: String) {
        val clean = input.filter { it.isDigit() }
        _dialogState.update { it.copy(partialAmountInput = clean) }
    }

    fun setCreateIncomeInPartial(create: Boolean) {
        _dialogState.update { it.copy(createIncomeInPartial = create) }
    }

    fun confirmSettle() {
        val target = _dialogState.value.settleTarget ?: return
        val mode = _dialogState.value.settleMode
        val incomeAccountId = _dialogState.value.selectedIncomeAccountId
        val categoryId = _dialogState.value.selectedCategoryId
        val createTxInPartial = _dialogState.value.createIncomeInPartial

        viewModelScope.launch {
            try {
                when (mode) {
                    SettleMode.WITH_TRANSACTION -> {
                        if (target.isForPerson) {
                            val shareIds = target.openDongs.map { it.shareId }
                            groupExpenseRepository.settleMultipleShares(
                                shareIds = shareIds,
                                totalAmount = target.totalDebt,
                                incomeAccountId = incomeAccountId,
                                categoryId = categoryId,
                                customNote = "تسویه کل دنگ‌های ${target.personName}"
                            )
                            _userMessage.value = "تمام دنگ‌های ${target.personName} با ثبت تراکنش واریز تسویه شدند"
                        } else {
                            val share = target.singleShare ?: return@launch
                            groupExpenseRepository.settleShare(
                                shareId = share.id,
                                incomeAccountId = incomeAccountId,
                                categoryId = categoryId,
                                customNote = "تسویه دنگ ${share.personName}"
                            )
                            _userMessage.value = "دنگ ${share.personName} با موفقیت تسویه شد"
                        }
                    }
                    SettleMode.WITHOUT_TRANSACTION -> {
                        if (target.isForPerson) {
                            val shareIds = target.openDongs.map { it.shareId }
                            groupExpenseRepository.settleMultipleShares(
                                shareIds = shareIds,
                                totalAmount = 0L,
                                incomeAccountId = null,
                                categoryId = null,
                                customNote = null
                            )
                            _userMessage.value = "دنگ‌های ${target.personName} بدون ثبت تراکنش تسویه شدند"
                        } else {
                            val share = target.singleShare ?: return@launch
                            groupExpenseRepository.settleShare(
                                shareId = share.id,
                                incomeAccountId = null,
                                categoryId = null,
                                customNote = null
                            )
                            _userMessage.value = "دنگ ${share.personName} بدون ثبت تراکنش تسویه شد"
                        }
                    }
                    SettleMode.PARTIAL -> {
                        val amount = _dialogState.value.partialAmountInput.toLongOrNull() ?: 0L
                        if (amount <= 0) {
                            _userMessage.value = "لطفاً مبلغ معتبری برای تسویه جزئی وارد کنید"
                            return@launch
                        }
                        if (amount > target.totalDebt) {
                            _userMessage.value = "مبلغ وارد شده بیشتر از کل بدهی است"
                            return@launch
                        }

                        val targetAccountId = if (createTxInPartial) incomeAccountId else null
                        val targetCatId = if (createTxInPartial) categoryId else null

                        if (target.isForPerson) {
                            groupExpenseRepository.settlePartialPersonDebt(
                                personName = target.personName,
                                amountToSettle = amount,
                                incomeAccountId = targetAccountId,
                                categoryId = targetCatId,
                                customNote = "تسویه جزئی دنگ‌های ${target.personName}"
                            )
                            _userMessage.value = "مبلغ ${AmountFormatter.formatAmountWithCurrency(amount)} از دنگ‌های ${target.personName} تسویه شد"
                        } else {
                            val share = target.singleShare ?: return@launch
                            groupExpenseRepository.settlePartialShare(
                                shareId = share.id,
                                settleAmount = amount,
                                incomeAccountId = targetAccountId,
                                categoryId = targetCatId,
                                customNote = "تسویه جزئی دنگ ${share.personName}"
                            )
                            _userMessage.value = "مبلغ ${AmountFormatter.formatAmountWithCurrency(amount)} از دنگ ${share.personName} تسویه شد"
                        }
                    }
                }
                closeSettleDialog()
            } catch (e: Exception) {
                _userMessage.value = "خطا در تسویه: ${e.message}"
            }
        }
    }

    fun openUnsettleDialog(share: GroupExpenseShareEntity) {
        _dialogState.update { it.copy(unsettlingShare = share) }
    }

    fun closeUnsettleDialog() {
        _dialogState.update { it.copy(unsettlingShare = null) }
    }

    fun confirmUnsettleShare() {
        val share = _dialogState.value.unsettlingShare ?: return
        viewModelScope.launch {
            try {
                groupExpenseRepository.unsettleShare(share.id)
                _dialogState.update { it.copy(unsettlingShare = null) }
                _userMessage.value = "وضعیت دنگ ${share.personName} به تسویه‌نشده بازگردانده شد"
            } catch (e: Exception) {
                _userMessage.value = "خطا در بازگردانی وضعیت: ${e.message}"
            }
        }
    }

    fun shareSingleDong(context: Context, expense: GroupExpenseWithShares, share: GroupExpenseShareEntity) {
        val txTitle = expense.transaction?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
        val amountStr = AmountFormatter.formatAmountWithCurrency(share.amount)

        val account = uiState.value.accounts.find { it.id == expense.transaction?.accountId }
            ?: uiState.value.accounts.firstOrNull()

        val cardInfo = if (!account?.cardNumber.isNullOrBlank()) {
            "\nشماره کارت جهت واریز:\n${account?.cardNumber}"
        } else ""

        val text = """
            سلام ${share.personName} عزیز،
            دنگ شما از بابت «$txTitle» مبلغ $amountStr می‌باشد.$cardInfo

            با تشکر
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "ارسال دنگ به ${share.personName}")
        context.startActivity(shareIntent)
    }

    fun shareAllDongs(context: Context, expense: GroupExpenseWithShares) {
        val txTitle = expense.transaction?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
        val totalAmountStr = AmountFormatter.formatAmountWithCurrency(expense.transaction?.amount ?: 0L)

        val sharesList = expense.shares.joinToString("\n") {
            val status = if (it.isSettled) " (تسویه شده ✓)" else ""
            "- ${it.personName}: ${AmountFormatter.formatAmountWithCurrency(it.amount)}$status"
        }

        val text = """
            📋 صورت‌حساب هزینه مشترک: «$txTitle»
            مبلغ کل: $totalAmountStr

            سهم افراد:
            $sharesList
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "اشتراک‌گذاری صورت‌حساب دنگ‌ها")
        context.startActivity(shareIntent)
    }

    fun sharePersonStatement(context: Context, person: PersonDebtSummary) {
        val totalStr = AmountFormatter.formatAmountWithCurrency(person.totalDebt)
        val dongsList = person.openDongs.joinToString("\n") { dong ->
            val dateStr = com.example.util.DateFormatter.formatDate(dong.transactionDate)
            "- بابت «${dong.parentNote}» ($dateStr): ${AmountFormatter.formatAmountWithCurrency(dong.amount)}"
        }

        val text = """
            📋 صورت‌حساب دنگ‌های پرداخت‌نشده ${person.personName} عزیز:
            مجموع کل بدهی: $totalStr
            تعداد دنگ‌ها: ${person.openDongsCount}

            ریز دنگ‌ها:
            $dongsList

            با تشکر
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "ارسال صورت‌حساب به ${person.personName}")
        context.startActivity(shareIntent)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    class Factory(
        private val groupExpenseRepository: GroupExpenseRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupExpensesViewModel(groupExpenseRepository, accountRepository, categoryRepository) as T
        }
    }
}
