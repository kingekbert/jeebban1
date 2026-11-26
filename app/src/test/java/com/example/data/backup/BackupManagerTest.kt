package com.example.data.backup

import com.example.data.local.FinTrackDatabase
import com.example.data.local.entity.AccountBalanceSnapshotEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountSmsSenderEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.ChequeEntity
import com.example.data.local.entity.ChequeStatus
import com.example.data.local.entity.ChequeType
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtType
import com.example.data.local.entity.GroupExpenseEntity
import com.example.data.local.entity.GroupExpenseShareEntity
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.RecurrencePeriod
import com.example.data.local.entity.RecurringTransactionEntity
import com.example.data.local.entity.SavingsEntryType
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.SavingsGoalEntryEntity
import com.example.data.local.entity.SmsPatternEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    /**
     * In-memory transactional storage adapter that tracks every write attempt and enforces
     * atomic replacement semantics so tests can verify schema alignment, full relational
     * preservation (including account_sms_senders), rejection before touching DB, and rollback.
     */
    private class FakeBackupStorage(
        initialState: BackupData,
        private val failOnReplace: Boolean = false
    ) : BackupStorageAdapter {
        var currentState: BackupData = initialState
            private set
        var replaceAttemptCount: Int = 0
            private set

        override suspend fun readSnapshot(): BackupData = currentState.copy(
            backupFormatVersion = BackupManager.CURRENT_BACKUP_FORMAT_VERSION,
            databaseSchemaVersion = BackupManager.CURRENT_DATABASE_SCHEMA_VERSION,
            version = BackupManager.LEGACY_BACKUP_VERSION
        )

        override suspend fun replaceAllDataAtomically(backupData: BackupData) {
            replaceAttemptCount++
            val snapshotBefore = currentState
            try {
                if (failOnReplace) {
                    throw IllegalStateException("Simulated SQLite write failure mid-restore")
                }
                // Verify foreign key integrity on incoming snapshot just as SQLite does
                val accIds = backupData.accounts.map { it.id }.toSet()
                val catIds = backupData.categories.map { it.id }.toSet()
                val txIds = backupData.transactions.map { it.id }.toSet()
                val goalIds = backupData.savings_goals.orEmpty().map { it.id }.toSet()
                val geIds = backupData.group_expenses.orEmpty().map { it.id }.toSet()

                backupData.account_sms_senders.orEmpty().forEach {
                    require(it.accountId in accIds) { "FK violation: account_sms_senders.accountId=${it.accountId}" }
                }
                backupData.account_balance_snapshots.orEmpty().forEach {
                    require(it.accountId in accIds) { "FK violation: account_balance_snapshots.accountId=${it.accountId}" }
                }
                backupData.transactions.forEach {
                    if (it.accountId != null) require(it.accountId in accIds) { "FK violation: transactions.accountId" }
                    if (it.toAccountId != null) require(it.toAccountId in accIds) { "FK violation: transactions.toAccountId" }
                    if (it.categoryId != null) require(it.categoryId in catIds) { "FK violation: transactions.categoryId" }
                }
                backupData.recurring_transactions.orEmpty().forEach {
                    require(it.accountId in accIds) { "FK violation: recurring.accountId" }
                }
                backupData.savings_goal_entries.orEmpty().forEach {
                    require(it.goalId in goalIds) { "FK violation: savings_goal_entries.goalId" }
                    if (it.transactionId != null) require(it.transactionId in txIds) { "FK violation: savings_goal_entries.transactionId" }
                }
                backupData.group_expenses.orEmpty().forEach {
                    require(it.transactionId in txIds) { "FK violation: group_expenses.transactionId" }
                }
                backupData.group_expense_shares.orEmpty().forEach {
                    require(it.groupExpenseId in geIds) { "FK violation: group_expense_shares.groupExpenseId" }
                    if (it.settledTransactionId != null) require(it.settledTransactionId in txIds) { "FK violation: group_expense_shares.settledTransactionId" }
                }

                currentState = backupData
            } catch (t: Throwable) {
                // Roll back to pre-transaction snapshot
                currentState = snapshotBefore
                throw t
            }
        }
    }

    private fun sampleInitialState(): BackupData {
        val account1 = AccountEntity(
            id = 1L,
            name = "بانک ملت",
            type = AccountType.BANK,
            initialBalance = 10_000_000L,
            color = 0xFF1565C0.toInt(),
            icon = "bank",
            cardNumber = "6104337712345678"
        )
        val account2 = AccountEntity(
            id = 2L,
            name = "بانک سامان",
            type = AccountType.BANK,
            initialBalance = 5_000_000L,
            color = 0xFF00897B.toInt(),
            icon = "bank",
            cardNumber = "6219861098764321"
        )
        val catExpense = CategoryEntity(
            id = 10L,
            name = "خوراک",
            type = CategoryType.EXPENSE,
            color = 0xFFE53935.toInt(),
            icon = "food"
        )
        val catIncome = CategoryEntity(
            id = 11L,
            name = "حقوق",
            type = CategoryType.INCOME,
            color = 0xFF43A047.toInt(),
            icon = "income"
        )
        val txExpense = TransactionEntity(
            id = 100L,
            type = TransactionType.EXPENSE,
            amount = 900_000L,
            accountId = 1L,
            categoryId = 10L,
            date = 1_710_000_000_000L,
            note = "شام گروهی",
            isBookmarked = true
        )
        val txSettle = TransactionEntity(
            id = 101L,
            type = TransactionType.INCOME,
            amount = 300_000L,
            accountId = 2L,
            categoryId = 11L,
            date = 1_710_050_000_000L,
            note = "تسویه دنگ علی"
        )
        val sender1 = AccountSmsSenderEntity(id = 1L, accountId = 1L, address = "BankMellat")
        val sender2 = AccountSmsSenderEntity(id = 2L, accountId = 2L, address = "+9830001234")
        val snapshot1 = AccountBalanceSnapshotEntity(
            id = 1L,
            accountId = 1L,
            smsDate = 1_710_000_000_000L,
            balance = 9_100_000L,
            smsHash = "hash_snap_1"
        )
        val debt = DebtEntity(
            id = 1L,
            personName = "سارا",
            type = DebtType.OWED_TO_ME,
            amount = 500_000L
        )
        val pattern = SmsPatternEntity(
            id = 1L,
            accountIdentifier = "5678",
            sampleSms = "برداشت 100000 ریال",
            extractedFields = "[]",
            bankName = "ملت"
        )
        val cheque = ChequeEntity(
            id = 1L,
            sayadNumber = "1234567890123456",
            type = ChequeType.PAYABLE,
            amount = 2_000_000L,
            dueDate = 1_720_000_000_000L,
            partyName = "فروشگاه",
            bankName = "ملت",
            accountId = 1L,
            status = ChequeStatus.PENDING
        )
        val recurring = RecurringTransactionEntity(
            id = 1L,
            title = "اجاره",
            type = TransactionType.EXPENSE,
            amount = 4_000_000L,
            accountId = 1L,
            categoryId = 10L,
            period = RecurrencePeriod.MONTHLY,
            startDate = 1_700_000_000_000L,
            nextDueDate = 1_712_000_000_000L
        )
        val goal = SavingsGoalEntity(
            id = 1L,
            title = "سفر",
            targetAmount = 20_000_000L
        )
        val goalEntry = SavingsGoalEntryEntity(
            id = 1L,
            goalId = 1L,
            transactionId = 100L,
            amount = 900_000L,
            type = SavingsEntryType.DEPOSIT,
            date = 1_710_000_000_000L
        )
        val groupExpense = GroupExpenseEntity(
            id = 1L,
            transactionId = 100L,
            shareMode = GroupExpenseShareMode.EQUAL
        )
        val share1 = GroupExpenseShareEntity(
            id = 1L,
            groupExpenseId = 1L,
            personName = "علی",
            amount = 300_000L,
            isSettled = true,
            settledTransactionId = 101L
        )

        return BackupData(
            backupFormatVersion = BackupManager.CURRENT_BACKUP_FORMAT_VERSION,
            databaseSchemaVersion = FinTrackDatabase.DATABASE_VERSION,
            version = BackupManager.LEGACY_BACKUP_VERSION,
            accounts = listOf(account1, account2),
            categories = listOf(catExpense, catIncome),
            transactions = listOf(txExpense, txSettle),
            debts = listOf(debt),
            sms_patterns = listOf(pattern),
            cheques = listOf(cheque),
            recurring_transactions = listOf(recurring),
            savings_goals = listOf(goal),
            savings_goal_entries = listOf(goalEntry),
            group_expenses = listOf(groupExpense),
            group_expense_shares = listOf(share1),
            account_sms_senders = listOf(sender1, sender2),
            account_balance_snapshots = listOf(snapshot1)
        )
    }

    private fun minimalExistingState(): BackupData {
        return BackupData(
            backupFormatVersion = BackupManager.CURRENT_BACKUP_FORMAT_VERSION,
            databaseSchemaVersion = FinTrackDatabase.DATABASE_VERSION,
            accounts = listOf(
                AccountEntity(
                    id = 99L,
                    name = "حساب سالم فعلی",
                    type = AccountType.CASH,
                    initialBalance = 1_000_000L,
                    color = 0,
                    icon = "cash"
                )
            ),
            categories = listOf(
                CategoryEntity(
                    id = 99L,
                    name = "دسته فعلی",
                    type = CategoryType.EXPENSE,
                    color = 0,
                    icon = "wallet"
                )
            ),
            transactions = emptyList(),
            account_sms_senders = listOf(
                AccountSmsSenderEntity(id = 99L, accountId = 99L, address = "ExistingBank")
            )
        )
    }

    @Test
    fun `backup schemaVersion matches Room DATABASE_VERSION and round-trip preserves all tables including account_sms_senders`() = runTest {
        assertEquals(FinTrackDatabase.DATABASE_VERSION, BackupManager.CURRENT_DATABASE_SCHEMA_VERSION)
        assertEquals(FinTrackDatabase.DATABASE_VERSION, BackupData(accounts = emptyList(), categories = emptyList(), transactions = emptyList()).databaseSchemaVersion)

        val sourceData = sampleInitialState()
        val sourceManager = BackupManager(FakeBackupStorage(sourceData))

        val json = sourceManager.createBackupJson()
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(FinTrackDatabase.DATABASE_VERSION, root.get("databaseSchemaVersion").asInt)
        assertEquals(BackupManager.CURRENT_BACKUP_FORMAT_VERSION, root.get("backupFormatVersion").asInt)
        assertTrue(root.has("account_sms_senders"))
        assertEquals(2, root.getAsJsonArray("account_sms_senders").size())

        // Restore into another storage instance and verify every relation & table is preserved
        val targetStorage = FakeBackupStorage(minimalExistingState())
        val targetManager = BackupManager(targetStorage)

        val validation = targetManager.validateBackupDetailed(json)
        assertTrue(validation is BackupValidationResult.Valid)

        val restoreResult = targetManager.restoreBackupDetailed(json)
        assertEquals(BackupRestoreResult.Success, restoreResult)
        assertEquals(1, targetStorage.replaceAttemptCount)

        val restored = targetStorage.currentState
        assertEquals(2, restored.accounts.size)
        assertEquals(2, restored.categories.size)
        assertEquals(2, restored.transactions.size)
        assertTrue(restored.transactions.first().isBookmarked)
        assertEquals(2, restored.account_sms_senders?.size)
        assertEquals("BankMellat", restored.account_sms_senders?.get(0)?.address)
        assertEquals(1L, restored.account_sms_senders?.get(0)?.accountId)
        assertEquals("+9830001234", restored.account_sms_senders?.get(1)?.address)
        assertEquals(2L, restored.account_sms_senders?.get(1)?.accountId)
        assertEquals(1, restored.account_balance_snapshots?.size)
        assertEquals(1, restored.group_expenses?.size)
        assertEquals(1, restored.group_expense_shares?.size)
        assertEquals(1, restored.savings_goals?.size)
        assertEquals(1, restored.savings_goal_entries?.size)
        assertEquals(1, restored.recurring_transactions?.size)
        assertEquals(1, restored.cheques?.size)
        assertEquals(1, restored.debts?.size)
        assertEquals(1, restored.sms_patterns?.size)
    }

    @Test
    fun `valid older schema backup without account_sms_senders remains backward compatible`() = runTest {
        val legacyJson = """
            {
              "backupFormatVersion": 1,
              "databaseSchemaVersion": 8,
              "version": 4,
              "accounts": [
                {"id": 1, "name": "بانک ملی", "type": "BANK", "initialBalance": 500000, "color": 0, "icon": "bank"}
              ],
              "categories": [
                {"id": 1, "name": "خرید", "type": "EXPENSE", "color": 0, "icon": "shop"}
              ],
              "transactions": [
                {"id": 1, "type": "EXPENSE", "amount": 120000, "accountId": 1, "categoryId": 1, "date": 1700000000000}
              ]
            }
        """.trimIndent()

        val storage = FakeBackupStorage(minimalExistingState())
        val manager = BackupManager(storage)

        assertTrue(manager.validateBackupJson(legacyJson))
        assertEquals(BackupRestoreResult.Success, manager.restoreBackupDetailed(legacyJson))
        assertEquals("بانک ملی", storage.currentState.accounts.single().name)
        assertEquals(1, storage.currentState.transactions.size)
    }

    @Test
    fun `backup from newer schema version is rejected without touching existing database`() = runTest {
        val existing = minimalExistingState()
        val storage = FakeBackupStorage(existing)
        val manager = BackupManager(storage)

        val futureVersion = FinTrackDatabase.DATABASE_VERSION + 2
        val futureBackupJson = """
            {
              "backupFormatVersion": 1,
              "databaseSchemaVersion": $futureVersion,
              "accounts": [
                {"id": 1, "name": "حساب جدید", "type": "BANK", "initialBalance": 0, "color": 0, "icon": "bank"}
              ],
              "categories": [
                {"id": 1, "name": "دسته جدید", "type": "EXPENSE", "color": 0, "icon": "shop"}
              ],
              "transactions": []
            }
        """.trimIndent()

        val validation = manager.validateBackupDetailed(futureBackupJson)
        assertTrue(validation is BackupValidationResult.Invalid)
        assertTrue((validation as BackupValidationResult.Invalid).reason.contains(futureVersion.toString()))

        val restoreResult = manager.restoreBackupDetailed(futureBackupJson)
        assertTrue(restoreResult is BackupRestoreResult.Failure)
        assertEquals(0, storage.replaceAttemptCount)
        assertEquals(existing, storage.currentState)
    }

    @Test
    fun `corrupted malformed or structurally incomplete backups are rejected without altering existing data`() = runTest {
        val existing = minimalExistingState()
        val storage = FakeBackupStorage(existing)
        val manager = BackupManager(storage)

        val invalidPayloads = listOf(
            "",
            "   ",
            "{not-valid-json",
            "[]",
            "{\"accounts\": [], \"categories\": [], \"transactions\": []}", // missing version + empty accounts
            """
            {
              "backupFormatVersion": 1,
              "databaseSchemaVersion": ${FinTrackDatabase.DATABASE_VERSION},
              "accounts": [{"id": 1, "name": "حساب", "type": "UNKNOWN_ENUM", "initialBalance": 0, "color": 0, "icon": "bank"}],
              "categories": [{"id": 1, "name": "دسته", "type": "EXPENSE", "color": 0, "icon": "shop"}],
              "transactions": []
            }
            """.trimIndent(),
            """
            {
              "backupFormatVersion": 1,
              "databaseSchemaVersion": ${FinTrackDatabase.DATABASE_VERSION},
              "accounts": [{"id": 1, "name": "حساب", "type": "BANK", "initialBalance": 0, "color": 0, "icon": "bank"}],
              "categories": [{"id": 1, "name": "دسته", "type": "EXPENSE", "color": 0, "icon": "shop"}],
              "transactions": [{"id": 1, "type": "EXPENSE", "amount": -500, "accountId": 1, "categoryId": 1, "date": 1700000000000}]
            }
            """.trimIndent()
        )

        for (payload in invalidPayloads) {
            assertFalse("Expected invalid payload to fail validation: $payload", manager.validateBackupJson(payload))
            val res = manager.restoreBackupDetailed(payload)
            assertTrue("Expected invalid payload to fail restore: $payload", res is BackupRestoreResult.Failure)
        }

        // Zero DB write attempts and existing data completely intact
        assertEquals(0, storage.replaceAttemptCount)
        assertEquals(existing, storage.currentState)
    }

    @Test
    fun `broken foreign key in account_sms_senders or group_expense_shares is rejected before wiping database`() = runTest {
        val existing = minimalExistingState()
        val storage = FakeBackupStorage(existing)
        val manager = BackupManager(storage)

        val orphanSenderBackup = """
            {
              "backupFormatVersion": 1,
              "databaseSchemaVersion": ${FinTrackDatabase.DATABASE_VERSION},
              "accounts": [{"id": 1, "name": "بانک ملت", "type": "BANK", "initialBalance": 0, "color": 0, "icon": "bank"}],
              "categories": [{"id": 1, "name": "خوراک", "type": "EXPENSE", "color": 0, "icon": "food"}],
              "transactions": [],
              "account_sms_senders": [
                {"id": 1, "accountId": 777, "address": "BankGhost"}
              ]
            }
        """.trimIndent()

        val senderRes = manager.restoreBackupDetailed(orphanSenderBackup)
        assertTrue(senderRes is BackupRestoreResult.Failure)
        assertEquals(0, storage.replaceAttemptCount)
        assertEquals(existing, storage.currentState)

        val orphanShareBackup = """
            {
              "backupFormatVersion": 1,
              "databaseSchemaVersion": ${FinTrackDatabase.DATABASE_VERSION},
              "accounts": [{"id": 1, "name": "بانک ملت", "type": "BANK", "initialBalance": 0, "color": 0, "icon": "bank"}],
              "categories": [{"id": 1, "name": "خوراک", "type": "EXPENSE", "color": 0, "icon": "food"}],
              "transactions": [{"id": 10, "type": "EXPENSE", "amount": 100000, "accountId": 1, "categoryId": 1, "date": 1710000000000}],
              "group_expenses": [],
              "group_expense_shares": [
                {"id": 1, "groupExpenseId": 999, "personName": "علی", "amount": 50000, "isSettled": false}
              ]
            }
        """.trimIndent()

        val shareRes = manager.restoreBackupDetailed(orphanShareBackup)
        assertTrue(shareRes is BackupRestoreResult.Failure)
        assertEquals(0, storage.replaceAttemptCount)
        assertEquals(existing, storage.currentState)
    }

    @Test
    fun `mid-restore database failure rolls back cleanly leaving existing data intact`() = runTest {
        val existing = minimalExistingState()
        val failingStorage = FakeBackupStorage(existing, failOnReplace = true)
        val manager = BackupManager(failingStorage)

        val validJson = BackupManager(FakeBackupStorage(sampleInitialState())).createBackupJson()
        val res = manager.restoreBackupDetailed(validJson)

        assertTrue(res is BackupRestoreResult.Failure)
        assertEquals(1, failingStorage.replaceAttemptCount)
        assertEquals(existing, failingStorage.currentState)
    }
}
