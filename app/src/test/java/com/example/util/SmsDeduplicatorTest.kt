package com.example.util

import com.example.data.local.dao.RawCategorySummary
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AccountType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.local.relation.TransactionWithDetails
import com.example.data.repository.DuplicateSmsTransactionException
import com.example.data.repository.TransactionRepository
import com.example.data.repository.TransferInsertResult
import com.example.domain.usecase.AddTransactionUseCase
import com.example.ui.screens.scan_sms.ScannedSmsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsDeduplicatorTest {

    private class InMemoryTransactionDao : TransactionDao {
        val committed = mutableListOf<TransactionEntity>()
        private var activeStaging: MutableList<TransactionEntity>? = null
        private var idSeq = 0L

        suspend fun runInTransaction(block: suspend () -> TransferInsertResult): TransferInsertResult {
            val staging = committed.toMutableList()
            val prevSeq = idSeq
            activeStaging = staging
            return try {
                val res = block()
                committed.clear()
                committed.addAll(staging)
                res
            } catch (t: Throwable) {
                idSeq = prevSeq
                throw t
            } finally {
                activeStaging = null
            }
        }

        private fun currentList(): MutableList<TransactionEntity> = activeStaging ?: committed

        override suspend fun insert(transaction: TransactionEntity): Long {
            idSeq++
            val saved = transaction.copy(id = idSeq)
            currentList().add(saved)
            return idSeq
        }

        override suspend fun update(transaction: TransactionEntity) = Unit
        override suspend fun delete(transaction: TransactionEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun updateBookmark(id: Long, isBookmarked: Boolean) = Unit
        override fun getAll(): Flow<List<TransactionEntity>> = emptyFlow()
        override fun getAllWithDetails(): Flow<List<TransactionWithDetails>> = emptyFlow()
        override fun getByIdWithDetails(id: Long): Flow<TransactionWithDetails?> = emptyFlow()
        override fun getById(id: Long): Flow<TransactionEntity?> = emptyFlow()
        override fun getRecent(limit: Int): Flow<List<TransactionWithDetails>> = emptyFlow()
        override fun getByAccount(accountId: Long): Flow<List<TransactionWithDetails>> = emptyFlow()
        override fun getByDateRange(start: Long, end: Long): Flow<List<TransactionWithDetails>> = emptyFlow()
        override fun getTodayIncome(todayStart: Long, todayEnd: Long): Flow<Long?> = emptyFlow()
        override fun getTodayExpense(todayStart: Long, todayEnd: Long): Flow<Long?> = emptyFlow()
        override fun getCategorySummaryRaw(start: Long, end: Long, type: TransactionType): Flow<List<RawCategorySummary>> = emptyFlow()
        override fun getTotalIncome(start: Long, end: Long): Flow<Long?> = emptyFlow()
        override fun getTotalExpense(start: Long, end: Long): Flow<Long?> = emptyFlow()
        override suspend fun deleteAll() = Unit
        override fun getAllSmsHashes(): Flow<List<String>> = emptyFlow()
        override suspend fun getAllSmsHashesList(): List<String> =
            currentList().mapNotNull { it.smsHash }.filter { it.isNotBlank() }

        override suspend fun getAllWithDetailsList(): List<TransactionWithDetails> = emptyList()
        override suspend fun findMatchingTransactions(amount: Long, dayStart: Long, dayEnd: Long): List<TransactionWithDetails> = emptyList()
        override suspend fun countByRecurringAndDate(recurringId: Long, executedForDate: Long): Int = 0
        override suspend fun getTransactionsForAccountUpToDate(accountId: Long, date: Long): List<TransactionEntity> = emptyList()
    }

    private val accountMellat = AccountEntity(
        id = 1L,
        name = "بانک ملت",
        type = AccountType.BANK,
        initialBalance = 0L,
        color = 0xFF1565C0.toInt(),
        icon = "bank",
        cardNumber = "6104337712345678",
        shabaNumber = "IR120120000000001234567890"
    )
    private val accountSaman = AccountEntity(
        id = 2L,
        name = "بانک سامان",
        type = AccountType.BANK,
        initialBalance = 0L,
        color = 0xFF00897B.toInt(),
        icon = "bank",
        cardNumber = "6219861098764321",
        shabaNumber = "IR980560000000009876543210"
    )
    private val accountsMap = mapOf(
        accountMellat.id to accountMellat,
        accountSaman.id to accountSaman
    )

    private fun toDetails(tx: TransactionEntity): TransactionWithDetails {
        val acc = tx.accountId?.let { accountsMap[it] }
        val toAcc = tx.toAccountId?.let { accountsMap[it] }
        return TransactionWithDetails(
            transaction = tx,
            accountName = acc?.name,
            accountCardNumber = acc?.cardNumber,
            toAccountName = toAcc?.name,
            categoryName = "عمومی",
            categoryIcon = "wallet",
            categoryColor = 0
        )
    }

    private fun buildUseCase(dao: InMemoryTransactionDao): AddTransactionUseCase {
        val repo = TransactionRepository(
            transactionDao = dao,
            database = null,
            transactionRunner = { block -> dao.runInTransaction(block) }
        )
        return AddTransactionUseCase(transactionRepository = repo)
    }

    @Test
    fun `same amount deposit and withdrawal on same account and day are both preserved`() = runTest {
        val dao = InMemoryTransactionDao()
        val useCase = buildUseCase(dao)

        val dayBase = 1_710_000_000_000L
        val amount = 1_500_000L
        val sender = "BankMellat"

        val withdrawalText = "برداشت از حساب 5678 مبلغ 1,500,000 ریال مانده 10,000,000"
        val depositText = "واریز به حساب 5678 مبلغ 1,500,000 ریال مانده 11,500,000"

        val withdrawalHash = SmsHashUtil.calculateHash(
            sender = sender,
            amount = amount,
            date = dayBase,
            text = withdrawalText,
            transactionType = TransactionType.EXPENSE,
            accountIdentifier = "5678"
        )
        val depositHash = SmsHashUtil.calculateHash(
            sender = sender,
            amount = amount,
            date = dayBase + 60_000L,
            text = depositText,
            transactionType = TransactionType.INCOME,
            accountIdentifier = "5678"
        )

        // Even with identical text & timestamp, differing transactionType must produce distinct hashes
        val sameTextDepositHash = SmsHashUtil.calculateHash(
            sender = sender,
            amount = amount,
            date = dayBase,
            text = withdrawalText,
            transactionType = TransactionType.INCOME,
            accountIdentifier = "5678"
        )
        assertNotEquals(withdrawalHash, depositHash)
        assertNotEquals(withdrawalHash, sameTextDepositHash)

        // 1. Register the withdrawal
        val wResult = useCase(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = amount,
                accountId = accountMellat.id,
                categoryId = 1L,
                date = dayBase,
                note = "برداشت",
                smsHash = withdrawalHash
            )
        )
        assertTrue(wResult.isSuccess)

        // 2. Scan check: withdrawal is registered, but same-amount deposit on same account/day is NOT
        val existingDetails = dao.committed.map { toDetails(it) }
        val consumedManual = mutableSetOf<Long>()

        val withdrawalCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = dayBase,
            primaryHash = withdrawalHash,
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        val depositCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.INCOME,
            amount = amount,
            date = dayBase + 60_000L,
            primaryHash = depositHash,
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )

        assertTrue(SmsDeduplicator.isSmsRegistered(withdrawalCandidate, existingDetails, accountsMap, consumedManual))
        assertFalse(SmsDeduplicator.isSmsRegistered(depositCandidate, existingDetails, accountsMap, consumedManual))

        // 3. Register the deposit — must succeed and preserve both records
        val dResult = useCase(
            TransactionEntity(
                type = TransactionType.INCOME,
                amount = amount,
                accountId = accountMellat.id,
                categoryId = 2L,
                date = dayBase + 60_000L,
                note = "واریز",
                smsHash = depositHash
            )
        )
        assertTrue(dResult.isSuccess)
        assertEquals(2, dao.committed.size)
        assertEquals(TransactionType.EXPENSE, dao.committed[0].type)
        assertEquals(TransactionType.INCOME, dao.committed[1].type)
    }

    @Test
    fun `repeated identical SMS is caught as duplicate and only one transaction is persisted`() = runTest {
        val dao = InMemoryTransactionDao()
        val useCase = buildUseCase(dao)

        val smsDate = 1_710_100_000_000L
        val amount = 450_000L
        val smsBody = "خرید کارت 5678\nمبلغ 450,000 ریال\nمانده 9,550,000"
        val smsBodyExtraSpaces = "خرید کارت   5678  مبلغ 450,000 ریال   مانده 9,550,000"

        val hash1 = SmsHashUtil.calculateHash(
            sender = "BankMellat",
            amount = amount,
            date = smsDate,
            text = smsBody,
            transactionType = TransactionType.EXPENSE,
            accountIdentifier = "5678"
        )
        val hash2 = SmsHashUtil.calculateHash(
            sender = "bankmellat",
            amount = amount,
            date = smsDate,
            text = smsBodyExtraSpaces,
            transactionType = TransactionType.EXPENSE,
            accountIdentifier = "۵۶۷۸"
        )
        assertEquals(hash1, hash2)

        val firstInsert = useCase(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = amount,
                accountId = accountMellat.id,
                categoryId = 1L,
                date = smsDate,
                note = "خرید فروشگاه",
                smsHash = hash1
            )
        )
        assertTrue(firstInsert.isSuccess)
        assertEquals(1, dao.committed.size)

        // Scanner / deduplicator detects it as already registered
        val candidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = smsDate,
            primaryHash = hash2,
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        assertTrue(SmsDeduplicator.isSmsRegistered(candidate, dao.committed.map { toDetails(it) }, accountsMap))
        assertTrue(useCase.isSmsAlreadyRegistered(hash2))

        // Re-submitting the same SMS (e.g. from PreConfirm, Widget, or Import) must fail cleanly
        val duplicateInsert = useCase(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = amount,
                accountId = accountMellat.id,
                categoryId = 1L,
                date = smsDate,
                note = "خرید فروشگاه",
                smsHash = hash2
            )
        )
        assertTrue(duplicateInsert.isFailure)
        assertTrue(duplicateInsert.exceptionOrNull() is DuplicateSmsTransactionException)
        assertEquals(1, dao.committed.size)
    }

    @Test
    fun `two independent real purchases with same amount on same account and day are both preserved`() = runTest {
        val dao = InMemoryTransactionDao()
        val useCase = buildUseCase(dao)

        val amount = 300_000L
        val morningTime = 1_710_200_000_000L
        val afternoonTime = morningTime + (3 * 60 * 60 * 1000L) // 3 hours later, same Jalali day

        val purchase1Text = "خرید کارت 5678 مبلغ 300,000 ریال ساعت 09:15 مانده 8,700,000"
        val purchase2Text = "خرید کارت 5678 مبلغ 300,000 ریال ساعت 12:15 مانده 8,400,000"

        val hash1 = SmsHashUtil.calculateHash(
            sender = "BankMellat",
            amount = amount,
            date = morningTime,
            text = purchase1Text,
            transactionType = TransactionType.EXPENSE,
            accountIdentifier = "5678"
        )
        val hash2 = SmsHashUtil.calculateHash(
            sender = "BankMellat",
            amount = amount,
            date = afternoonTime,
            text = purchase2Text,
            transactionType = TransactionType.EXPENSE,
            accountIdentifier = "5678"
        )
        assertNotEquals(hash1, hash2)

        // Register first purchase
        assertTrue(
            useCase(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = accountMellat.id,
                    categoryId = 1L,
                    date = morningTime,
                    note = "قهوه صبح",
                    smsHash = hash1
                )
            ).isSuccess
        )

        val existingAfterFirst = dao.committed.map { toDetails(it) }
        val consumedManual = mutableSetOf<Long>()

        val candidate1 = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = morningTime,
            primaryHash = hash1,
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        val candidate2 = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = afternoonTime,
            primaryHash = hash2,
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )

        // First purchase is registered, second independent purchase MUST remain unregistered
        assertTrue(SmsDeduplicator.isSmsRegistered(candidate1, existingAfterFirst, accountsMap, consumedManual))
        assertFalse(SmsDeduplicator.isSmsRegistered(candidate2, existingAfterFirst, accountsMap, consumedManual))

        // Register second purchase — must succeed and NOT be merged/dropped
        assertTrue(
            useCase(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = accountMellat.id,
                    categoryId = 1L,
                    date = afternoonTime,
                    note = "ناهار",
                    smsHash = hash2
                )
            ).isSuccess
        )
        assertEquals(2, dao.committed.size)

        // Now both are registered on re-scan
        val existingAfterBoth = dao.committed.map { toDetails(it) }
        assertTrue(SmsDeduplicator.isSmsRegistered(candidate1, existingAfterBoth, accountsMap))
        assertTrue(SmsDeduplicator.isSmsRegistered(candidate2, existingAfterBoth, accountsMap))
    }

    @Test
    fun `two-sms transfer composite hash and fee do not conflict and prevent re-registration of either leg`() = runTest {
        val dao = InMemoryTransactionDao()
        val useCase = buildUseCase(dao)

        val transferAmount = 5_000_000L
        val feeAmount = 7_200L
        val ts = 1_710_300_000_000L

        val wHash = SmsHashUtil.calculateHash(
            sender = "BankMellat",
            amount = transferAmount + feeAmount,
            date = ts,
            text = "برداشت کارت 5678 مبلغ 5,007,200 ریال",
            transactionType = TransactionType.EXPENSE,
            accountIdentifier = "5678"
        )
        val dHash = SmsHashUtil.calculateHash(
            sender = "BankSaman",
            amount = transferAmount,
            date = ts + 10_000L,
            text = "واریز کارت 4321 مبلغ 5,000,000 ریال",
            transactionType = TransactionType.INCOME,
            accountIdentifier = "4321"
        )
        val compositeHash = "$wHash|$dHash"
        val feeHash = "${compositeHash}_fee"

        // Save atomic transfer + fee
        val transferResult = useCase.addTransferWithOptionalFee(
            transferTransaction = TransactionEntity(
                type = TransactionType.TRANSFER,
                amount = transferAmount,
                accountId = accountMellat.id,
                toAccountId = accountSaman.id,
                date = ts,
                note = "انتقال ملت به سامان",
                smsHash = compositeHash
            ),
            feeTransaction = TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = feeAmount,
                accountId = accountMellat.id,
                categoryId = 9L,
                date = ts,
                note = "کارمزد انتقال بین حساب‌ها",
                smsHash = feeHash
            )
        )
        assertTrue(transferResult.isSuccess)
        assertEquals(2, dao.committed.size)

        // Neither leg nor the composite transfer can be re-saved
        val retryTransfer = useCase.addTransferWithOptionalFee(
            transferTransaction = TransactionEntity(
                type = TransactionType.TRANSFER,
                amount = transferAmount,
                accountId = accountMellat.id,
                toAccountId = accountSaman.id,
                date = ts,
                smsHash = compositeHash
            ),
            feeTransaction = TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = feeAmount,
                accountId = accountMellat.id,
                categoryId = 9L,
                date = ts,
                smsHash = feeHash
            )
        )
        assertTrue(retryTransfer.isFailure)
        assertTrue(retryTransfer.exceptionOrNull() is DuplicateSmsTransactionException)

        val retryWithdrawalLeg = useCase(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = transferAmount + feeAmount,
                accountId = accountMellat.id,
                categoryId = 1L,
                date = ts,
                smsHash = wHash
            )
        )
        assertTrue(retryWithdrawalLeg.isFailure)
        assertTrue(retryWithdrawalLeg.exceptionOrNull() is DuplicateSmsTransactionException)

        val retryDepositLeg = useCase(
            TransactionEntity(
                type = TransactionType.INCOME,
                amount = transferAmount,
                accountId = accountSaman.id,
                categoryId = 2L,
                date = ts + 10_000L,
                smsHash = dHash
            )
        )
        assertTrue(retryDepositLeg.isFailure)
        assertTrue(retryDepositLeg.exceptionOrNull() is DuplicateSmsTransactionException)
        assertEquals(2, dao.committed.size)

        // Scanner & TransferDetector mark both legs and the detected transfer as registered
        val existingDetails = dao.committed.map { toDetails(it) }
        val wCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = transferAmount + feeAmount,
            date = ts,
            primaryHash = wHash,
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        val dCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.INCOME,
            amount = transferAmount,
            date = ts + 10_000L,
            primaryHash = dHash,
            matchedAccountId = accountSaman.id,
            accountIdent = "4321",
            bankName = "بانک سامان"
        )
        assertTrue(SmsDeduplicator.isSmsRegistered(wCandidate, existingDetails, accountsMap))
        assertTrue(SmsDeduplicator.isSmsRegistered(dCandidate, existingDetails, accountsMap))

        val scannedItems = listOf(
            ScannedSmsItem(
                id = wHash,
                parsedSms = ParsedSms(amount = transferAmount + feeAmount, transactionType = TransactionType.EXPENSE, date = ts, accountIdentifier = "5678", bankName = "بانک ملت"),
                rawText = "برداشت کارت 5678",
                smsHash = wHash,
                date = ts,
                isRegistered = false
            ),
            ScannedSmsItem(
                id = dHash,
                parsedSms = ParsedSms(amount = transferAmount, transactionType = TransactionType.INCOME, date = ts + 10_000L, accountIdentifier = "4321", bankName = "بانک سامان"),
                rawText = "واریز کارت 4321",
                smsHash = dHash,
                date = ts + 10_000L,
                isRegistered = false
            )
        )
        val detected = TransferDetector.findTransfers(scannedItems, listOf(accountMellat, accountSaman), existingDetails)
        assertEquals(1, detected.size)
        assertTrue(detected.first().isRegistered)
    }

    @Test
    fun `manual transactions allow same-amount duplicates and match SMS strictly one-to-one by type`() = runTest {
        val dao = InMemoryTransactionDao()
        val useCase = buildUseCase(dao)

        val amount = 250_000L
        val ts = 1_710_400_000_000L

        // Two manual transactions with same amount on same account/day must BOTH persist
        assertTrue(
            useCase(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = accountMellat.id,
                    categoryId = 1L,
                    date = ts,
                    note = "خرید دستی ۱",
                    smsHash = null
                )
            ).isSuccess
        )
        assertTrue(
            useCase(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = accountMellat.id,
                    categoryId = 1L,
                    date = ts + 5_000L,
                    note = "خرید دستی ۲",
                    smsHash = null
                )
            ).isSuccess
        )
        assertEquals(2, dao.committed.size)

        // Against a single manual EXPENSE transaction, 1-to-1 matching consumes it for only 1 EXPENSE SMS
        // and NEVER matches an INCOME SMS of the same amount on the same account/day
        val singleManualDetails = listOf(toDetails(dao.committed.first()))
        val consumedManual = mutableSetOf<Long>()

        val incomeCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.INCOME,
            amount = amount,
            date = ts + 10_000L,
            primaryHash = SmsHashUtil.calculateHash("BankMellat", amount, ts + 10_000L, "واریز 250000", TransactionType.INCOME, "5678"),
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        val expenseCandidate1 = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = ts + 20_000L,
            primaryHash = SmsHashUtil.calculateHash("BankMellat", amount, ts + 20_000L, "برداشت ۱ 250000", TransactionType.EXPENSE, "5678"),
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        val expenseCandidate2 = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = ts + 600_000L,
            primaryHash = SmsHashUtil.calculateHash("BankMellat", amount, ts + 600_000L, "برداشت ۲ 250000", TransactionType.EXPENSE, "5678"),
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )

        assertFalse(SmsDeduplicator.isSmsRegistered(incomeCandidate, singleManualDetails, accountsMap, consumedManual))
        assertTrue(SmsDeduplicator.isSmsRegistered(expenseCandidate1, singleManualDetails, accountsMap, consumedManual))
        assertFalse(SmsDeduplicator.isSmsRegistered(expenseCandidate2, singleManualDetails, accountsMap, consumedManual))
    }

    @Test
    fun `legacy v1 hash still matches same type but never collides with opposite transaction type`() {
        val amount = 800_000L
        val ts = 1_710_500_000_000L
        val body = "تراکنش 800,000 ریال حساب 5678"
        val legacyV1 = SmsHashUtil.calculateLegacyV1Hash("BankMellat", amount, ts, body)

        val storedExpense = toDetails(
            TransactionEntity(
                id = 50L,
                type = TransactionType.EXPENSE,
                amount = amount,
                accountId = accountMellat.id,
                categoryId = 1L,
                date = ts,
                smsHash = legacyV1
            )
        )

        val expenseCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.EXPENSE,
            amount = amount,
            date = ts,
            primaryHash = SmsHashUtil.calculateHash("BankMellat", amount, ts, body, TransactionType.EXPENSE, "5678"),
            legacyHashes = setOf(legacyV1),
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )
        val incomeCandidate = SmsDeduplicator.SmsCandidate(
            transactionType = TransactionType.INCOME,
            amount = amount,
            date = ts,
            primaryHash = SmsHashUtil.calculateHash("BankMellat", amount, ts, body, TransactionType.INCOME, "5678"),
            legacyHashes = setOf(legacyV1),
            matchedAccountId = accountMellat.id,
            accountIdent = "5678",
            bankName = "بانک ملت"
        )

        assertTrue(SmsDeduplicator.isSmsRegistered(expenseCandidate, listOf(storedExpense), accountsMap))
        assertFalse(SmsDeduplicator.isSmsRegistered(incomeCandidate, listOf(storedExpense), accountsMap))
    }
}
