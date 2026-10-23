package vn.edu.vlu.trippocket.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.edu.vlu.trippocket.domain.LoginValidator

/** Errors are string resources so the ViewModel needs no Context to produce them. */
data class LoginUiState(
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val signedIn: Boolean = false,
)

/**
 * Screen 1. Only validates the form; Firebase Auth comes in a later
 * milestone, so a valid form counts as signed in.
 */
class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Clear an error as soon as the user starts fixing the field.
    fun onEmailChanged() = _uiState.update { it.copy(emailError = null) }

    fun onPasswordChanged() = _uiState.update { it.copy(passwordError = null) }

    fun signIn(email: String, password: String) {
        val emailError = LoginValidator.emailError(email)
        val passwordError = LoginValidator.passwordError(password)
        _uiState.value = LoginUiState(
            emailError = emailError,
            passwordError = passwordError,
            signedIn = emailError == null && passwordError == null,
        )
    }
}
