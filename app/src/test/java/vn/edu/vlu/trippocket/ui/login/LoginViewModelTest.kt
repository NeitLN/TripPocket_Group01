package vn.edu.vlu.trippocket.ui.login

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.repository.FakeAuthRepository
import vn.edu.vlu.trippocket.domain.AuthProblem

/** A1: login and register modes against [FakeAuthRepository], so Firebase never starts. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val auth = FakeAuthRepository()
    private val validEmail = "an@gmail.com"
    private val validPassword = "dalat2026"

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Every uid the ViewModel asked the reminder reconciler to rebuild alarms for. */
    private val reminderSyncs = mutableListOf<String?>()

    private fun viewModel() = LoginViewModel(auth) { reminderSyncs += it }

    private suspend fun LoginViewModel.awaitState(condition: (LoginUiState) -> Boolean) =
        withTimeout(5_000) { uiState.first(condition) }

    @Test
    fun signingIn_withAValidForm_opensTheTripList(): Unit = runBlocking {
        val viewModel = viewModel()

        viewModel.signIn(validEmail, validPassword)

        val state = viewModel.awaitState { it.signedIn }
        assertFalse(state.loading)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.formError)
        assertEquals(1, auth.calls)
        assertEquals(validEmail, auth.lastEmail)
        assertEquals(FakeAuthRepository.UID, auth.currentUid.value)
        // The new account's reminders are set before the trip list opens.
        assertEquals(listOf<String?>(FakeAuthRepository.UID), reminderSyncs)
    }

    @Test
    fun theFormShowsLoading_untilTheCallReturns(): Unit = runBlocking {
        auth.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.signIn(validEmail, validPassword)
        assertTrue(viewModel.uiState.value.loading)

        auth.gate?.complete(Unit)

        assertFalse(viewModel.awaitState { it.signedIn }.loading)
    }

    @Test
    fun aWrongPassword_showsTheGeneralError_andLeavesLoading(): Unit = runBlocking {
        auth.failWith = AuthProblem.INVALID_CREDENTIALS
        val viewModel = viewModel()

        viewModel.signIn(validEmail, validPassword)

        val state = viewModel.awaitState { !it.loading && it.formError != null }
        assertEquals(R.string.error_auth_invalid_credentials, state.formError)
        assertFalse(state.signedIn)
        assertNull(state.emailError)
        assertNull(state.passwordError)
    }

    @Test
    fun losingTheNetwork_showsTheNetworkError_andLeavesLoading(): Unit = runBlocking {
        auth.failWith = AuthProblem.NETWORK
        val viewModel = viewModel()

        viewModel.signIn(validEmail, validPassword)

        val state = viewModel.awaitState { !it.loading && it.formError != null }
        assertEquals(R.string.error_auth_network, state.formError)
        assertFalse(state.signedIn)
    }

    @Test
    fun tooManyAttempts_showTheRateLimitError(): Unit = runBlocking {
        auth.failWith = AuthProblem.TOO_MANY_REQUESTS
        val viewModel = viewModel()

        viewModel.signIn(validEmail, validPassword)

        val state = viewModel.awaitState { !it.loading && it.formError != null }
        assertEquals(R.string.error_auth_too_many_requests, state.formError)
    }

    @Test
    fun anUnknownFailure_usesTheGenericMessage(): Unit = runBlocking {
        auth.failWith = AuthProblem.UNKNOWN
        val viewModel = viewModel()

        viewModel.signIn(validEmail, validPassword)

        val state = viewModel.awaitState { !it.loading && it.formError != null }
        assertEquals(R.string.error_auth_generic, state.formError)
    }

    @Test
    fun registeringWithATakenEmail_staysInRegisterMode_withTheReason(): Unit = runBlocking {
        auth.failWith = AuthProblem.EMAIL_IN_USE
        val viewModel = viewModel()
        viewModel.switchToRegister()

        viewModel.register(validEmail, validPassword, validPassword)

        val state = viewModel.awaitState { !it.loading && it.formError != null }
        assertEquals(R.string.error_auth_email_in_use, state.formError)
        assertEquals(AuthMode.REGISTER, state.mode)
        assertFalse(state.signedIn)
    }

    @Test
    fun aWeakPasswordFromFirebase_isShownInThePasswordField(): Unit = runBlocking {
        auth.failWith = AuthProblem.WEAK_PASSWORD
        val viewModel = viewModel()
        viewModel.switchToRegister()

        viewModel.register(validEmail, validPassword, validPassword)

        val state = viewModel.awaitState { !it.loading && it.passwordError != null }
        assertEquals(R.string.error_password_short, state.passwordError)
        assertNull(state.formError)
        assertFalse(state.signedIn)
    }

    @Test
    fun mismatchedPasswords_areRejectedBeforeAnyCall(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.switchToRegister()

        viewModel.register(validEmail, validPassword, "dalat2027")

        val state = viewModel.uiState.value
        assertEquals(R.string.error_password_mismatch, state.confirmPasswordError)
        assertEquals(0, auth.calls)
        assertFalse(state.loading)
    }

    @Test
    fun anInvalidForm_neverReachesTheRepository_andNeverStaysLoading(): Unit = runBlocking {
        val viewModel = viewModel()

        viewModel.signIn("an@gmail", "abc")

        val state = viewModel.uiState.value
        assertEquals(R.string.error_email_invalid, state.emailError)
        assertEquals(R.string.error_password_short, state.passwordError)
        assertEquals(0, auth.calls)
        assertFalse(state.loading)
        assertFalse(state.signedIn)
    }

    @Test
    fun switchingModes_clearsTheErrorsOfTheOtherMode(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.signIn("an@gmail", "abc")

        viewModel.switchToRegister()

        val register = viewModel.uiState.value
        assertEquals(AuthMode.REGISTER, register.mode)
        assertNull(register.emailError)
        assertNull(register.passwordError)
        assertNull(register.confirmPasswordError)

        viewModel.register(validEmail, validPassword, "other1234")
        assertEquals(R.string.error_password_mismatch, viewModel.uiState.value.confirmPasswordError)

        viewModel.switchToSignIn()

        val signIn = viewModel.uiState.value
        assertEquals(AuthMode.SIGN_IN, signIn.mode)
        assertNull(signIn.confirmPasswordError)
    }

    @Test
    fun aRestoredSession_isReportedOnce_asAlreadySignedIn(): Unit = runBlocking {
        val restored = LoginViewModel(FakeAuthRepository("uid-restored"))

        assertTrue(restored.uiState.value.alreadySignedIn)
        assertFalse(restored.uiState.value.signedIn)

        assertFalse(viewModel().uiState.value.alreadySignedIn)
    }
}
