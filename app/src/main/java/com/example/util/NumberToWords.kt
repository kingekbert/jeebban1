package com.example.util

object NumberToWords {
    private val yekan = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val dahgan = arrayOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val dahyek = arrayOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val sadgan = arrayOf("", "یکصد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val steps = arrayOf("", "هزار", "میلیون", "میلیارد", "تریلیون", "کوادریلیون", "کوینتیلیون")

    /**
     * Converts a numeric amount into Persian words with the caller-provided [suffix] (default: "ریال").
     * Does not perform any implicit unit conversion (no division or multiplication on [amount]).
     */
    fun convert(amount: Long, suffix: String = "ریال"): String {
        val cleanSuffix = suffix.trim()
        val unitTail = if (cleanSuffix.isNotEmpty()) " $cleanSuffix" else ""

        if (amount == 0L) {
            return "صفر$unitTail"
        }

        val isNegative = amount < 0L
        val magnitude: ULong = if (isNegative) {
            (-(amount + 1L)).toULong() + 1uL
        } else {
            amount.toULong()
        }

        val words = convertPositiveMagnitude(magnitude)
        val prefix = if (isNegative) "منفی " else ""
        return "$prefix$words$unitTail"
    }

    private fun convertPositiveMagnitude(value: ULong): String {
        val parts = mutableListOf<String>()
        var temp = value
        var stepIndex = 0

        while (temp > 0uL) {
            val chunk = (temp % 1000uL).toInt()
            if (chunk > 0) {
                val chunkText = convertChunk(chunk)
                val stepName = steps.getOrNull(stepIndex) ?: ""
                val part = if (stepName.isNotEmpty()) "$chunkText $stepName" else chunkText
                parts.add(0, part)
            }
            temp /= 1000uL
            stepIndex++
        }

        return parts.joinToString(" و ")
    }

    private fun convertChunk(number: Int): String {
        val s = number / 100
        val d = (number % 100) / 10
        val y = number % 10
        val list = mutableListOf<String>()

        if (s > 0) list.add(sadgan[s])

        if (d == 1) {
            list.add(dahyek[y])
        } else {
            if (d > 0) list.add(dahgan[d])
            if (y > 0) list.add(yekan[y])
        }

        return list.joinToString(" و ")
    }
}
