package vn.edu.vlu.trippocket.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ItemMemberBinding

/**
 * Adapter for the members list (E1).
 * Displays avatar initials on [R.attr.colorPrimaryContainer], the member name,
 * and whether the member is in use (payer or participant).
 */
class MemberAdapter(
    private val onRename: (MemberRow) -> Unit,
    private val onRemove: (MemberRow) -> Unit,
) : ListAdapter<MemberRow, MemberAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MemberRow) {
            val member = row.member
            binding.tvMemberName.text = member.name
            binding.tvAvatar.text = avatarInitial(member.name)
            binding.tvInUse.isVisible = row.inUse
            binding.btnRemove.isEnabled = !row.inUse

            val context = binding.root.context
            binding.btnRename.contentDescription = context.getString(R.string.members_rename, member.name)
            binding.btnRemove.contentDescription = context.getString(R.string.members_remove, member.name)

            binding.btnRename.setOnClickListener { onRename(row) }
            binding.btnRemove.setOnClickListener { onRemove(row) }
        }
    }

    private companion object {
        fun avatarInitial(name: String): String {
            val words = name.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            return when {
                words.isEmpty() -> ""
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "${words[0].first()}${words[1].first()}".uppercase()
            }
        }

        val DIFF = object : DiffUtil.ItemCallback<MemberRow>() {
            override fun areItemsTheSame(oldItem: MemberRow, newItem: MemberRow): Boolean =
                oldItem.member.memberId == newItem.member.memberId

            override fun areContentsTheSame(oldItem: MemberRow, newItem: MemberRow): Boolean =
                oldItem == newItem
        }
    }
}

