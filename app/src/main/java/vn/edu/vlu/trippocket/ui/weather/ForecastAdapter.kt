package vn.edu.vlu.trippocket.ui.weather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.ForecastDay
import vn.edu.vlu.trippocket.data.Trip
import vn.edu.vlu.trippocket.databinding.ItemForecastDayBinding
import vn.edu.vlu.trippocket.util.Format
import java.time.LocalDate

/** One forecast row; [forecast] is null for a trip day beyond the 16-day range. */
data class ForecastRow(val date: LocalDate, val tripDay: Int?, val forecast: ForecastDay?) {
    companion object {
        fun build(forecast: List<ForecastDay>, trip: Trip): List<ForecastRow> {
            fun tripDayOf(date: LocalDate): Int? =
                if (date < trip.startDate || date > trip.endDate) null
                else (date.toEpochDay() - trip.startDate.toEpochDay()).toInt() + 1

            val rows = forecast.map { ForecastRow(it.date, tripDayOf(it.date), it) }
            // Trip days after the last forecast day: "Forecast not available yet".
            val lastDate = forecast.last().date
            val missing = (0 until trip.dayCount)
                .map(trip::dateOfDay)
                .filter { it > lastDate }
                .map { ForecastRow(it, tripDayOf(it), null) }
            return rows + missing
        }
    }
}

class ForecastAdapter : ListAdapter<ForecastRow, ForecastAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemForecastDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemForecastDayBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ForecastRow) {
            val context = binding.root.context
            binding.tvDayName.text = row.date.format(Format.weekday)
            binding.tvDate.text = row.date.format(Format.dayMonth)
            binding.tvTripDay.isVisible = row.tripDay != null
            row.tripDay?.let { binding.tvTripDay.text = context.getString(R.string.weather_trip_day, it) }

            val day = row.forecast
            binding.groupForecastValues.isVisible = day != null
            binding.tvNotAvailable.isVisible = day == null
            if (day != null) {
                binding.ivCondition.setImageResource(day.condition.iconRes)
                binding.ivCondition.contentDescription = day.condition.label
                binding.tvRain.text = context.getString(R.string.weather_rain_chance, day.rainChance)
                binding.tvTempRange.text =
                    context.getString(R.string.weather_temp_range, day.maxTemp, day.minTemp)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ForecastRow>() {
            override fun areItemsTheSame(old: ForecastRow, new: ForecastRow) = old.date == new.date
            override fun areContentsTheSame(old: ForecastRow, new: ForecastRow) = old == new
        }
    }
}
