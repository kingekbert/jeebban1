package com.example.ui.screens.reconciliation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionType
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.DateFormatter
import com.example.util.JalaliDate
import com.example.util.ReconciliationCause
import com.example.util.toPersianDigits
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    viewModel: ReconciliationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "بررسی و تطبیق مانده",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Account Header Card
                item {
                    val account = state.account
                    val accountName = account?.name ?: "حساب بانکی"
                    val latestSnap = state.latestSnapshot
                    val snapDateStr = latestSnap?.smsDate?.let {
                        val jalali = JalaliDate.fromTimestamp(it)
                        val (h, m) = DateFormatter.extractHourAndMinute(it)
                        "${jalali.format(includeDayName = false)} - ${String.format("%02d:%02d", h, m)}".toPersianDigits()
                    } ?: "نامشخص"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
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
                                Text(
                                    text = accountName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (state.isSynced) IncomeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = if (state.isSynced) "همخوان با بانک" else "دارای اختلاف",
                                        color = if (state.isSynced) IncomeColor else MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تاریخ آخرین اسنپ‌شات پیامک: $snapDateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Top Summary: Discrepancy, Bank Balance, App Balance
                item {
                    val absDiff = abs(state.diff)
                    val diffColor = if (state.isSynced) IncomeColor else MaterialTheme.colorScheme.error

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isSynced) IncomeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (state.isSynced) IncomeColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (state.isSynced) "اختلاف مانده" else "مبلغ اختلاف با بانک",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state.isSynced) "۰ ریال (همخوان)" else "${AmountFormatter.format(absDiff)} ریال",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = diffColor
                            )
                            if (!state.isSynced) {
                                Text(
                                    text = "(${AmountFormatter.format(absDiff / 10)} تومان)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = diffColor.copy(alpha = 0.85f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "مانده در پیامک بانک",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = AmountFormatter.format(state.bankBalance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "مانده محاسبه‌شده اپ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = AmountFormatter.format(state.appBalance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Synced Celebration Card
                if (state.isSynced) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = IncomeColor.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "مانده حساب با بانک کاملاً همخوان است",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeColor
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "تمام تراکنش‌های پیامک‌ها دقیقاً ثبت شده و هیچ مغایرتی وجود ندارد.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Root Cause & 1-Click Fix Section (if not synced)
                if (!state.isSynced) {
                    item {
                        Text(
                            text = "علت محتمل و راهکار رفع",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        when (val cause = state.cause) {
                            is ReconciliationCause.UnregisteredTransactions -> {
                                CauseCard(
                                    icon = Icons.Default.ReceiptLong,
                                    title = "تراکنش‌های ثبت‌نشده در اپ",
                                    description = "سیستم پیامک‌های ثبت‌نشده‌ای پیدا کرد که جمع آنها دقیقاً برابر با اختلاف مانده است.",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                    accentColor = MaterialTheme.colorScheme.tertiary
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        cause.candidateItems.forEach { item ->
                                            val p = item.parsedSms
                                            val amt = p.amount ?: 0L
                                            val isInc = p.transactionType == TransactionType.INCOME
                                            val jalali = JalaliDate.fromTimestamp(if (p.date != null && p.date > 0) p.date else item.date)
                                            val dateStr = jalali.format(includeDayName = false).toPersianDigits()

                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${if (isInc) "واریز" else "برداشت"}: ${AmountFormatter.format(amt)} ریال",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isInc) IncomeColor else ExpenseColor
                                                        )
                                                        Text(
                                                            text = "$dateStr — ${p.rawDescription ?: item.rawText.take(40)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    OutlinedButton(
                                                        onClick = { viewModel.fixCandidate(item) },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("ثبت", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }

                                        if (cause.candidateItems.size > 1) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = { viewModel.fixAllCandidates(cause.candidateItems) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(imageVector = Icons.Default.DoneAll, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("ثبت همه کاندیدها و رفع اختلاف", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            is ReconciliationCause.BankFee -> {
                                CauseCard(
                                    icon = Icons.Default.ReceiptLong,
                                    title = "کارمزد بانکی ثبت‌نشده",
                                    description = "با توجه به مبلغ کم اختلاف، این کسر مانده به احتمال زیاد مربوط به کارمزد بانکی یا آبونمان پیامک است.",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    accentColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    Button(
                                        onClick = {
                                            val snapDate = state.latestSnapshot?.smsDate ?: System.currentTimeMillis()
                                            viewModel.fixBankFee(cause.feeAmount, snapDate)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ثبت کارمزد (${AmountFormatter.format(cause.feeAmount)} ریال)",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            is ReconciliationCause.WrongInitialBalance -> {
                                CauseCard(
                                    icon = Icons.Default.Edit,
                                    title = "مانده اولیه حساب اشتباه ثبت شده",
                                    description = "این اختلاف از اولین اسنپ‌شات ثبت‌شده وجود داشته و در طول زمان ثابت مانده است. با یک کلیک مانده اولیه حساب اصلاح می‌شود.",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    accentColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("مانده اولیه فعلی:", style = MaterialTheme.typography.bodySmall)
                                            Text("${AmountFormatter.format(cause.currentInitialBalance)} ریال", fontWeight = FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("مانده اولیه اصلاح‌شده:", style = MaterialTheme.typography.bodySmall)
                                            Text("${AmountFormatter.format(cause.proposedInitialBalance)} ریال", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { viewModel.fixInitialBalance(cause.diff) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("اصلاح مانده اولیه حساب", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            is ReconciliationCause.ManualAdjustment, null -> {
                                CauseCard(
                                    icon = Icons.Default.HelpOutline,
                                    title = "نیاز به بررسی و تراکنش اصلاحی دستی",
                                    description = "الگوی خودکاری برای این اختلاف شناسایی نشد. می‌توانید با ثبت یک تراکنش اصلاحی، مانده اپلیکیشن را با بانک همخوان کنید.",
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Button(
                                        onClick = { showManualDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("ثبت تراکنش اصلاحی دستی", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Secondary action to manually adjust if desired
                    if (state.cause !is ReconciliationCause.ManualAdjustment && state.cause != null) {
                        item {
                            OutlinedButton(
                                onClick = { showManualDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ثبت تراکنش اصلاحی دستی دلخواه")
                            }
                        }
                    }
                }

                // Discrepancy History Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تاریخچه اسنپ‌شات‌ها و اختلاف‌ها (${state.history.size.toString().toPersianDigits()})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (state.history.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "هنوز اسنپ‌شاتی از مانده بانک برای این حساب ذخیره نشده است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // History Table Header
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("تاریخ شمسی", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                                Text("مانده بانک", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                                Text("مانده اپ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                                Text("اختلاف", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                            }
                        }
                    }

                    items(state.history) { item ->
                        val jalali = JalaliDate.fromTimestamp(item.snapshot.smsDate)
                        val dateText = jalali.format(includeDayName = false).toPersianDigits()
                        val diffText = if (item.diff == 0L) "همخوان" else AmountFormatter.format(abs(item.diff))
                        val isItemSynced = item.diff == 0L

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dateText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.3f))
                                Text(AmountFormatter.format(item.snapshot.balance), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.3f))
                                Text(AmountFormatter.format(item.appBalance), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.3f))
                                Text(
                                    text = diffText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isItemSynced) IncomeColor else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1.1f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Adjustment Dialog
    if (showManualDialog) {
        val initialDiff = abs(state.diff)
        val defaultType = if (state.diff < 0) TransactionType.EXPENSE else TransactionType.INCOME
        var manualType by remember { mutableStateOf(defaultType) }
        var manualAmountStr by remember { mutableStateOf(if (initialDiff > 0) initialDiff.toString() else "") }
        var manualNote by remember { mutableStateOf("تراکنش اصلاحی تطبیق مانده") }
        var selectedCategory by remember {
            mutableStateOf(
                state.categories.firstOrNull { it.name.contains("تطبیق") || it.name.contains("سایر") }
                    ?: state.categories.firstOrNull()
            )
        }
        var expandedCat by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("ثبت تراکنش اصلاحی دستی", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "با ثبت این تراکنش، مانده اپلیکیشن به مانده بانک نزدیک می‌شود.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Type Selector (Expense / Income)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { manualType = TransactionType.EXPENSE },
                            modifier = Modifier.weight(1f),
                            colors = if (manualType == TransactionType.EXPENSE)
                                ButtonDefaults.outlinedButtonColors(containerColor = ExpenseColor.copy(alpha = 0.15f))
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("هزینه (کسر)", color = if (manualType == TransactionType.EXPENSE) ExpenseColor else Color.Unspecified)
                        }
                        OutlinedButton(
                            onClick = { manualType = TransactionType.INCOME },
                            modifier = Modifier.weight(1f),
                            colors = if (manualType == TransactionType.INCOME)
                                ButtonDefaults.outlinedButtonColors(containerColor = IncomeColor.copy(alpha = 0.15f))
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("درآمد (افزودن)", color = if (manualType == TransactionType.INCOME) IncomeColor else Color.Unspecified)
                        }
                    }

                    OutlinedTextField(
                        value = manualAmountStr,
                        onValueChange = { manualAmountStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("مبلغ (ریال)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedCat,
                        onExpandedChange = { expandedCat = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "انتخاب دسته",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("دسته‌بندی") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCat,
                            onDismissRequest = { expandedCat = false }
                        ) {
                            state.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategory = cat
                                        expandedCat = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = manualNote,
                        onValueChange = { manualNote = it },
                        label = { Text("یادداشت") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedAmt = manualAmountStr.toLongOrNull() ?: 0L
                        val catId = selectedCategory?.id ?: 1L
                        val snapDate = state.latestSnapshot?.smsDate ?: System.currentTimeMillis()
                        if (parsedAmt > 0) {
                            viewModel.fixManualAdjustment(
                                amount = parsedAmt,
                                type = manualType,
                                categoryId = catId,
                                note = manualNote,
                                date = snapDate
                            )
                            showManualDialog = false
                        }
                    }
                ) {
                    Text("ثبت و اصلاح مانده")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun CauseCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    containerColor: Color,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
