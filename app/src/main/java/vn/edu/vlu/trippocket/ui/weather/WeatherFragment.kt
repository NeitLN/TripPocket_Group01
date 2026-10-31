package vn.edu.vlu.trippocket.ui.weather

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.databinding.FragmentWeatherBinding
import vn.edu.vlu.trippocket.databinding.ViewPlaceChipBinding
import vn.edu.vlu.trippocket.util.Format

/** Screen 4: place search, saved places and the cached forecast (see [WeatherViewModel]). */
class WeatherFragment : Fragment() {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WeatherViewModel by tripViewModels { WeatherViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWeatherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val resultAdapter = PlaceResultAdapter(::onResultPicked)
        val forecastAdapter = ForecastAdapter()
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = resultAdapter
        binding.rvForecast.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForecast.adapter = forecastAdapter

        binding.etSearch.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.btnRefresh.setOnClickListener {
            viewModel.refresh()
            Snackbar.make(it, R.string.weather_refresh, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnRetry.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.search.collect { state ->
                        resultAdapter.submitList((state as? SearchState.Results)?.places.orEmpty())
                        renderSearch(state)
                    }
                }
                launch { viewModel.places.collect(::renderPlaces) }
                launch {
                    viewModel.forecast.collect { state ->
                        forecastAdapter.submitList(state.rows)
                        renderForecast(state)
                    }
                }
            }
        }
    }

    private fun onResultPicked(place: Place) {
        viewModel.pickPlace(place)
        binding.etSearch.setText("")
        binding.etSearch.clearFocus()
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun renderSearch(state: SearchState) {
        val results = (state as? SearchState.Results)?.places.orEmpty()
        binding.rvResults.isVisible = results.isNotEmpty()
        binding.tvNoResults.isVisible = state is SearchState.Empty
        binding.tilSearch.error =
            if (state is SearchState.Error) getString(R.string.weather_error_network) else null
    }

    private fun renderPlaces(state: SavedPlacesUiState) {
        binding.chipGroupPlaces.removeAllViews()
        for (place in state.places) {
            val chip = ViewPlaceChipBinding.inflate(layoutInflater, binding.chipGroupPlaces, false).root
            chip.text = place.name
            chip.id = View.generateViewId()
            chip.isChecked = place.placeId == state.selectedPlaceId
            chip.setOnClickListener { viewModel.selectPlace(place.placeId) }
            binding.chipGroupPlaces.addView(chip)
        }
        val hasPlaces = state.places.isNotEmpty()
        binding.tvSavedLabel.isVisible = hasPlaces
        binding.tvNoPlaces.isVisible = !hasPlaces
        // One snackbar per save: the message is cleared right after it is shown.
        state.message?.let { message ->
            Snackbar.make(binding.root, saveMessage(message), Snackbar.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    private fun saveMessage(message: SaveMessage): String = when (message) {
        is SaveMessage.Saved -> getString(R.string.weather_saved_toast, message.placeName)
        is SaveMessage.AlreadySaved -> getString(R.string.weather_place_exists, message.placeName)
        SaveMessage.LimitReached -> getString(R.string.weather_place_limit)
        SaveMessage.Failed -> getString(R.string.weather_save_failed)
    }

    private fun renderForecast(state: ForecastUiState) {
        val place = state.place
        binding.groupForecast.isVisible = place != null
        binding.tvForecastError.isVisible = place != null && state.failed && !state.hasForecast
        binding.btnRetry.isVisible = place != null && state.failed && !state.hasForecast
        // The badge reminds the user they are looking at stored data.
        binding.tvOfflineBadge.isVisible = state.fromCache
        if (place == null) return

        binding.tvPlaceName.text = getString(R.string.weather_place_title, place.name, place.country)
        val today = state.today
        val lastUpdated = state.lastUpdated
        if (!state.hasForecast || today == null || lastUpdated == null) {
            binding.tvLastUpdated.text = getString(R.string.weather_not_available)
            binding.tvTodayTemp.text = "--"
            binding.tvTodayCondition.text = ""
            binding.tvTodayRain.text = ""
            return
        }

        binding.tvLastUpdated.text =
            getString(R.string.weather_last_updated, lastUpdated.format(Format.dayMonthTime))
        binding.ivTodayIcon.setImageResource(today.condition.iconRes)
        binding.tvTodayTemp.text = getString(R.string.weather_temp, today.maxTemp)
        binding.tvTodayCondition.text =
            getString(R.string.weather_condition_low, today.condition.label, today.minTemp)
        binding.tvTodayRain.text = getString(R.string.weather_rain_chance, today.rainChance)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
