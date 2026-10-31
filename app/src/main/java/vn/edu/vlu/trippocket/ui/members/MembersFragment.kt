package vn.edu.vlu.trippocket.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentMembersBinding
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.domain.MemberNameRule
import vn.edu.vlu.trippocket.domain.MemberProblem
import vn.edu.vlu.trippocket.ui.detail.TripDetailViewModel
import vn.edu.vlu.trippocket.ui.detail.TripGate
import vn.edu.vlu.trippocket.ui.detail.tripViewModels

/**
 * Screen for managing trip members (E1, Expenses module).
 * Allows adding new members (capped at 15), renaming, and removing members not in use.
 */
class MembersFragment : Fragment() {

    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MembersViewModel by tripViewModels { MembersViewModel.Factory }
    private val detailViewModel: TripDetailViewModel by activityViewModels { TripDetailViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        childFragmentManager.setFragmentResultListener(
            RenameMemberDialogFragment.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val memberId = bundle.getString(RenameMemberDialogFragment.EXTRA_MEMBER_ID)
                ?: return@setFragmentResultListener
            val name = bundle.getString(RenameMemberDialogFragment.EXTRA_NAME)
                ?: return@setFragmentResultListener
            viewModel.renameMember(memberId, name)
        }

        childFragmentManager.setFragmentResultListener(
            ConfirmDeleteMemberDialogFragment.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val memberId = bundle.getString(ConfirmDeleteMemberDialogFragment.EXTRA_MEMBER_ID)
                ?: return@setFragmentResultListener
            viewModel.deleteMember(memberId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = MemberAdapter(
            onRename = { row ->
                RenameMemberDialogFragment.newInstance(row.member.memberId, row.member.name)
                    .show(childFragmentManager, "rename_member")
            },
            onRemove = { row ->
                ConfirmDeleteMemberDialogFragment.newInstance(row.member.memberId, row.member.name)
                    .show(childFragmentManager, "delete_member")
            },
        )

        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.adapter = adapter

        binding.etNewMember.doAfterTextChanged { binding.tilNewMember.error = null }
        binding.etNewMember.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptAddMember()
                true
            } else {
                false
            }
        }
        binding.btnAddMember.setOnClickListener { attemptAddMember() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    detailViewModel.gate.collect { gate ->
                        if (gate is TripGate.Ready) {
                            binding.stickerMembers.bind(gate.trip.destination, gate.trip.tripId)
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        adapter.submitList(state.members)
                        render(state)
                    }
                }
            }
        }
    }

    private fun render(state: MembersUiState) {
        binding.tvMemberCount.text = resources.getQuantityString(
            R.plurals.members_count,
            state.count,
            state.count,
            Limits.MAX_MEMBERS,
        )

        binding.btnAddMember.isEnabled = state.canAdd
        binding.tilNewMember.isEnabled = state.canAdd
        binding.tilNewMember.helperText = if (state.canAdd) null else getString(R.string.members_error_limit)

        val empty = !state.loading && state.members.isEmpty()
        binding.groupEmpty.isVisible = empty
        binding.rvMembers.isVisible = !empty

        state.message?.let { problem ->
            val text = when (problem) {
                MemberProblem.BLANK_NAME -> getString(R.string.members_error_name_empty)
                MemberProblem.NAME_TOO_LONG -> getString(R.string.members_error_name_too_long)
                MemberProblem.LIMIT -> getString(R.string.members_error_limit)
                MemberProblem.IN_USE -> getString(R.string.members_error_in_use)
                MemberProblem.NOT_FOUND -> getString(R.string.members_error_not_found)
            }
            Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    private fun attemptAddMember() {
        val rawName = binding.etNewMember.text?.toString().orEmpty()
        val problem = MemberNameRule.validate(rawName)
        if (problem != null) {
            binding.tilNewMember.error = when (problem) {
                MemberProblem.BLANK_NAME -> getString(R.string.members_error_name_empty)
                MemberProblem.NAME_TOO_LONG -> getString(R.string.members_error_name_too_long)
                else -> getString(R.string.members_error_name_empty)
            }
            binding.etNewMember.requestFocus()
        } else {
            binding.tilNewMember.error = null
            viewModel.addMember(MemberNameRule.clean(rawName))
            binding.etNewMember.text?.clear()
            binding.etNewMember.clearFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

