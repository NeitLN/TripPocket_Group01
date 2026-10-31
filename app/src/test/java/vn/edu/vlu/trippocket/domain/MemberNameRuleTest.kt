package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.edu.vlu.trippocket.domain.MemberProblem

/** E1: the member-name rule shared by the repository and the members screen. */
class MemberNameRuleTest {

    @Test
    fun blankOrWhitespaceOnly_isBlankName() {
        assertEquals(MemberProblem.BLANK_NAME, MemberNameRule.validate(""))
        assertEquals(MemberProblem.BLANK_NAME, MemberNameRule.validate("   "))
        assertEquals(MemberProblem.BLANK_NAME, MemberNameRule.validate("\n\t"))
    }

    @Test
    fun nameUpToTheLimit_isAccepted() {
        assertNull(MemberNameRule.validate("Bình"))
        assertNull(MemberNameRule.validate("a".repeat(Limits.MAX_TEXT)))
        // Spaces around the name do not count towards the limit.
        assertNull(MemberNameRule.validate("  " + "a".repeat(Limits.MAX_TEXT) + "  "))
    }

    @Test
    fun nameLongerThanTheLimit_isTooLong() {
        assertEquals(
            MemberProblem.NAME_TOO_LONG,
            MemberNameRule.validate("a".repeat(Limits.MAX_TEXT + 1)),
        )
        assertEquals(
            MemberProblem.NAME_TOO_LONG,
            MemberNameRule.validate("  " + "a".repeat(Limits.MAX_TEXT + 1)),
        )
    }

    @Test
    fun clean_returnsTheTrimmedName() {
        assertEquals("Bình", MemberNameRule.clean("  Bình  "))
        assertEquals("a".repeat(Limits.MAX_TEXT), MemberNameRule.clean("  " + "a".repeat(Limits.MAX_TEXT) + "  "))
    }
}
