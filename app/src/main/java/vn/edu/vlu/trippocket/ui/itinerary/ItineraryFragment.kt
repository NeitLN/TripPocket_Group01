package vn.edu.vlu.trippocket.ui.itinerary

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.MockData
import vn.edu.vlu.trippocket.data.Trip
import vn.edu.vlu.trippocket.databinding.FragmentItineraryBinding
import vn.edu.vlu.trippocket.ui.detail.tripId
import vn.edu.vlu.trippocket.util.Format

/** Screen 3: day-by-day itinerary. */
class ItineraryFragment : Fragment(R.layout.fragment_itinerary) {

    private var _binding: FragmentItineraryBinding? = null
    private val binding get() = _binding!!

    private val adapter = ActivityAdapter()
    private lateinit var trip: Trip

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentItineraryBinding.bind(view)
        trip = MockData.trip(tripId) ?: return

        binding.rvActivities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActivities.adapter = adapter

        for (day in 0 until trip.dayCount) {
            val label = getString(
                R.string.itinerary_day_tab, day + 1, trip.dateOfDay(day).format(Format.dayMonth)
            )
            binding.tabDays.addTab(binding.tabDays.newTab().setText(label), false)
        }
        binding.tabDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showDay(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        val selected = savedInstanceState?.getInt(KEY_DAY) ?: 0
        binding.tabDays.getTabAt(selected)?.select()

        val (done, total) = MockData.checklistOf(trip.tripId)
        binding.btnChecklist.text = getString(R.string.itinerary_checklist, done, total)
        binding.btnChecklist.setOnClickListener { showNotYetAvailable(it) }
        binding.fabAddActivity.setOnClickListener { showNotYetAvailable(it) }
    }

    private fun showDay(dayIndex: Int) {
        val date = trip.dateOfDay(dayIndex)
        val activities = MockData.activitiesOf(trip.tripId, dayIndex)

        binding.tvDayDate.text = date.format(Format.weekdayDayMonth)
        // Weather of the trip's first saved place, if that date is in the cached forecast.
        val forecast = MockData.savedPlacesOf(trip.tripId).firstOrNull()
            ?.let(MockData::forecastFor)
            ?.firstOrNull { it.date == date }
        binding.tvDayWeather.text = if (forecast == null) {
            getString(R.string.weather_not_available)
        } else {
            getString(R.string.weather_temp_range, forecast.maxTemp, forecast.minTemp) + " · " +
                getString(R.string.weather_rain_chance, forecast.rainChance)
        }
        val estimated = activities.sumOf { it.estimatedCost }
        binding.tvDayCost.isVisible = estimated > 0
        binding.tvDayCost.text = getString(R.string.itinerary_estimated, Format.vnd(estimated))

        adapter.submitList(activities)
        binding.rvActivities.isVisible = activities.isNotEmpty()
        binding.groupEmptyDay.isVisible = activities.isEmpty()
    }

    private fun showNotYetAvailable(anchor: View) {
        Snackbar.make(anchor, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.fabAddActivity)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.let { outState.putInt(KEY_DAY, it.tabDays.selectedTabPosition) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val KEY_DAY = "selected_day"
    }
}
