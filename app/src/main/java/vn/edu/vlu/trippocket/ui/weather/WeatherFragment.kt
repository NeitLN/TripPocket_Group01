package vn.edu.vlu.trippocket.ui.weather

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.data.repository.PlaceProblem
import vn.edu.vlu.trippocket.databinding.FragmentWeatherBinding
import vn.edu.vlu.trippocket.databinding.ViewPlaceChipBinding
import vn.edu.vlu.trippocket.ui.detail.TripDetailViewModel
import vn.edu.vlu.trippocket.ui.detail.TripGate
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import vn.edu.vlu.trippocket.util.Format
import vn.edu.vlu.trippocket.util.clearRemote
import vn.edu.vlu.trippocket.util.loadRemote

/** Screen 4: place search, saved places and the cached forecast (see [WeatherViewModel]). */
class WeatherFragment : Fragment() {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WeatherViewModel by tripViewModels { WeatherViewModel.Factory }
    private val tripDetailViewModel: TripDetailViewModel by activityViewModels { TripDetailViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        childFragmentManager.setFragmentResultListener(
            RenamePlaceDialogFragment.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val placeId = bundle.getString(RenamePlaceDialogFragment.EXTRA_PLACE_ID)
                ?: return@setFragmentResultListener
            val name = bundle.getString(RenamePlaceDialogFragment.EXTRA_NAME)
                ?: return@setFragmentResultListener
            viewModel.renamePlace(placeId, name)
        }

        childFragmentManager.setFragmentResultListener(
            EditPlaceNoteDialogFragment.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val placeId = bundle.getString(EditPlaceNoteDialogFragment.EXTRA_PLACE_ID)
                ?: return@setFragmentResultListener
            val note = bundle.getString(EditPlaceNoteDialogFragment.EXTRA_NOTE)
                ?: return@setFragmentResultListener
            viewModel.updatePlaceNote(placeId, note)
        }

        childFragmentManager.setFragmentResultListener(
            ConfirmDeletePlaceDialogFragment.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val placeId = bundle.getString(ConfirmDeletePlaceDialogFragment.EXTRA_PLACE_ID)
                ?: return@setFragmentResultListener
            viewModel.deletePlace(placeId)
        }
    }

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
        binding.btnPlaceOptions.setOnClickListener { anchor ->
            viewModel.places.value.selected?.let { selected ->
                showPlaceMenu(selected, anchor)
            }
        }
        binding.btnRetry.setOnClickListener { viewModel.retry() }
        binding.btnSearchRetry.setOnClickListener { viewModel.retrySearch() }
        binding.btnAboutRetry.setOnClickListener { viewModel.retryPlaceInfo() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.btnEmptySearch.setOnClickListener {
            binding.etSearch.requestFocus()
            requireContext().getSystemService<InputMethodManager>()
                ?.showSoftInput(binding.etSearch, 0)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    tripDetailViewModel.gate.collect { gate ->
                        if (gate is TripGate.Ready) {
                            val trip = gate.trip
                            binding.stickerWeather.bind(trip.destination, trip.tripId)
                            val daysText = resources.getQuantityString(R.plurals.trip_days, trip.dayCount, trip.dayCount)
                            binding.tvTripSubtitle.text = getString(R.string.trip_dates_days, trip.destination, daysText)
                        }
                    }
                }
                launch {
                    viewModel.search.collect { state ->
                        resultAdapter.submitList((state as? SearchState.Results)?.results.orEmpty())
                        renderSearch(state)
                    }
                }
                launch { viewModel.places.collect(::renderPlaces) }
                launch { viewModel.placeInfo.collect(::renderPlaceInfo) }
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
        val results = (state as? SearchState.Results)?.results.orEmpty()
        binding.rvResults.isVisible = results.isNotEmpty()
        binding.progressSearch.isVisible = state is SearchState.Loading
        binding.tvNoResults.isVisible = state is SearchState.Empty
        binding.layoutSearchError.isVisible = state is SearchState.Error
        if (state is SearchState.Error) {
            binding.tvSearchError.setText(state.error.messageRes)
        }
    }

    /**
     * The "About this place" card (Wikipedia). Only three states reach the screen: Loading, Info
     * (the article arrived, even if it has no introduction) and Error. A load that finished with
     * no article at all (HTTP 404, or a page with no summary and no photo, so info == null) hides
     * the whole card: no empty card, no error.
     */
    private fun renderPlaceInfo(state: PlaceInfoUiState) {
        val info = state.info
        val error = state.error
        val loading = state.loading

        binding.vAboutPanel.isVisible = loading || info != null || error != null
        binding.progressAbout.isVisible = loading

        // Info: the photo stays even when the article has no introduction; the text lines then
        // hide on their own and the card is exactly as tall as the photo.
        binding.ivAboutPhoto.isVisible = info != null
        binding.tvAboutExtract.isVisible = info != null && state.hasExtract
        // The photo is Wikipedia content too, so the source line stays even without an introduction.
        binding.tvAboutSource.isVisible = info != null

        binding.tvAboutError.isVisible = error != null
        binding.btnAboutRetry.isVisible = error != null
        if (error != null) binding.tvAboutError.setText(error.messageRes)

        if (info != null) {
            val placeName = state.placeName.orEmpty()
            binding.ivAboutPhoto.loadRemote(info.imageUrl, R.drawable.ic_photo, R.drawable.ic_photo)
            // A missing thumbnail shows the default image, which needs no description.
            binding.ivAboutPhoto.contentDescription = info.imageUrl?.let {
                getString(R.string.weather_photo_of, placeName)
            }
            binding.tvAboutExtract.text = info.extract
        } else {
            // Loading, error or hidden: the photo of the previous place must not stay behind.
            binding.ivAboutPhoto.clearRemote()
            binding.ivAboutPhoto.contentDescription = null
        }
    }

    private fun renderPlaces(state: SavedPlacesUiState) {
        binding.chipGroupPlaces.removeAllViews()
        for (place in state.places) {
            val chip = ViewPlaceChipBinding.inflate(layoutInflater, binding.chipGroupPlaces, false).root
            chip.text = place.name
            chip.id = View.generateViewId()
            chip.isChecked = place.placeId == state.selectedPlaceId
            chip.setOnClickListener { viewModel.selectPlace(place.placeId) }
            chip.setOnLongClickListener {
                showPlaceMenu(place, chip)
                true
            }
            binding.chipGroupPlaces.addView(chip)
        }
        val hasPlaces = state.places.isNotEmpty()
        binding.tvSavedLabel.isVisible = hasPlaces
        binding.chipGroupPlaces.isVisible = hasPlaces
        binding.groupEmptyPlaces.isVisible = !hasPlaces

        val note = state.selectedNote
        binding.tvPlaceNote.isVisible = hasPlaces && note.isNotBlank()
        binding.tvPlaceNote.text = note

        // One snackbar per save: the message is cleared right after it is shown.
        state.message?.let { message ->
            Snackbar.make(binding.root, saveMessage(message), Snackbar.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    private fun showPlaceMenu(place: SavedPlace, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_place_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename_place -> {
                    RenamePlaceDialogFragment.newInstance(place.placeId, place.name)
                        .show(childFragmentManager, "rename_place")
                    true
                }
                R.id.action_edit_note -> {
                    EditPlaceNoteDialogFragment.newInstance(place.placeId, place.name, place.note)
                        .show(childFragmentManager, "edit_place_note")
                    true
                }
                R.id.action_delete_place -> {
                    ConfirmDeletePlaceDialogFragment.newInstance(place.placeId, place.name)
                        .show(childFragmentManager, "delete_place")
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun saveMessage(message: SaveMessage): String = when (message) {
        is SaveMessage.Saved -> getString(R.string.weather_saved_toast, message.placeName)
        is SaveMessage.AlreadySaved -> getString(R.string.weather_place_exists, message.placeName)
        SaveMessage.LimitReached -> getString(R.string.weather_place_limit)
        SaveMessage.Failed -> getString(R.string.weather_save_failed)
        is SaveMessage.Renamed -> getString(R.string.weather_place_renamed, message.placeName)
        is SaveMessage.NoteSaved -> getString(R.string.weather_place_note_saved, message.placeName)
        is SaveMessage.Deleted -> getString(R.string.weather_place_deleted, message.placeName)
        is SaveMessage.Invalid -> getString(placeProblemMessage(message.problem))
    }

    /** WP: the sentence for a refused rename / note, so the dialog can say what to fix. */
    private fun placeProblemMessage(problem: PlaceProblem): Int = when (problem) {
        PlaceProblem.BLANK_NAME -> R.string.weather_place_name_blank
        PlaceProblem.NAME_TOO_LONG -> R.string.weather_place_name_long
        PlaceProblem.NOTE_TOO_LONG -> R.string.weather_place_note_long
        PlaceProblem.NOT_FOUND -> R.string.weather_place_not_found
    }

    private fun renderForecast(state: ForecastUiState) {
        binding.swipeRefresh.isRefreshing = state.refreshing
        val place = state.place
        binding.groupForecast.isVisible = place != null
        binding.tvForecastError.isVisible = place != null && state.failed && !state.hasForecast
        binding.btnRetry.isVisible = place != null && state.failed && !state.hasForecast
        val error = state.error
        if (error != null && !state.hasForecast) binding.tvForecastError.setText(error.messageRes)
        // The badge reminds the user they are looking at stored data.
        binding.tvOfflineBadge.isVisible = state.fromCache
        if (place == null) {
            binding.tvPlaceNote.isVisible = false
            return
        }

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
        binding.ivAboutPhoto.clearRemote()
        super.onDestroyView()
        _binding = null
    }
}
