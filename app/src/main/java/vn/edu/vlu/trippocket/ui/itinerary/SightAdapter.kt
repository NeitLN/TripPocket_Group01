package vn.edu.vlu.trippocket.ui.itinerary

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.Sight
import vn.edu.vlu.trippocket.databinding.ItemSightBinding
import vn.edu.vlu.trippocket.util.clearRemote
import vn.edu.vlu.trippocket.util.loadRemote
import kotlin.math.roundToInt

/**
 * One Wikipedia sight per row (Milestone 4): photo, name, description and distance, plus the
 * "Add to day" button. The photo is a remote URL loaded by Glide here; a missing or broken one
 * falls back to the app's default images instead of an empty box. The button is off and reads
 * "Added" for a sight already in the day, or "Trip is full" at the activity limit.
 */
class SightAdapter(
    private val onAddToDay: (Sight) -> Unit,
) : ListAdapter<SightRow, SightAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSightBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onAddToDay,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    class ViewHolder(
        private val binding: ItemSightBinding,
        private val onAddToDay: (Sight) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SightRow) {
            val sight = row.sight
            binding.ivSightImage.loadRemote(
                sight.imageUrl,
                R.drawable.ic_image,
                R.drawable.ic_broken_image,
            )
            binding.tvSightTitle.text = sight.title
            binding.tvSightDescription.isVisible = sight.description != null
            binding.tvSightDescription.text = sight.description
            binding.tvSightDistance.text = distanceText(binding.root.context, sight.distanceMeters)
            binding.btnAddToDay.contentDescription =
                binding.root.context.getString(R.string.sights_add_desc, sight.title)
            binding.btnAddToDay.isEnabled = row.addState == AddState.CAN_ADD
            binding.btnAddToDay.setText(
                when (row.addState) {
                    AddState.CAN_ADD -> R.string.sights_add_to_day
                    AddState.ADDED -> R.string.sights_already_added
                    AddState.TRIP_FULL -> R.string.sights_trip_full
                }
            )
            binding.btnAddToDay.setOnClickListener { onAddToDay(sight) }
        }

        /** Stops the download of a row that scrolled away and frees its bitmap. */
        fun clear() = binding.ivSightImage.clearRemote()
    }

    private companion object {
        /** "350 m" up close, "2.4 km" further away, which reads better than 2400 m. */
        fun distanceText(context: Context, meters: Double): String = if (meters.roundToInt() < 1_000) {
            // Compare the rounded value: 999.6 m would otherwise read "1000 m" instead of "1.0 km".
            context.getString(R.string.sights_distance_m, meters.roundToInt())
        } else {
            context.getString(R.string.sights_distance_km, meters / 1_000)
        }

        val DIFF = object : DiffUtil.ItemCallback<SightRow>() {
            override fun areItemsTheSame(old: SightRow, new: SightRow) = old.sight.pageId == new.sight.pageId

            override fun areContentsTheSame(old: SightRow, new: SightRow) = old == new
        }
    }
}
