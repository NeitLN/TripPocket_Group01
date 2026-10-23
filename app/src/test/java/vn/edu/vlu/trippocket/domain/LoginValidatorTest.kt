package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.edu.vlu.trippocket.R

class LoginValidatorTest {

    @Test
    fun email() {
        assertEquals(R.string.error_email_empty, LoginValidator.emailError(" "))
        assertEquals(R.string.error_email_invalid, LoginValidator.emailError("an@gmail"))
        assertNull(LoginValidator.emailError("an@gmail.com"))
    }

    @Test
    fun password() {
        assertEquals(R.string.error_password_short, LoginValidator.passwordError("abc1"))
        assertEquals(R.string.error_password_no_digit, LoginValidator.passwordError("abcdefgh"))
        assertNull(LoginValidator.passwordError("dalat2026"))
    }
}
