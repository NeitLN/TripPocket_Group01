package vn.edu.vlu.trippocket.ui.checklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentChecklistBinding
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.tripViewModels

/** Packing checklist tab (I2): add, tick, rename and delete items, with Undo after a delete. */
class ChecklistFragment : Fragment() {

    private var _binding: FragmentChecklistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChecklistViewModel by tripViewModels { ChecklistViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChecklistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ChecklistAdapter(
            onToggle = viewModel::setDone,
            onRename = { item ->
                RenameChecklistItemDialogFragment.newInstance(item.itemId, item.title)
                    .show(childFragmentManager, RenameChecklistItemDialogFragment.TAG)
            },
            onDelete = viewModel::delete,
        )
        binding.rvItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvItems.adapter = adapter

        binding.btnAdd.setOnClickListener { addTyped() }
        binding.etNewItem.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTyped()
                true
            } else {
                false
            }
        }
        childFragmentManager.setFragmentResultListener(
            RenameChecklistItemDialogFragment.REQUEST_KEY, viewLifecycleOwner,
        ) { _, result ->
            val itemId = result.getString(RenameChecklistItemDialogFragment.RESULT_ITEM_ID) ?: return@setFragmentResultListener
            viewModel.rename(itemId, result.getString(RenameChecklistItemDialogFragment.RESULT_TITLE).orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        adapter.submitList(state.items)
                        render(state)
                    }
                }
                launch { viewModel.message.collect { it?.let(::show) } }
            }
        }
    }

    private fun addTyped() {
        if (!viewModel.uiState.value.canAdd) return
        viewModel.add(binding.etNewItem.text?.toString().orEmpty())
    }

    private fun render(state: ChecklistUiState) {
        binding.tvCount.text = getString(R.string.checklist_count, state.done, state.total)
        binding.tvCount.isVisible = state.total > 0
        binding.groupEmpty.isVisible = !state.loading && state.items.isEmpty()
        binding.btnAdd.isEnabled = state.canAdd
        binding.tilNewItem.isEnabled = state.canAdd
        binding.tilNewItem.helperText = if (state.canAdd) {
            null
        } else {
            getString(R.string.checklist_at_limit, state.total, Limits.MAX_CHECKLIST_ITEMS)
        }
    }

    private fun show(message: ChecklistMessage) {
        when (message) {
            ChecklistMessage.Added -> {
                binding.etNewItem.text = null
                binding.tilNewItem.error = null
            }
            is ChecklistMessage.Problem -> binding.tilNewItem.error = getString(message.text)
            is ChecklistMessage.Deleted ->
                Snackbar.make(binding.root, getString(R.string.checklist_deleted, message.item.title), Snackbar.LENGTH_LONG)
                    .setAction(R.string.checklist_undo) { viewModel.undoDelete(message.item) }
                    .show()
        }
        viewModel.messageShown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
