package com.example.util

import android.content.Context
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionType

object CategoryGuesser {

    private const val PREFS_NAME = "jeebban_category_prefs"
    private const val KEY_LAST_EXPENSE_CAT = "last_expense_cat_id"
    private const val KEY_LAST_INCOME_CAT = "last_income_cat_id"

    fun saveLastUsedCategory(context: Context, type: TransactionType, categoryId: Long) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (type == TransactionType.INCOME) KEY_LAST_INCOME_CAT else KEY_LAST_EXPENSE_CAT
            prefs.edit().putLong(key, categoryId).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastUsedCategoryId(context: Context, type: TransactionType): Long? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (type == TransactionType.INCOME) KEY_LAST_INCOME_CAT else KEY_LAST_EXPENSE_CAT
            val id = prefs.getLong(key, -1L)
            if (id > 0) id else null
        } catch (e: Exception) {
            null
        }
    }

    fun resolveCategory(
        text: String,
        categories: List<CategoryEntity>,
        context: Context,
        type: TransactionType
    ): CategoryEntity? {
        if (categories.isEmpty()) return null

        // 1. Keyword match from SMS description & bank text
        val guessed = guessCategory(text, categories)
        if (guessed != null) return guessed

        // 2. Last used category of the same type
        val lastUsedId = getLastUsedCategoryId(context, type)
        if (lastUsedId != null) {
            val matchedLast = categories.find { it.id == lastUsedId }
            if (matchedLast != null) return matchedLast
        }

        // 3. Fallback to first category only as last resort
        return categories.firstOrNull()
    }

    fun guessCategory(
        text: String,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        if (text.isBlank() || categories.isEmpty()) return null
        val cleanText = text.replace(Regex("[\\u200c\\s]+"), " ").trim()

        // 1. Direct match with user categories
        for (cat in categories) {
            val cleanCat = cat.name.replace(Regex("[\\u200c\\s]+"), " ").trim()
            if (cleanCat.isNotBlank() && cleanText.contains(cleanCat, ignoreCase = true)) {
                return cat
            }
        }

        // 2. Common category synonyms mapping for Iranian bank SMS
        val synonyms = mapOf(
            listOf("خوراک", "غذا", "سوپرمارکت", "رستوران") to listOf(
                "غذا", "رستوران", "کافه", "قهوه", "نان", "سوپر", "هایپر", "میوه",
                "اسنپ فود", "اسنپفود", "فست فود", "خوراک", "پروتئین", "قصابی", "قنادی", "شیرینی"
            ),
            listOf("حمل و نقل", "تاکسی", "خودرو", "بنزین") to listOf(
                "اسنپ", "تپسی", "بنزین", "پمپ بنزین", "جایگاه", "گازوئیل", "کرایه", "تاکسی",
                "مترو", "اتوبوس", "حمل", "نقل", "عوارض", "پارکینگ", "تعمیرگاه"
            ),
            listOf("قبوض", "خانه", "ارتباطات") to listOf(
                "قبض", "برق", "گاز", "آب", "مخابرات", "همراه اول", "ایرانسل", "رایتل",
                "اینترنت", "شارژ", "همراه", "آسیاتک", "شاتل"
            ),
            listOf("سلامت", "درمان", "پزشکی") to listOf(
                "دارو", "درمان", "پزشک", "دکتر", "بیمارستان", "درمانگاه", "کلینیک",
                "آزمایشگاه", "دندانپزشک", "داروخانه", "ویزیت", "فیزیوتراپی"
            ),
            listOf("پوشاک", "لباس") to listOf(
                "پوشاک", "لباس", "کفش", "شلوار", "پیراهن", "کت", "بوتیک", "کیف"
            ),
            listOf("تفریح", "ورزش") to listOf(
                "سینما", "تئاتر", "کتاب", "بازی", "ورزش", "باشگاه", "استخر", "تفریح", "شهربازی"
            ),
            listOf("خرید", "عمومی") to listOf(
                "دیجی کالا", "دیجیکالا", "خرید", "پوز", "شاپرک", "فروشگاه"
            ),
            listOf("حقوق", "درآمد") to listOf(
                "حقوق", "پاداش", "دستمزد", "عیدی", "مساعده", "واریز حقوق"
            ),
            listOf("قرض دادن") to listOf(
                "قرض دادن", "قرض به", "وام دادن", "دستی به", "بابت قرض به"
            ),
            listOf("قرض گرفتن") to listOf(
                "قرض گرفتن", "قرض از", "وام گرفتن", "مساعده از", "بابت قرض از"
            )
        )

        for ((targetCatNames, keywords) in synonyms) {
            for (kw in keywords) {
                if (cleanText.contains(kw, ignoreCase = true)) {
                    val matched = categories.find { cat ->
                        targetCatNames.any { target -> cat.name.contains(target, ignoreCase = true) }
                    }
                    if (matched != null) return matched
                }
            }
        }

        return null
    }

    fun guessCategoryId(
        text: String,
        categories: List<CategoryEntity>
    ): Long? {
        return guessCategory(text, categories)?.id
    }
}
