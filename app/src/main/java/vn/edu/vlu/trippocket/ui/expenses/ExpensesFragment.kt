package vn.edu.vlu.trippocket.ui.expenses

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.Expense
import vn.edu.vlu.trippocket.data.ExpenseCalculator
import vn.edu.vlu.trippocket.data.ExpenseCategory
import vn.edu.vlu.trippocket.data.MockData
import vn.edu.vlu.trippocket.data.Settlement
import vn.edu.vlu.trippocket.data.Trip
import vn.edu.vlu.trippocket.data.TripMember
import vn.edu.vlu.trippocket.databinding.FragmentExpensesBinding
import vn.edu.vlu.trippocket.ui.detail.tripId
import vn.edu.vlu.trippocket.util.Format

/** Screen 5: budget summary, expenses, member balances and suggested payments. */
class ExpensesFragment : Fragment(R.layout.fragment_expenses) {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!

    private lateinit var trip: Trip
    private lateinit var members: List<TripMember>
    private lateinit var expenses: List<Expense>

    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var balancesAdapter: ConcatAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExpensesBinding.bind(view)
        trip = MockData.trip(tripId) ?: return
        members = MockData.membersOf(trip.tripId)
        expenses = MockData.expensesOf(trip.tripId)
        val names = members.associate { it.memberId to it.name }

        expenseAdapter = ExpenseAdapter(names).also { it.submitList(expenses) }

        val balances = ExpenseCalculator.balances(members, expenses)
        val settlements = ExpenseCalculator.settle(balances)
        val balanceAdapter = BalanceAdapter(members, expenses, balances)
        balancesAdapter = if (settlements.isEmpty()) {
            ConcatAdapter(balanceAdapter)
        } else {
            ConcatAdapter(
                balanceAdapter,
                SectionHeaderAdapter(getString(R.string.expenses_settle_header)),
                SettlementAdapter(settlements, members.associateBy { it.memberId }),
            )
        }

        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showMode(checkedId)
        }
        showMode(binding.toggleMode.checkedButtonId)

        renderSummary()
        binding.btnShare.setOnClickListener { shareSummary(names, settlements) }
        binding.fabAddExpense.setOnClickListener {
            Snackbar.make(it, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT)
                .setAnchorView(it)
                .show()
        }
    }

    private fun showMode(checkedId: Int) {
        val balancesMode = checkedId == R.id.btnModeBalances
        binding.rvExpenses.adapter = if (balancesMode) balancesAdapter else expenseAdapter
        val empty = expenses.isEmpty()
        binding.rvExpenses.isVisible = !empty
        binding.tvEmptyExpenses.isVisible = empty
    }

    private fun renderSummary() {
        val spent = expenses.sumOf { it.amount }
        val planned = MockData.estimatedTotal(trip.tripId)
        binding.tvSpentTotal.text = Format.vnd(spent)
        binding.tvPlanned.text = getString(R.string.expenses_of_planned, Format.vnd(planned))
        binding.progressBudget.progress =
            if (planned == 0L) 0 else (spent * 100 / planned).toInt().coerceAtMost(100)

        fun total(category: ExpenseCategory) =
            Format.amount(expenses.filter { it.category == category }.sumOf { it.amount })
        binding.tvFoodValue.text = total(ExpenseCategory.FOOD)
        binding.tvStayValue.text = total(ExpenseCategory.STAY)
        binding.tvTransportValue.text = total(ExpenseCategory.TRANSPORT)
    }

    /** Plain-text summary sent through the Android share sheet. */
    private fun shareSummary(names: Map<String, String>, settlements: List<Settlement>) {
        val text = buildString {
            appendLine("${trip.name} - ${Format.dateRange(trip.startDate, trip.endDate)}")
            appendLine("Total spent: ${Format.vnd(expenses.sumOf { it.amount })}")
            if (settlements.isNotEmpty()) {
                appendLine()
                appendLine("Suggested payments:")
                settlements.forEach {
                    appendLine("- ${names[it.fromId]} pays ${names[it.toId]} ${Format.vnd(it.amount)}")
                }
            }
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, trip.name)
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.expenses_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
