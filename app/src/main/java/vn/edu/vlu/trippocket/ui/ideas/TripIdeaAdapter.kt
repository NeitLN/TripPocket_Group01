package vn.edu.vlu.trippocket.ui.ideas

import android.content.res.Resources
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
 *
 * Stage 4: the row text is formatted once by [withText] when the state is emitted, so
 * onBindViewHolder only assigns strings — no getString/getQuantityString/Format per scroll.
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
            // Already formatted when the list was emitted; the fallback only runs if a caller
            // forgot withText (then it costs one call per bind, still correct).
            val text = card.text ?: card.withText(binding.root.resources).text!!

            binding.ivCover.loadRemote(idea.coverUrl, placeholder = R.drawable.ic_image, error = R.drawable.ic_broken_image)
            binding.ivCover.contentDescription = text.coverDescription

            binding.tvIdeaTitle.text = idea.title
            binding.tvIdeaMeta.text = text.meta
            binding.tvIdeaBudget.text = text.budget
            binding.tvIdeaSummary.text = idea.summary
            binding.tvIdeaSummary.isVisible = idea.summary.isNotEmpty()
            binding.tvIdeaAuthor.text = text.author

            binding.btnPlan.setOnClickListener { onPlan(idea) }
            // Only the account that shared the idea may change it (PUT) or remove it (DELETE).
            binding.btnOptions.isVisible = card.mine
            binding.btnOptions.contentDescription = text.optionsDescription
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

/**
 * Stage 4: fills [TripIdeaCard.text] once per emitted list, on the main thread in the Activity
 * (the ViewModel has no Context). Bind then only calls setText; formatting is not repeated while
 * scrolling a recycled row.
 */
fun TripIdeaCard.withText(resources: Resources): TripIdeaCard {
    val days = resources.getQuantityString(R.plurals.trip_days, idea.days, idea.days)
    return copy(
        text = IdeaCardText(
            meta = if (idea.destination.isEmpty()) days
            else resources.getString(R.string.ideas_meta, idea.destination, days),
            budget = if (idea.budgetVnd > 0) resources.getString(R.string.ideas_budget, Format.vnd(idea.budgetVnd))
            else resources.getString(R.string.ideas_budget_unknown),
            author = when {
                mine -> resources.getString(R.string.ideas_by_you)
                idea.author.isNotEmpty() -> resources.getString(R.string.ideas_by, idea.author)
                else -> resources.getString(R.string.ideas_by_someone)
            },
            coverDescription = resources.getString(
                R.string.ideas_cover_description,
                idea.destination.ifEmpty { idea.title },
            ),
            optionsDescription = resources.getString(R.string.ideas_options, idea.title),
        ),
    )
}
