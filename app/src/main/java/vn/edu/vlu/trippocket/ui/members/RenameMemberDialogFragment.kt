package vn.edu.vlu.trippocket.ui.members

import android.app.Dialog
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.DialogMemberNameBinding
import vn.edu.vlu.trippocket.domain.MemberNameRule
import vn.edu.vlu.trippocket.domain.MemberProblem

/**
 * Dialog to rename a trip member (E1).
 * Uses [MemberNameRule.validate] to show errors directly in the TextInputLayout.
 * Delivers results via the Fragment Result API to parentFragmentManager.
 */
class RenameMemberDialogFragment : DialogFragment() {

    private var _binding: DialogMemberNameBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMemberNameBinding.inflate(layoutInflater)
        val memberId = requireArguments().getString(ARG_MEMBER_ID).orEmpty()
        val currentName = requireArguments().getString(ARG_CURRENT_NAME).orEmpty()

        if (savedInstanceState == null) {
            binding.etName.setText(currentName)
            binding.etName.setSelection(binding.etName.text?.length ?: 0)
        }

        binding.etName.doAfterTextChanged { binding.tilName.error = null }
        binding.etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSave(memberId)
                true
            } else {
                false
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.members_rename, currentName))
            .setView(binding.root)
            .setPositiveButton(R.string.expense_save, null)
            .setNegativeButton(R.string.expense_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                attemptSave(memberId)
            }
        }

        return dialog
    }

    private fun attemptSave(memberId: String) {
        val rawName = binding.etName.text?.toString().orEmpty()
        val problem = MemberNameRule.validate(rawName)
        if (problem != null) {
            binding.tilName.error = when (problem) {
                MemberProblem.BLANK_NAME -> getString(R.string.members_error_name_empty)
                MemberProblem.NAME_TOO_LONG -> getString(R.string.members_error_name_too_long)
                else -> getString(R.string.members_error_name_empty)
            }
        } else {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply {
                    putString(EXTRA_MEMBER_ID, memberId)
                    putString(EXTRA_NAME, MemberNameRule.clean(rawName))
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
        const val REQUEST_KEY = "request_rename_member"
        const val EXTRA_MEMBER_ID = "extra_member_id"
        const val EXTRA_NAME = "extra_name"

        private const val ARG_MEMBER_ID = "arg_member_id"
        private const val ARG_CURRENT_NAME = "arg_current_name"

        fun newInstance(memberId: String, currentName: String): RenameMemberDialogFragment =
            RenameMemberDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEMBER_ID, memberId)
                    putString(ARG_CURRENT_NAME, currentName)
                }
            }
    }
}

