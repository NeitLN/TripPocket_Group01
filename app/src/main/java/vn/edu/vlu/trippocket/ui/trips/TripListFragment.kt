package vn.edu.vlu.trippocket.ui.trips

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.MockData
import vn.edu.vlu.trippocket.data.SyncStatus
import vn.edu.vlu.trippocket.databinding.FragmentTripListBinding
import vn.edu.vlu.trippocket.ui.detail.TripDetailActivity

/** Screen 2: the user's trips. */
class TripListFragment : Fragment(R.layout.fragment_trip_list) {

    private var _binding: FragmentTripListBinding? = null
    private val binding get() = _binding!!

    private val adapter = TripAdapter { trip ->
        startActivity(TripDetailActivity.newIntent(requireContext(), trip.tripId))
    }
    private var showEmptyPreview = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTripListBinding.bind(view)
        showEmptyPreview = savedInstanceState?.getBoolean(KEY_EMPTY) ?: false

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
        render()
    }

    fun toggleEmptyPreview() {
        showEmptyPreview = !showEmptyPreview
        render()
    }

    private fun render() {
        val trips = if (showEmptyPreview) emptyList() else MockData.trips
        adapter.submitList(trips)
        binding.rvTrips.isVisible = trips.isNotEmpty()
        binding.groupEmpty.isVisible = trips.isEmpty()
        binding.tvSummary.isVisible = trips.isNotEmpty()
        binding.tvSummary.text = resources.getQuantityString(
            R.plurals.trips_summary,
            trips.size,
            trips.size,
            trips.count { it.syncStatus != SyncStatus.SYNCED },
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_EMPTY, showEmptyPreview)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val KEY_EMPTY = "show_empty_preview"
    }
}
