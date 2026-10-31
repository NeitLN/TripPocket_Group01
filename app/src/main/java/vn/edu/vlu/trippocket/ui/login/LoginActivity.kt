package vn.edu.vlu.trippocket.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityLoginBinding
import vn.edu.vlu.trippocket.ui.trips.MainActivity
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/** Screen 1. Validation lives in [LoginViewModel]; this class only renders its state. */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Skip the login form immediately if a session already exists to avoid frame flash
        if (viewModel.uiState.value.alreadySignedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        showSampleStickers()

        binding.etEmail.doAfterTextChanged { viewModel.onEmailChanged() }
        binding.etPassword.doAfterTextChanged { viewModel.onPasswordChanged() }
        binding.etConfirmPassword.doAfterTextChanged { viewModel.onConfirmPasswordChanged() }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && viewModel.uiState.value.mode == AuthMode.SIGN_IN) {
                attemptSubmit()
                true
            } else {
                false
            }
        }
        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSubmit()
                true
            } else {
                false
            }
        }

        binding.btnSignIn.setOnClickListener { attemptSubmit() }
        binding.btnCreateAccount.setOnClickListener { viewModel.switchToRegister() }
        binding.btnSignInInstead.setOnClickListener { viewModel.switchToSignIn() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun attemptSubmit() {
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        if (viewModel.uiState.value.mode == AuthMode.REGISTER) {
            viewModel.register(email = email, password = password, confirmPassword = confirmPassword)
        } else {
            viewModel.signIn(email = email, password = password)
        }

        // Move the cursor to the first field the user has to fix.
        val state = viewModel.uiState.value
        when {
            state.emailError != null -> binding.etEmail.requestFocus()
            state.passwordError != null -> binding.etPassword.requestFocus()
            state.confirmPasswordError != null -> binding.etConfirmPassword.requestFocus()
        }
    }

    private fun render(state: LoginUiState) {
        if (state.signedIn || state.alreadySignedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val isRegister = state.mode == AuthMode.REGISTER
        binding.tvTitle.setText(if (isRegister) R.string.login_create_account else R.string.login_sign_in)
        binding.btnSignIn.setText(if (isRegister) R.string.login_create_account else R.string.login_sign_in)
        binding.tilConfirmPassword.isVisible = isRegister
        binding.etPassword.imeOptions = if (isRegister) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE

        binding.flowCreateAccount.isVisible = !isRegister
        binding.tvNoAccount.isVisible = !isRegister
        binding.btnCreateAccount.isVisible = !isRegister

        binding.flowSignInInstead.isVisible = isRegister
        binding.tvHaveAccount.isVisible = isRegister
        binding.btnSignInInstead.isVisible = isRegister

        binding.tilEmail.error = state.emailError?.let(::getString)
        binding.tilPassword.error = state.passwordError?.let(::getString)
        binding.tilConfirmPassword.error = state.confirmPasswordError?.let(::getString)

        if (state.formError != null) {
            binding.tvAuthError.setText(state.formError)
            binding.tvAuthError.isVisible = true
        } else {
            binding.tvAuthError.isVisible = false
        }

        binding.progressAuth.visibility = if (state.loading) View.VISIBLE else View.INVISIBLE
        binding.btnSignIn.isEnabled = !state.loading
        binding.btnCreateAccount.isEnabled = !state.loading
        binding.btnSignInInstead.isEnabled = !state.loading
    }

    /**
     * Three decorative sample stickers (DESIGN.md). They step aside when the keyboard is open or
     * the font is very large, so the form is never squeezed.
     */
    private fun showSampleStickers() {
        val samples = listOf(
            Triple(binding.sticker1, "Đà Lạt", -7f),
            Triple(binding.sticker2, "Hội An", 5f),
            Triple(binding.sticker3, "Phú Quốc", -3f),
        )
        for ((view, destination, tilt) in samples) {
            view.bind(destination, destination)
            view.rotation = tilt
        }
        val largeFont = resources.configuration.fontScale >= LARGE_FONT_SCALE
        binding.clusterStickers.isVisible = !largeFont
        ViewCompat.setOnApplyWindowInsetsListener(binding.clusterStickers) { view, insets ->
            view.isVisible = !largeFont && !insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }
    }

    private companion object {
        const val LARGE_FONT_SCALE = 1.3f
    }
}
