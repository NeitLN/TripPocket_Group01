package vn.edu.vlu.trippocket.domain

import androidx.annotation.StringRes
import vn.edu.vlu.trippocket.R

/**
 * Validation rules for the login form. Returns the string resource of the
 * error to show in the TextInputLayout, or null when the value is valid.
 * Kept free of Views so it can be unit-tested.
 */
object LoginValidator {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 8

    @StringRes
    fun emailError(email: String): Int? = when {
        email.isBlank() -> R.string.error_email_empty
        !EMAIL.matches(email.trim()) -> R.string.error_email_invalid
        else -> null
    }

    @StringRes
    fun passwordError(password: String): Int? = when {
        password.length < MIN_PASSWORD_LENGTH -> R.string.error_password_short
        password.none { it.isDigit() } -> R.string.error_password_no_digit
        else -> null
    }
}
