package vn.edu.vlu.trippocket.ui.detail

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R

/** A2: confirms deleting a trip; reports back to TripDetailActivity through the Fragment Result API. */
class ConfirmDeleteTripDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString(ARG_NAME).orEmpty()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.trip_delete_confirm_title, name))
            .setMessage(R.string.trip_delete_confirm_message)
            .setPositiveButton(R.string.trip_action_delete) { _, _ ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to true))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        const val TAG = "ConfirmDeleteTripDialogFragment"
        const val REQUEST_KEY = "request_delete_trip"
        const val RESULT_CONFIRMED = "confirmed"
        private const val ARG_NAME = "name"

        fun newInstance(name: String): ConfirmDeleteTripDialogFragment =
            ConfirmDeleteTripDialogFragment().apply { arguments = bundleOf(ARG_NAME to name) }
    }
}
