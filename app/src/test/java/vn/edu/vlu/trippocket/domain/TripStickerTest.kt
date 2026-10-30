package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.time.LocalDate

class TripStickerTest {

    @Test
    fun code_takesFirstGraphemeOfTwoWords_keepingVietnameseAccents() {
        assertEquals("ĐL", TripSticker.code("Đà Lạt, Lâm Đồng"))
        assertEquals("HA", TripSticker.code("Hội An, Quảng Nam"))
        assertEquals("PQ", TripSticker.code("Phú Quốc, Kiên Giang"))
        assertEquals("ỨD", TripSticker.code("ứng dụng"))
    }

    @Test
    fun code_singleWord_takesTwoGraphemes_andDecomposedInputMatchesComposed() {
        assertEquals("HU", TripSticker.code("Huế"))
        val decomposed = Normalizer.normalize("Ữ Ặ", Normalizer.Form.NFD)
        assertEquals("ỮẶ", TripSticker.code(decomposed))
    }

    @Test
    fun code_withoutLetters_isNull() {
        assertNull(TripSticker.code(""))
        assertNull(TripSticker.code("  , - "))
    }

    @Test
    fun colorIndex_isStableAndIgnoresCaseSpacingAndComposition() {
        val index = TripSticker.colorIndex("Đà Lạt")
        assertEquals(index, TripSticker.colorIndex("  đà   LẠT "))
        assertEquals(index, TripSticker.colorIndex(Normalizer.normalize("Đà Lạt", Normalizer.Form.NFD)))
        assertEquals(index, TripSticker.colorIndex("Đà Lạt, Lâm Đồng"))
        assertTrue(index in 0 until TripSticker.FILL_COUNT)
    }

    @Test
    fun tilt_isOneOfThreeSmallAngles_andStablePerTrip() {
        val tilt = TripSticker.tiltDegrees("trip-dalat")
        assertTrue(tilt in listOf(-3f, 0f, 3f))
        assertEquals(tilt, TripSticker.tiltDegrees("trip-dalat"))
    }
}

class TripGroupingTest {

    private data class T(val id: String, val start: LocalDate, val end: LocalDate)

    private val today = LocalDate.of(2026, 10, 31)

    private fun group(vararg trips: T) =
        TripGrouping.group(trips.toList(), today, T::start, T::end).map { (g, list) -> g to list.map { it.id } }

    @Test
    fun splitsByInclusiveDates_sortsEachGroup_andOmitsEmptyGroups() {
        val result = group(
            T("pq", LocalDate.of(2027, 6, 20), LocalDate.of(2027, 6, 24)),
            T("hue", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3)),
            T("dl", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18)),
            T("ha", LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 7)),
            T("endsToday", LocalDate.of(2026, 10, 29), today),
        )
        assertEquals(
            listOf(
                TripGroup.HAPPENING_NOW to listOf("endsToday"),
                TripGroup.UPCOMING to listOf("hue", "pq"),
                TripGroup.PAST to listOf("dl", "ha"),
            ),
            result,
        )
    }

    @Test
    fun noTrips_noGroups() {
        assertTrue(group().isEmpty())
    }
}
