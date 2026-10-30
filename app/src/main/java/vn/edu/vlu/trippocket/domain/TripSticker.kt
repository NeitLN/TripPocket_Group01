package vn.edu.vlu.trippocket.domain

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/**
 * The generated sticker that identifies a trip (DESIGN.md, signature move). Plain Kotlin so the
 * rules are unit-tested: the same destination always gets the same code and colour, the same
 * trip always the same tilt. A sticker is decoration next to the real name, never a status.
 */
object TripSticker {

    /** Number of sticker fills (violet, lime, pink, sky, apricot), in that fixed order. */
    const val FILL_COUNT = 5

    /** Tilts in degrees, picked by trip id; small enough that rows still scan as aligned. */
    private val TILTS = floatArrayOf(-3f, 0f, 3f)

    /**
     * First grapheme of the first two words, or the first two graphemes of a single word,
     * upper-cased with accents kept: "Đà Lạt, Lâm Đồng" -> "ĐL", "Huế" -> "HU".
     * null when the destination has no usable letters (the view then shows the pocket symbol).
     */
    fun code(destination: String): String? {
        val words = canonical(destination)
            .split(' ', ',', '-', '/', '.', '(', ')')
            .filter { word -> word.isNotEmpty() && word.first().isLetterOrDigit() }
        val graphemes = when {
            words.size >= 2 -> words.take(2).map { graphemes(it).first() }
            words.size == 1 -> graphemes(words[0]).take(2)
            else -> return null
        }
        return graphemes.joinToString("").uppercase(Locale.ROOT)
    }

    /**
     * Index into the fill set from the place name before the first comma, so "Đà Lạt, Lâm Đồng"
     * and "Đà Lạt" share a colour. Stable regardless of case, spacing or composition.
     */
    fun colorIndex(destination: String): Int {
        val place = canonical(destination).substringBefore(',').trim().lowercase(Locale.ROOT)
        return Math.floorMod(place.hashCode(), FILL_COUNT)
    }

    /** -3, 0 or +3 degrees, stable for a trip across binds and relaunches. */
    fun tiltDegrees(tripId: String): Float = TILTS[Math.floorMod(tripId.hashCode(), TILTS.size)]

    private fun canonical(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ")

    private fun graphemes(word: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(word) }
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result += word.substring(start, end)
            start = end
            end = iterator.next()
        }
        return result
    }
}
