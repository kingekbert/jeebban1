package com.example.util

import com.example.data.local.entity.ExtractedField
import com.example.data.local.entity.FieldType

object PatternExtractor {

    /**
     * Extracts parts/tokens from the SMS text line by line.
     * Accurately extracts combined date-times, pure dates, pure times, signed amounts,
     * key:value pairs, account numbers, and words into distinct ExtractedFields.
     */
    fun extractParts(smsText: String): List<ExtractedField> {
        val cleanedText = cleanSharedText(smsText)
        if (cleanedText.isBlank()) return emptyList()

        val parts = mutableListOf<ExtractedField>()
        val lines = cleanedText.lines()

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            val tokensInLine = extractTokensFromLine(trimmedLine)
            for (token in tokensInLine) {
                if (token.isNotBlank()) {
                    parts.add(
                        ExtractedField(
                            text = token,
                            lineIndex = lineIndex,
                            fieldType = FieldType.IGNORE
                        )
                    )
                }
            }
        }

        return parts
    }

    private fun extractTokensFromLine(line: String): List<String> {
        val tokens = mutableListOf<String>()

        // Check if line is Key:Value (e.g. برداشت:680,000- or حساب:04007 or مانده:702,045,020)
        // Ensure colon is not part of a time (like 18:20, 21:05, 0706-21:05)
        val colonIndex = line.indexOf(':')
        if (colonIndex > 0 && colonIndex < line.length - 1) {
            val prefix = line.substring(0, colonIndex).trim()
            val suffix = line.substring(colonIndex + 1).trim()
            val isTimeColon = prefix.matches(Regex(""".*[\d۰-۹٠-٩]{1,2}$""")) && suffix.matches(Regex("""^[\d۰-۹٠-٩]{2}.*"""))
            val hasLettersInPrefix = prefix.any { it.isLetter() }

            if (!isTimeColon && hasLettersInPrefix) {
                // Key part
                tokens.addAll(tokenizeSegment(prefix))
                // Value part
                tokens.addAll(tokenizeSegment(suffix))
                return tokens
            }
        }

        // Otherwise tokenize the entire line segment
        tokens.addAll(tokenizeSegment(line))
        return tokens
    }

    private fun toEnglishDigits(input: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var result = input
        for (i in 0..9) {
            result = result.replace(persianDigits[i], ('0' + i)).replace(arabicDigits[i], ('0' + i))
        }
        return result
    }

    private fun tokenizeSegment(segment: String): List<String> {
        val tokens = mutableListOf<String>()

        val chunks = segment.split(Regex("""\s+""")).filter { it.isNotBlank() }
        for (chunk in chunks) {
            var workingChunk = chunk

            // Check date/time patterns from longest to shortest
            // Pattern 4: YY/M/D-H:MM or YYYY/M/D-H:MM (e.g. 05/04/21-16:34, 1405/9/2-3:51, 1405/9/3-12:37)
            val p4 = Regex("""([\d۰-۹٠-٩]{2,4})/([\d۰-۹٠-٩]{1,2})/([\d۰-۹٠-٩]{1,2})[_\-]([\d۰-۹٠-٩]{1,2}):([\d۰-۹٠-٩]{2})""")
            val m4 = p4.find(workingChunk)
            if (m4 != null) {
                val rawY = m4.groupValues[1]
                val rawM = m4.groupValues[2]
                val rawD = m4.groupValues[3]
                val rawH = m4.groupValues[4]
                val rawMin = m4.groupValues[5]
                val yLen = rawY.length
                val mInt = toEnglishDigits(rawM).toIntOrNull() ?: -1
                val dInt = toEnglishDigits(rawD).toIntOrNull() ?: -1
                val hInt = toEnglishDigits(rawH).toIntOrNull() ?: -1
                val minInt = toEnglishDigits(rawMin).toIntOrNull() ?: -1
                if ((yLen == 2 || yLen == 4) && mInt in 1..12 && dInt in 1..31 && hInt in 0..23 && minInt in 0..59) {
                    tokens.add(rawY)
                    tokens.add(rawM)
                    tokens.add(rawD)
                    tokens.add(rawH)
                    tokens.add(rawMin)
                    workingChunk = workingChunk.removeRange(m4.range).trim()
                    if (workingChunk.isEmpty()) continue
                }
            }

            // Pattern 2 & 3: M/D_H:MM or M/D-H:MM (e.g. 05/20_18:20, 9/2-3:51)
            val p23 = Regex("""([\d۰-۹٠-٩]{1,2})/([\d۰-۹٠-٩]{1,2})[_\-]([\d۰-۹٠-٩]{1,2}):([\d۰-۹٠-٩]{2})""")
            val m23 = p23.find(workingChunk)
            if (m23 != null) {
                val rawM = m23.groupValues[1]
                val rawD = m23.groupValues[2]
                val rawH = m23.groupValues[3]
                val rawMin = m23.groupValues[4]
                val mInt = toEnglishDigits(rawM).toIntOrNull() ?: -1
                val dInt = toEnglishDigits(rawD).toIntOrNull() ?: -1
                val hInt = toEnglishDigits(rawH).toIntOrNull() ?: -1
                val minInt = toEnglishDigits(rawMin).toIntOrNull() ?: -1
                if (mInt in 1..12 && dInt in 1..31 && hInt in 0..23 && minInt in 0..59) {
                    tokens.add(rawM)
                    tokens.add(rawD)
                    tokens.add(rawH)
                    tokens.add(rawMin)
                    workingChunk = workingChunk.removeRange(m23.range).trim()
                    if (workingChunk.isEmpty()) continue
                }
            }

            // Priority Pattern: MMDD-H:MM or MMDD_H:MM (e.g. 0706-21:05 or 0525-7:28)
            // Group 1 = Month (01-12), Group 2 = Day (01-31), Group 3 = Hour (0-23), Group 4 = Minute (00-59)
            val pMmDdTime = Regex("""([\d۰-۹٠-٩]{2})([\d۰-۹٠-٩]{2})[_\-]([\d۰-۹٠-٩]{1,2}):([\d۰-۹٠-٩]{2})""")
            val mMmDdTime = pMmDdTime.find(workingChunk)
            if (mMmDdTime != null) {
                val rawM = mMmDdTime.groupValues[1]
                val rawD = mMmDdTime.groupValues[2]
                val rawH = mMmDdTime.groupValues[3]
                val rawMin = mMmDdTime.groupValues[4]
                val mInt = toEnglishDigits(rawM).toIntOrNull() ?: -1
                val dInt = toEnglishDigits(rawD).toIntOrNull() ?: -1
                val hInt = toEnglishDigits(rawH).toIntOrNull() ?: -1
                val minInt = toEnglishDigits(rawMin).toIntOrNull() ?: -1
                if (mInt in 1..12 && dInt in 1..31 && hInt in 0..23 && minInt in 0..59) {
                    tokens.add(rawM)
                    tokens.add(rawD)
                    tokens.add(rawH)
                    tokens.add(rawMin)
                    workingChunk = workingChunk.removeRange(mMmDdTime.range).trim()
                    if (workingChunk.isEmpty()) continue
                }
            }

            // Pattern 5: YYYY/M/D or YY/M/D (e.g. 1405/03/01 or 1405/9/2)
            val p5 = Regex("""([\d۰-۹٠-٩]{2,4})/([\d۰-۹٠-٩]{1,2})/([\d۰-۹٠-٩]{1,2})""")
            val m5 = p5.find(workingChunk)
            if (m5 != null) {
                val rawY = m5.groupValues[1]
                val rawM = m5.groupValues[2]
                val rawD = m5.groupValues[3]
                val yLen = rawY.length
                val mInt = toEnglishDigits(rawM).toIntOrNull() ?: -1
                val dInt = toEnglishDigits(rawD).toIntOrNull() ?: -1
                if ((yLen == 2 || yLen == 4) && mInt in 1..12 && dInt in 1..31) {
                    tokens.add(rawY)
                    tokens.add(rawM)
                    tokens.add(rawD)
                    workingChunk = workingChunk.removeRange(m5.range).trim()
                    if (workingChunk.isEmpty()) continue
                }
            }

            // Pattern 6: H:MM:SS (e.g. 21:05:28 or 3:05:28)
            val p6 = Regex("""([\d۰-۹٠-٩]{1,2}):([\d۰-۹٠-٩]{2}):([\d۰-۹٠-٩]{2})""")
            val m6 = p6.find(workingChunk)
            if (m6 != null) {
                val rawH = m6.groupValues[1]
                val rawMin = m6.groupValues[2]
                val rawSec = m6.groupValues[3]
                val hInt = toEnglishDigits(rawH).toIntOrNull() ?: -1
                val minInt = toEnglishDigits(rawMin).toIntOrNull() ?: -1
                val secInt = toEnglishDigits(rawSec).toIntOrNull() ?: -1
                if (hInt in 0..23 && minInt in 0..59 && secInt in 0..59) {
                    tokens.add(rawH)
                    tokens.add(rawMin)
                    tokens.add(rawSec)
                    workingChunk = workingChunk.removeRange(m6.range).trim()
                    if (workingChunk.isEmpty()) continue
                }
            }

            // Pattern 7: H:MM (e.g. 18:20 or 3:51)
            val p7 = Regex("""([\d۰-۹٠-٩]{1,2}):([\d۰-۹٠-٩]{2})""")
            val m7 = p7.find(workingChunk)
            if (m7 != null) {
                val rawH = m7.groupValues[1]
                val rawMin = m7.groupValues[2]
                val hInt = toEnglishDigits(rawH).toIntOrNull() ?: -1
                val minInt = toEnglishDigits(rawMin).toIntOrNull() ?: -1
                if (hInt in 0..23 && minInt in 0..59) {
                    tokens.add(rawH)
                    tokens.add(rawMin)
                    workingChunk = workingChunk.removeRange(m7.range).trim()
                    if (workingChunk.isEmpty()) continue
                }
            }

            // Pattern 8: Standalone MMDD (e.g. "0706" in line with month 01-12 and day 01-31)
            val pMmDdOnly = Regex("""^([\d۰-۹٠-٩]{2})([\d۰-۹٠-٩]{2})$""")
            val mMmDdOnly = pMmDdOnly.find(workingChunk)
            if (mMmDdOnly != null) {
                val rawM = mMmDdOnly.groupValues[1]
                val rawD = mMmDdOnly.groupValues[2]
                val mInt = toEnglishDigits(rawM).toIntOrNull() ?: -1
                val dInt = toEnglishDigits(rawD).toIntOrNull() ?: -1
                if (mInt in 1..12 && dInt in 1..31 && (chunks.size == 1 || segment.contains("تاریخ") || rawM.startsWith("0") || rawM.startsWith("۰") || rawM.startsWith("٠"))) {
                    tokens.add(rawM)
                    tokens.add(rawD)
                    continue
                }
            }

            // Signed amount with leading sign: "-6,500,000" or "+250,000,000"
            val leadingSignMatch = Regex("""^([+\-])([\d۰-۹٠-٩,،\.]+)$""").find(workingChunk)
            if (leadingSignMatch != null) {
                tokens.add(leadingSignMatch.groupValues[1])
                tokens.add(leadingSignMatch.groupValues[2])
                continue
            }

            // Signed amount with trailing sign: "680,000-" or "680,000+"
            val trailingSignMatch = Regex("""^([\d۰-۹٠-٩,،\.]+)([+\-])$""").find(workingChunk)
            if (trailingSignMatch != null) {
                tokens.add(trailingSignMatch.groupValues[1])
                tokens.add(trailingSignMatch.groupValues[2])
                continue
            }

            // Tokenize remaining sub-tokens: dotted account numbers, amounts with commas, signs, and words
            val subTokenRegex = Regex("""([+\-])|([\d۰-۹٠-٩]+(?:\.[\d۰-۹٠-٩]+)+)|([\d۰-۹٠-٩]+(?:[,\.،][\d۰-۹٠-٩]+)+)|([\d۰-۹٠-٩]+)|([^\s\d۰-۹٠-٩+\-:,/،\._()]+)""")
            val matches = subTokenRegex.findAll(workingChunk).toList()
            if (matches.isNotEmpty()) {
                for (m in matches) {
                    val v = m.value.trim()
                    if (v.isNotBlank()) tokens.add(v)
                }
            } else {
                tokens.add(workingChunk)
            }
        }

        return tokens
    }

    fun cleanSharedText(text: String): String {
        return text
            .replace("%20", " ")
            .trim()
    }
}
