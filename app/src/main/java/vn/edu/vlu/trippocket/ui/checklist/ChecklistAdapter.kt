package vn.edu.vlu.trippocket.ui.checklist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.ChecklistItem
import vn.edu.vlu.trippocket.databinding.ItemChecklistBinding

/** Rows of the packing list (item_checklist.xml). */
class ChecklistAdapter(
    private val onToggle: (ChecklistItem, Boolean) -> Unit,
    private val onRename: (ChecklistItem) -> Unit,
    private val onDelete: (ChecklistItem) -> Unit,
) : ListAdapter<ChecklistItem, ChecklistAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemChecklistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemChecklistBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChecklistItem) {
            val context = binding.root.context
            // Detach the listener first: setting the state of a recycled row is not a user tap.
            binding.cbItem.setOnCheckedChangeListener(null)
            binding.cbItem.text = item.title
            binding.cbItem.isChecked = item.isDone
            binding.cbItem.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }
            binding.btnEdit.contentDescription = context.getString(R.string.checklist_edit_desc, item.title)
            binding.btnDelete.contentDescription = context.getString(R.string.checklist_delete_desc, item.title)
            binding.btnEdit.setOnClickListener { onRename(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChecklistItem>() {
            override fun areItemsTheSame(old: ChecklistItem, new: ChecklistItem) = old.itemId == new.itemId
            override fun areContentsTheSame(old: ChecklistItem, new: ChecklistItem) = old == new
        }
    }
}
