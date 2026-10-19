package vn.edu.vlu.trippocket.ui.trips

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.MockData
import vn.edu.vlu.trippocket.data.Trip
import vn.edu.vlu.trippocket.databinding.ItemTripBinding
import vn.edu.vlu.trippocket.util.Format
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TripAdapter(
    private val onClick: (Trip) -> Unit,
) : ListAdapter<Trip, TripAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemTripBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(trip: Trip) {
            val context = binding.root.context
            binding.tvTripName.text = trip.name
            binding.tvDestination.text = trip.destination
            binding.tvDates.text = Format.dateRange(trip.startDate, trip.endDate)

            binding.chipSync.setText(trip.syncStatus.labelRes)
            binding.chipSync.setChipIconResource(trip.syncStatus.iconRes)

            val today = LocalDate.now()
            binding.tvCountdown.text = when {
                today.isBefore(trip.startDate) -> {
                    val days = ChronoUnit.DAYS.between(today, trip.startDate).toInt()
                    context.resources.getQuantityString(R.plurals.trip_starts_in, days, days)
                }
                today.isAfter(trip.endDate) -> context.getString(R.string.trip_finished)
                else -> context.getString(R.string.trip_ongoing)
            }

            val memberCount = MockData.membersOf(trip.tripId).size
            binding.tvMembers.text =
                context.resources.getQuantityString(R.plurals.trip_members, memberCount, memberCount)
            binding.tvSpent.text =
                context.getString(R.string.trip_spent, Format.vnd(MockData.spentTotal(trip.tripId)))

            binding.cardTrip.setOnClickListener { onClick(trip) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Trip>() {
            override fun areItemsTheSame(old: Trip, new: Trip) = old.tripId == new.tripId
            override fun areContentsTheSame(old: Trip, new: Trip) = old == new
        }
    }
}
