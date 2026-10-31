package vn.edu.vlu.trippocket.ui.checklist

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ChecklistItem
import vn.edu.vlu.trippocket.data.repository.ChecklistProblem
import vn.edu.vlu.trippocket.data.repository.ChecklistRepository
import vn.edu.vlu.trippocket.data.repository.InvalidChecklistItemException
import vn.edu.vlu.trippocket.domain.LimitExceededException
import vn.edu.vlu.trippocket.domain.Limits
import vn.edu.vlu.trippocket.ui.detail.ARG_TRIP_ID

data class ChecklistUiState(
    val loading: Boolean = true,
    val items: List<ChecklistItem> = emptyList(),
) {
    val done: Int get() = items.count { it.isDone }
    val total: Int get() = items.size

    /** The Add button is off at the cap; the repository checks it again inside the transaction. */
    val canAdd: Boolean get() = total < Limits.MAX_CHECKLIST_ITEMS
}

/** One-shot feedback for the screen; cleared with [ChecklistViewModel.messageShown]. */
sealed interface ChecklistMessage {
    /** The new item is saved: the screen clears the input. */
    data object Added : ChecklistMessage
    data class Problem(@param:StringRes val text: Int) : ChecklistMessage

    /** The item is gone; the screen offers Undo. */
    data class Deleted(val item: ChecklistItem) : ChecklistMessage
}

/** Packing checklist tab (I2): add, tick, rename and delete items, at most 100 per trip. */
class ChecklistViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ChecklistRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle[ARG_TRIP_ID] ?: ""

    val uiState: StateFlow<ChecklistUiState> = repository.observe(tripId)
        .map { ChecklistUiState(loading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChecklistUiState())

    private val _message = MutableStateFlow<ChecklistMessage?>(null)
    val message: StateFlow<ChecklistMessage?> = _message.asStateFlow()

    fun add(title: String) = write {
        repository.add(tripId, title)
        _message.value = ChecklistMessage.Added
    }

    fun setDone(item: ChecklistItem, done: Boolean) = write { repository.setDone(tripId, item.itemId, done) }

    fun rename(itemId: String, title: String) = write { repository.rename(tripId, itemId, title) }

    fun delete(item: ChecklistItem) = write {
        _message.value = ChecklistMessage.Deleted(repository.delete(tripId, item.itemId))
    }

    fun undoDelete(item: ChecklistItem) = write { repository.restore(item) }

    fun messageShown() {
        _message.value = null
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidChecklistItemException) {
                _message.value = ChecklistMessage.Problem(
                    when (e.problem) {
                        ChecklistProblem.BLANK -> R.string.checklist_error_blank
                        ChecklistProblem.TOO_LONG -> R.string.checklist_error_too_long
                        ChecklistProblem.NOT_FOUND -> R.string.checklist_error_generic
                    }
                )
            } catch (e: LimitExceededException) {
                _message.value = ChecklistMessage.Problem(R.string.checklist_error_limit)
            } catch (e: Exception) {
                _message.value = ChecklistMessage.Problem(R.string.checklist_error_generic)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ChecklistViewModel(createSavedStateHandle(), appContainer().checklistRepository) }
        }
    }
}
