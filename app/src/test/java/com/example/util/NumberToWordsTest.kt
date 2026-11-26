package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberToWordsTest {

    @Test
    fun `zero converts cleanly with default Rial suffix, custom suffix, and empty suffix`() {
        assertEquals("صفر ریال", NumberToWords.convert(0L))
        assertEquals("صفر ریال", NumberToWords.convert(0L, suffix = "ریال"))
        assertEquals("صفر تومان", NumberToWords.convert(0L, suffix = "تومان"))
        assertEquals("صفر", NumberToWords.convert(0L, suffix = ""))
        assertEquals("صفر", NumberToWords.convert(0L, suffix = "   "))
    }

    @Test
    fun `small and standard Rial amounts preserve exact value without dividing by ten`() {
        assertEquals("یک ریال", NumberToWords.convert(1L, suffix = "ریال"))
        assertEquals("پنج ریال", NumberToWords.convert(5L, suffix = "ریال"))
        assertEquals("نه ریال", NumberToWords.convert(9L, suffix = "ریال"))
        assertEquals("ده ریال", NumberToWords.convert(10L, suffix = "ریال"))
        assertEquals("پانزده ریال", NumberToWords.convert(15L, suffix = "ریال"))
        assertEquals("نوزده ریال", NumberToWords.convert(19L, suffix = "ریال"))
        assertEquals("بیست و سه ریال", NumberToWords.convert(23L, suffix = "ریال"))
        assertEquals("یکصد ریال", NumberToWords.convert(100L, suffix = "ریال"))
        assertEquals("یکصد و پنج ریال", NumberToWords.convert(105L, suffix = "ریال"))
        assertEquals("یک هزار ریال", NumberToWords.convert(1_000L, suffix = "ریال"))
        assertEquals("پنجاه هزار ریال", NumberToWords.convert(50_000L, suffix = "ریال"))
        assertEquals("دویست و پنجاه هزار و سیصد و چهل و پنج ریال", NumberToWords.convert(250_345L, suffix = "ریال"))
    }

    @Test
    fun `large amount 2666666 converts accurately in Rial without tenfold mismatch or truncation`() {
        assertEquals(
            "دو میلیون و ششصد و شصت و شش هزار و ششصد و شصت و شش ریال",
            NumberToWords.convert(2_666_666L, suffix = "ریال")
        )
        assertEquals(
            "دو میلیون و ششصد و شصت و شش هزار و ششصد و شصت و شش ریال",
            NumberToWords.convert(2_666_666L)
        )
    }

    @Test
    fun `very large numbers up to Long MAX_VALUE include accurate scale words`() {
        assertEquals("یک میلیارد ریال", NumberToWords.convert(1_000_000_000L, suffix = "ریال"))
        assertEquals("یک تریلیون ریال", NumberToWords.convert(1_000_000_000_000L, suffix = "ریال"))
        assertEquals("یک کوادریلیون ریال", NumberToWords.convert(1_000_000_000_000_000L, suffix = "ریال"))
        assertEquals(
            "نه کوینتیلیون و دویست و بیست و سه کوادریلیون و سیصد و هفتاد و دو تریلیون و سی و شش میلیارد و هشتصد و پنجاه و چهار میلیون و هفتصد و هفتاد و پنج هزار و هشتصد و هفت ریال",
            NumberToWords.convert(Long.MAX_VALUE, suffix = "ریال")
        )
    }

    @Test
    fun `negative numbers including Long MIN_VALUE convert safely without overflow`() {
        assertEquals("منفی یک ریال", NumberToWords.convert(-1L, suffix = "ریال"))
        assertEquals("منفی پنجاه هزار ریال", NumberToWords.convert(-50_000L, suffix = "ریال"))
        assertEquals(
            "منفی دو میلیون و ششصد و شصت و شش هزار و ششصد و شصت و شش ریال",
            NumberToWords.convert(-2_666_666L, suffix = "ریال")
        )
        assertEquals("منفی پانصد", NumberToWords.convert(-500L, suffix = ""))
        assertEquals(
            "منفی نه کوینتیلیون و دویست و بیست و سه کوادریلیون و سیصد و هفتاد و دو تریلیون و سی و شش میلیارد و هشتصد و پنجاه و چهار میلیون و هفتصد و هفتاد و پنج هزار و هشتصد و هشت ریال",
            NumberToWords.convert(Long.MIN_VALUE, suffix = "ریال")
        )
    }

    @Test
    fun `custom suffix from caller is respected verbatim without unit scaling`() {
        assertEquals("پانصد هزار تومان", NumberToWords.convert(500_000L, suffix = "تومان"))
        assertEquals("پانصد هزار", NumberToWords.convert(500_000L, suffix = ""))
    }
}
