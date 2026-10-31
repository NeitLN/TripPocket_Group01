package vn.edu.vlu.trippocket.ui.trips

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityMainBinding
import vn.edu.vlu.trippocket.ui.login.LoginActivity
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/** Hosts Screen 2 (TripListFragment, declared in activity_main.xml). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Same instance as TripListFragment's activityViewModels().
    private val tripListViewModel: TripListViewModel by viewModels { TripListViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_empty -> {
                    tripListViewModel.toggleEmptyPreview()
                    true
                }
                R.id.action_sign_out -> {
                    showSignOutConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_out_dialog_title)
            .setMessage(R.string.sign_out_dialog_message)
            .setPositiveButton(R.string.action_sign_out) { _, _ ->
                // signOut() updates currentUid on Main.immediate so LoginActivity starts with currentUid = null.
                tripListViewModel.signOut()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
