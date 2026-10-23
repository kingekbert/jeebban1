package com.example.ui.screens.scan_sms

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionType
import com.example.ui.components.CategoryPicker
import com.example.ui.components.FinTrackTimePickerDialog
import com.example.ui.components.JalaliDatePickerDialog
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.BankLogoBadge
import com.example.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreConfirmScreen(
    viewModel: PreConfirmViewModel,
    sharedScanViewModel: SharedScanViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingItem by sharedScanViewModel.pendingScanItem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }

    val (currentHour, currentMinute) = remember(state.dateTimestamp) {
        DateFormatter.extractHourAndMinute(state.dateTimestamp)
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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

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
            // 1. Suggested Dong Settlement Card for Income
            if (state.transactionType == TransactionType.INCOME && state.matchedDongs.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
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
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
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

            // 1.5 Smart Suggestion: Convert to Transfer if detected transfer available
            if (state.detectedTransfer != null && state.transactionType != TransactionType.TRANSFER) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
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

            // 2. Transaction Summary Card
            item {
                val isTransfer = state.transactionType == TransactionType.TRANSFER
                val isExpense = state.transactionType == TransactionType.EXPENSE
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
                            Text(
                                text = if (isTransfer) "انتقال بین حساب‌ها" else (state.bankName ?: "اطلاعات پیامک"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(typeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = typeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = typeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Expense / Income / Transfer Toggle Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { viewModel.setTransactionType(TransactionType.EXPENSE) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.transactionType == TransactionType.EXPENSE) ExpenseColor else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.transactionType == TransactionType.EXPENSE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("هزینه", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.setTransactionType(TransactionType.INCOME) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.transactionType == TransactionType.INCOME) IncomeColor else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.transactionType == TransactionType.INCOME) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("درآمد", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.setTransactionType(TransactionType.TRANSFER) },
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.transactionType == TransactionType.TRANSFER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.transactionType == TransactionType.TRANSFER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("انتقال حسابی", fontSize = 11.sp)
                            }
                        }

                        Text(
                            text = AmountFormatter.formatRial(state.amount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = typeColor
                        )

                        // If transfer with fee, display fee info
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
                            // Date
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1.3f),
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

                            // Time
                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(0.9f),
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
                    }
                }
            }

            // 3. Account Selector(s) Card
            item {
                val isTransfer = state.transactionType == TransactionType.TRANSFER

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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isTransfer) {
                            // Transfer: Source Account + Destination Account
                            Text(
                                text = "حساب‌های انتقال",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Source Account Picker
                            var srcExpanded by remember { mutableStateOf(false) }
                            val srcAccount = state.accounts.find { it.id == state.selectedAccountId }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "حساب مبدأ (برداشت از):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ExpenseColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { srcExpanded = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (srcAccount != null) {
                                                BankLogoBadge(
                                                    bankId = srcAccount.logoResName,
                                                    accountName = srcAccount.name,
                                                    cardNumber = srcAccount.cardNumber,
                                                    size = 28.dp,
                                                    shapeRadius = 6.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalance,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = srcAccount?.name ?: "انتخاب حساب مبدأ...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = srcExpanded,
                                        onDismissRequest = { srcExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        state.accounts.forEach { acc ->
                                            DropdownMenuItem(
                                                text = { Text(acc.name, fontWeight = FontWeight.Medium) },
                                                leadingIcon = {
                                                    BankLogoBadge(
                                                        bankId = acc.logoResName,
                                                        accountName = acc.name,
                                                        cardNumber = acc.cardNumber,
                                                        size = 24.dp,
                                                        shapeRadius = 6.dp
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setSelectedAccount(acc.id)
                                                    srcExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Downward arrow indicator between accounts
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Destination Account Picker
                            var destExpanded by remember { mutableStateOf(false) }
                            val destAccount = state.accounts.find { it.id == state.selectedToAccountId }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "حساب مقصد (واریز به):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IncomeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { destExpanded = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (destAccount != null) {
                                                BankLogoBadge(
                                                    bankId = destAccount.logoResName,
                                                    accountName = destAccount.name,
                                                    cardNumber = destAccount.cardNumber,
                                                    size = 28.dp,
                                                    shapeRadius = 6.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalance,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = destAccount?.name ?: "انتخاب حساب مقصد...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = destExpanded,
                                        onDismissRequest = { destExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        state.accounts.forEach { acc ->
                                            DropdownMenuItem(
                                                text = { Text(acc.name, fontWeight = FontWeight.Medium) },
                                                leadingIcon = {
                                                    BankLogoBadge(
                                                        bankId = acc.logoResName,
                                                        accountName = acc.name,
                                                        cardNumber = acc.cardNumber,
                                                        size = 24.dp,
                                                        shapeRadius = 6.dp
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setSelectedToAccount(acc.id)
                                                    destExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Standard Single Account Picker
                            Text(
                                text = "انتخاب حساب",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            var expanded by remember { mutableStateOf(false) }
                            val selectedAccount = state.accounts.find { it.id == state.selectedAccountId }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (selectedAccount != null) {
                                            BankLogoBadge(
                                                bankId = selectedAccount.logoResName,
                                                accountName = selectedAccount.name,
                                                cardNumber = selectedAccount.cardNumber,
                                                size = 28.dp,
                                                shapeRadius = 6.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalance,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = selectedAccount?.name ?: "انتخاب حساب...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    state.accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text(acc.name, fontWeight = FontWeight.Medium) },
                                            leadingIcon = {
                                                BankLogoBadge(
                                                    bankId = acc.logoResName,
                                                    accountName = acc.name,
                                                    cardNumber = acc.cardNumber,
                                                    size = 24.dp,
                                                    shapeRadius = 6.dp
                                                )
                                            },
                                            onClick = {
                                                viewModel.setSelectedAccount(acc.id)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Category Picker Card (Only for EXPENSE / INCOME)
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
                                text = "دسته تراکنش (الزامی)",
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

            // 5. Group Expense (Dong) Section (Only when EXPENSE)
            if (state.transactionType == TransactionType.EXPENSE) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isGroupExpenseEnabled)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = if (state.isGroupExpenseEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "هزینه مشترک (دنگ)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "تقسیم مبلغ بین افراد و ثبت طلب",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = state.isGroupExpenseEnabled,
                                    onCheckedChange = { viewModel.setGroupExpenseEnabled(it) }
                                )
                            }

                            if (state.isGroupExpenseEnabled) {
                                // Mode Selection Tabs
                                TabRow(
                                    selectedTabIndex = if (state.groupShareMode == GroupExpenseShareMode.EQUAL) 0 else 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    Tab(
                                        selected = state.groupShareMode == GroupExpenseShareMode.EQUAL,
                                        onClick = { viewModel.setGroupShareMode(GroupExpenseShareMode.EQUAL) },
                                        text = { Text("تقسیم مساوی") }
                                    )
                                    Tab(
                                        selected = state.groupShareMode == GroupExpenseShareMode.CUSTOM,
                                        onClick = { viewModel.setGroupShareMode(GroupExpenseShareMode.CUSTOM) },
                                        text = { Text("سهم دلخواه") }
                                    )
                                }

                                // Add Person Input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newPersonName,
                                        onValueChange = { newPersonName = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("نام دوست یا هم‌سفر...") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (newPersonName.isNotBlank()) {
                                                viewModel.addGroupPerson(newPersonName)
                                                newPersonName = ""
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("افزودن")
                                    }
                                }

                                // Suggested Names Chips (from previous dongs)
                                val availableSuggestions = state.recentPersonNames.filter { name ->
                                    state.groupShares.none { it.name.equals(name, ignoreCase = true) }
                                }
                                if (availableSuggestions.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "افراد پیشنهادی اخیر:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(availableSuggestions) { name ->
                                                AssistChip(
                                                    onClick = { viewModel.addGroupPerson(name) },
                                                    label = { Text(name) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Added Friends List
                                if (state.groupShares.isEmpty()) {
                                    Text(
                                        text = "هنوز فردی اضافه نشده است. نام دوستان خود را وارد کنید.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.groupShares.forEach { person ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Text(
                                                                text = person.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            if (state.groupShareMode == GroupExpenseShareMode.EQUAL) {
                                                                Text(
                                                                    text = AmountFormatter.formatAmountWithCurrency(person.amount),
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // If CUSTOM mode, editable amount field
                                                    if (state.groupShareMode == GroupExpenseShareMode.CUSTOM) {
                                                        OutlinedTextField(
                                                            value = if (person.amount > 0) person.amount.toString() else "",
                                                            onValueChange = { input ->
                                                                val clean = input.filter { it.isDigit() }
                                                                val num = clean.toLongOrNull() ?: 0L
                                                                viewModel.updateGroupPersonAmount(person.id, num)
                                                            },
                                                            modifier = Modifier.width(130.dp),
                                                            placeholder = { Text("مبلغ ریال") },
                                                            singleLine = true,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { viewModel.removeGroupPerson(person.id) }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "حذف فرد",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // In CUSTOM mode, calculate user's own share automatically (amount - sum of friends' shares)
                                if (state.groupShareMode == GroupExpenseShareMode.CUSTOM) {
                                    val sumFriends = state.groupShares.sumOf { it.amount }
                                    val myShare = state.amount - sumFriends

                                    if (myShare < 0L) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "مازاد سهم دوستان:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = AmountFormatter.formatAmountWithCurrency(kotlin.math.abs(myShare)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "سهم خودم:",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = AmountFormatter.formatAmountWithCurrency(myShare),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
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

            // 6. Note Text Field Card
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
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("توضیحات اختیاری...") },
                            singleLine = true
                        )
                    }
                }
            }

            // 7. Save Action Buttons: ثبت سریع و ثبت عادی
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.quickSaveTransaction() },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.transactionType == TransactionType.TRANSFER) "ثبت سریع انتقال" else "ثبت سریع",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.saveTransaction() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.transactionType == TransactionType.TRANSFER) "ثبت انتقال" else "ثبت عادی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
