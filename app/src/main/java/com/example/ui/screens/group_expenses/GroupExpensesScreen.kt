package com.example.ui.screens.group_expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.relation.GroupExpenseWithShares
import com.example.ui.components.EmptyState
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupExpensesScreen(
    viewModel: GroupExpensesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("هزینه‌های مشترک (دنگ)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary Cards Header - Always stays at top
            GroupExpenseSummaryHeader(
                totalPending = state.totalPendingAmount,
                pendingCount = state.pendingPeopleCount,
                totalSettled = state.totalSettledAmount
            )

            // Primary Tabs: صورت‌حساب افراد (Item 1) vs لیست تمام هزینه‌ها
            TabRow(
                selectedTabIndex = state.selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = {
                        Text(
                            "صورت‌حساب افراد (${state.personSummaries.size})",
                            fontWeight = if (state.selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = {
                        Text(
                            "لیست هزینه‌ها",
                            fontWeight = if (state.selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }

            if (state.selectedTab == 0) {
                // Tab 0: صورت‌حساب هر شخص در هزینه‌های مشترک
                if (state.personSummaries.isEmpty()) {
                    EmptyState(
                        title = "تمام دنگ‌ها تسویه شده‌اند و هیچ بدهی بازی وجود ندارد",
                        icon = Icons.Default.Groups,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = state.personSummaries,
                            key = { it.personName }
                        ) { person ->
                            PersonDebtCard(
                                person = person,
                                onSettlePerson = { viewModel.openSettleDialogForPerson(person) },
                                onSettleDong = { dong ->
                                    viewModel.openSettleDialogForShare(dong.shareEntity, dong.expenseWithShares)
                                },
                                onShareStatement = { viewModel.sharePersonStatement(context, person) },
                                onShareSingleDong = { dong ->
                                    viewModel.shareSingleDong(context, dong.expenseWithShares, dong.shareEntity)
                                }
                            )
                        }
                    }
                }
            } else {
                // Tab 1: لیست تمام هزینه‌ها
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { viewModel.setFilterOnlyUnsettled(!state.filterOnlyUnsettled) }
                    ) {
                        Text(
                            text = if (state.filterOnlyUnsettled) "نمایش همه (شامل تسویه‌شده)" else "فقط تسویه‌نشده‌ها",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (state.groupExpenses.isEmpty()) {
                    EmptyState(
                        title = if (state.filterOnlyUnsettled) "دنگ تسویه‌نشده‌ای وجود ندارد" else "هنوز هزینه مشترکی ثبت نشده است",
                        icon = Icons.Default.Groups,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = state.groupExpenses,
                            key = { it.groupExpense.id }
                        ) { expenseItem ->
                            GroupExpenseCard(
                                expense = expenseItem,
                                onSettleShare = { share -> viewModel.openSettleDialogForShare(share, expenseItem) },
                                onUnsettleShare = { share -> viewModel.openUnsettleDialog(share) },
                                onShareDong = { share -> viewModel.shareSingleDong(context, expenseItem, share) },
                                onShareAll = { viewModel.shareAllDongs(context, expenseItem) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 3-Mode Settle Dialog (Item 2)
    state.settleTarget?.let { target ->
        ThreeModeSettleDialog(
            target = target,
            settleMode = state.settleMode,
            accounts = state.accounts,
            categories = state.categories,
            selectedAccountId = state.selectedIncomeAccountId,
            selectedCategoryId = state.selectedCategoryId,
            partialAmountInput = state.partialAmountInput,
            createIncomeInPartial = state.createIncomeInPartial,
            onSetSettleMode = { viewModel.setSettleMode(it) },
            onSelectAccount = { viewModel.setSelectedIncomeAccountId(it) },
            onSelectCategory = { viewModel.setSelectedCategoryId(it) },
            onSetPartialAmount = { viewModel.setPartialAmountInput(it) },
            onSetCreateIncomeInPartial = { viewModel.setCreateIncomeInPartial(it) },
            onConfirm = { viewModel.confirmSettle() },
            onDismiss = { viewModel.closeSettleDialog() }
        )
    }

    // Unsettle Share Dialog (لغو تسویه با قابلیت حذف تراکنش درآمدی مرتبط)
    state.unsettlingShare?.let { share ->
        AlertDialog(
            onDismissRequest = { viewModel.closeUnsettleDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = ExpenseColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "لغو تسویه دنگ",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "آیا از بازگردانی وضعیت دنگ «${share.personName}» به تسویه‌نشده مطمئن هستید؟ در صورتی که تراکنش درآمدی ثبت شده باشد، آن تراکنش حذف خواهد شد.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmUnsettleShare() },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("لغو تسویه")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeUnsettleDialog() }) {
                    Text("انصراف")
                }
            }
        )
    }
}

/**
 * کارت صورت‌حساب هر شخص در هزینه‌های مشترک (مورد ۱)
 */
@Composable
fun PersonDebtCard(
    person: PersonDebtSummary,
    onSettlePerson: () -> Unit,
    onSettleDong: (PersonOpenDong) -> Unit,
    onShareStatement: () -> Unit,
    onShareSingleDong: (PersonOpenDong) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Avatar, Name, Open dongs count, Total Debt
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = person.personName.firstOrNull()?.toString() ?: "؟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = person.personName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${person.openDongsCount} دنگ پرداخت‌نشده",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Debt amount
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "جمع کل بدهی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AmountFormatter.formatAmountWithCurrency(person.totalDebt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSettlePerson,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تسویه حساب", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onShareStatement,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "ارسال صورت‌حساب",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Text(
                        text = if (expanded) "بستن ریز دنگ‌ها" else "مشاهده ریز دنگ‌ها",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Open Dongs List (تاریخ + توضیح تراکنش اصلی + مبلغ هرکدام)
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "دنگ‌های باز ${person.personName}:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    person.openDongs.forEach { dong ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dong.parentNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = DateFormatter.formatDate(dong.transactionDate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = AmountFormatter.formatAmountWithCurrency(dong.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseColor
                                    )

                                    OutlinedButton(
                                        onClick = { onSettleDong(dong) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("تسویه", style = MaterialTheme.typography.labelSmall)
                                    }

                                    IconButton(
                                        onClick = { onShareSingleDong(dong) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "اشتراک",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * دیالوگ تسویه سه‌حالته (مورد ۲)
 * ① تسویه با تراکنش واریز
 * ② تسویه بدون تراکنش
 * ③ تسویه جزئی (با پیش‌نمایش دنگ‌های تسویه‌شونده و بررسی خطا)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreeModeSettleDialog(
    target: SettleTarget,
    settleMode: SettleMode,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    selectedAccountId: Long?,
    selectedCategoryId: Long?,
    partialAmountInput: String,
    createIncomeInPartial: Boolean,
    onSetSettleMode: (SettleMode) -> Unit,
    onSelectAccount: (Long) -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onSetPartialAmount: (String) -> Unit,
    onSetCreateIncomeInPartial: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedAccountDropdown by remember { mutableStateOf(false) }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val incomeCategories = remember(categories) {
        categories.filter { it.type == com.example.data.local.entity.CategoryType.INCOME }
    }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    val partialAmountLong = partialAmountInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val isAmountOverDebt = partialAmountLong > target.totalDebt
    val debtDifference = if (isAmountOverDebt) partialAmountLong - target.totalDebt else 0L

    // Simulate partial preview for Mode 3
    val partialPreviews = remember(partialAmountLong, target.openDongs) {
        val list = mutableListOf<Pair<PersonOpenDong, String>>()
        var rem = partialAmountLong
        target.openDongs.forEach { dong ->
            if (rem <= 0) {
                list.add(dong to "بدون تغییر")
            } else if (rem >= dong.amount) {
                list.add(dong to "تسویه کامل (${AmountFormatter.formatAmountWithCurrency(dong.amount)})")
                rem -= dong.amount
            } else {
                list.add(dong to "تسویه جزئی: ${AmountFormatter.formatAmountWithCurrency(rem)} (باقیمانده: ${AmountFormatter.formatAmountWithCurrency(dong.amount - rem)})")
                rem = 0L
            }
        }
        list
    }

    val isConfirmEnabled = when (settleMode) {
        SettleMode.WITH_TRANSACTION -> selectedAccount != null
        SettleMode.WITHOUT_TRANSACTION -> true
        SettleMode.PARTIAL -> partialAmountLong > 0 && !isAmountOverDebt && (!createIncomeInPartial || selectedAccount != null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = IncomeColor,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (target.isForPerson) "تسویه حساب «${target.personName}»" else "تسویه دنگ «${target.personName}»",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "کل مبلغ بدهی: ${AmountFormatter.formatAmountWithCurrency(target.totalDebt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mode 1: تسویه با تراکنش واریز
                SettleOptionCard(
                    title = "① تسویه با تراکنش واریز",
                    subtitle = "ثبت تراکنش درآمد و افزایش موجودی حساب",
                    isSelected = settleMode == SettleMode.WITH_TRANSACTION,
                    onClick = { onSetSettleMode(SettleMode.WITH_TRANSACTION) }
                )

                if (settleMode == SettleMode.WITH_TRANSACTION) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Account Selector
                        ExposedDropdownMenuBox(
                            expanded = expandedAccountDropdown,
                            onExpandedChange = { expandedAccountDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedAccount?.name ?: "انتخاب حساب...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("واریز به حساب") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAccountDropdown,
                                onDismissRequest = { expandedAccountDropdown = false }
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            onSelectAccount(acc.id)
                                            expandedAccountDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Optional Category Selector
                        ExposedDropdownMenuBox(
                            expanded = expandedCategoryDropdown,
                            onExpandedChange = { expandedCategoryDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCategory?.name ?: "دسته‌بندی اختیاری (پیش‌فرض: تسویه دنگ)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("دسته‌بندی (اختیاری)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCategoryDropdown,
                                onDismissRequest = { expandedCategoryDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("بدون دسته‌بندی خاص") },
                                    onClick = {
                                        onSelectCategory(null)
                                        expandedCategoryDropdown = false
                                    }
                                )
                                incomeCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            onSelectCategory(cat.id)
                                            expandedCategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Mode 2: تسویه بدون تراکنش
                SettleOptionCard(
                    title = "② تسویه بدون تراکنش",
                    subtitle = "بدون ثبت هیچ تراکنش مالی (برای وقتی دوست به‌جای واریز، دفعه بعد هزینه را حساب می‌کند)",
                    isSelected = settleMode == SettleMode.WITHOUT_TRANSACTION,
                    onClick = { onSetSettleMode(SettleMode.WITHOUT_TRANSACTION) }
                )

                // Mode 3: تسویه جزئی
                SettleOptionCard(
                    title = "③ تسویه جزئی",
                    subtitle = "تسویه از قدیمی‌ترین دنگ تا سقف مبلغ وارد شده",
                    isSelected = settleMode == SettleMode.PARTIAL,
                    onClick = { onSetSettleMode(SettleMode.PARTIAL) }
                )

                if (settleMode == SettleMode.PARTIAL) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = partialAmountInput,
                            onValueChange = onSetPartialAmount,
                            label = { Text("مبلغ تسویه جزئی (تومان)") },
                            singleLine = true,
                            isError = isAmountOverDebt,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Error if amount > total debt with difference amount
                        if (isAmountOverDebt) {
                            Text(
                                text = "مبلغ وارد شده بیشتر از کل بدهی است (اختلاف: ${AmountFormatter.formatAmountWithCurrency(debtDifference)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Preview of which dongs will be settled
                        if (partialAmountLong > 0 && !isAmountOverDebt) {
                            Text(
                                text = "پیش‌نمایش دنگ‌هایی که تسویه می‌شوند:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                partialPreviews.forEach { (dong, status) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = dong.parentNote,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (status.startsWith("تسویه")) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Checkbox to create income transaction in partial mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetCreateIncomeInPartial(!createIncomeInPartial) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = createIncomeInPartial,
                                    onCheckedChange = { onSetCreateIncomeInPartial(it) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ثبت به عنوان تراکنش واریز در حساب", style = MaterialTheme.typography.bodySmall)
                            }

                            if (createIncomeInPartial) {
                                ExposedDropdownMenuBox(
                                    expanded = expandedAccountDropdown,
                                    onExpandedChange = { expandedAccountDropdown = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedAccount?.name ?: "انتخاب حساب...",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("واریز به حساب") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedAccountDropdown,
                                        onDismissRequest = { expandedAccountDropdown = false }
                                    ) {
                                        accounts.forEach { acc ->
                                            DropdownMenuItem(
                                                text = { Text(acc.name) },
                                                onClick = {
                                                    onSelectAccount(acc.id)
                                                    expandedAccountDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isConfirmEnabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IncomeColor)
            ) {
                Text("تأیید تسویه", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
private fun SettleOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun GroupExpenseSummaryHeader(
    totalPending: Long,
    pendingCount: Int,
    totalSettled: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "مجموع طلب‌های دنگ تسویه‌نشده",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$pendingCount مورد",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = AmountFormatter.formatAmountWithCurrency(totalPending),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (totalPending > 0) ExpenseColor else MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "مجموع تسویه‌شده تا کنون:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = AmountFormatter.formatAmountWithCurrency(totalSettled),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = IncomeColor
                )
            }
        }
    }
}

@Composable
fun GroupExpenseCard(
    expense: GroupExpenseWithShares,
    onSettleShare: (GroupExpenseShareEntity) -> Unit,
    onUnsettleShare: (GroupExpenseShareEntity) -> Unit,
    onShareDong: (GroupExpenseShareEntity) -> Unit,
    onShareAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tx = expense.transaction
    val title = tx?.note?.ifBlank { "هزینه مشترک" } ?: "هزینه مشترک"
    val dateStr = tx?.date?.let { DateFormatter.formatDate(it) } ?: DateFormatter.formatDate(expense.groupExpense.createdAt)
    val totalAmount = tx?.amount ?: expense.shares.sumOf { it.amount }

    val pendingCount = expense.shares.count { !it.isSettled }
    val allSettled = expense.shares.isNotEmpty() && pendingCount == 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = AmountFormatter.formatAmountWithCurrency(totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (allSettled) {
                        Text(
                            text = "کاملاً تسویه شده ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = IncomeColor,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "$pendingCount دنگ باز",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                expense.shares.forEach { share ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = share.personName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = AmountFormatter.formatAmountWithCurrency(share.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (share.isSettled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IncomeColor.copy(alpha = 0.12f))
                                        .clickable { onUnsettleShare(share) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = IncomeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تسویه شده", fontSize = 12.sp, color = IncomeColor, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { onSettleShare(share) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("دریافت دنگ", fontSize = 12.sp)
                                }
                            }

                            IconButton(
                                onClick = { onShareDong(share) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "ارسال دنگ",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShareAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ارسال صورت‌حساب کامل برای همه", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
