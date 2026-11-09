package vn.edu.vlu.trippocket.ui.ideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.TripIdea
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepository
import vn.edu.vlu.trippocket.domain.ApiError

/** One row of the list: the idea and whether the signed-in account shared it (Edit / Delete). */
data class TripIdeaCard(val idea: TripIdea, val mine: Boolean)

/** One-shot messages shown as a Snackbar, then cleared with [TripIdeasViewModel.messageShown]. */
sealed interface TripIdeasMessage {
    data object Deleted : TripIdeasMessage
    data class DeleteFailed(val error: ApiError) : TripIdeasMessage
}

/**
 * The three states of the screen (Milestone 4):
 * - Loading: [loading] with no rows yet -> ProgressBar.
 * - Success: [ideas] -> RecyclerView (or the empty state when the server has none).
 * - Error: [error] -> error banner with Retry; rows from an earlier load stay on screen.
 */
data class TripIdeasUiState(
    val loading: Boolean = true,
    /** Pull-to-refresh spinner. */
    val refreshing: Boolean = false,
    val ideas: List<TripIdeaCard> = emptyList(),
    val error: ApiError? = null,
    /** Ids being deleted, so their row cannot be deleted twice. */
    val deleting: Set<String> = emptySet(),
    val message: TripIdeasMessage? = null,
) {
    val showEmpty: Boolean get() = !loading && !refreshing && error == null && ideas.isEmpty()
}

/**
 * Trip Ideas list (Milestone 4, owner: Tiến). API -> [TripIdeaRepository] -> this ViewModel ->
 * StateFlow -> TripIdeasActivity (View Binding). Requests start in [viewModelScope], so they are
 * cancelled when the screen is closed for good; the repository runs them on Dispatchers.IO.
 */
class TripIdeasViewModel(private val repository: TripIdeaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TripIdeasUiState())
    val uiState: StateFlow<TripIdeasUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Retry button of the error banner. */
    fun retry() = load()

    /** Pull to refresh: the list stays visible while it reloads. */
    fun refresh() = load(pullToRefresh = true)

    /** The form shared or edited an idea: show the server's version of the list. */
    fun onIdeaSaved() = load()

    private fun load(pullToRefresh: Boolean = false) {
        // A new load replaces the one in flight (Retry pressed twice, refresh during a load).
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                loading = !pullToRefresh,
                refreshing = pullToRefresh,
                error = null,
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.ideas()
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        loading = false,
                        refreshing = false,
                        ideas = result.data.map { it.toCard() },
                        error = null,
                    )
                    // Keep the rows of the last good load so a failed refresh hides nothing.
                    is ApiResult.Failure -> state.copy(loading = false, refreshing = false, error = result.error)
                }
            }
        }
    }

    /** DELETE /ideas/{id}; the row leaves the list only once the server confirms. */
    fun delete(id: String) {
        if (id in _uiState.value.deleting) return
        _uiState.update { it.copy(deleting = it.deleting + id) }
        viewModelScope.launch {
            val result = repository.delete(id)
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        deleting = state.deleting - id,
                        ideas = state.ideas.filterNot { it.idea.id == id },
                        message = TripIdeasMessage.Deleted,
                    )
                    is ApiResult.Failure -> state.copy(
                        deleting = state.deleting - id,
                        message = TripIdeasMessage.DeleteFailed(result.error),
                    )
                }
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun TripIdea.toCard(): TripIdeaCard {
        val uid = repository.currentUid
        return TripIdeaCard(this, mine = uid != null && authorUid == uid)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripIdeasViewModel(appContainer().tripIdeaRepository) }
        }
    }
}
