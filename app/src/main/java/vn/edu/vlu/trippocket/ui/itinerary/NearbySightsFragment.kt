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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentNearbySightsBinding
import vn.edu.vlu.trippocket.ui.detail.TripDetailActivity
import vn.edu.vlu.trippocket.ui.detail.tripViewModels

/**
 * Wikipedia sights around the trip's first saved place (Milestone 4). Only renders
 * [NearbySightsUiState] and forwards clicks: every request goes
 * NearbySightsViewModel -> repository -> Retrofit, never from here.
 *
 * The trip and the selected day arrive through [tripViewModels] and SavedStateHandle; the
 * Activity's toolbar already has Back, so this screen adds no toolbar of its own.
 */
class NearbySightsFragment : Fragment() {

    private var _binding: FragmentNearbySightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NearbySightsViewModel by tripViewModels { NearbySightsViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNearbySightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SightAdapter(onAddToDay = viewModel::addToDay)
        binding.rvSights.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSights.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.networkError.btnNetworkRetry.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.sights)
                    render(state)
                }
            }
        }
    }

    private fun render(state: NearbySightsUiState) {
        binding.pbLoading.isVisible = state.loading
        binding.tvLoading.isVisible = state.loading
        // No place: a pull could not help, so it is turned off instead of only hiding Retry.
        binding.swipeRefresh.isEnabled = !state.noPlace
        binding.swipeRefresh.isRefreshing = state.refreshing
        binding.rvSights.isVisible = state.sights.isNotEmpty()

        val error = state.error
        binding.networkError.root.isVisible = error != null
        if (error != null) binding.networkError.tvNetworkError.setText(error.messageRes)

        binding.tvSubtitle.isVisible = !state.noPlace
        binding.tvSubtitle.text = state.placeName?.let { placeName ->
            getString(R.string.sights_subtitle, placeName)
        } ?: getString(R.string.sights_subtitle_any)

        // Empty and No place share one group but never say the same thing.
        val showEmpty = state.empty || state.noPlace
        binding.groupEmpty.isVisible = showEmpty
        if (showEmpty) {
            binding.tvEmptyTitle.setText(
                if (state.noPlace) R.string.sights_no_place_title else R.string.sights_empty_title
            )
            binding.tvEmptyBody.setText(
                if (state.noPlace) R.string.sights_no_place_body else R.string.sights_empty_body
            )
        }

        state.message?.let { message ->
            val text = when (message) {
                is SightsMessage.Added -> getString(R.string.sights_added, message.dayNumber)
                SightsMessage.LimitReached -> getString(R.string.itinerary_limit_reached)
                SightsMessage.AddFailed -> getString(R.string.sights_add_failed)
            }
            Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT)
                .setAnchorView((activity as? TripDetailActivity)?.snackbarAnchor)
                .show()
            viewModel.messageShown()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
