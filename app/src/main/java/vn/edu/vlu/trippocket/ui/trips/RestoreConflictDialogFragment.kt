package vn.edu.vlu.trippocket.ui.trips

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateUtils
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R

/**
 * A4 (proposal 6.2): trips that differ between this phone and the backup. Ticked trips take the
 * backup version; the rest keep the phone version. Nothing is ticked at first, so a quick
 * "Restore" never overwrites what is on the phone. The list comes from RestoreViewModel, so it
 * survives a rotation; the ticks are saved with the dialog.
 */
class RestoreConflictDialogFragment : DialogFragment() {

    private val viewModel: RestoreViewModel by activityViewModels { RestoreViewModel.Factory }

    private lateinit var checked: BooleanArray

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val conflicts = viewModel.uiState.value.conflicts
        checked = savedInstanceState?.getBooleanArray(KEY_CHECKED)?.takeIf { it.size == conflicts.size }
            ?: BooleanArray(conflicts.size)
        val labels = conflicts.map { conflict ->
            val backupTime = time(conflict.backup.updatedAt)
            val phone = if (conflict.localDeleted) {
                getString(R.string.restore_conflict_phone_deleted)
            } else {
                getString(R.string.restore_conflict_phone, time(conflict.localUpdatedAt))
            }
            getString(R.string.restore_conflict_item, conflict.backup.name, backupTime, phone)
        }.toTypedArray<CharSequence>()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_conflict_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.restore_conflict_confirm) { _, _ ->
                viewModel.choose(conflicts.filterIndexed { i, _ -> checked[i] }.map { it.backup.tripId }.toSet())
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> viewModel.cancel() }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBooleanArray(KEY_CHECKED, checked)
    }

    private fun time(millis: Long): String = DateUtils.formatDateTime(
        requireContext(), millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

    companion object {
        const val TAG = "restore_conflicts"
        private const val KEY_CHECKED = "checked"

        /** Shown while [RestoreUiState.conflicts] has trips, gone otherwise (also after process death). */
        fun sync(fragmentManager: FragmentManager, state: RestoreUiState) {
            val shown = fragmentManager.findFragmentByTag(TAG) as DialogFragment?
            when {
                state.conflicts.isNotEmpty() && shown == null -> RestoreConflictDialogFragment().show(fragmentManager, TAG)
                state.conflicts.isEmpty() && shown != null -> shown.dismissAllowingStateLoss()
            }
        }
    }
}
