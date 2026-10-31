package vn.edu.vlu.trippocket.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.repository.AuthException
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.domain.AuthProblem
import vn.edu.vlu.trippocket.domain.LoginValidator

/** Register is a mode of the login screen, not a second screen (A1). */
enum class AuthMode { SIGN_IN, REGISTER }

/** Errors are string resources so the ViewModel needs no Context to produce them. */
data class LoginUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    /** A Firebase call is in flight: the button is disabled and [progressAuth] is visible. */
    val loading: Boolean = false,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    /** Register mode only; the field is hidden while signing in. */
    @param:StringRes val confirmPasswordError: Int? = null,
    /** A failure that does not belong to one field (wrong password, no network): [tvAuthError]. */
    @param:StringRes val formError: Int? = null,
    /** A sign-in or register call just succeeded: the screen opens the trip list. */
    val signedIn: Boolean = false,
    /**
     * A session was already cached when this ViewModel was created ("still signed in after
     * restarting the app"). Sampled once, so a just-completed call only sets [signedIn];
     * A1-ui uses it to skip the form instead of showing it for a frame.
     */
    val alreadySignedIn: Boolean = false,
)

/**
 * Screen 1 in both modes. Validates with [LoginValidator], then calls [AuthRepository]; the
 * repository reports an [AuthProblem] and this class turns it into a string resource. The
 * state leaves [LoginUiState.loading] in every path, including errors.
 */
class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(alreadySignedIn = auth.currentUid.value != null),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Clear an error as soon as the user starts fixing the field.
    fun onEmailChanged() = _uiState.update { it.copy(emailError = null, formError = null) }

    fun onPasswordChanged() = _uiState.update { it.copy(passwordError = null, formError = null) }

    fun onConfirmPasswordChanged() = _uiState.update { it.copy(confirmPasswordError = null, formError = null) }

    fun clearFormError() = _uiState.update { it.copy(formError = null) }

    /** "Create an account": the same screen, with the confirm field and register labels. */
    fun switchToRegister() = _uiState.update {
        it.copy(
            mode = AuthMode.REGISTER,
            emailError = null,
            passwordError = null,
            confirmPasswordError = null,
            formError = null,
        )
    }

    /** "Already have an account? Sign in": back to the shorter form, errors cleared. */
    fun switchToSignIn() = _uiState.update {
        it.copy(
            mode = AuthMode.SIGN_IN,
            emailError = null,
            passwordError = null,
            confirmPasswordError = null,
            formError = null,
        )
    }

    fun signIn(email: String, password: String) {
        if (_uiState.value.loading) return
        val emailError = LoginValidator.emailError(email)
        val passwordError = LoginValidator.passwordError(password)
        if (emailError != null || passwordError != null) {
            // Invalid form: no call, so the state never sits in Loading.
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, formError = null) }
            return
        }

        _uiState.update { it.copy(emailError = null, passwordError = null, formError = null, loading = true) }
        viewModelScope.launch {
            try {
                auth.signIn(email.trim(), password)
                _uiState.update { it.copy(loading = false, signedIn = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _uiState.update { it.withAuthProblem(e.problem) }
            }
        }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        if (_uiState.value.loading) return
        val emailError = LoginValidator.emailError(email)
        val passwordError = LoginValidator.passwordError(password)
        val confirmPasswordError = if (confirmPassword != password) R.string.error_password_mismatch else null
        if (emailError != null || passwordError != null || confirmPasswordError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                    formError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(emailError = null, passwordError = null, confirmPasswordError = null, formError = null, loading = true)
        }
        viewModelScope.launch {
            try {
                auth.register(email.trim(), password)
                _uiState.update { it.copy(loading = false, signedIn = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _uiState.update { it.withAuthProblem(e.problem) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(appContainer().authRepository) }
        }
    }
}

/** Where each reason belongs: a field for a weak password, the shared error line otherwise. */
private fun LoginUiState.withAuthProblem(problem: AuthProblem): LoginUiState = when (problem) {
    AuthProblem.INVALID_CREDENTIALS -> copy(loading = false, formError = R.string.error_auth_invalid_credentials)
    AuthProblem.EMAIL_IN_USE -> copy(loading = false, formError = R.string.error_auth_email_in_use)
    AuthProblem.WEAK_PASSWORD -> copy(loading = false, passwordError = R.string.error_password_short)
    AuthProblem.NETWORK -> copy(loading = false, formError = R.string.error_auth_network)
    AuthProblem.TOO_MANY_REQUESTS -> copy(loading = false, formError = R.string.error_auth_too_many_requests)
    AuthProblem.UNKNOWN -> copy(loading = false, formError = R.string.error_auth_generic)
}
