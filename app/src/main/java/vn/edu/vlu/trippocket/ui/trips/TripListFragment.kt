package vn.edu.vlu.trippocket.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentTripListBinding
import vn.edu.vlu.trippocket.ui.detail.TripDetailActivity

/** Screen 2: the user's trips. */
class TripListFragment : Fragment() {

    private var _binding: FragmentTripListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TripListViewModel by activityViewModels { TripListViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTripListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = TripAdapter { card ->
            startActivity(TripDetailActivity.newIntent(requireContext(), card.trip.tripId))
        }

        // 1 column on phones, 2 in landscape / on tablets (res/values*/integers.xml).
        binding.rvTrips.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.trip_grid_span))
        binding.rvTrips.adapter = adapter

        // Shrink the extended FAB to an icon while scrolling down.
        binding.rvTrips.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabNewTrip.shrink() else if (dy < 0) binding.fabNewTrip.extend()
            }
        })
        binding.fabNewTrip.setOnClickListener {
            Snackbar.make(it, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT)
                .setAnchorView(it)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.trips)
                    render(state)
                }
            }
        }
    }

    private fun render(state: TripListUiState) {
        val hasTrips = state.trips.isNotEmpty()
        binding.rvTrips.isVisible = hasTrips
        binding.groupEmpty.isVisible = !hasTrips
        binding.tvSummary.isVisible = hasTrips
        binding.tvSummary.text = resources.getQuantityString(
            R.plurals.trips_summary, state.trips.size, state.trips.size, state.unsyncedCount
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
