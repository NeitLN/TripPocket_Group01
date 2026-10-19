package vn.edu.vlu.trippocket.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityLoginBinding
import vn.edu.vlu.trippocket.ui.trips.MainActivity
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/**
 * Screen 1. Milestone 1 only validates the form; Firebase Auth is added in
 * Milestone 2, so a valid form opens the trip list directly.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        // Clear an error as soon as the user starts fixing the field.
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }

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
    }

    private fun attemptSignIn() {
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        val emailError = LoginValidator.emailError(email)
        val passwordError = LoginValidator.passwordError(password)
        binding.tilEmail.error = emailError?.let(::getString)
        binding.tilPassword.error = passwordError?.let(::getString)

        when {
            emailError != null -> binding.etEmail.requestFocus()
            passwordError != null -> binding.etPassword.requestFocus()
            else -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun showNotYetAvailable() {
        Snackbar.make(binding.root, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT).show()
    }
}
