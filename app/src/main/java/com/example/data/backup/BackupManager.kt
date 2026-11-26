package com.example.data.backup

import androidx.room.withTransaction
import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountBalanceSnapshotEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountSmsSenderEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ChequeEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.GroupExpenseEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.entity.RecurringTransactionEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.SavingsGoalEntryEntity
import com.example.data.local.entity.SmsPatternEntity
import com.example.data.local.entity.TransactionEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class BackupData(
    val backupFormatVersion: Int = BackupManager.CURRENT_BACKUP_FORMAT_VERSION,
    @SerializedName(value = "databaseSchemaVersion", alternate = ["schemaVersion"])
    val databaseSchemaVersion: Int = FinTrackDatabase.DATABASE_VERSION,
    val version: Int? = null, // Backward compatibility with legacy backups
    val timestamp: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val debts: List<DebtEntity>? = null,
    val sms_patterns: List<SmsPatternEntity>? = null,
    val cheques: List<ChequeEntity>? = null,
    val recurring_transactions: List<RecurringTransactionEntity>? = null,
    val savings_goals: List<SavingsGoalEntity>? = null,
    val savings_goal_entries: List<SavingsGoalEntryEntity>? = null,
    val group_expenses: List<GroupExpenseEntity>? = null,
    val group_expense_shares: List<GroupExpenseShareEntity>? = null,
    val account_sms_senders: List<AccountSmsSenderEntity>? = null,
    val account_balance_snapshots: List<AccountBalanceSnapshotEntity>? = null
)

sealed class BackupValidationResult {
    data class Valid(val backupData: BackupData) : BackupValidationResult()
    data class Invalid(val reason: String) : BackupValidationResult()
}

sealed class BackupRestoreResult {
    data object Success : BackupRestoreResult()
    data class Failure(val reason: String) : BackupRestoreResult()
}

internal interface BackupStorageAdapter {
    suspend fun readSnapshot(): BackupData
    suspend fun replaceAllDataAtomically(backupData: BackupData)
}

private class RoomBackupStorageAdapter(
    private val database: FinTrackDatabase
) : BackupStorageAdapter {

    override suspend fun readSnapshot(): BackupData {
        val accounts = database.accountDao().getAll().first()
        val categories = database.categoryDao().getAll().first()
        val transactions = database.transactionDao().getAll().first()
        val debts = database.debtDao().getAll().first()
        val smsPatterns = database.smsPatternDao().getAll().first()
        val cheques = database.chequeDao().getAll().first()
        val recurringTransactions = database.recurringDao().getAllEntities().first()
        val savingsGoals = database.savingsGoalDao().getAllGoalsRaw().first()
        val savingsGoalEntries = database.savingsGoalDao().getAllEntries().first()
        val groupExpenses = database.groupExpenseDao().getAllEntities()
        val groupExpenseShares = database.groupExpenseDao().getAllShares()
        val accountSmsSenders = database.accountSmsSenderDao().getAllSendersList()
        val balanceSnapshots = database.accountBalanceSnapshotDao().getAll().first()

        return BackupData(
            backupFormatVersion = BackupManager.CURRENT_BACKUP_FORMAT_VERSION,
            databaseSchemaVersion = BackupManager.CURRENT_DATABASE_SCHEMA_VERSION,
            version = BackupManager.LEGACY_BACKUP_VERSION,
            timestamp = System.currentTimeMillis(),
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            debts = debts,
            sms_patterns = smsPatterns,
            cheques = cheques,
            recurring_transactions = recurringTransactions,
            savings_goals = savingsGoals,
            savings_goal_entries = savingsGoalEntries,
            group_expenses = groupExpenses,
            group_expense_shares = groupExpenseShares,
            account_sms_senders = accountSmsSenders,
            account_balance_snapshots = balanceSnapshots
        )
    }

    override suspend fun replaceAllDataAtomically(backupData: BackupData) {
        database.withTransaction {
            // 1. Delete children first in strict reverse foreign-key dependency order
            database.accountBalanceSnapshotDao().deleteAll()
            database.accountSmsSenderDao().deleteAll()
            database.savingsGoalDao().deleteAllEntries()
            database.savingsGoalDao().deleteAll()
            database.groupExpenseDao().deleteAllShares()
            database.groupExpenseDao().deleteAllGroupExpenses()
            database.recurringDao().deleteAll()
            database.transactionDao().deleteAll()
            database.chequeDao().deleteAll()
            database.debtDao().deleteAll()
            database.smsPatternDao().deleteAll()
            database.categoryDao().deleteAll()
            database.accountDao().deleteAll()

            // 2. Insert root parents first
            backupData.accounts.forEach { database.accountDao().insert(it) }
            database.categoryDao().insertAll(backupData.categories)

            // 3. Insert account-dependent SMS sender links
            backupData.account_sms_senders?.takeIf { it.isNotEmpty() }?.let { senders ->
                database.accountSmsSenderDao().insertAll(senders)
            }

            // 4. Insert core transactions (depend on accounts & categories)
            backupData.transactions.forEach { database.transactionDao().insert(it) }

            // 5. Insert independent and secondary account/category-dependent tables
            backupData.debts?.forEach { database.debtDao().insert(it) }
            backupData.sms_patterns?.forEach { database.smsPatternDao().insert(it) }
            backupData.cheques?.forEach { database.chequeDao().insert(it) }
            backupData.recurring_transactions?.forEach { database.recurringDao().insert(it) }

            // 6. Insert savings goals before their entries (entries depend on goals & transactions)
            backupData.savings_goals?.forEach { database.savingsGoalDao().insert(it) }
            backupData.savings_goal_entries?.forEach { database.savingsGoalDao().insertEntry(it) }

            // 7. Insert group expenses (depend on transactions) before shares (depend on group_expenses & transactions)
            backupData.group_expenses?.forEach { database.groupExpenseDao().insertGroupExpense(it) }
            backupData.group_expense_shares?.takeIf { it.isNotEmpty() }?.let { shareList ->
                database.groupExpenseDao().insertShares(shareList)
            }

            // 8. Insert account balance snapshots (depend on accounts)
            backupData.account_balance_snapshots?.forEach { database.accountBalanceSnapshotDao().insert(it) }
        }
    }
}

class BackupManager internal constructor(
    private val storage: BackupStorageAdapter
) {
    constructor(database: FinTrackDatabase) : this(RoomBackupStorageAdapter(database))

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        const val CURRENT_BACKUP_FORMAT_VERSION = 1
        const val CURRENT_DATABASE_SCHEMA_VERSION = FinTrackDatabase.DATABASE_VERSION
        const val LEGACY_BACKUP_VERSION = 4

        private val MANDATORY_ARRAY_KEYS = listOf("accounts", "categories", "transactions")
        private val OPTIONAL_ARRAY_KEYS = listOf(
            "debts",
            "sms_patterns",
            "cheques",
            "recurring_transactions",
            "savings_goals",
            "savings_goal_entries",
            "group_expenses",
            "group_expense_shares",
            "account_sms_senders",
            "account_balance_snapshots"
        )
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val snapshot = storage.readSnapshot()
        gson.toJson(snapshot)
    }

    suspend fun restoreBackupDetailed(jsonString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val validation = validateBackupDetailed(jsonString)
        if (validation is BackupValidationResult.Invalid) {
            return@withContext BackupRestoreResult.Failure(validation.reason)
        }

        val backupData = (validation as BackupValidationResult.Valid).backupData
        try {
            storage.replaceAllDataAtomically(backupData)
            BackupRestoreResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupRestoreResult.Failure(
                "خطا در بازیابی اطلاعات؛ داده‌های فعلی بدون تغییر باقی ماندند: ${e.message ?: "خطای ناشناخته"}"
            )
        }
    }

    suspend fun restoreBackupJson(jsonString: String): Boolean {
        return restoreBackupDetailed(jsonString) is BackupRestoreResult.Success
    }

    fun validateBackupDetailed(jsonString: String): BackupValidationResult {
        if (jsonString.isBlank()) {
            return BackupValidationResult.Invalid("فایل پشتیبان خالی است.")
        }

        val rootObject: JsonObject = try {
            val parsedElement = JsonParser.parseString(jsonString)
            if (!parsedElement.isJsonObject) {
                return BackupValidationResult.Invalid("ساختار فایل پشتیبان نامعتبر است (JSON باید یک شیء معتبر باشد).")
            }
            parsedElement.asJsonObject
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("فایل پشتیبان خراب است یا فرمت JSON معتبر ندارد.")
        }

        // 1. Validate backup format version & database schema version before deserializing entities
        val versionError = validateVersions(rootObject)
        if (versionError != null) {
            return BackupValidationResult.Invalid(versionError)
        }

        // 2. Validate mandatory and optional table shapes at JSON level
        for (key in MANDATORY_ARRAY_KEYS) {
            val elem = rootObject.get(key)
            if (elem == null || elem.isJsonNull || !elem.isJsonArray) {
                return BackupValidationResult.Invalid("ساختار فایل پشتیبان ناقص است (بخش «$key» یافت نشد یا نامعتبر است).")
            }
        }
        for (key in OPTIONAL_ARRAY_KEYS) {
            val elem = rootObject.get(key)
            if (elem != null && !elem.isJsonNull && !elem.isJsonArray) {
                return BackupValidationResult.Invalid("ساختار فایل پشتیبان در بخش «$key» نامعتبر است.")
            }
        }

        // 3. Deserialize model
        val backupData = try {
            gson.fromJson(rootObject, BackupData::class.java)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid("خطا در خواندن داده‌های فایل پشتیبان: ساختار رکوردها نامعتبر است.")
        } ?: return BackupValidationResult.Invalid("فایل پشتیبان قابل خواندن نیست.")

        // 4. Deep structural, field, enum, and foreign-key integrity validation
        val integrityError = validateIntegrity(backupData)
        if (integrityError != null) {
            return BackupValidationResult.Invalid(integrityError)
        }

        return BackupValidationResult.Valid(backupData)
    }

    fun validateBackupJson(jsonString: String): Boolean {
        return validateBackupDetailed(jsonString) is BackupValidationResult.Valid
    }

    private fun validateVersions(root: JsonObject): String? {
        val formatVersionElem = root.get("backupFormatVersion")
        if (formatVersionElem != null && !formatVersionElem.isJsonNull) {
            val formatVersion = parseStrictInt(formatVersionElem)
                ?: return "نسخه فرمت فایل پشتیبان نامعتبر است."
            if (formatVersion < 1) {
                return "نسخه فرمت فایل پشتیبان نامعتبر است."
            }
            if (formatVersion > CURRENT_BACKUP_FORMAT_VERSION) {
                return "نسخه فرمت فایل پشتیبان ($formatVersion) از نسخه پشتیبانی‌شده برنامه ($CURRENT_BACKUP_FORMAT_VERSION) جدیدتر است. لطفاً برنامه را بروزرسانی کنید."
            }
        }

        val schemaElem = root.get("databaseSchemaVersion") ?: root.get("schemaVersion")
        if (schemaElem != null && !schemaElem.isJsonNull) {
            val fileDbVersion = parseStrictInt(schemaElem)
                ?: return "نسخه پایگاه داده فایل پشتیبان نامعتبر است."
            if (fileDbVersion < 1) {
                return "نسخه پایگاه داده فایل پشتیبان ($fileDbVersion) نامعتبر است."
            }
            if (fileDbVersion > CURRENT_DATABASE_SCHEMA_VERSION) {
                return "نسخه پایگاه داده فایل پشتیبان ($fileDbVersion) از نسخه برنامه شما ($CURRENT_DATABASE_SCHEMA_VERSION) جدیدتر است. لطفاً برنامه را بروزرسانی کنید."
            }
            return null
        }

        // Backward compatibility: accept legacy backups that only carry `version`
        val legacyVersionElem = root.get("version")
        if (legacyVersionElem != null && !legacyVersionElem.isJsonNull) {
            val legacyVersion = parseStrictInt(legacyVersionElem)
                ?: return "نسخه فایل پشتیبان نامعتبر است."
            if (legacyVersion < 1 || legacyVersion > CURRENT_DATABASE_SCHEMA_VERSION) {
                return "نسخه فایل پشتیبان ($legacyVersion) با این نسخه از برنامه سازگار نیست."
            }
            return null
        }

        return "اطلاعات نسخه فایل پشتیبان یافت نشد؛ فایل معتبر جیب‌بان نیست."
    }

    private fun validateIntegrity(backupData: BackupData): String? {
        if (isNullRef(backupData.accounts) || backupData.accounts.isEmpty()) {
            return "فایل پشتیبان فاقد فهرست حساب‌ها است."
        }
        if (isNullRef(backupData.categories) || backupData.categories.isEmpty()) {
            return "فایل پشتیبان فاقد فهرست دسته‌بندی‌ها است."
        }
        if (isNullRef(backupData.transactions)) {
            return "فایل پشتیبان فاقد فهرست تراکنش‌ها است."
        }

        // Accounts
        val accountIds = mutableSetOf<Long>()
        for (acc in backupData.accounts) {
            if (isNullRef(acc) || isNullOrBlankRef(acc.name) || isNullRef(acc.type) || isNullRef(acc.icon)) {
                return "اطلاعات یکی از حساب‌ها در فایل پشتیبان ناقص یا نامعتبر است."
            }
            if (acc.id < 0L) {
                return "شناسه یکی از حساب‌ها در فایل پشتیبان نامعتبر است."
            }
            if (acc.id > 0L && !accountIds.add(acc.id)) {
                return "شناسه تکراری در فهرست حساب‌های فایل پشتیبان یافت شد."
            }
        }

        // Categories
        val categoryIds = mutableSetOf<Long>()
        for (cat in backupData.categories) {
            if (isNullRef(cat) || isNullOrBlankRef(cat.name) || isNullRef(cat.type) || isNullRef(cat.icon)) {
                return "اطلاعات یکی از دسته‌بندی‌ها در فایل پشتیبان ناقص یا نامعتبر است."
            }
            if (cat.id < 0L) {
                return "شناسه یکی از دسته‌بندی‌ها در فایل پشتیبان نامعتبر است."
            }
            if (cat.id > 0L && !categoryIds.add(cat.id)) {
                return "شناسه تکراری در فهرست دسته‌بندی‌های فایل پشتیبان یافت شد."
            }
        }

        // Transactions + FK checks to accounts & categories
        val transactionIds = mutableSetOf<Long>()
        for (tx in backupData.transactions) {
            if (isNullRef(tx) || isNullRef(tx.type) || tx.amount < 0L || tx.date <= 0L || tx.id < 0L) {
                return "اطلاعات یکی از تراکنش‌ها در فایل پشتیبان نامعتبر است."
            }
            if (tx.id > 0L && !transactionIds.add(tx.id)) {
                return "شناسه تکراری در فهرست تراکنش‌های فایل پشتیبان یافت شد."
            }
            if (tx.accountId != null && tx.accountId !in accountIds) {
                return "ارجاع نامعتبر به حساب در یکی از تراکنش‌ها یافت شد."
            }
            if (tx.toAccountId != null && tx.toAccountId !in accountIds) {
                return "ارجاع نامعتبر به حساب مقصد در یکی از تراکنش‌ها یافت شد."
            }
            if (tx.categoryId != null && tx.categoryId !in categoryIds) {
                return "ارجاع نامعتبر به دسته‌بندی در یکی از تراکنش‌ها یافت شد."
            }
        }

        // Account SMS Senders + FK check to accounts + uniqueness of (accountId, address)
        backupData.account_sms_senders?.let { senders ->
            val seenPairs = mutableSetOf<Pair<Long, String>>()
            for (sender in senders) {
                if (isNullRef(sender) || isNullOrBlankRef(sender.address) || sender.id < 0L) {
                    return "اطلاعات فرستنده پیامک حساب در فایل پشتیبان نامعتبر است."
                }
                if (sender.accountId !in accountIds) {
                    return "ارجاع نامعتبر به حساب در جدول فرستنده‌های پیامک یافت شد."
                }
                if (!seenPairs.add(sender.accountId to sender.address)) {
                    return "رکورد تکراری در جدول فرستنده‌های پیامک حساب یافت شد."
                }
            }
        }

        // Account Balance Snapshots + FK check to accounts
        backupData.account_balance_snapshots?.let { snapshots ->
            for (snap in snapshots) {
                if (isNullRef(snap) || isNullRef(snap.smsHash) || snap.smsDate <= 0L || snap.id < 0L) {
                    return "اطلاعات اسنپ‌شات مانده حساب در فایل پشتیبان نامعتبر است."
                }
                if (snap.accountId !in accountIds) {
                    return "ارجاع نامعتبر به حساب در اسنپ‌شات مانده حساب یافت شد."
                }
            }
        }

        // Debts
        backupData.debts?.let { debts ->
            for (debt in debts) {
                if (isNullRef(debt) || isNullOrBlankRef(debt.personName) || isNullRef(debt.type) || debt.amount < 0L || debt.id < 0L) {
                    return "اطلاعات یکی از رکوردهای بدهی/طلب در فایل پشتیبان نامعتبر است."
                }
            }
        }

        // SMS Patterns
        backupData.sms_patterns?.let { patterns ->
            for (pattern in patterns) {
                if (isNullRef(pattern) || isNullRef(pattern.accountIdentifier) || isNullRef(pattern.sampleSms) || isNullRef(pattern.extractedFields) || pattern.id < 0L) {
                    return "اطلاعات یکی از الگوهای پیامک در فایل پشتیبان نامعتبر است."
                }
            }
        }

        // Cheques
        backupData.cheques?.let { cheques ->
            for (cheque in cheques) {
                if (isNullRef(cheque) || isNullRef(cheque.type) || isNullRef(cheque.status) ||
                    isNullRef(cheque.sayadNumber) || isNullRef(cheque.partyName) || isNullRef(cheque.bankName) ||
                    cheque.amount < 0L || cheque.dueDate <= 0L || cheque.id < 0L
                ) {
                    return "اطلاعات یکی از چک‌ها در فایل پشتیبان نامعتبر است."
                }
            }
        }

        // Recurring Transactions + FK checks to accounts & categories
        backupData.recurring_transactions?.let { recurringList ->
            for (rec in recurringList) {
                if (isNullRef(rec) || isNullOrBlankRef(rec.title) || isNullRef(rec.type) || isNullRef(rec.period) || rec.amount < 0L || rec.id < 0L) {
                    return "اطلاعات یکی از تراکنش‌های دوره‌ای در فایل پشتیبان نامعتبر است."
                }
                if (rec.accountId !in accountIds) {
                    return "ارجاع نامعتبر به حساب در یکی از تراکنش‌های دوره‌ای یافت شد."
                }
                if (rec.toAccountId != null && rec.toAccountId !in accountIds) {
                    return "ارجاع نامعتبر به حساب مقصد در یکی از تراکنش‌های دوره‌ای یافت شد."
                }
                if (rec.categoryId != null && rec.categoryId !in categoryIds) {
                    return "ارجاع نامعتبر به دسته‌بندی در یکی از تراکنش‌های دوره‌ای یافت شد."
                }
            }
        }

        // Savings Goals & Entries + FK checks to goals & transactions
        val savingsGoalIds = mutableSetOf<Long>()
        backupData.savings_goals?.let { goals ->
            for (goal in goals) {
                if (isNullRef(goal) || isNullOrBlankRef(goal.title) || isNullRef(goal.icon) || goal.targetAmount < 0L || goal.id < 0L) {
                    return "اطلاعات یکی از اهداف پس‌انداز در فایل پشتیبان نامعتبر است."
                }
                if (goal.id > 0L && !savingsGoalIds.add(goal.id)) {
                    return "شناسه تکراری در اهداف پس‌انداز فایل پشتیبان یافت شد."
                }
            }
        }
        backupData.savings_goal_entries?.let { entries ->
            for (entry in entries) {
                if (isNullRef(entry) || isNullRef(entry.type) || entry.amount < 0L || entry.id < 0L) {
                    return "اطلاعات یکی از واریز/برداشت‌های پس‌انداز در فایل پشتیبان نامعتبر است."
                }
                if (entry.goalId !in savingsGoalIds) {
                    return "ارجاع نامعتبر به هدف پس‌انداز در فایل پشتیبان یافت شد."
                }
                if (entry.transactionId != null && entry.transactionId !in transactionIds) {
                    return "ارجاع نامعتبر به تراکنش در رکورد پس‌انداز یافت شد."
                }
            }
        }

        // Group Expenses & Shares + FK checks to transactions & group_expenses
        val groupExpenseIds = mutableSetOf<Long>()
        backupData.group_expenses?.let { groupList ->
            for (ge in groupList) {
                if (isNullRef(ge) || isNullRef(ge.shareMode) || ge.id < 0L) {
                    return "اطلاعات یکی از هزینه‌های مشترک در فایل پشتیبان نامعتبر است."
                }
                if (ge.transactionId !in transactionIds) {
                    return "ارجاع نامعتبر به تراکنش اصلی در هزینه مشترک یافت شد."
                }
                if (ge.id > 0L && !groupExpenseIds.add(ge.id)) {
                    return "شناسه تکراری در جدول هزینه‌های مشترک فایل پشتیبان یافت شد."
                }
            }
        }
        backupData.group_expense_shares?.let { shareList ->
            for (share in shareList) {
                if (isNullRef(share) || isNullOrBlankRef(share.personName) || share.amount < 0L || share.id < 0L) {
                    return "اطلاعات یکی از سهم‌های هزینه مشترک در فایل پشتیبان نامعتبر است."
                }
                if (share.groupExpenseId !in groupExpenseIds) {
                    return "ارجاع نامعتبر به هزینه مشترک در جدول سهم‌ها یافت شد."
                }
                if (share.settledTransactionId != null && share.settledTransactionId !in transactionIds) {
                    return "ارجاع نامعتبر به تراکنش تسویه در جدول سهم‌ها یافت شد."
                }
            }
        }

        return null
    }

    private fun parseStrictInt(element: JsonElement): Int? {
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        if (!primitive.isNumber) return null
        val doubleVal = try {
            primitive.asDouble
        } catch (_: Exception) {
            return null
        }
        if (doubleVal.isNaN() || doubleVal.isInfinite() || doubleVal % 1.0 != 0.0) return null
        if (doubleVal < Int.MIN_VALUE || doubleVal > Int.MAX_VALUE) return null
        return doubleVal.toInt()
    }

    private fun isNullRef(value: Any?): Boolean = value == null

    private fun isNullOrBlankRef(value: Any?): Boolean {
        return value == null || (value as? String)?.isBlank() == true
    }
}
