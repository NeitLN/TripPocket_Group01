package vn.edu.vlu.trippocket.ui.weather

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.MockData
import vn.edu.vlu.trippocket.data.Place
import vn.edu.vlu.trippocket.data.Trip
import vn.edu.vlu.trippocket.databinding.FragmentWeatherBinding
import vn.edu.vlu.trippocket.ui.detail.tripId
import vn.edu.vlu.trippocket.util.Format
import java.time.LocalDateTime

/**
 * Screen 4: place search, saved places and the cached forecast.
 *
 * Milestone 1 simulates the API with MockData so every UI state can be shown:
 * - type a city (accents optional, e.g. "da lat")  -> result dropdown
 * - type something unknown, e.g. "xyz"             -> "No places found"
 * - type "offline"                                  -> search error in the TextInputLayout
 * - pick a place that is not saved yet              -> place is saved, first forecast load
 *                                                      fails, Retry loads it
 */
class WeatherFragment : Fragment(R.layout.fragment_weather) {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private lateinit var trip: Trip
    private val savedPlaces = mutableListOf<Place>()
    private val placesWithCache = mutableSetOf<String>()
    private var selected: Place? = null
    private var lastUpdated: LocalDateTime = MockData.forecastFetchedAt

    private val resultAdapter = PlaceResultAdapter(::onResultPicked)
    private val forecastAdapter = ForecastAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentWeatherBinding.bind(view)
        trip = MockData.trip(tripId) ?: return

        savedPlaces += MockData.savedPlacesOf(trip.tripId)
        placesWithCache += savedPlaces.map { it.name }
        selected = savedPlaces.firstOrNull()

        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = resultAdapter
        binding.rvForecast.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForecast.adapter = forecastAdapter

        binding.etSearch.doAfterTextChanged { onQueryChanged(it?.toString().orEmpty()) }
        binding.btnRefresh.setOnClickListener {
            lastUpdated = LocalDateTime.now()
            renderForecast()
            Snackbar.make(it, R.string.weather_refresh, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnRetry.setOnClickListener {
            selected?.let { place -> placesWithCache += place.name }
            lastUpdated = LocalDateTime.now()
            renderForecast()
        }

        renderChips()
        renderForecast()
    }

    // ----- Search -----

    private fun onQueryChanged(query: String) {
        binding.tilSearch.error = null
        when {
            // A blank query clears the results without any API call.
            query.isBlank() -> showResults(null)
            // Simulated network failure.
            query.trim().equals("offline", ignoreCase = true) -> {
                showResults(null)
                binding.tilSearch.error = getString(R.string.weather_error_network)
            }
            else -> showResults(MockData.searchPlaces(query))
        }
    }

    /** null hides the dropdown; an empty list shows "No places found". */
    private fun showResults(results: List<Place>?) {
        resultAdapter.submitList(results.orEmpty())
        binding.rvResults.isVisible = !results.isNullOrEmpty()
        binding.tvNoResults.isVisible = results != null && results.isEmpty()
    }

    private fun onResultPicked(place: Place) {
        if (savedPlaces.none { it.name == place.name }) {
            savedPlaces += place
            Snackbar.make(binding.root, getString(R.string.weather_saved_toast, place.name),
                Snackbar.LENGTH_SHORT).show()
        }
        selected = place
        binding.etSearch.setText("")
        binding.etSearch.clearFocus()
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        renderChips()
        renderForecast()
    }

    // ----- Saved places + forecast -----

    private fun renderChips() {
        binding.chipGroupPlaces.removeAllViews()
        for (place in savedPlaces) {
            val chip = layoutInflater.inflate(
                R.layout.view_place_chip, binding.chipGroupPlaces, false
            ) as Chip
            chip.text = place.name
            chip.id = View.generateViewId()
            chip.isChecked = place == selected
            chip.setOnClickListener {
                selected = place
                renderForecast()
            }
            binding.chipGroupPlaces.addView(chip)
        }
        val hasPlaces = savedPlaces.isNotEmpty()
        binding.tvSavedLabel.isVisible = hasPlaces
        binding.tvNoPlaces.isVisible = !hasPlaces
    }

    private fun renderForecast() {
        val place = selected
        val hasCache = place != null && place.name in placesWithCache

        binding.groupForecast.isVisible = place != null
        binding.tvForecastError.isVisible = place != null && !hasCache
        binding.btnRetry.isVisible = place != null && !hasCache
        // The badge reminds the user they are looking at stored data.
        binding.tvOfflineBadge.isVisible = hasCache && lastUpdated == MockData.forecastFetchedAt
        if (place == null) return

        binding.tvPlaceName.text = getString(R.string.weather_place_title, place.name, place.country)
        if (!hasCache) {
            binding.tvLastUpdated.text = getString(R.string.weather_not_available)
            binding.tvTodayTemp.text = "--"
            binding.tvTodayCondition.text = ""
            binding.tvTodayRain.text = ""
            forecastAdapter.submitList(emptyList())
            return
        }

        val forecast = MockData.forecastFor(place)
        val today = forecast.first()
        binding.tvLastUpdated.text =
            getString(R.string.weather_last_updated, lastUpdated.format(Format.dayMonthTime))
        binding.ivTodayIcon.setImageResource(today.condition.iconRes)
        binding.tvTodayTemp.text = getString(R.string.weather_temp, today.maxTemp)
        binding.tvTodayCondition.text =
            getString(R.string.weather_condition_low, today.condition.label, today.minTemp)
        binding.tvTodayRain.text = getString(R.string.weather_rain_chance, today.rainChance)

        forecastAdapter.submitList(ForecastRow.build(forecast, trip))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
