package com.example.ui.screens.scan_sms

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.TransactionType
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.BankLogoBadge
import com.example.util.DateFormatter
import com.example.util.DetectedTransfer
import com.example.util.toPersianDigits

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScanSmsScreen(
    viewModel: ScanSmsViewModel,
    sharedScanViewModel: SharedScanViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPreConfirm: () -> Unit,
    onNavigateToPatternLearnerWithText: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentlySavedSmsHash by sharedScanViewModel.recentlySavedSmsHash.collectAsStateWithLifecycle()
    val savedHashes by sharedScanViewModel.savedHashes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var rawSmsDialogText by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRegisteredStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(recentlySavedSmsHash) {
        recentlySavedSmsHash?.let { hash ->
            viewModel.markItemAsRegistered(hash)
            viewModel.refreshRegisteredStatus()
            sharedScanViewModel.clearRecentlySavedSmsHash()
        }
    }

    LaunchedEffect(savedHashes) {
        if (savedHashes.isNotEmpty()) {
            savedHashes.forEach { hash ->
                viewModel.markItemAsRegistered(hash)
            }
            viewModel.refreshRegisteredStatus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshRegisteredStatus()
    }

    if (rawSmsDialogText != null) {
        AlertDialog(
            onDismissRequest = { rawSmsDialogText = null },
            title = {
                Text(
                    text = "متن کامل پیامک",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        SelectionContainer {
                            Text(
                                text = rawSmsDialogText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "می‌توانید متن بالا را لمس کرده و بخش دلخواه را کپی کنید، یا دکمه کپی را بزنید.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rawSmsDialogText?.let { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "متن پیامک کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("کپی کردن متن", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rawSmsDialogText = null }) {
                    Text("بستن")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updatePermissionState(isGranted)
        if (isGranted) {
            try {
                viewModel.scanSms(context.applicationContext)
            } catch (e: Throwable) {
                Toast.makeText(context, "خطا در شروع اسکن: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermissionState(isGranted)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسکن پیامک‌های بانکی", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item 1: Scan Card (Compact when completed, full when not yet scanned)
            item {
                if (state.isScanCompleted) {
                    // Compact single-row card after scan
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val daysOptions = listOf(7, 30, 90)
                                daysOptions.forEach { days ->
                                    FilterChip(
                                        selected = state.selectedDays == days,
                                        onClick = { viewModel.setSelectedDays(days) },
                                        label = { Text("$days روز".toPersianDigits(), fontSize = 11.sp) },
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    try {
                                        if (state.hasPermission) {
                                            viewModel.scanSms(context.applicationContext)
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                                        }
                                    } catch (e: Throwable) {
                                        Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !state.isLoading,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("در حال اسکن...", fontSize = 11.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("اسکن مجدد", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Full Scan Card before scan
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "اسکن و تحلیل پیامک‌های بانکی",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "پیامک‌های دریافتی از بانک‌ها طبق الگوهای تعریف‌شده شناسایی و آماده ثبت می‌شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // Range Selector Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val daysOptions = listOf(7, 30, 90)
                                daysOptions.forEach { days ->
                                    FilterChip(
                                        selected = state.selectedDays == days,
                                        onClick = { viewModel.setSelectedDays(days) },
                                        label = { Text("$days روز گذشته".toPersianDigits()) },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            // Scan Action Button
                            Button(
                                onClick = {
                                    try {
                                        if (state.hasPermission) {
                                            viewModel.scanSms(context.applicationContext)
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                                        }
                                    } catch (e: Throwable) {
                                        Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("در حال اسکن و پردازش...")
                                } else {
                                    Text(if (state.hasPermission) "شروع اسکن پیامک‌ها" else "درخواست مجوز و اسکن")
                                }
                            }
                        }
                    }
                }
            }

            if (!state.hasPermission) {
                item {
                    Text(
                        text = "برای اسکن پیامک‌ها نیاز به مجوز خواندن پیامک است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Results Section
            if (state.isScanCompleted && !state.isLoading) {
                if (state.scannedList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "پیامک بانکی جدیدی پیدا نشد",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "همه تراکنش‌های یافت شده قبلاً ثبت شده‌اند یا پیامک جدیدی وجود ندارد.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    if (state.detectedTransfers.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "انتقال‌های بین حسابی شناسایی شده (${state.detectedTransfers.size})".toPersianDigits(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        itemsIndexed(state.detectedTransfers, key = { index, tr -> "transfer_${tr.id}_$index" }) { _, transfer ->
                            DetectedTransferCard(
                                transfer = transfer,
                                onRegisterClick = {
                                    try {
                                        ScanSmsDataHolder.detectedTransfer = transfer
                                        ScanSmsDataHolder.openAsTransfer = true
                                        ScanSmsDataHolder.selectedItem = transfer.withdrawalItem
                                        sharedScanViewModel.setPendingScanItem(transfer.withdrawalItem)
                                        onNavigateToPreConfirm()
                                    } catch (e: Throwable) {
                                        Log.e("ScanSmsScreen", "Crash opening pre_confirm transfer", e)
                                        Toast.makeText(context, "خطا در باز کردن صفحه", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = {
                                    rawSmsDialogText = "پیامک برداشت:\n${transfer.withdrawalItem.rawText}\n\nپیامک واریز:\n${transfer.depositItem.rawText}"
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "پیامک‌های دریافتی (${state.scannedList.size})".toPersianDigits(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    itemsIndexed(state.scannedList, key = { index, it -> "${it.id}_$index" }) { _, item ->
                        val matchingTransfer = state.detectedTransfers.find {
                            it.withdrawalItem.id == item.id || it.depositItem.id == item.id
                        }
                        ScannedSmsCard(
                            item = item,
                            matchingTransfer = matchingTransfer,
                            onAddClick = {
                                try {
                                    if (matchingTransfer != null && !matchingTransfer.isRegistered) {
                                        ScanSmsDataHolder.detectedTransfer = matchingTransfer
                                        ScanSmsDataHolder.openAsTransfer = true
                                    } else {
                                        ScanSmsDataHolder.detectedTransfer = null
                                        ScanSmsDataHolder.openAsTransfer = false
                                    }
                                    sharedScanViewModel.setPendingScanItem(item)
                                    ScanSmsDataHolder.selectedItem = item
                                    onNavigateToPreConfirm()
                                } catch (e: Throwable) {
                                    Log.e("ScanSmsScreen", "Crash opening pre_confirm", e)
                                    Toast.makeText(context, "خطا در باز کردن صفحه", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {
                                rawSmsDialogText = item.rawText
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetectedTransferCard(
    transfer: DetectedTransfer,
    onRegisterClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val isRegistered = transfer.isRegistered

    val cardBg = if (isRegistered) {
        if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color(0xFFF1F3F5)
    } else {
        if (isDark) MaterialTheme.colorScheme.surface else Color.White
    }

    val cardBorder = if (isRegistered) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (!isRegistered) {
                        onRegisterClick()
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRegistered) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = if (isRegistered) MaterialTheme.colorScheme.onSurfaceVariant else primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "انتقال بین حساب‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRegistered) MaterialTheme.colorScheme.onSurfaceVariant else primaryColor
                    )
                }

                if (isRegistered) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E3320) else Color(0xFFE8F5E9))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ثبت‌شده",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(primaryColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "شناسایی شد",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Transfer details: Source -> Destination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source (مبدأ)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مبدأ (برداشت):",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BankLogoBadge(
                            bankId = transfer.sourceAccount?.logoResName,
                            accountName = transfer.sourceAccount?.name ?: transfer.withdrawalItem.parsedSms.bankName,
                            cardNumber = transfer.sourceAccount?.cardNumber,
                            size = 20.dp,
                            shapeRadius = 4.dp
                        )
                        Text(
                            text = transfer.sourceAccount?.name ?: transfer.withdrawalItem.parsedSms.bankName ?: "بانک مبدأ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = DateFormatter.formatLong(transfer.withdrawalItem.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Arrow icon: correctly points towards destination account
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                // Destination (مقصد)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "مقصد (واریز):",
                        style = MaterialTheme.typography.labelSmall,
                        color = IncomeColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = transfer.destAccount?.name ?: transfer.depositItem.parsedSms.bankName ?: "بانک مقصد",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        BankLogoBadge(
                            bankId = transfer.destAccount?.logoResName,
                            accountName = transfer.destAccount?.name ?: transfer.depositItem.parsedSms.bankName,
                            cardNumber = transfer.destAccount?.cardNumber,
                            size = 20.dp,
                            shapeRadius = 4.dp
                        )
                    }
                    Text(
                        text = DateFormatter.formatLong(transfer.depositItem.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Amount & Fee
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مبلغ انتقال:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = AmountFormatter.formatRial(transfer.transferAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }

                if (transfer.feeAmount > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "کارمزد بانکی:",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExpenseColor
                        )
                        Text(
                            text = AmountFormatter.formatRial(transfer.feeAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseColor
                        )
                    }
                }
            }

            // Action Button
            if (!isRegistered) {
                Button(
                    onClick = onRegisterClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ثبت این انتقال بین حساب‌ها", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScannedSmsCard(
    item: ScannedSmsItem,
    matchingTransfer: DetectedTransfer? = null,
    onAddClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val parsed = item.parsedSms
    val isExpense = parsed.transactionType != TransactionType.INCOME
    val typeText = if (isExpense) "هزینه" else "درآمد"
    val baseTypeColor = if (isExpense) ExpenseColor else IncomeColor

    // Distinct greyed-out background for registered items
    val cardBg = if (item.isRegistered) {
        if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color(0xFFF1F3F5)
    } else {
        if (isDark) MaterialTheme.colorScheme.surface else Color.White
    }

    val cardBorder = if (item.isRegistered) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    val textColor = if (item.isRegistered) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subtextColor = if (item.isRegistered) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val typeColor = if (item.isRegistered) {
        baseTypeColor.copy(alpha = 0.6f)
    } else {
        baseTypeColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    if (!item.isRegistered) {
                        onAddClick()
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isRegistered) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parsed.bankName ?: "بانک",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(typeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    if (matchingTransfer != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "انتقال بین حسابی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (item.isRegistered) {
                        // برچسب «ثبت‌شده» خوانا و باکلاس با آیکون تیک سبز
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF1E3320) else Color(0xFFE8F5E9))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "ثبت‌شده",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // برچسب «جدید» برجسته
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(IncomeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "جدید",
                                style = MaterialTheme.typography.labelSmall,
                                color = IncomeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = AmountFormatter.formatRial(parsed.amount ?: 0L),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    parsed.accountIdentifier?.let { acc ->
                        Text(
                            text = "حساب: $acc",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                    parsed.date?.let { ts ->
                        Text(
                            text = DateFormatter.formatLong(ts),
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (item.isRegistered) {
                // آیکون تیک شکیل به جای دکمه برای تراکنش‌های ثبت‌شده
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E3320) else Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "قبلاً ثبت شده",
                        tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "ثبت تراکنش",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnrecognizedSmsCard(
    item: ScannedSmsItem,
    onLearnPatternClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "هیچ الگویی برای این پیامک تعریف نشده",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = DateFormatter.formatLong(item.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = item.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )

            OutlinedButton(
                onClick = onLearnPatternClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Pattern,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("تعریف و آموزش الگوی این پیامک")
            }
        }
    }
}
