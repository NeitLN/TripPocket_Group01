package vn.edu.vlu.trippocket.ui.weather

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.DialogPlaceNoteBinding
import vn.edu.vlu.trippocket.domain.Limits

/**
 * Dialog to edit the note of a saved place (WP).
 * Uses [layoutInflater] directly. Allows empty note (to clear), validates length <= [Limits.MAX_NOTE].
 * Delivers results via the Fragment Result API to parentFragmentManager.
 */
class EditPlaceNoteDialogFragment : DialogFragment() {

    private var _binding: DialogPlaceNoteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPlaceNoteBinding.inflate(layoutInflater)
        val placeId = requireArguments().getString(ARG_PLACE_ID).orEmpty()
        val placeName = requireArguments().getString(ARG_PLACE_NAME).orEmpty()
        val currentNote = requireArguments().getString(ARG_CURRENT_NOTE).orEmpty()

        if (savedInstanceState == null) {
            binding.etNote.setText(currentNote)
            binding.etNote.setSelection(binding.etNote.text?.length ?: 0)
        }

        binding.etNote.doAfterTextChanged { binding.tilNote.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.weather_place_note_title, placeName))
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
        val rawNote = binding.etNote.text?.toString().orEmpty()
        val trimmed = rawNote.trim()
        if (trimmed.length > Limits.MAX_NOTE) {
            binding.tilNote.error = getString(R.string.weather_place_note_long)
        } else {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply {
                    putString(EXTRA_PLACE_ID, placeId)
                    putString(EXTRA_NOTE, trimmed)
                },
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "request_edit_place_note"
        const val EXTRA_PLACE_ID = "extra_place_id"
        const val EXTRA_NOTE = "extra_note"

        private const val ARG_PLACE_ID = "arg_place_id"
        private const val ARG_PLACE_NAME = "arg_place_name"
        private const val ARG_CURRENT_NOTE = "arg_current_note"

        fun newInstance(placeId: String, placeName: String, currentNote: String): EditPlaceNoteDialogFragment =
            EditPlaceNoteDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLACE_ID, placeId)
                    putString(ARG_PLACE_NAME, placeName)
                    putString(ARG_CURRENT_NOTE, currentNote)
                }
            }
    }
}

