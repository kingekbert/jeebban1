package com.example.ui.screens.scan_sms

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.TransactionType
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.DateFormatter
import com.example.util.toPersianDigits
import com.example.widget.WidgetStatusHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetScanScreen(
    viewModel: ScanSmsViewModel,
    sharedScanViewModel: SharedScanViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPreConfirm: () -> Unit,
    onNavigateToFullScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedHashes by sharedScanViewModel.savedHashes.collectAsStateWithLifecycle()
    val recentlySavedHash by sharedScanViewModel.recentlySavedSmsHash.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        viewModel.updatePermissionState(isGranted)
        if (isGranted) {
            viewModel.setSelectedDays(7)
            viewModel.scanSms(context)
        }
    }

    // Auto-scan 7 days of SMS on launch if permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.setSelectedDays(7)
            viewModel.scanSms(context)
        }
    }

    // Track newly saved SMS to mark as registered in ViewModel
    LaunchedEffect(recentlySavedHash) {
        recentlySavedHash?.let { hash ->
            viewModel.markItemAsRegistered(hash)
            sharedScanViewModel.clearRecentlySavedSmsHash()
        }
    }

    // Filter strictly to ONLY NEW / UNREGISTERED items
    val newItems = remember(state.scannedList, savedHashes) {
        WidgetStatusHelper.filterUnregisteredSms(state.scannedList).filter { it.smsHash !in savedHashes }
    }

    // Sync widget status when scan completes
    LaunchedEffect(state.isScanCompleted, state.isLoading, newItems.size) {
        if (!state.isLoading && state.isScanCompleted) {
            WidgetStatusHelper.saveScanResult(context, newItems.size)
            WidgetStatusHelper.updateAllWidgets(context)
        }
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
                title = {
                    Column {
                        Text(
                            text = "اسکن سریع پیامک‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "پیامک‌های ثبت‌نشده ۷ روز اخیر",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (hasPermission) {
                                viewModel.setSelectedDays(7)
                                viewModel.scanSms(context)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "اسکن مجدد",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToFullScan
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "اسکن کامل پیامک‌ها",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 1. Permission not granted state
                !hasPermission -> {
                    WidgetScanPermissionState(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }

                // 2. Loading / Scanning state
                state.isLoading -> {
                    WidgetScanLoadingState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }

                // 3. Empty state: All SMS of last 7 days are already registered
                newItems.isEmpty() && state.isScanCompleted -> {
                    WidgetScanEmptyState(
                        onNavigateToFullScan = onNavigateToFullScan,
                        onRescan = {
                            viewModel.setSelectedDays(7)
                            viewModel.scanSms(context)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }

                // 4. List of new unregistered SMS
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${newItems.size.toString().toPersianDigits()} پیامک جدید آماده ثبت",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "بازگشت خودکار پس از ثبت",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        items(
                            items = newItems,
                            key = { it.smsHash }
                        ) { item ->
                            WidgetScanItemCard(
                                item = item,
                                onRegisterClick = {
                                    sharedScanViewModel.setPendingScanItem(item)
                                    ScanSmsDataHolder.selectedItem = item
                                    onNavigateToPreConfirm()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetScanItemCard(
    item: ScannedSmsItem,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsed = item.parsedSms
    val isIncome = parsed.transactionType == TransactionType.INCOME
    val amountColor = if (isIncome) IncomeColor else ExpenseColor
    val typeIcon = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward
    val typeText = if (isIncome) "واریز" else "برداشت"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onRegisterClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Bank & Account badge + Type tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(amountColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = typeText,
                            tint = amountColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = parsed.bankName?.ifBlank { "بانک" } ?: "بانک",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!parsed.accountIdentifier.isNullOrBlank()) {
                            Text(
                                text = "حساب: ${parsed.accountIdentifier.toPersianDigits()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = amountColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = typeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Amount & Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مبلغ استخراج‌شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = AmountFormatter.formatAmountWithCurrency(parsed.amount ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val dateFormatted = DateFormatter.formatShort(item.date)
                    Text(
                        text = "تاریخ پیامک",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Raw SMS text excerpt
            if (item.rawText.isNotBlank()) {
                Text(
                    text = item.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Register Action Button (ثبت +)
            Button(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ثبت تراکنش",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WidgetScanLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "در حال اسکن پیامک‌های ۷ روز گذشته...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "فیلتر هوشمند کدهای یکبار مصرف و پیامک‌های ثبت‌شده",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WidgetScanEmptyState(
    onNavigateToFullScan: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(IncomeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = IncomeColor,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "همه پیامک‌های ۷ روز گذشته ثبت شده‌اند ✅",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "هیچ پیامک بانکی ثبت‌نشده‌ای در ۷ روز اخیر یافت نشد.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRescan,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("اسکن مجدد")
            }

            OutlinedButton(
                onClick = onNavigateToFullScan,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("مشاهده اسکن کامل")
            }
        }
    }
}

@Composable
private fun WidgetScanPermissionState(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "نیاز به دسترسی پیامک",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "برای شناسایی خودکار پیامک‌های بانکی ۷ روز اخیر و ثبت بدون دردسر هزینه‌ها، لطفاً دسترسی خواندن پیامک را فعال کنید.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.Sms, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "اعطای مجوز و شروع اسکن",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
