package vn.edu.vlu.trippocket.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityLoginBinding
import vn.edu.vlu.trippocket.ui.trips.MainActivity
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/** Screen 1. Validation lives in [LoginViewModel]; this class only renders its state. */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        binding.etEmail.doAfterTextChanged { viewModel.onEmailChanged() }
        binding.etPassword.doAfterTextChanged { viewModel.onPasswordChanged() }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSignIn()
                true
            } else {
                false
            }
        }
        binding.btnSignIn.setOnClickListener { attemptSignIn() }
        binding.btnForgotPassword.setOnClickListener { showNotYetAvailable() }
        binding.btnCreateAccount.setOnClickListener { showNotYetAvailable() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun attemptSignIn() {
        viewModel.signIn(
            email = binding.etEmail.text?.toString().orEmpty(),
            password = binding.etPassword.text?.toString().orEmpty(),
        )
        // Move the cursor to the first field the user has to fix.
        val state = viewModel.uiState.value
        when {
            state.emailError != null -> binding.etEmail.requestFocus()
            state.passwordError != null -> binding.etPassword.requestFocus()
        }
    }

    private fun render(state: LoginUiState) {
        binding.tilEmail.error = state.emailError?.let(::getString)
        binding.tilPassword.error = state.passwordError?.let(::getString)
        if (state.signedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun showNotYetAvailable() {
        Snackbar.make(binding.root, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT).show()
    }
}
