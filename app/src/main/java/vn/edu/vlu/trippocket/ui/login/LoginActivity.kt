package vn.edu.vlu.trippocket.ui.login

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.snackbar.Snackbar
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
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        showSampleStickers()

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

    private fun showNotYetAvailable() {
        Snackbar.make(binding.root, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT).show()
    }

    private companion object {
        const val LARGE_FONT_SCALE = 1.3f
    }
}
