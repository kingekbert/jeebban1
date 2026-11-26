package com.example.ui.screens.scan_sms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.TransactionType
import com.example.ui.components.CategoryPicker
import com.example.ui.components.FinTrackTimePickerDialog
import com.example.ui.components.JalaliDatePickerDialog
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.BankLogoBadge
import com.example.util.DateFormatter
import com.example.util.toPersianDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreConfirmScreen(
    viewModel: PreConfirmViewModel,
    sharedScanViewModel: SharedScanViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReconciliation: (accountId: Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pendingItem by sharedScanViewModel.pendingScanItem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTypePickerSheet by remember { mutableStateOf(false) }
    var showAccountPickerSheet by remember { mutableStateOf(false) }
    var showDestAccountPickerSheet by remember { mutableStateOf(false) }
    var showRawSmsDialog by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }

    val (currentHour, currentMinute) = remember(state.dateTimestamp) {
        DateFormatter.extractHourAndMinute(state.dateTimestamp)
    }

    val selectedAccount = remember(state.accounts, state.selectedAccountId) {
        state.accounts.find { it.id == state.selectedAccountId }
    }

    LaunchedEffect(pendingItem) {
        viewModel.initFromItem(pendingItem ?: ScanSmsDataHolder.selectedItem)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(state.isSavedSuccess) {
        if (state.isSavedSuccess) {
            val hashToMark = state.smsHash.ifBlank {
                pendingItem?.smsHash ?: ScanSmsDataHolder.selectedItem?.smsHash ?: ""
            }
            if (hashToMark.isNotBlank()) {
                sharedScanViewModel.markSmsSaved(hashToMark)
                try {
                    com.example.widget.WidgetDataStore.removeItem(context, hashToMark)
                    com.example.widget.WidgetStatusHelper.updateAllWidgets(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            sharedScanViewModel.clearPendingScanItem()
            onNavigateBack()
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialTimestamp = state.dateTimestamp,
            onDismiss = { showDatePicker = false },
            onDateSelected = { timestamp ->
                val newTimestamp = DateFormatter.combineDateAndTime(timestamp, currentHour, currentMinute)
                viewModel.setDateTimestamp(newTimestamp)
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        FinTrackTimePickerDialog(
            initialHour = currentHour,
            initialMinute = currentMinute,
            onTimeSelected = { h, m ->
                val newTimestamp = DateFormatter.combineDateAndTime(state.dateTimestamp, h, m)
                viewModel.setDateTimestamp(newTimestamp)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showTypePickerSheet) {
        PreConfirmTypePickerSheet(
            currentType = state.transactionType,
            onSelectType = { viewModel.setTransactionType(it) },
            onDismiss = { showTypePickerSheet = false }
        )
    }

    if (showAccountPickerSheet) {
        PreConfirmAccountPickerSheet(
            title = if (state.transactionType == TransactionType.TRANSFER) "انتخاب حساب مبدأ" else "تغییر حساب بانکی",
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onSelectAccount = { viewModel.setSelectedAccount(it) },
            onDismiss = { showAccountPickerSheet = false }
        )
    }

    if (showDestAccountPickerSheet) {
        PreConfirmAccountPickerSheet(
            title = "انتخاب حساب مقصد",
            accounts = state.accounts,
            selectedAccountId = state.selectedToAccountId,
            onSelectAccount = { viewModel.setSelectedToAccount(it) },
            onDismiss = { showDestAccountPickerSheet = false }
        )
    }

    if (showRawSmsDialog && state.rawSmsText.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showRawSmsDialog = false },
            title = {
                Text(
                    text = "متن اصلی پیامک",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = state.rawSmsText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawSmsDialog = false }) {
                    Text("بستن", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تأیید و ثبت تراکنش", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        sharedScanViewModel.clearPendingScanItem()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("pre_confirm_more_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "گزینه‌های بیشتر"
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تغییر نوع تراکنش") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showTypePickerSheet = true
                                }
                            )

                            if (state.accounts.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("تغییر حساب بانکی")
                                            selectedAccount?.let { acc ->
                                                Text(
                                                    text = "فعلی: ${acc.name}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showAccountPickerSheet = true
                                    }
                                )
                            }

                            if (state.transactionType == TransactionType.EXPENSE) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.isGroupExpenseEnabled) "لغو هزینه مشترک (دنگ)"
                                            else "ثبت به عنوان هزینه مشترک (دنگ)"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = if (state.isGroupExpenseEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = if (state.isGroupExpenseEnabled) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "فعال",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.setGroupExpenseEnabled(!state.isGroupExpenseEnabled)
                                    }
                                )
                            }

                            if (state.rawSmsText.isNotBlank()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("مشاهده متن پیامک") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Sms,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showRawSmsDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Discrepancy warning badge if SMS balance does not match app balance
            if (state.balance != null && state.balanceDiff != null && state.balanceDiff != 0L && state.selectedAccountId != null) {
                item {
                    val diffVal = kotlin.math.abs(state.balanceDiff!!)
                    val diffStr = AmountFormatter.format(diffVal)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToReconciliation(state.selectedAccountId!!) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "مانده با اپ نمی‌خواند ($diffStr ریال)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(
                                onClick = { onNavigateToReconciliation(state.selectedAccountId!!) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "بررسی اختلاف",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 2. Suggested Dong Settlement Card for Income (dynamic, only when open dong matches)
            if (state.transactionType == TransactionType.INCOME && state.matchedDongs.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "پیشنهاد تسویه دنگ باز",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = state.settleDongEnabled,
                                    onCheckedChange = { viewModel.setSettleDongEnabled(it) }
                                )
                            }

                            if (state.settleDongEnabled) {
                                state.matchedDongs.forEach { dong ->
                                    val isSelected = dong.shareId == state.selectedMatchedDongId
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectMatchedDong(dong.shareId) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                        ),
                                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = dong.personName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (!dong.isExact) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "مبلغ کمی متفاوت است",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.error,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "بابت «${dong.parentNote}» (مبلغ دنگ: ${AmountFormatter.formatAmountWithCurrency(dong.amount)})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "انتخاب شده",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
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

            // 3. Smart Suggestion: Convert to Transfer if detected transfer pair available
            if (state.detectedTransfer != null && state.transactionType != TransactionType.TRANSFER) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "پیشنهاد هوشمند: انتقال بین حساب‌ها",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "این پیامک هم‌زمان با پیامک دیگری به عنوان انتقال بین حساب‌ها شناسایی شده است. مایلید به عنوان انتقال بین حساب‌ها ثبت شود؟",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.applyDetectedTransfer() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تبدیل به انتقال بین حساب‌ها")
                            }
                        }
                    }
                }
            }

            // 4. Streamlined Transaction Summary Card (Bank/Account + Detected Type + Amount + Date/Time)
            item {
                val isTransfer = state.transactionType == TransactionType.TRANSFER
                val typeText = when (state.transactionType) {
                    TransactionType.EXPENSE -> "هزینه (برداشت)"
                    TransactionType.INCOME -> "درآمد (واریز)"
                    TransactionType.TRANSFER -> "انتقال بین حساب‌ها"
                }
                val typeColor = when (state.transactionType) {
                    TransactionType.EXPENSE -> ExpenseColor
                    TransactionType.INCOME -> IncomeColor
                    TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (!isTransfer && selectedAccount != null) {
                                    BankLogoBadge(
                                        bankId = selectedAccount.logoResName,
                                        accountName = selectedAccount.name,
                                        cardNumber = selectedAccount.cardNumber,
                                        size = 34.dp,
                                        shapeRadius = 8.dp
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isTransfer) {
                                            "انتقال بین حساب‌ها"
                                        } else {
                                            selectedAccount?.name ?: state.bankName ?: "اطلاعات پیامک"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isTransfer && !state.accountIdent.isNullOrBlank()) {
                                        Text(
                                            text = "حساب: ${state.accountIdent!!.toPersianDigits()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(typeColor.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = typeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = typeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = AmountFormatter.formatRial(state.amount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = typeColor
                        )

                        if (isTransfer && state.transferFeeAmount > 0L) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = ExpenseColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "کارمزد بانکی: ${AmountFormatter.formatRial(state.transferFeeAmount)} (از مبدأ کسر می‌شود)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Date and Time Selectors
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("pre_confirm_date_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = DateFormatter.formatMedium(state.dateTimestamp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("pre_confirm_time_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = DateFormatter.formatTime(currentHour, currentMinute),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Inline prompt ONLY if no account was matched for a non-transfer SMS
                        if (!isTransfer && selectedAccount == null && state.accounts.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showAccountPickerSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("انتخاب حساب بانکی")
                            }
                        }
                    }
                }
            }

            // 5. Transfer Source/Destination Accounts Card (only when TRANSFER)
            if (state.transactionType == TransactionType.TRANSFER) {
                item {
                    PreConfirmTransferAccountsCard(
                        accounts = state.accounts,
                        sourceAccountId = state.selectedAccountId,
                        destAccountId = state.selectedToAccountId,
                        onPickSourceClick = { showAccountPickerSheet = true },
                        onPickDestClick = { showDestAccountPickerSheet = true }
                    )
                }
            }

            // 6. Category Picker Card (for EXPENSE / INCOME)
            if (state.transactionType != TransactionType.TRANSFER) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "دسته تراکنش",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            CategoryPicker(
                                categories = state.categories,
                                selectedCategoryId = state.selectedCategoryId,
                                onCategorySelected = { category -> viewModel.setSelectedCategory(category.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 7. Group Expense (Dong) Card — Hidden by default, shown only when enabled from 3-dot menu
            if (state.transactionType == TransactionType.EXPENSE) {
                item {
                    AnimatedVisibility(visible = state.isGroupExpenseEnabled) {
                        PreConfirmGroupExpenseCard(
                            state = state,
                            newPersonName = newPersonName,
                            onNewPersonNameChange = { newPersonName = it },
                            onDisable = { viewModel.setGroupExpenseEnabled(false) },
                            onShareModeChange = { viewModel.setGroupShareMode(it) },
                            onAddPerson = { viewModel.addGroupPerson(it) },
                            onRemovePerson = { viewModel.removeGroupPerson(it) },
                            onUpdatePersonAmount = { id, amt -> viewModel.updateGroupPersonAmount(id, amt) }
                        )
                    }
                }
            }

            // 8. Note Text Field Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "توضیحات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = state.note,
                            onValueChange = { viewModel.setNote(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pre_confirm_note_input"),
                            placeholder = { Text("توضیحات اختیاری...") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // 9. Single Primary Confirm & Save Action Button
            item {
                val canSave = !state.isSaving && !state.isSavedSuccess
                Button(
                    onClick = { viewModel.quickSaveTransaction() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .height(54.dp)
                        .testTag("pre_confirm_save_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            state.isSaving -> "در حال ثبت..."
                            state.transactionType == TransactionType.TRANSFER -> "تأیید و ثبت انتقال"
                            else -> "تأیید و ثبت تراکنش"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
