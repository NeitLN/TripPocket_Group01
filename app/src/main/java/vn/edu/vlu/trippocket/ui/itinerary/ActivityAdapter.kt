package vn.edu.vlu.trippocket.ui.itinerary

import android.view.LayoutInflater
import android.view.ViewGroup
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
) : ListAdapter<ItineraryActivity, ActivityAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onItemClick,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        private val binding: ItemActivityBinding,
        private val onItemClick: (ItineraryActivity) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: ItineraryActivity) {
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
