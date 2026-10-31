package vn.edu.vlu.trippocket.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.SplitType
import vn.edu.vlu.trippocket.databinding.ItemBalanceBinding
import vn.edu.vlu.trippocket.databinding.ItemExpenseBinding
import vn.edu.vlu.trippocket.databinding.ItemSectionHeaderBinding
import vn.edu.vlu.trippocket.databinding.ItemSettlementBinding
import vn.edu.vlu.trippocket.util.Format

/** "Expenses" mode: one card per expense (item_expense.xml). */
class ExpenseAdapter(
    private val onClick: (ExpenseRow) -> Unit,
) : ListAdapter<ExpenseRow, ExpenseAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ExpenseRow) {
            binding.cardExpense.setOnClickListener { onClick(row) }
            val context = binding.root.context
            val expense = row.expense
            binding.ivCategory.setImageResource(expense.category.iconRes)
            binding.tvExpenseTitle.text = expense.title
            binding.tvExpenseMeta.text = when (expense.splitType) {
                SplitType.EQUAL -> context.resources.getQuantityString(
                    R.plurals.expenses_paid_by, expense.shares.size, row.payerName, expense.shares.size
                )
                SplitType.CUSTOM -> context.getString(R.string.expenses_paid_by_custom, row.payerName)
            }
            binding.tvExpenseAmount.text = Format.vnd(expense.amount)
            binding.tvExpenseDate.text = expense.spentAt.format(Format.dayMonth)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ExpenseRow>() {
            override fun areItemsTheSame(old: ExpenseRow, new: ExpenseRow) =
                old.expense.expenseId == new.expense.expenseId

            override fun areContentsTheSame(old: ExpenseRow, new: ExpenseRow) = old == new
        }
    }
}

/** "Balances" mode, first part: one row per member (item_balance.xml). */
class BalanceAdapter : ListAdapter<MemberBalance, BalanceAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemBalanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MemberBalance) {
            val context = binding.root.context
            binding.tvAvatar.text = row.member.initial
            binding.tvMemberName.text = row.member.name
            binding.tvBalanceDetail.text = context.getString(
                R.string.expenses_balance_detail, Format.vnd(row.paid), Format.vnd(row.share)
            )
            binding.tvBalanceAmount.text = Format.signedVnd(row.balance)
            val (statusRes, colorRes) = when {
                row.balance > 0 -> R.string.expenses_gets_back to R.color.balance_positive
                row.balance < 0 -> R.string.expenses_owes to R.color.balance_negative
                else -> R.string.expenses_settled to R.color.md_on_surface_variant
            }
            binding.tvBalanceStatus.setText(statusRes)
            binding.tvBalanceAmount.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MemberBalance>() {
            override fun areItemsTheSame(old: MemberBalance, new: MemberBalance) =
                old.member.memberId == new.member.memberId

            override fun areContentsTheSame(old: MemberBalance, new: MemberBalance) = old == new
        }
    }
}

/** "Balances" mode, title between the balances and the suggested payments. */
class SectionHeaderAdapter(private val title: String) :
    RecyclerView.Adapter<SectionHeaderAdapter.ViewHolder>() {

    /** Hidden (no item) when there is nothing to settle. */
    var isVisible = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    override fun getItemCount() = if (isVisible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.tvSectionTitle.text = title
    }

    class ViewHolder(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}

/** "Balances" mode, second part: suggested payments (item_settlement.xml). */
class SettlementAdapter : ListAdapter<SettlementRow, SettlementAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSettlementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemSettlementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SettlementRow) {
            binding.tvFromAvatar.text = row.from.initial
            binding.tvToAvatar.text = row.to.initial
            binding.tvSettlementText.text = binding.root.context.getString(
                R.string.expenses_settlement, row.from.name, row.to.name
            )
            binding.tvSettlementAmount.text = Format.vnd(row.amount)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SettlementRow>() {
            override fun areItemsTheSame(old: SettlementRow, new: SettlementRow) =
                old.from.memberId == new.from.memberId && old.to.memberId == new.to.memberId

            override fun areContentsTheSame(old: SettlementRow, new: SettlementRow) = old == new
        }
    }
}
