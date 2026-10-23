package com.example.domain.usecase

import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.DebtType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.data.repository.CategoryRepository
import com.example.data.repository.DebtRepository
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull

class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository? = null,
    private val debtRepository: DebtRepository? = null
) {
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
