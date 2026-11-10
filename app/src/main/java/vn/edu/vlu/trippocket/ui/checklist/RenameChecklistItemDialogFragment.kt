package vn.edu.vlu.trippocket.ui.checklist

import android.app.Dialog
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.DialogChecklistRenameBinding

/**
 * Renames one checklist item. Reports the new title to [ChecklistFragment] through the Fragment
 * Result API, so it survives rotation; validation happens in the repository.
 */
class RenameChecklistItemDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val binding = DialogChecklistRenameBinding.inflate(layoutInflater)
        if (savedInstanceState == null) {
            binding.etTitle.setText(args.getString(ARG_TITLE))
            binding.etTitle.setSelection(binding.etTitle.length())
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.checklist_rename_title)
            .setView(binding.root)
            .setPositiveButton(R.string.checklist_save) { _, _ -> send(args, binding) }
            .setNegativeButton(R.string.checklist_cancel, null)
            .create()
        binding.etTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                send(args, binding)
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        return dialog
    }

    private fun send(args: Bundle, binding: DialogChecklistRenameBinding) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ITEM_ID, args.getString(ARG_ITEM_ID))
                putString(RESULT_TITLE, binding.etTitle.text?.toString().orEmpty())
            },
        )
    }

    companion object {
        const val TAG = "RenameChecklistItemDialogFragment"
        const val REQUEST_KEY = "request_rename_checklist_item"
        const val RESULT_ITEM_ID = "item_id"
        const val RESULT_TITLE = "title"
        private const val ARG_ITEM_ID = "item_id"
        private const val ARG_TITLE = "title"

        fun newInstance(itemId: String, title: String) = RenameChecklistItemDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ITEM_ID, itemId)
                putString(ARG_TITLE, title)
            }
        }
    }
}
