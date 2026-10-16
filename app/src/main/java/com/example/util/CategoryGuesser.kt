package com.example.util

import com.example.data.local.entity.CategoryEntity

object CategoryGuesser {

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
