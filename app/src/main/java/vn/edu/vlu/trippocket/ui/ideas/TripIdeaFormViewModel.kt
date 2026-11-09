package vn.edu.vlu.trippocket.ui.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import vn.edu.vlu.trippocket.domain.TripIdeaProblem
import vn.edu.vlu.trippocket.domain.TripIdeaRule

/** Id of the idea to edit; absent when sharing a new one. */
const val ARG_IDEA_ID = "idea_id"

/** The text of the six form fields, as typed. */
data class TripIdeaFields(
    val title: String = "",
    val destination: String = "",
    val days: String = "3",
    val budget: String = "",
    val coverUrl: String = "",
    val summary: String = "",
)

data class TripIdeaFormUiState(
    val editing: Boolean = false,
    /** GET /ideas/{id} in flight (edit only). */
    val loading: Boolean = false,
    /** GET /ideas/{id} failed with something other than 404; the form offers Retry. */
    val loadError: ApiError? = null,
    /** One-shot: the server's fields to put into the inputs (after the GET); cleared by [TripIdeaFormViewModel.fieldsShown]. */
    val fillFields: TripIdeaFields? = null,
    val problems: Set<TripIdeaProblem> = emptySet(),
    /** POST / PUT in flight. */
    val saving: Boolean = false,
    /** POST / PUT failed; the banner says why and Save can be pressed again. */
    val saveError: ApiError? = null,
    /** The idea was deleted on the server (404) while it was being edited. */
    val notFound: Boolean = false,
    /** One-shot: saved; the dialog closes and the list reloads. */
    val saved: Boolean = false,
) {
    fun has(problem: TripIdeaProblem) = problem in problems
}

/**
 * Share (POST) or edit (PUT) a trip idea (Milestone 4, owner: Tiến). Fields are checked with
 * [TripIdeaRule] before any request; editing first loads the idea with GET /ideas/{id}.
 */
class TripIdeaFormViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: TripIdeaRepository,
) : ViewModel() {

    private val ideaId: String? = savedStateHandle[ARG_IDEA_ID]

    /** True once the inputs were filled; after process death the views restore what was typed. */
    private val filled: Boolean get() = savedStateHandle[KEY_FILLED] ?: false

    private val _uiState = MutableStateFlow(
        // A new idea starts from the default fields (3 days); an edit waits for the GET.
        TripIdeaFormUiState(
            editing = ideaId != null,
            fillFields = if (ideaId == null && !filled) TripIdeaFields() else null,
        )
    )
    val uiState: StateFlow<TripIdeaFormUiState> = _uiState.asStateFlow()

    /** What the inputs hold now; kept here so rotation does not lose it. */
    private var fields = TripIdeaFields()

    /** The idea as the GET returned it (edit only), so the PUT keeps its author. */
    private var original: TripIdea? = null

    init {
        if (ideaId != null && !filled) loadIdea(ideaId)
    }

    /** Retry after a failed GET. */
    fun retryLoad() {
        ideaId?.let(::loadIdea)
    }

    private fun loadIdea(id: String) {
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.idea(id)) {
                is ApiResult.Success -> {
                    original = result.data
                    fields = result.data.toFields()
                    _uiState.update { it.copy(loading = false, fillFields = fields) }
                }
                is ApiResult.Failure -> _uiState.update {
                    if (result.error == ApiError.NOT_FOUND) it.copy(loading = false, notFound = true)
                    else it.copy(loading = false, loadError = result.error)
                }
            }
        }
    }

    fun fieldsShown() {
        savedStateHandle[KEY_FILLED] = true
        _uiState.update { it.copy(fillFields = null) }
    }

    fun onFieldsChanged(value: TripIdeaFields) {
        fields = value
        // A fixed field loses its error at once; the others wait for the next Save.
        val now = problemsOf(value)
        _uiState.update { it.copy(problems = it.problems intersect now) }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.loading || state.saved || state.notFound) return
        val f = fields
        val input = TripIdeaRule.toInput(f.title, f.destination, f.days, f.budget, f.coverUrl, f.summary)
        if (input == null) {
            _uiState.update { it.copy(problems = problemsOf(f)) }
            return
        }
        _uiState.update { it.copy(saving = true, saveError = null, problems = emptySet()) }
        viewModelScope.launch {
            // After process death [original] is gone; the repository then reads the idea again.
            val result = if (ideaId == null) repository.share(input) else repository.update(ideaId, input, original)
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(saving = false, saved = true)
                    is ApiResult.Failure ->
                        if (result.error == ApiError.NOT_FOUND && ideaId != null) it.copy(saving = false, notFound = true)
                        else it.copy(saving = false, saveError = result.error)
                }
            }
        }
    }

    private fun problemsOf(f: TripIdeaFields) =
        TripIdeaRule.problems(f.title, f.destination, f.days, f.budget, f.coverUrl, f.summary)

    private fun TripIdea.toFields() = TripIdeaFields(
        title = title,
        destination = destination,
        days = days.toString(),
        budget = if (budgetVnd == 0L) "" else budgetVnd.toString(),
        coverUrl = coverUrl.orEmpty(),
        summary = summary,
    )

    companion object {
        private const val KEY_FILLED = "fields_filled"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TripIdeaFormViewModel(createSavedStateHandle(), appContainer().tripIdeaRepository) }
        }
    }
}
