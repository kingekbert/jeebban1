package com.example.ui.screens.scan_sms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionType
import com.example.ui.theme.ExpenseColor
import com.example.ui.theme.IncomeColor
import com.example.util.AmountFormatter
import com.example.util.BankLogoBadge
import com.example.util.toPersianDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreConfirmTypePickerSheet(
    currentType: TransactionType,
    onSelectType: (TransactionType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "تغییر نوع تراکنش",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "در صورت نیاز می‌توانید نوع شناسایی‌شده را به صورت دستی تغییر دهید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            TypeOptionRow(
                title = "هزینه (برداشت)",
                subtitle = "ثبت به عنوان مخارج و کسر از حساب",
                icon = Icons.Default.ArrowUpward,
                accentColor = ExpenseColor,
                selected = currentType == TransactionType.EXPENSE,
                onClick = {
                    onSelectType(TransactionType.EXPENSE)
                    onDismiss()
                }
            )

            TypeOptionRow(
                title = "درآمد (واریز)",
                subtitle = "ثبت به عنوان واریز و افزایش موجودی حساب",
                icon = Icons.Default.ArrowDownward,
                accentColor = IncomeColor,
                selected = currentType == TransactionType.INCOME,
                onClick = {
                    onSelectType(TransactionType.INCOME)
                    onDismiss()
                }
            )

            TypeOptionRow(
                title = "انتقال بین حساب‌ها",
                subtitle = "جابه‌جایی وجه بین دو حساب بانکی خودتان",
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                accentColor = MaterialTheme.colorScheme.primary,
                selected = currentType == TransactionType.TRANSFER,
                onClick = {
                    onSelectType(TransactionType.TRANSFER)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun TypeOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (selected) BorderStroke(1.5.dp, accentColor) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "انتخاب شده",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreConfirmAccountPickerSheet(
    title: String,
    accounts: List<AccountEntity>,
    selectedAccountId: Long?,
    onSelectAccount: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (accounts.isEmpty()) {
                Text(
                    text = "هنوز هیچ حسابی ثبت نشده است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        val isSelected = acc.id == selectedAccountId
                        Surface(
                            onClick = {
                                onSelectAccount(acc.id)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BankLogoBadge(
                                    bankId = acc.logoResName,
                                    accountName = acc.name,
                                    cardNumber = acc.cardNumber,
                                    size = 34.dp,
                                    shapeRadius = 8.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val last4 = acc.cardNumber?.takeLast(4)?.takeIf { it.isNotBlank() }
                                    if (last4 != null) {
                                        Text(
                                            text = "شماره کارت: **** ${last4.toPersianDigits()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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

@Composable
internal fun PreConfirmTransferAccountsCard(
    accounts: List<AccountEntity>,
    sourceAccountId: Long?,
    destAccountId: Long?,
    onPickSourceClick: () -> Unit,
    onPickDestClick: () -> Unit
) {
    val srcAccount = accounts.find { it.id == sourceAccountId }
    val destAccount = accounts.find { it.id == destAccountId }

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
                text = "حساب‌های انتقال",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            TransferAccountRow(
                label = "حساب مبدأ (برداشت از):",
                labelColor = ExpenseColor,
                account = srcAccount,
                placeholder = "انتخاب حساب مبدأ...",
                onClick = onPickSourceClick
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            TransferAccountRow(
                label = "حساب مقصد (واریز به):",
                labelColor = IncomeColor,
                account = destAccount,
                placeholder = "انتخاب حساب مقصد...",
                onClick = onPickDestClick
            )
        }
    }
}

@Composable
private fun TransferAccountRow(
    label: String,
    labelColor: Color,
    account: AccountEntity?,
    placeholder: String,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = FontWeight.Bold
        )
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (account != null) {
                    BankLogoBadge(
                        bankId = account.logoResName,
                        accountName = account.name,
                        cardNumber = account.cardNumber,
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
                    text = account?.name ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun PreConfirmGroupExpenseCard(
    state: PreConfirmUiState,
    newPersonName: String,
    onNewPersonNameChange: (String) -> Unit,
    onDisable: () -> Unit,
    onShareModeChange: (GroupExpenseShareMode) -> Unit,
    onAddPerson: (String) -> Unit,
    onRemovePerson: (String) -> Unit,
    onUpdatePersonAmount: (String, Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
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
                        tint = MaterialTheme.colorScheme.primary
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
                IconButton(onClick = onDisable) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "لغو هزینه مشترک",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TabRow(
                selectedTabIndex = if (state.groupShareMode == GroupExpenseShareMode.EQUAL) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = state.groupShareMode == GroupExpenseShareMode.EQUAL,
                    onClick = { onShareModeChange(GroupExpenseShareMode.EQUAL) },
                    text = { Text("تقسیم مساوی") }
                )
                Tab(
                    selected = state.groupShareMode == GroupExpenseShareMode.CUSTOM,
                    onClick = { onShareModeChange(GroupExpenseShareMode.CUSTOM) },
                    text = { Text("سهم دلخواه") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newPersonName,
                    onValueChange = onNewPersonNameChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("نام دوست یا هم‌سفر...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = {
                        if (newPersonName.isNotBlank()) {
                            onAddPerson(newPersonName)
                            onNewPersonNameChange("")
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن")
                }
            }

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
                                onClick = { onAddPerson(name) },
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

                                if (state.groupShareMode == GroupExpenseShareMode.CUSTOM) {
                                    OutlinedTextField(
                                        value = if (person.amount > 0) person.amount.toString() else "",
                                        onValueChange = { input ->
                                            val clean = input.filter { it.isDigit() }
                                            val num = clean.toLongOrNull() ?: 0L
                                            onUpdatePersonAmount(person.id, num)
                                        },
                                        modifier = Modifier.width(130.dp),
                                        placeholder = { Text("مبلغ ریال") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemovePerson(person.id) }
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
