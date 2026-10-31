package vn.edu.vlu.trippocket.ui.members

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.vlu.trippocket.R

/**
 * Confirmation dialog before removing a trip member (E1).
 * Delivers results via the Fragment Result API to parentFragmentManager.
 */
class ConfirmDeleteMemberDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val memberId = requireArguments().getString(ARG_MEMBER_ID).orEmpty()
        val name = requireArguments().getString(ARG_NAME).orEmpty()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.members_delete_confirm_title, name))
            .setMessage(R.string.members_delete_confirm_body)
            .setPositiveButton(R.string.members_delete_confirm_action) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putString(EXTRA_MEMBER_ID, memberId) },
                )
            }
            .setNegativeButton(R.string.expense_cancel, null)
            .create()
    }

    companion object {
        const val REQUEST_KEY = "request_delete_member"
        const val EXTRA_MEMBER_ID = "extra_member_id"

        private const val ARG_MEMBER_ID = "arg_member_id"
        private const val ARG_NAME = "arg_name"

        fun newInstance(memberId: String, name: String): ConfirmDeleteMemberDialogFragment =
            ConfirmDeleteMemberDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEMBER_ID, memberId)
                    putString(ARG_NAME, name)
                }
            }
    }
}