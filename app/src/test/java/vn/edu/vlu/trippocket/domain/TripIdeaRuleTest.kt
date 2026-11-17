package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone 4: the trip idea form is checked before any POST / PUT. */
class TripIdeaRuleTest {

    private fun problems(
        title: String = "Da Lat",
        destination: String = "Lam Dong",
        days: String = "3",
        budget: String = "",
        coverUrl: String = "",
        summary: String = "",
    ) = TripIdeaRule.problems(title, destination, days, budget, coverUrl, summary)

    @Test
    fun validForm_hasNoProblems_andIsTrimmed() {
        assertTrue(problems().isEmpty())
        val input = TripIdeaRule.toInput("  Da Lat ", " Lam Dong ", " 3 ", "4.500.000", " https://a.b/c.jpg ", " hi ")!!
        assertEquals("Da Lat", input.title)
        assertEquals("Lam Dong", input.destination)
        assertEquals(3, input.days)
        assertEquals(4_500_000L, input.budgetVnd)
        assertEquals("https://a.b/c.jpg", input.coverUrl)
        assertEquals("hi", input.summary)
    }

    @Test
    fun budget_acceptsDotsCommasAndBlank_only() {
        assertEquals(4_500_000L, TripIdeaRule.parseBudget("4,500,000"))
        assertEquals(0L, TripIdeaRule.parseBudget(" "))
        assertNull(TripIdeaRule.parseBudget("-5"))
        assertNull(TripIdeaRule.parseBudget("12abc"))
        assertNull(TripIdeaRule.parseBudget("2000000000"))
    }

    @Test
    fun days_mustBe1to30() {
        assertEquals(setOf(TripIdeaProblem.BAD_DAYS), problems(days = "0"))
        assertEquals(setOf(TripIdeaProblem.BAD_DAYS), problems(days = "31"))
        assertEquals(setOf(TripIdeaProblem.BAD_DAYS), problems(days = ""))
        assertTrue(problems(days = "30").isEmpty())
    }

    @Test
    fun cover_isBlankOrHttps() {
        assertEquals(setOf(TripIdeaProblem.BAD_COVER_URL), problems(coverUrl = "http://a.b/c.jpg"))
        assertEquals(setOf(TripIdeaProblem.BAD_COVER_URL), problems(coverUrl = "https://a b.jpg"))
        assertTrue(problems(coverUrl = "https://upload.wikimedia.org/x.jpg").isEmpty())
    }

    @Test
    fun lengths_areCapped() {
        assertEquals(
            setOf(TripIdeaProblem.BLANK_TITLE, TripIdeaProblem.DESTINATION_TOO_LONG, TripIdeaProblem.SUMMARY_TOO_LONG),
            problems(title = " ", destination = "x".repeat(61), summary = "y".repeat(281)),
        )
        assertNull(TripIdeaRule.toInput(" ", "a", "1", "", "", ""))
    }
}
