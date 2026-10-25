package com.example.ui.screens.accounts

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.AccountType
import com.example.ui.components.AmountInput
import com.example.ui.components.AppColorPalette
import com.example.ui.components.AppIconsList
import com.example.util.BankLogoBadge
import com.example.util.BankLogos
import com.example.util.JalaliDate
import com.example.util.SmsConversationInfo
import com.example.util.toPersianDigits
import java.io.ByteArrayOutputStream

val AccountColorPalette = AppColorPalette
val AccountIconsList = AppIconsList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPatternLearner: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val maxDim = 256
                    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        if (ratio > 1) {
                            Bitmap.createScaledBitmap(bitmap, maxDim, (maxDim / ratio).toInt(), true)
                        } else {
                            Bitmap.createScaledBitmap(bitmap, (maxDim * ratio).toInt(), maxDim, true)
                        }
                    } else bitmap
                    val outputStream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    viewModel.setLogoImage(base64)
                }
            } catch (e: Exception) {
                // handle error silently
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setShowConversationDialog(true, context)
        } else {
            Toast.makeText(context, "برای مشاهده کانورسیشن‌ها مجوز خواندن پیامک لازم است", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) "ویرایش حساب" else "افزودن حساب جدید",
                        fontWeight = FontWeight.Bold
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Name
            Text("نام حساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("مثال: بانک ملی، کیف پول") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Account Type Selection
            Text("نوع حساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.type == AccountType.BANK,
                    onClick = { viewModel.setType(AccountType.BANK) },
                    label = { Text("بانکی") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.type == AccountType.CASH,
                    onClick = { viewModel.setType(AccountType.CASH) },
                    label = { Text("نقدی / کیف پول") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.type == AccountType.CREDIT_CARD,
                    onClick = { viewModel.setType(AccountType.CREDIT_CARD) },
                    label = { Text("کارت اعتباری") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Initial Balance
            Text("موجودی اولیه (ریال)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AmountInput(
                amount = state.initialBalance,
                onAmountChange = { viewModel.setInitialBalance(it) }
            )

            // Card & Bank Details (Optional)
            Text("اطلاعات کارت و حساب (اختیاری)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = state.cardNumber,
                onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) viewModel.setCardNumber(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("شماره کارت (۱۶ رقم)") },
                placeholder = { Text("۶۰۳۷۹۹۷۵۱۲۳۴۵۶۷۸") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.shabaNumber,
                    onValueChange = { viewModel.setShabaNumber(it) },
                    modifier = Modifier.weight(1.5f),
                    label = { Text("شماره شبا") },
                    placeholder = { Text("IR000000000000000000000000") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = state.cardExpiry,
                    onValueChange = { viewModel.setCardExpiry(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("انقضا") },
                    placeholder = { Text("04/08") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // SMS Senders Section (اتصال سرشماره پیامک به حساب)
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = "سرشماره‌های پیامک",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "سرشماره‌های پیامک بانکی",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (state.smsSenders.isNotEmpty()) {
                            Text(
                                "${state.smsSenders.size.toString().toPersianDigits()} سرشماره",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        "با اتصال سرشماره‌های بانک به این حساب، پیامک‌های این سرشماره مستقیماً با بالاترین اولویت به این حساب مرتبط می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Action buttons: Select Conversation and Add Manually
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_SMS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    viewModel.setShowConversationDialog(true, context)
                                } else {
                                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "انتخاب کانورسیشن",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("انتخاب کانورسیشن", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.setShowManualSenderDialog(true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "افزودن دستی",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("افزودن دستی", fontSize = 12.sp)
                        }
                    }

                    // Chips list
                    if (state.smsSenders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "سرشماره‌ای ثبت نشده است (در این حالت کل اینباکس اسکن می‌شود)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.smsSenders.forEach { sender ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = {
                                        Text(
                                            text = sender.toPersianDigits(),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "حذف $sender",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.removeSender(sender) }
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Bank Logo Selection (17 Major Iranian Banks + Gallery + No Logo)
            Text("لوگوی حساب و بانک", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val detectedBank = if (state.isNoLogo) null else (
                BankLogos.getBankById(state.logoResName)
                    ?: BankLogos.detectBank(state.cardNumber, state.name)
            )

            // Logo Options Actions (Gallery Picker, No Logo, Auto-detect)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "انتخاب از گالری",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("از گالری", fontSize = 12.sp)
                }

                FilterChip(
                    selected = state.isNoLogo,
                    onClick = { viewModel.setNoLogo(!state.isNoLogo) },
                    label = { Text("بدون لوگو", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "بدون لوگو",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                if (state.logoResName != null || state.logoImage != null || state.isNoLogo) {
                    TextButton(
                        onClick = {
                            viewModel.setNoLogo(false)
                            viewModel.setLogoResName(null)
                            viewModel.setLogoImage(null)
                        }
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "خودکار", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("خودکار", fontSize = 12.sp)
                    }
                }
            }

            // Current Selected Logo Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BankLogoBadge(
                    bankId = if (state.isNoLogo) null else state.logoResName,
                    logoImage = if (state.isNoLogo) null else state.logoImage,
                    accountName = state.name,
                    cardNumber = state.cardNumber,
                    size = 46.dp,
                    fallbackColor = Color(state.color)
                )
                Column(modifier = Modifier.weight(1f)) {
                    val statusTitle = when {
                        state.isNoLogo -> "بدون لوگوی اختصاصی"
                        state.logoImage != null -> "تصویر انتخاب‌شده از گالری"
                        detectedBank != null -> "بانک انتخاب‌شده: ${detectedBank.namePersian}"
                        else -> "لوگوی پیش‌فرض (یا تشخیص هوشمند)"
                    }
                    val statusSubtitle = when {
                        state.isNoLogo -> "آیکون استاندارد نمایش داده می‌شود"
                        state.logoImage != null -> "تصویر سفارشی برای این حساب ذخیره می‌شود"
                        detectedBank != null -> detectedBank.nameEnglish
                        else -> "شماره کارت یا نام بانک را وارد کنید"
                    }
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bank Logos List (17 Iranian Banks)
            Text("انتخاب از بانک‌های کشور", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BankLogos.banks, key = { it.id }) { bank ->
                    val isSelected = !state.isNoLogo && state.logoImage == null && (state.logoResName == bank.id || (state.logoResName == null && detectedBank?.id == bank.id))
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setLogoResName(bank.id)
                            viewModel.setColor(bank.color.toInt())
                        },
                        label = { Text(bank.namePersian, fontSize = 12.sp) },
                        leadingIcon = {
                            BankLogoBadge(
                                bankId = bank.id,
                                size = 22.dp,
                                shapeRadius = 5.dp
                            )
                        }
                    )
                }
            }

            // Icon Picker (Bank & General Icons)
            Text("آیکون / بانک حساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(com.example.ui.components.IranianBankIcons, key = { it.id }) { bank ->
                    val isSelected = state.icon == bank.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setIcon(bank.id) },
                        label = { Text(bank.name) },
                        leadingIcon = {
                            com.example.ui.components.AccountIconDisplay(
                                iconName = bank.id,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            // Color Picker
            Text("رنگ حساب (۲۴+ رنگ)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AccountColorPalette) { color ->
                    val colorArgb = color.toArgb()
                    val isSelected = state.color == colorArgb

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setColor(colorArgb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "انتخاب شده",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveAccount() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(state.color))
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "ذخیره")
                Spacer(modifier = Modifier.width(8.dp))
                Text("ذخیره حساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Modal Dialog: Select Conversation
    if (state.showConversationDialog) {
        ConversationSelectionDialog(
            conversations = state.conversationList,
            isLoading = state.isLoadingConversations,
            onDismiss = { viewModel.setShowConversationDialog(false) },
            onSelect = { conv ->
                viewModel.addSender(conv.address)
                viewModel.setShowConversationDialog(false)
            }
        )
    }

    // Modal Dialog: Manual Sender Input
    if (state.showManualSenderDialog) {
        ManualSenderDialog(
            onDismiss = { viewModel.setShowManualSenderDialog(false) },
            onAdd = { sender ->
                viewModel.addSender(sender)
                viewModel.setShowManualSenderDialog(false)
            }
        )
    }
}

@Composable
fun ConversationSelectionDialog(
    conversations: List<SmsConversationInfo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (SmsConversationInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.address.contains(searchQuery, ignoreCase = true) ||
            it.snippet.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("انتخاب کانورسیشن پیامک", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جستجوی سرشماره یا متن پیام...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "جستجو")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (conversations.isEmpty()) "هیچ پیامکی در دستگاه یافت نشد." else "موردی مطابق با جستجو پیدا نشد.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.address }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(item) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.address.toPersianDigits(),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (item.date > 0) {
                                            val jalali = JalaliDate.fromTimestamp(item.date)
                                            Text(
                                                text = "${jalali.year}/${jalali.month.toString().padStart(2, '0')}/${jalali.day.toString().padStart(2, '0')}".toPersianDigits(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (item.snippet.isNotBlank()) {
                                        Text(
                                            text = item.snippet.toPersianDigits(),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@Composable
fun ManualSenderDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن دستی سرشماره", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "سرشماره یا نام فرستنده پیامک‌های بانک را وارد کنید (مانند B.QMEHRIRAN یا +98700717 یا 2000...):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("مثال: B.QMEHRIRAN") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onAdd(text.trim())
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("افزودن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
