package vn.edu.vlu.trippocket.ui.trips

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.TripPocketApp
import vn.edu.vlu.trippocket.databinding.ActivityMainBinding
import vn.edu.vlu.trippocket.ui.login.LoginActivity
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/** Hosts Screen 2 (TripListFragment, declared in activity_main.xml). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Same instance as TripListFragment's activityViewModels().
    private val tripListViewModel: TripListViewModel by viewModels { TripListViewModel.Factory }

    // Same instance as RestoreConflictDialogFragment's activityViewModels().
    private val restoreViewModel: RestoreViewModel by viewModels { RestoreViewModel.Factory }

    private var busySnackbar: Snackbar? = null

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
                R.id.action_restore -> {
                    restoreViewModel.restore()
                    true
                }
                R.id.action_sign_out -> {
                    showSignOutConfirmation()
                    true
                }
                else -> false
            }
        }
        // Without google-services.json there is no backup to restore from.
        binding.toolbar.menu.findItem(R.id.action_restore).isVisible =
            !(application as TripPocketApp).container.usesDevAuth

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    restoreViewModel.uiState.collect { state ->
                        binding.toolbar.menu.findItem(R.id.action_restore).isEnabled = !state.busy
                        showBusy(state.busy)
                        RestoreConflictDialogFragment.sync(supportFragmentManager, state)
                    }
                }
                launch { restoreViewModel.messages.collect(::showRestoreMessage) }
            }
        }
    }

    private fun showBusy(busy: Boolean) {
        if (busy && busySnackbar == null) {
            busySnackbar = snackbar(getString(R.string.restore_busy), Snackbar.LENGTH_INDEFINITE).apply { show() }
        } else if (!busy) {
            busySnackbar?.dismiss()
            busySnackbar = null
        }
    }

    private fun showRestoreMessage(message: RestoreMessage) {
        val text = when (message) {
            is RestoreMessage.Done -> restoreSummary(message)
            RestoreMessage.NoBackup -> getString(R.string.restore_no_backup)
            RestoreMessage.Offline -> getString(R.string.restore_offline)
            RestoreMessage.AccountChanged -> getString(R.string.restore_account_changed)
            RestoreMessage.Cancelled -> getString(R.string.restore_cancelled)
            RestoreMessage.Failed -> getString(R.string.restore_failed)
        }
        snackbar(text, Snackbar.LENGTH_LONG).show()
    }

    /** "2 trips restored · 1 kept from this phone": zero counts are left out. */
    private fun restoreSummary(message: RestoreMessage.Done): String {
        val r = message.result
        val parts = listOfNotNull(
            r.restored.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.restore_done_restored, it, it) },
            r.kept.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.restore_done_kept, it, it) },
            r.changedMeanwhile.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.restore_done_changed, it, it) },
            r.rejected.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.restore_done_rejected, it, it) },
        )
        return if (parts.isEmpty()) getString(R.string.restore_up_to_date) else parts.joinToString(" · ")
    }

    /** Above the "New trip" button of TripListFragment, so it never covers it. */
    private fun snackbar(text: String, duration: Int) = Snackbar.make(binding.root, text, duration).apply {
        this@MainActivity.findViewById<View>(R.id.fabNewTrip)?.let { anchorView = it }
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
