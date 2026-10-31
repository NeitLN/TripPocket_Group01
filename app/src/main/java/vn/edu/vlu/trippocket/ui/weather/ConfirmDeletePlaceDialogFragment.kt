package vn.edu.vlu.trippocket.ui.weather

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R

/**
 * Confirmation dialog before removing a saved place (WP).
 * Delivers results via the Fragment Result API to parentFragmentManager.
 */
class ConfirmDeletePlaceDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val placeId = requireArguments().getString(ARG_PLACE_ID).orEmpty()
        val placeName = requireArguments().getString(ARG_PLACE_NAME).orEmpty()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.weather_place_delete_confirm_title, placeName))
            .setMessage(R.string.weather_place_delete_confirm_body)
            .setPositiveButton(R.string.weather_place_delete_confirm_action) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putString(EXTRA_PLACE_ID, placeId) },
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    companion object {
        const val REQUEST_KEY = "request_delete_place"
        const val EXTRA_PLACE_ID = "extra_place_id"

        private const val ARG_PLACE_ID = "arg_place_id"
        private const val ARG_PLACE_NAME = "arg_place_name"

        fun newInstance(placeId: String, placeName: String): ConfirmDeletePlaceDialogFragment =
            ConfirmDeletePlaceDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLACE_ID, placeId)
                    putString(ARG_PLACE_NAME, placeName)
                }
            }
    }
}

