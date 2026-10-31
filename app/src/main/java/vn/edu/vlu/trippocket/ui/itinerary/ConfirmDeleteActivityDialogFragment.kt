package vn.edu.vlu.trippocket.ui.itinerary

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R

/**
 * Confirms deletion of an itinerary activity. Uses Fragment Result API to report back
 * to [ActivityFormFragment].
 */
class ConfirmDeleteActivityDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = arguments?.getString(ARG_TITLE).orEmpty()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.itinerary_delete_confirm_title, title))
            .setMessage(R.string.itinerary_delete_confirm_message)
            .setPositiveButton(R.string.itinerary_delete) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(RESULT_CONFIRMED, true) },
                )
            }
            .setNegativeButton(R.string.itinerary_cancel, null)
            .create()
    }

    companion object {
        const val TAG = "ConfirmDeleteActivityDialogFragment"
        const val REQUEST_KEY = "request_delete_activity"
        const val RESULT_CONFIRMED = "confirmed"
        private const val ARG_TITLE = "title"

        fun newInstance(title: String): ConfirmDeleteActivityDialogFragment =
            ConfirmDeleteActivityDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
            }
    }
}
