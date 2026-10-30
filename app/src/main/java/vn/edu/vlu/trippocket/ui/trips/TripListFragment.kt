package vn.edu.vlu.trippocket.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import vn.edu.vlu.trippocket.ui.common.StickerHighlightSpan
import vn.edu.vlu.trippocket.ui.detail.TripDetailActivity

/** Screen 2: the user's trips, grouped Happening now / Upcoming / Past. */
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
        highlightTitle()

        val adapter = TripAdapter(
            onClick = { card -> startActivity(TripDetailActivity.newIntent(requireContext(), card.trip.tripId)) },
            // Retry backup arrives with A3 (BackupWorker).
            onRetry = { showNotYet(binding.fabNewTrip) },
        )

        // 1 column on phones, 2 in landscape / on tablets (res/values*/integers.xml); labels span them all.
        val span = resources.getInteger(R.integer.trip_grid_span)
        binding.rvTrips.layoutManager = GridLayoutManager(requireContext(), span).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.getItemViewType(position) == TripAdapter.TYPE_HEADER) span else 1
            }
        }
        binding.rvTrips.adapter = adapter

        // Shrink the extended FAB to an icon while scrolling down.
        binding.rvTrips.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabNewTrip.shrink() else if (dy < 0) binding.fabNewTrip.extend()
            }
        })
        binding.fabNewTrip.setOnClickListener { showNotYet(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.rows)
                    render(state)
                }
            }
        }
    }

    /** "My trips" with "trips" on the lime sticker highlight (DESIGN.md, one word per screen). */
    private fun highlightTitle() {
        val context = requireContext()
        val title = getString(R.string.trips_title)
        val word = title.substringAfterLast(' ')
        binding.tvTitle.text = StickerHighlightSpan.apply(
            title, word,
            StickerHighlightSpan(
                fill = ContextCompat.getColor(context, R.color.brand_lime),
                border = ContextCompat.getColor(context, R.color.sticker_border),
                textColor = ContextCompat.getColor(context, R.color.on_brand_lime),
                borderWidth = resources.getDimension(R.dimen.sticker_border_width),
                radius = resources.getDimension(R.dimen.title_highlight_radius),
                padX = resources.getDimension(R.dimen.title_highlight_pad),
            ),
        )
    }

    private fun render(state: TripListUiState) {
        val hasTrips = state.trips.isNotEmpty()
        binding.rvTrips.isVisible = hasTrips
        binding.groupEmpty.isVisible = !state.loading && !hasTrips
        binding.tvSummary.isVisible = hasTrips
        binding.tvSummary.text = summary(state)
    }

    /** "3 trips · 1 waiting for backup · 1 backup failed": zero counts are left out. */
    private fun summary(state: TripListUiState): String = listOfNotNull(
        resources.getQuantityString(R.plurals.trips_count, state.trips.size, state.trips.size),
        state.pendingCount.takeIf { it > 0 }?.let { getString(R.string.trips_summary_waiting, it) },
        state.failedCount.takeIf { it > 0 }?.let {
            resources.getQuantityString(R.plurals.trips_summary_failed, it, it)
        },
    ).joinToString(" · ")

    private fun showNotYet(anchor: View) {
        Snackbar.make(anchor, R.string.msg_not_in_milestone1, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.fabNewTrip)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
