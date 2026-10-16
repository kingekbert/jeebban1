package com.example.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.FinTrackApp
import com.example.MainActivity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.FinTrackTheme
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.CategoryGuesser
import com.example.util.toPersianDigits
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class WidgetCategoryPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val smsHash = intent.getStringExtra("extra_sms_hash") ?: run {
            finish()
            return
        }
        val amount = intent.getLongExtra("extra_amount", 0L)
        val typeStr = intent.getStringExtra("extra_type") ?: TransactionType.EXPENSE.name
        val txType = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.EXPENSE }
        val date = intent.getLongExtra("extra_date", System.currentTimeMillis())
        val bankName = intent.getStringExtra("extra_bank_name") ?: ""
        val accountIdent = intent.getStringExtra("extra_account_ident") ?: ""
        val note = intent.getStringExtra("extra_note") ?: ""

        val app = application as FinTrackApp
        val appModule = app.appModule

        setContent {
            val themeMode = appModule.settingsRepository.themeMode
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            FinTrackTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CategoryPickerSheet(
                        amount = amount,
                        txType = txType,
                        bankName = bankName,
                        accountIdent = accountIdent,
                        note = note,
                        onDismiss = { finish() },
                        onCategorySelected = { category ->
                            saveTransactionWithCategory(
                                appModule = appModule,
                                category = category,
                                smsHash = smsHash,
                                amount = amount,
                                txType = txType,
                                date = date,
                                bankName = bankName,
                                accountIdent = accountIdent,
                                note = note
                            )
                        },
                        onOpenFullApp = {
                            openFullAppPreConfirm()
                        }
                    )
                }
            }
        }
    }

    private fun saveTransactionWithCategory(
        appModule: com.example.di.AppModule,
        category: CategoryEntity,
        smsHash: String,
        amount: Long,
        txType: TransactionType,
        date: Long,
        bankName: String,
        accountIdent: String,
        note: String
    ) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                val allAccounts = appModule.accountRepository.getAllAccountsList()
                val matchedAccount = allAccounts.find { acc ->
                    (accountIdent.isNotBlank() && (
                        (acc.cardNumber != null && acc.cardNumber.contains(accountIdent)) ||
                        (acc.shabaNumber != null && acc.shabaNumber.contains(accountIdent)) ||
                        acc.name.contains(accountIdent)
                    ))
                } ?: allAccounts.find { acc ->
                    bankName.isNotBlank() && (acc.name.contains(bankName, ignoreCase = true) || bankName.contains(acc.name, ignoreCase = true))
                }
                val accountId = matchedAccount?.id ?: allAccounts.firstOrNull()?.id

                CategoryGuesser.saveLastUsedCategory(this@WidgetCategoryPickerActivity, txType, category.id)

                val transaction = TransactionEntity(
                    type = txType,
                    amount = amount,
                    accountId = accountId,
                    categoryId = category.id,
                    date = date,
                    note = note.ifBlank { null },
                    smsHash = smsHash
                )

                var isSuccess = false
                if (txType == TransactionType.INCOME) {
                    val matchingShares = try {
                        appModule.groupExpenseRepository.findMatchingOpenShares(amount)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val bestMatch = matchingShares.firstOrNull()
                    if (bestMatch != null) {
                        try {
                            appModule.groupExpenseRepository.settleShareWithIncomeTransaction(
                                shareId = bestMatch.share.id,
                                incomeTransaction = transaction
                            )
                            isSuccess = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (!isSuccess) {
                    val result = appModule.addTransactionUseCase(transaction)
                    isSuccess = result.isSuccess
                }

                if (isSuccess) {
                    val remaining = WidgetDataStore.removeItem(this@WidgetCategoryPickerActivity, smsHash)
                    WidgetStatusHelper.saveScanResult(this@WidgetCategoryPickerActivity, remaining.size)
                    WidgetStatusHelper.updateAllWidgets(this@WidgetCategoryPickerActivity)

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            this@WidgetCategoryPickerActivity,
                            "ثبت شد — ${category.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            this@WidgetCategoryPickerActivity,
                            "خطا در ثبت — اپ را باز کنید",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@WidgetCategoryPickerActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openFullAppPreConfirm() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_auto_scan", true)
            putExtra("navigate_to", "widget_scan")
        }
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerSheet(
    amount: Long,
    txType: TransactionType,
    bankName: String,
    accountIdent: String,
    note: String,
    onDismiss: () -> Unit,
    onCategorySelected: (CategoryEntity) -> Unit,
    onOpenFullApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as FinTrackApp
    var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    var suggestedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val isIncome = txType == TransactionType.INCOME
    val amountColor = if (isIncome) IncomeColor else ExpenseColor
    val typeTitle = if (isIncome) "واریز" else "برداشت"

    LaunchedEffect(Unit) {
        val allCats = try { app.appModule.categoryRepository.allCategories.firstOrNull() ?: emptyList() } catch (e: Exception) { emptyList() }
        val filtered = allCats.filter {
            when (txType) {
                TransactionType.EXPENSE -> it.type == CategoryType.EXPENSE || it.type == CategoryType.BOTH
                TransactionType.INCOME -> it.type == CategoryType.INCOME || it.type == CategoryType.BOTH
                else -> true
            }
        }
        val fullText = "$note $bankName $accountIdent"
        suggestedCategory = CategoryGuesser.guessCategory(fullText, filtered) ?: filtered.firstOrNull()
        categories = filtered
        isLoading = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}, // consume clicks so sheet doesn't close
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .align(Alignment.CenterHorizontally)
            )

            // Header Row: Amount + Bank + Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = amountColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = typeTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bankName.ifBlank { "بانک" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = AmountFormatter.formatAmountWithCurrency(amount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = amountColor
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                }
            }

            Text(
                text = "دسته‌بندی این تراکنش را انتخاب کنید:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Categories FlowRow / Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categories.forEach { cat ->
                        val isSuggested = cat.id == suggestedCategory?.id
                        CategoryChip(
                            category = cat,
                            isSuggested = isSuggested,
                            onClick = { onCategorySelected(cat) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action to open full app if user wants to edit note/account/dong
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenFullApp) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ویرایش کامل جزئیات در برنامه",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "انصراف",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: CategoryEntity,
    isSuggested: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuggested)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSuggested) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSuggested) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "پیشنهادی",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSuggested) FontWeight.Bold else FontWeight.Medium,
                color = if (isSuggested) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )

            if (isSuggested) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "پیشنهادی",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
