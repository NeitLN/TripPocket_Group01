package vn.edu.vlu.trippocket.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.vlu.trippocket.R

/** The ViewModels hold no View or Context, so they run as plain JVM tests. Owner: Tiến. */
class LoginViewModelTest {

    @Test
    fun login_showsErrorsThenSignsIn() {
        val viewModel = LoginViewModel()
        viewModel.signIn("an@gmail", "abc")
        assertEquals(R.string.error_email_invalid, viewModel.uiState.value.emailError)
        assertEquals(R.string.error_password_short, viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.signedIn)

        viewModel.onEmailChanged()
        assertNull(viewModel.uiState.value.emailError)

        viewModel.signIn("an@gmail.com", "dalat2026")
        assertTrue(viewModel.uiState.value.signedIn)
    }
}
