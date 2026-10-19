package vn.edu.vlu.trippocket.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.Expense
import vn.edu.vlu.trippocket.data.ExpenseCalculator
import vn.edu.vlu.trippocket.data.Settlement
import vn.edu.vlu.trippocket.data.SplitType
import vn.edu.vlu.trippocket.data.TripMember
import vn.edu.vlu.trippocket.databinding.ItemBalanceBinding
import vn.edu.vlu.trippocket.databinding.ItemExpenseBinding
import vn.edu.vlu.trippocket.databinding.ItemSectionHeaderBinding
import vn.edu.vlu.trippocket.databinding.ItemSettlementBinding
import vn.edu.vlu.trippocket.util.Format

/** "Expenses" mode: one card per expense (item_expense.xml). */
class ExpenseAdapter(
    private val memberNames: Map<String, String>,
) : ListAdapter<Expense, ExpenseAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            val context = binding.root.context
            val payer = memberNames[expense.payerId].orEmpty()
            binding.ivCategory.setImageResource(expense.category.iconRes)
            binding.tvExpenseTitle.text = expense.title
            binding.tvExpenseMeta.text = when (expense.splitType) {
                SplitType.EQUAL -> context.resources.getQuantityString(
                    R.plurals.expenses_paid_by, expense.shares.size, payer, expense.shares.size
                )
                SplitType.CUSTOM -> context.getString(R.string.expenses_paid_by_custom, payer)
            }
            binding.tvExpenseAmount.text = Format.vnd(expense.amount)
            binding.tvExpenseDate.text = expense.spentAt.format(Format.dayMonth)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Expense>() {
            override fun areItemsTheSame(old: Expense, new: Expense) = old.expenseId == new.expenseId
            override fun areContentsTheSame(old: Expense, new: Expense) = old == new
        }
    }
}

/** "Balances" mode, first part: one row per member (item_balance.xml). */
class BalanceAdapter(
    private val members: List<TripMember>,
    private val expenses: List<Expense>,
    private val balances: Map<String, Long>,
) : RecyclerView.Adapter<BalanceAdapter.ViewHolder>() {

    override fun getItemCount() = members.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(members[position])

    inner class ViewHolder(private val binding: ItemBalanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(member: TripMember) {
            val context = binding.root.context
            val balance = balances[member.memberId] ?: 0L
            binding.tvAvatar.text = member.initial
            binding.tvMemberName.text = member.name
            binding.tvBalanceDetail.text = context.getString(
                R.string.expenses_balance_detail,
                Format.vnd(ExpenseCalculator.paidBy(member.memberId, expenses)),
                Format.vnd(ExpenseCalculator.shareOf(member.memberId, expenses)),
            )
            binding.tvBalanceAmount.text = Format.signedVnd(balance)
            val (statusRes, colorRes) = when {
                balance > 0 -> R.string.expenses_gets_back to R.color.balance_positive
                balance < 0 -> R.string.expenses_owes to R.color.balance_negative
                else -> R.string.expenses_settled to R.color.md_on_surface_variant
            }
            binding.tvBalanceStatus.setText(statusRes)
            binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }
}

/** "Balances" mode, title between the balances and the suggested payments. */
class SectionHeaderAdapter(private val title: String) :
    RecyclerView.Adapter<SectionHeaderAdapter.ViewHolder>() {

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.tvSectionTitle.text = title
    }

    class ViewHolder(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}

/** "Balances" mode, second part: suggested payments (item_settlement.xml). */
class SettlementAdapter(
    private val settlements: List<Settlement>,
    private val members: Map<String, TripMember>,
) : RecyclerView.Adapter<SettlementAdapter.ViewHolder>() {

    override fun getItemCount() = settlements.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSettlementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(settlements[position])

    inner class ViewHolder(private val binding: ItemSettlementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(settlement: Settlement) {
            val from = members.getValue(settlement.fromId)
            val to = members.getValue(settlement.toId)
            binding.tvFromAvatar.text = from.initial
            binding.tvToAvatar.text = to.initial
            binding.tvSettlementText.text =
                binding.root.context.getString(R.string.expenses_settlement, from.name, to.name)
            binding.tvSettlementAmount.text = Format.vnd(settlement.amount)
        }
    }
}
