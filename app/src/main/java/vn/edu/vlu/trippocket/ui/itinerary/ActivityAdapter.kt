package vn.edu.vlu.trippocket.ui.itinerary

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.ItineraryActivity
import vn.edu.vlu.trippocket.databinding.ItemActivityBinding
import vn.edu.vlu.trippocket.util.Format

class ActivityAdapter(
    private val onItemClick: (ItineraryActivity) -> Unit = {},
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
    private val onMoveUp: (ItineraryActivity) -> Unit = {},
    private val onMoveDown: (ItineraryActivity) -> Unit = {},
) : ListAdapter<ItineraryActivity, ActivityAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onItemClick,
        onStartDrag,
        onMoveUp,
        onMoveDown,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), itemCount)

    class ViewHolder(
        private val binding: ItemActivityBinding,
        private val onItemClick: (ItineraryActivity) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        private val onMoveUp: (ItineraryActivity) -> Unit,
        private val onMoveDown: (ItineraryActivity) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(activity: ItineraryActivity, totalCount: Int) {
            binding.root.setOnClickListener { onItemClick(activity) }
            binding.tvTime.text = activity.startTime.format(Format.time)
            binding.ivReminder.isVisible = activity.reminderMinutesBefore != null
            binding.tvActivityTitle.text = activity.title
            binding.tvActivityPlace.text = activity.place
            binding.tvActivityCost.text = if (activity.estimatedCost > 0) {
                Format.vnd(activity.estimatedCost)
            } else {
                binding.root.context.getString(R.string.itinerary_free)
            }
            binding.tvActivityNote.text = activity.note
            binding.tvActivityNote.isVisible = activity.note.isNotBlank()

            val canReorder = totalCount > 1
            binding.ivDragHandle.isVisible = canReorder
            binding.btnActivityMore.isVisible = canReorder

            if (canReorder) {
                // TalkBack users use the "Move up" / "Move down" options in btnActivityMore menu (WCAG 2.2 AA).
                binding.ivDragHandle.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            onStartDrag(this)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.performClick()
                            false
                        }
                        else -> false
                    }
                }

                binding.btnActivityMore.setOnClickListener { anchor ->
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val total = bindingAdapter?.itemCount ?: totalCount
                    val isFirst = pos == 0
                    val isLast = pos == total - 1

                    val popup = PopupMenu(anchor.context, anchor)
                    popup.menuInflater.inflate(R.menu.menu_activity_options, popup.menu)
                    popup.menu.findItem(R.id.action_move_up)?.isVisible = !isFirst
                    popup.menu.findItem(R.id.action_move_down)?.isVisible = !isLast
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_move_up -> {
                                onMoveUp(activity)
                                true
                            }
                            R.id.action_move_down -> {
                                onMoveDown(activity)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            } else {
                binding.ivDragHandle.setOnTouchListener(null)
                binding.btnActivityMore.setOnClickListener(null)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ItineraryActivity>() {
            override fun areItemsTheSame(old: ItineraryActivity, new: ItineraryActivity) =
                old.activityId == new.activityId

            override fun areContentsTheSame(old: ItineraryActivity, new: ItineraryActivity) = old == new
        }
    }
}
