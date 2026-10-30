package vn.edu.vlu.trippocket.ui.trips

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.SyncStatus
import vn.edu.vlu.trippocket.databinding.ItemTripBinding
import vn.edu.vlu.trippocket.databinding.ItemTripGroupBinding
import vn.edu.vlu.trippocket.domain.TripGroup
import vn.edu.vlu.trippocket.util.Format

/** Grouped trip list: group labels and trip cards (item_trip_group.xml, item_trip.xml). */
class TripAdapter(
    private val onClick: (TripCard) -> Unit,
    private val onRetry: (TripCard) -> Unit,
) : ListAdapter<TripListRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is TripListRow.Header -> TYPE_HEADER
        is TripListRow.Item -> TYPE_TRIP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemTripGroupBinding.inflate(inflater, parent, false))
        } else {
            TripHolder(ItemTripBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is TripListRow.Header -> (holder as HeaderHolder).bind(row.group)
            is TripListRow.Item -> (holder as TripHolder).bind(row.card)
        }
    }

    class HeaderHolder(private val binding: ItemTripGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: TripGroup) {
            binding.tvGroup.setText(
                when (group) {
                    TripGroup.HAPPENING_NOW -> R.string.trip_ongoing
                    TripGroup.UPCOMING -> R.string.trips_group_upcoming
                    TripGroup.PAST -> R.string.trips_group_past
                }
            )
        }
    }

    inner class TripHolder(private val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: TripCard) {
            val context = binding.root.context
            val trip = card.trip
            binding.sticker.bind(trip.destination, trip.tripId)
            binding.tvTripName.text = trip.name
            binding.tvDestination.text = trip.destination
            val days = context.resources.getQuantityString(R.plurals.trip_days, trip.dayCount, trip.dayCount)
            binding.tvDates.text = context.getString(
                R.string.trip_dates_days, Format.dateRange(trip.startDate, trip.endDate), days
            )
            binding.tvMeta.text = context.getString(
                R.string.trip_meta,
                context.resources.getQuantityString(R.plurals.trip_members, card.memberCount, card.memberCount),
                context.getString(R.string.trip_spent, Format.vnd(card.spent)),
            )

            val status = trip.syncStatus
            binding.tvSync.setText(status.labelRes)
            binding.ivSync.setImageResource(status.iconRes)
            val iconColor = MaterialColors.getColor(
                binding.ivSync,
                if (status == SyncStatus.FAILED) androidx.appcompat.R.attr.colorError
                else com.google.android.material.R.attr.colorOnSurfaceVariant,
            )
            binding.ivSync.imageTintList = ColorStateList.valueOf(iconColor)
            binding.tvSyncNote.isVisible = status != SyncStatus.SYNCED
            binding.btnRetry.isVisible = status == SyncStatus.FAILED
            binding.btnRetry.setOnClickListener { onRetry(card) }

            binding.cardTrip.setOnClickListener { onClick(card) }
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TRIP = 1

        private val DIFF = object : DiffUtil.ItemCallback<TripListRow>() {
            override fun areItemsTheSame(old: TripListRow, new: TripListRow) = when {
                old is TripListRow.Header && new is TripListRow.Header -> old.group == new.group
                old is TripListRow.Item && new is TripListRow.Item -> old.card.trip.tripId == new.card.trip.tripId
                else -> false
            }

            override fun areContentsTheSame(old: TripListRow, new: TripListRow) = old == new
        }
    }
}
