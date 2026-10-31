package vn.edu.vlu.trippocket.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.databinding.FragmentExpenseFormBinding
import vn.edu.vlu.trippocket.databinding.ItemSplitMemberBinding
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import vn.edu.vlu.trippocket.util.Format
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** E2: add or edit an expense. The draft lives in [ExpenseFormViewModel], so rotation keeps it. */
class ExpenseFormFragment : Fragment() {

    private var _binding: FragmentExpenseFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseFormViewModel by tripViewModels { ExpenseFormViewModel.Factory }

    /** memberId -> its row in splitContainer; rebuilt with the view. */
    private val rows = linkedMapOf<String, ItemSplitMemberBinding>()
    private var fieldsFilled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExpenseFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fieldsFilled = false

        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            categoryOf(checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener)?.let(viewModel::setCategory)
        }
        binding.toggleSplit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.setSplitType(if (checkedId == R.id.btnSplitCustom) SplitType.CUSTOM else SplitType.EQUAL)
        }
        binding.btnDate.setOnClickListener { showDatePicker() }
        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        // A picker restored after rotation needs its listener again.
        (childFragmentManager.findFragmentByTag(TAG_DATE) as? MaterialDatePicker<*>)?.let(::listenTo)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ExpenseFormState) {
        if (state.done) {
            findNavController().navigateUp()
            return
        }
        binding.tvFormTitle.setText(if (state.isEdit) R.string.expense_form_edit else R.string.expense_form_add)
        binding.btnDelete.isVisible = state.isEdit
        if (state.loading) return

        if (!fieldsFilled) fillFields(state)

        binding.tilTitle.error = state.titleError?.let(::getString)
        binding.tilAmount.error = state.amountError?.let(::getString)
        binding.tilPayer.error = state.payerError?.let(::getString)
        binding.actPayer.setText(state.members.firstOrNull { it.memberId == state.payerId }?.name.orEmpty(), false)
        binding.chipGroupCategory.check(chipOf(state.category))
        binding.btnDate.text = state.spentAt.format(DATE)
        binding.toggleSplit.check(if (state.splitType == SplitType.CUSTOM) R.id.btnSplitCustom else R.id.btnSplitEqual)
        renderSplit(state)
        binding.btnSave.isEnabled = !state.saving && state.members.isNotEmpty()

        state.message?.let { message ->
            val text = state.messageArg?.let { getString(message, it) } ?: getString(message)
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    /** Once per view: copy the draft into the text fields, then let the fields drive the draft. */
    private fun fillFields(state: ExpenseFormState) {
        fieldsFilled = true
        binding.etTitle.setText(state.title)
        binding.etAmount.setText(state.amountText)
        binding.etTitle.doAfterTextChanged { viewModel.setTitle(it?.toString().orEmpty()) }
        binding.etAmount.doAfterTextChanged { viewModel.setAmount(it?.toString().orEmpty()) }

        binding.actPayer.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, state.members.map { it.name })
        )
        binding.actPayer.setOnItemClickListener { _, _, position, _ ->
            viewModel.setPayer(state.members[position].memberId)
        }
        buildRows(state)
    }

    private fun buildRows(state: ExpenseFormState) {
        binding.splitContainer.removeAllViews()
        rows.clear()
        if (state.members.isEmpty()) {
            binding.tvSplitStatus.setText(R.string.expenses_need_members)
            return
        }
        for (member in state.members) {
            val row = ItemSplitMemberBinding.inflate(layoutInflater, binding.splitContainer, true)
            row.cbMember.text = member.name
            row.tvName.text = member.name
            row.cbMember.isChecked = member.memberId in state.participants
            row.cbMember.setOnCheckedChangeListener { _, checked -> viewModel.setParticipant(member.memberId, checked) }
            row.etShare.setText(state.customShares[member.memberId].orEmpty())
            row.etShare.doAfterTextChanged { viewModel.setCustomShare(member.memberId, it?.toString().orEmpty()) }
            row.tilShare.contentDescription = getString(R.string.expense_share_hint) + " " + member.name
            rows[member.memberId] = row
        }
    }

    private fun renderSplit(state: ExpenseFormState) {
        if (state.members.isEmpty()) return
        val custom = state.splitType == SplitType.CUSTOM
        val equalShares = state.equalShares
        for ((memberId, row) in rows) {
            row.cbMember.isVisible = !custom
            row.tvName.isVisible = custom
            row.tilShare.isVisible = custom
            row.tvShare.isVisible = !custom
            if (row.cbMember.isChecked != memberId in state.participants) {
                row.cbMember.isChecked = memberId in state.participants
            }
            row.tvShare.text = equalShares[memberId]?.let(Format::vnd).orEmpty()
        }

        val error = state.splitError
        binding.tvSplitStatus.text = when {
            error != null -> getString(error)
            !custom -> equalShares.values.maxOrNull()?.let { getString(R.string.expense_split_equal_status, Format.vnd(it)) }.orEmpty()
            else -> when (val remaining = state.customRemaining) {
                null -> getString(R.string.expense_error_custom_number)
                0L -> getString(R.string.expense_split_balanced)
                in 1..Long.MAX_VALUE -> getString(R.string.expense_split_remaining, Format.vnd(remaining))
                else -> getString(R.string.expense_split_over, Format.vnd(-remaining))
            }
        }
        val bad = error != null || (custom && state.customRemaining.let { it == null || it < 0 })
        binding.tvSplitStatus.setTextColor(
            MaterialColors.getColor(
                binding.tvSplitStatus,
                if (bad) androidx.appcompat.R.attr.colorError else com.google.android.material.R.attr.colorOnSurfaceVariant,
            )
        )
    }

    private fun showDatePicker() {
        if (childFragmentManager.findFragmentByTag(TAG_DATE) != null) return
        val selected = viewModel.state.value.spentAt.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.expense_field_date)
            .setSelection(selected)
            .build()
        listenTo(picker)
        picker.show(childFragmentManager, TAG_DATE)
    }

    private fun listenTo(picker: MaterialDatePicker<*>) {
        picker.addOnPositiveButtonClickListener { selection ->
            val millis = selection as? Long ?: return@addOnPositiveButtonClickListener
            // The picker works in UTC midnights.
            viewModel.setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.expense_delete_confirm_title)
            .setMessage(R.string.expense_delete_confirm_body)
            .setNegativeButton(R.string.expense_cancel, null)
            .setPositiveButton(R.string.expense_delete_confirm_action) { _, _ -> viewModel.delete() }
            .show()
    }

    private fun chipOf(category: ExpenseCategory) = when (category) {
        ExpenseCategory.FOOD -> R.id.chipFood
        ExpenseCategory.STAY -> R.id.chipStay
        ExpenseCategory.TRANSPORT -> R.id.chipTransport
    }

    private fun categoryOf(chipId: Int) = when (chipId) {
        R.id.chipFood -> ExpenseCategory.FOOD
        R.id.chipStay -> ExpenseCategory.STAY
        R.id.chipTransport -> ExpenseCategory.TRANSPORT
        else -> null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rows.clear()
        _binding = null
    }

    private companion object {
        const val TAG_DATE = "expense_date"
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
    }
}
