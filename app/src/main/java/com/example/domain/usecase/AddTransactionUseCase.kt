package com.example.domain.usecase

import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtType
import com.example.data.local.entity.GroupExpenseShareMode
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.CategoryRepository
import com.example.data.repository.DebtRepository
import com.example.data.repository.GroupExpenseInsertResult
import com.example.data.repository.GroupExpenseRepository
import com.example.data.repository.TransactionRepository
import com.example.data.repository.TransferInsertResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull

class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository? = null,
    private val debtRepository: DebtRepository? = null,
    private val groupExpenseRepository: GroupExpenseRepository? = null
) {
    suspend fun isSmsAlreadyRegistered(smsHash: String?): Boolean {
        return transactionRepository.isSmsHashRegistered(smsHash)
    }

    suspend operator fun invoke(transaction: TransactionEntity): Result<Long> {
        if (transaction.amount <= 0) {
            return Result.failure(IllegalArgumentException("مبلغ باید بزرگتر از صفر باشد"))
        }
        if (transaction.accountId == null) {
            return Result.failure(IllegalArgumentException("حساب انتخاب نشده است"))
        }
        return try {
            val id = transactionRepository.insertTransaction(transaction)

            // Auto-sync Debt / Loan (بدهی و طلب)
            syncDebtLoanIfApplicable(transaction)

            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addTransferWithOptionalFee(
        transferTransaction: TransactionEntity,
        feeTransaction: TransactionEntity? = null
    ): Result<TransferInsertResult> {
        if (transferTransaction.type != TransactionType.TRANSFER) {
            return Result.failure(IllegalArgumentException("نوع تراکنش باید انتقال بین حساب‌ها باشد"))
        }
        if (transferTransaction.amount <= 0L) {
            return Result.failure(IllegalArgumentException("مبلغ انتقال باید بزرگتر از صفر باشد"))
        }
        if (transferTransaction.accountId == null) {
            return Result.failure(IllegalArgumentException("حساب مبدأ انتخاب نشده است"))
        }
        if (transferTransaction.toAccountId == null) {
            return Result.failure(IllegalArgumentException("حساب مقصد انتخاب نشده است"))
        }
        if (transferTransaction.accountId == transferTransaction.toAccountId) {
            return Result.failure(IllegalArgumentException("حساب مبدأ و مقصد نمی‌توانند یکسان باشند"))
        }

        if (feeTransaction != null) {
            if (feeTransaction.type != TransactionType.EXPENSE) {
                return Result.failure(IllegalArgumentException("نوع تراکنش کارمزد باید هزینه باشد"))
            }
            if (feeTransaction.amount <= 0L) {
                return Result.failure(IllegalArgumentException("مبلغ کارمزد باید بزرگتر از صفر باشد"))
            }
            if (feeTransaction.accountId == null) {
                return Result.failure(IllegalArgumentException("حساب مبدأ برای کارمزد انتخاب نشده است"))
            }
            if (feeTransaction.accountId != transferTransaction.accountId) {
                return Result.failure(IllegalArgumentException("حساب کارمزد باید با حساب مبدأ یکسان باشد"))
            }
        }

        return try {
            val result = transactionRepository.insertTransferWithFee(
                transferTransaction = transferTransaction,
                feeTransaction = feeTransaction
            )
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addGroupExpenseWithShares(
        expenseTransaction: TransactionEntity,
        shareMode: GroupExpenseShareMode,
        shares: List<Pair<String, Long>>,
        repositoryOverride: GroupExpenseRepository? = null
    ): Result<GroupExpenseInsertResult> {
        val targetRepo = repositoryOverride ?: groupExpenseRepository
            ?: return Result.failure(IllegalStateException("مخزن هزینه مشترک پیکربندی نشده است"))

        if (expenseTransaction.type != TransactionType.EXPENSE) {
            return Result.failure(IllegalArgumentException("نوع تراکنش هزینه مشترک باید هزینه باشد"))
        }
        if (expenseTransaction.amount <= 0L) {
            return Result.failure(IllegalArgumentException("مبلغ باید بزرگتر از صفر باشد"))
        }
        if (expenseTransaction.accountId == null) {
            return Result.failure(IllegalArgumentException("حساب انتخاب نشده است"))
        }
        if (shares.isEmpty()) {
            return Result.failure(IllegalArgumentException("لطفاً حداقل نام یک دوست را برای هزینه مشترک وارد کنید"))
        }
        if (shares.any { it.first.isBlank() }) {
            return Result.failure(IllegalArgumentException("نام تمام افراد در هزینه مشترک باید مشخص باشد"))
        }
        if (shares.any { it.second < 0L }) {
            return Result.failure(IllegalArgumentException("مبلغ سهم افراد نمی‌تواند منفی باشد"))
        }
        if (shares.sumOf { it.second } > expenseTransaction.amount) {
            return Result.failure(IllegalArgumentException("مجموع سهم دوستان نمی‌تواند بیشتر از مبلغ کل هزینه باشد"))
        }

        return try {
            val result = targetRepo.saveExpenseWithSharesAtomic(
                expenseTransaction = expenseTransaction,
                shareMode = shareMode,
                shares = shares
            )
            syncDebtLoanIfApplicable(expenseTransaction)
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncDebtLoanIfApplicable(transaction: TransactionEntity) {
        val catRepo = categoryRepository ?: return
        val dRepo = debtRepository ?: return
        val catId = transaction.categoryId ?: return

        try {
            val cat = catRepo.getCategoryById(catId).firstOrNull() ?: return
            val catName = cat.name.trim()

            val isLending = catName == "قرض دادن" || catName.contains("قرض دادن") ||
                    (catName.contains("قرض") && transaction.type == TransactionType.EXPENSE)
            val isBorrowing = catName == "قرض گرفتن" || catName.contains("قرض گرفتن") ||
                    (catName.contains("قرض") && transaction.type == TransactionType.INCOME)

            if (isLending) {
                // I lent money to someone -> They owe me (DebtType.OWED_TO_ME: طلب‌های من)
                val personName = extractPersonName(transaction.note) ?: "قرض داده شده"
                val debt = DebtEntity(
                    personName = personName,
                    type = DebtType.OWED_TO_ME,
                    amount = transaction.amount,
                    note = transaction.note?.ifBlank { "ثبت خودکار از تراکنش قرض دادن" } ?: "ثبت خودکار از تراکنش قرض دادن",
                    createdAt = transaction.date
                )
                dRepo.insertDebt(debt)
            } else if (isBorrowing) {
                // I borrowed money from someone -> I owe them (DebtType.I_OWE: بدهی‌های من)
                val personName = extractPersonName(transaction.note) ?: "قرض گرفته شده"
                val debt = DebtEntity(
                    personName = personName,
                    type = DebtType.I_OWE,
                    amount = transaction.amount,
                    note = transaction.note?.ifBlank { "ثبت خودکار از تراکنش قرض گرفتن" } ?: "ثبت خودکار از تراکنش قرض گرفتن",
                    createdAt = transaction.date
                )
                dRepo.insertDebt(debt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractPersonName(note: String?): String? {
        if (note.isNullOrBlank()) return null
        var cleaned = note.trim()
        val prefixes = listOf(
            "قرض به", "قرض دادن به", "قرض از", "قرض گرفتن از",
            "بابت قرض به", "بابت قرض از", "بابت قرض", "قرض"
        )
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
            }
        }
        return cleaned.ifBlank { null }
    }
}
