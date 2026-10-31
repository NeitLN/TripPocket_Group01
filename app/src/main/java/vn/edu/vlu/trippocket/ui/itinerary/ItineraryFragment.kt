package vn.edu.vlu.trippocket.ui.itinerary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentItineraryBinding
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import vn.edu.vlu.trippocket.util.Format

/** Screen 3: day-by-day itinerary. */
class ItineraryFragment : Fragment() {

    private var _binding: FragmentItineraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItineraryViewModel by tripViewModels { ItineraryViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentItineraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ActivityAdapter { activity ->
            findNavController().navigate(
                R.id.action_itinerary_to_form,
                Bundle().apply { putString(ARG_ACTIVITY_ID, activity.activityId) },
            )
        }
        binding.rvActivities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActivities.adapter = adapter

        binding.tabDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.selectDay(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.btnChecklist.setOnClickListener { showNotYetAvailable(it) }
        binding.fabAddActivity.setOnClickListener {
            if (viewModel.uiState.value.canAdd) {
                findNavController().navigate(R.id.action_itinerary_to_form)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.activities)
                    render(state)
                }
            }
        }
    }

    private fun render(state: ItineraryUiState) {
        // The day tabs are built once; later states only move the selection.
        if (binding.tabDays.tabCount != state.days.size) {
            binding.tabDays.removeAllTabs()
            state.days.forEachIndexed { index, date ->
                val label = getString(R.string.itinerary_day_tab, index + 1, date.format(Format.dayMonth))
                binding.tabDays.addTab(binding.tabDays.newTab().setText(label), false)
            }
        }
        if (binding.tabDays.selectedTabPosition != state.selectedDay) {
            binding.tabDays.getTabAt(state.selectedDay)?.select()
        }

        binding.btnChecklist.text =
            getString(R.string.itinerary_checklist, state.checklistDone, state.checklistTotal)

        val date = state.selectedDate ?: return
        binding.tvDayDate.text = date.format(Format.weekdayDayMonth)
        val forecast = state.forecast
        binding.tvDayWeather.text = if (forecast == null) {
            getString(R.string.weather_not_available)
        } else {
            getString(R.string.weather_temp_range, forecast.maxTemp, forecast.minTemp) + " · " +
                getString(R.string.weather_rain_chance, forecast.rainChance)
        }
        binding.tvDayCost.isVisible = state.estimatedCost > 0
        binding.tvDayCost.text = getString(R.string.itinerary_estimated, Format.vnd(state.estimatedCost))

        binding.rvActivities.isVisible = state.activities.isNotEmpty()
        binding.groupEmptyDay.isVisible = state.activities.isEmpty()

        binding.fabAddActivity.isEnabled = state.canAdd
        binding.fabAddActivity.text = if (state.canAdd) {
            getString(R.string.itinerary_add)
        } else {
            getString(R.string.itinerary_limit_count, state.activityCount, Limits.MAX_ACTIVITIES)
        }
    }

    private fun showNotYetAvailable(anchor: View) {
        Snackbar.make(anchor, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.fabAddActivity)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
