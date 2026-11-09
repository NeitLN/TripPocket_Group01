package vn.edu.vlu.trippocket.ui.ideas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.TripIdea
import vn.edu.vlu.trippocket.databinding.ActivityTripIdeasBinding
import vn.edu.vlu.trippocket.ui.detail.TripDetailActivity
import vn.edu.vlu.trippocket.ui.trips.TripFormDialogFragment
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/**
 * Trip Ideas screen (Milestone 4, owner: Tiến). Only renders [TripIdeasUiState] and forwards
 * clicks: every request goes TripIdeasViewModel -> TripIdeaRepository -> Retrofit, never from here.
 */
class TripIdeasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripIdeasBinding
    private val viewModel: TripIdeasViewModel by viewModels { TripIdeasViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripIdeasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()

        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = TripIdeaAdapter(
            onPlan = ::planTrip,
            onEdit = { idea -> TripIdeaFormDialogFragment.show(supportFragmentManager, idea.id) },
            onDelete = ::confirmDelete,
        )
        binding.rvIdeas.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.networkError.btnNetworkRetry.setOnClickListener { viewModel.retry() }
        binding.fabShare.setOnClickListener { TripIdeaFormDialogFragment.show(supportFragmentManager) }

        // The form saved (POST / PUT): reload so the list shows what the server stored.
        supportFragmentManager.setFragmentResultListener(TripIdeaFormDialogFragment.REQUEST_KEY, this) { _, _ ->
            viewModel.onIdeaSaved()
        }
        // "Plan this trip" -> trip form -> the new trip opens, like from the trip list.
        supportFragmentManager.setFragmentResultListener(TripFormDialogFragment.REQUEST_KEY, this) { _, bundle ->
            bundle.getString(TripFormDialogFragment.RESULT_TRIP_ID)?.let { tripId ->
                startActivity(TripDetailActivity.newIntent(this, tripId))
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.ideas)
                    render(state)
                }
            }
        }
    }

    private fun render(state: TripIdeasUiState) {
        val firstLoad = state.loading && state.ideas.isEmpty()
        binding.pbLoading.isVisible = firstLoad
        binding.tvLoading.isVisible = firstLoad
        binding.swipeRefresh.isRefreshing = state.refreshing
        binding.rvIdeas.isVisible = state.ideas.isNotEmpty()
        binding.groupEmpty.isVisible = state.showEmpty

        val error = state.error
        binding.networkError.root.isVisible = error != null
        if (error != null) binding.networkError.tvNetworkError.setText(error.messageRes)

        state.message?.let { message ->
            val text = when (message) {
                TripIdeasMessage.Deleted -> getString(R.string.ideas_deleted)
                is TripIdeasMessage.DeleteFailed -> getString(message.error.messageRes)
            }
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).setAnchorView(binding.fabShare).show()
            viewModel.messageShown()
        }
    }

    private fun confirmDelete(idea: TripIdea) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ideas_delete_title)
            .setMessage(getString(R.string.ideas_delete_message, idea.title))
            .setPositiveButton(R.string.ideas_action_delete) { _, _ -> viewModel.delete(idea.id) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun planTrip(idea: TripIdea) {
        TripFormDialogFragment.show(
            supportFragmentManager,
            prefillName = idea.title,
            prefillDestination = idea.destination,
        )
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, TripIdeasActivity::class.java)
    }
}
