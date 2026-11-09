package vn.edu.vlu.trippocket.ui.ideas

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.TripIdea
import vn.edu.vlu.trippocket.databinding.ItemTripIdeaBinding
import vn.edu.vlu.trippocket.util.Format
import vn.edu.vlu.trippocket.util.clearRemote
import vn.edu.vlu.trippocket.util.loadRemote

/**
 * Trip idea cards (item_trip_idea.xml). The cover is a remote URL loaded asynchronously by Glide
 * with a placeholder, an error drawable and Glide's memory + disk cache (util/ImageLoading.kt).
 */
class TripIdeaAdapter(
    private val onPlan: (TripIdea) -> Unit,
    private val onEdit: (TripIdea) -> Unit,
    private val onDelete: (TripIdea) -> Unit,
) : ListAdapter<TripIdeaCard, TripIdeaAdapter.IdeaHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        IdeaHolder(ItemTripIdeaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: IdeaHolder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: IdeaHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    inner class IdeaHolder(private val binding: ItemTripIdeaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: TripIdeaCard) {
            val context = binding.root.context
            val idea = card.idea

            binding.ivCover.loadRemote(idea.coverUrl, placeholder = R.drawable.ic_image, error = R.drawable.ic_broken_image)
            binding.ivCover.contentDescription = context.getString(R.string.ideas_cover_description, idea.destination.ifEmpty { idea.title })

            binding.tvIdeaTitle.text = idea.title
            val days = context.resources.getQuantityString(R.plurals.trip_days, idea.days, idea.days)
            binding.tvIdeaMeta.text =
                if (idea.destination.isEmpty()) days else context.getString(R.string.ideas_meta, idea.destination, days)
            binding.tvIdeaBudget.text =
                if (idea.budgetVnd > 0) context.getString(R.string.ideas_budget, Format.vnd(idea.budgetVnd))
                else context.getString(R.string.ideas_budget_unknown)
            binding.tvIdeaSummary.text = idea.summary
            binding.tvIdeaSummary.isVisible = idea.summary.isNotEmpty()
            binding.tvIdeaAuthor.text = when {
                card.mine -> context.getString(R.string.ideas_by_you)
                idea.author.isNotEmpty() -> context.getString(R.string.ideas_by, idea.author)
                else -> context.getString(R.string.ideas_by_someone)
            }

            binding.btnPlan.setOnClickListener { onPlan(idea) }
            // Only the account that shared the idea may change it (PUT) or remove it (DELETE).
            binding.btnOptions.isVisible = card.mine
            binding.btnOptions.contentDescription = context.getString(R.string.ideas_options, idea.title)
            binding.btnOptions.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    inflate(R.menu.menu_idea_options)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit_idea -> onEdit(idea)
                            R.id.action_delete_idea -> onDelete(idea)
                        }
                        true
                    }
                }.show()
            }
        }

        fun recycle() = binding.ivCover.clearRemote()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TripIdeaCard>() {
            override fun areItemsTheSame(old: TripIdeaCard, new: TripIdeaCard) = old.idea.id == new.idea.id
            override fun areContentsTheSame(old: TripIdeaCard, new: TripIdeaCard) = old == new
        }
    }
}
