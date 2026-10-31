package vn.edu.vlu.trippocket.ui.expenses

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.ExpenseCategory
import vn.edu.vlu.trippocket.databinding.FragmentExpensesBinding
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.util.Format

/** Screen 5: budget summary, expenses, member balances and suggested payments. */
class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpensesViewModel by tripViewModels { ExpensesViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val expenseAdapter = ExpenseAdapter { row -> openForm(row.expense.expenseId) }
        // A new expense lands at the top (newest first); scroll so the user sees it.
        expenseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) _binding?.rvExpenses?.scrollToPosition(0)
            }
        })
        val balanceAdapter = BalanceAdapter()
        val headerAdapter = SectionHeaderAdapter(getString(R.string.expenses_settle_header))
        val settlementAdapter = SettlementAdapter()
        val balancesModeAdapter = ConcatAdapter(balanceAdapter, headerAdapter, settlementAdapter)

        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.showBalances(checkedId == R.id.btnModeBalances)
        }
        binding.btnShare.setOnClickListener { shareSummary() }
        binding.fabAddExpense.setOnClickListener { fab ->
            val state = viewModel.uiState.value
            when {
                state.loading -> Unit
                // Members come with E1; until then a trip without members cannot split anything.
                state.memberCount == 0 -> showMessage(fab, getString(R.string.expenses_need_members))
                state.atExpenseLimit ->
                    showMessage(fab, getString(R.string.expenses_limit_reached, Limits.MAX_EXPENSES))
                else -> openForm(expenseId = null)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    expenseAdapter.submitList(state.expenses)
                    balanceAdapter.submitList(state.balances)
                    headerAdapter.isVisible = state.settlements.isNotEmpty()
                    settlementAdapter.submitList(state.settlements)
                    val adapter = if (state.showBalances) balancesModeAdapter else expenseAdapter
                    if (binding.rvExpenses.adapter !== adapter) binding.rvExpenses.adapter = adapter
                    render(state)
                }
            }
        }
    }

    private fun render(state: ExpensesUiState) {
        binding.toggleMode.check(if (state.showBalances) R.id.btnModeBalances else R.id.btnModeExpenses)
        val empty = !state.loading && state.expenses.isEmpty()
        binding.rvExpenses.isVisible = !empty
        binding.tvEmptyExpenses.isVisible = empty

        binding.tvSpentTotal.text = Format.vnd(state.spent)
        binding.tvPlanned.text = getString(R.string.expenses_of_planned, Format.vnd(state.planned))
        binding.progressBudget.progress = state.budgetProgress

        fun total(category: ExpenseCategory) = Format.amount(state.categoryTotals[category] ?: 0L)
        binding.tvFoodValue.text = total(ExpenseCategory.FOOD)
        binding.tvStayValue.text = total(ExpenseCategory.STAY)
        binding.tvTransportValue.text = total(ExpenseCategory.TRANSPORT)
    }

    private fun openForm(expenseId: String?) {
        val args = Bundle().apply { putString(ExpenseFormViewModel.ARG_EXPENSE_ID, expenseId) }
        findNavController().navigate(R.id.action_expenses_to_form, args)
    }

    private fun showMessage(anchor: View, text: String) {
        Snackbar.make(anchor, text, Snackbar.LENGTH_SHORT).setAnchorView(anchor).show()
    }

    private fun shareSummary() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, viewModel.uiState.value.tripName)
            .putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
        startActivity(Intent.createChooser(send, getString(R.string.expenses_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
