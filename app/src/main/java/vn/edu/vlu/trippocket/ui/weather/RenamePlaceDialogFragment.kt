package vn.edu.vlu.trippocket.ui.weather

import android.app.Dialog
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.DialogPlaceNameBinding
import vn.edu.vlu.trippocket.domain.Limits

/**
 * Dialog to rename a saved place (WP).
 * Uses [layoutInflater] directly and validates non-blank + length <= [Limits.MAX_TEXT].
 * Delivers results via the Fragment Result API to parentFragmentManager.
 */
class RenamePlaceDialogFragment : DialogFragment() {

    private var _binding: DialogPlaceNameBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPlaceNameBinding.inflate(layoutInflater)
        val placeId = requireArguments().getString(ARG_PLACE_ID).orEmpty()
        val currentName = requireArguments().getString(ARG_CURRENT_NAME).orEmpty()

        if (savedInstanceState == null) {
            binding.etName.setText(currentName)
            binding.etName.setSelection(binding.etName.text?.length ?: 0)
        }

        binding.etName.doAfterTextChanged { binding.tilName.error = null }
        binding.etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSave(placeId)
                true
            } else {
                false
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.weather_place_rename_title, currentName))
            .setView(binding.root)
            .setPositiveButton(R.string.weather_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                attemptSave(placeId)
            }
        }

        return dialog
    }

    private fun attemptSave(placeId: String) {
        val rawName = binding.etName.text?.toString().orEmpty()
        val trimmed = rawName.trim()
        when {
            trimmed.isEmpty() -> {
                binding.tilName.error = getString(R.string.weather_place_name_blank)
            }
            trimmed.length > Limits.MAX_TEXT -> {
                binding.tilName.error = getString(R.string.weather_place_name_long)
            }
            else -> {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply {
                        putString(EXTRA_PLACE_ID, placeId)
                        putString(EXTRA_NAME, trimmed)
                    },
                )
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "request_rename_place"
        const val EXTRA_PLACE_ID = "extra_place_id"
        const val EXTRA_NAME = "extra_name"

        private const val ARG_PLACE_ID = "arg_place_id"
        private const val ARG_CURRENT_NAME = "arg_current_name"

        fun newInstance(placeId: String, currentName: String): RenamePlaceDialogFragment =
            RenamePlaceDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLACE_ID, placeId)
                    putString(ARG_CURRENT_NAME, currentName)
                }
            }
    }
}

