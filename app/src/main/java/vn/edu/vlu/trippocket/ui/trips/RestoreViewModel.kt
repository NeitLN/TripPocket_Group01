package vn.edu.vlu.trippocket.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.repository.AccountChangedException
import vn.edu.vlu.trippocket.data.repository.RestoreConflict
import vn.edu.vlu.trippocket.data.repository.RestorePlan
import vn.edu.vlu.trippocket.data.repository.RestoreResult
import java.io.IOException

data class RestoreUiState(
    /** Reading the backup or writing it to the phone. */
    val busy: Boolean = false,
    /** Not empty while the user chooses which trips take the backup version. */
    val conflicts: List<RestoreConflict> = emptyList(),
)

/** One-off outcomes, shown once as a snackbar. */
sealed interface RestoreMessage {
    data class Done(val result: RestoreResult) : RestoreMessage
    data object NoBackup : RestoreMessage
    data object Offline : RestoreMessage
    data object AccountChanged : RestoreMessage
    data object Cancelled : RestoreMessage
    data object Failed : RestoreMessage
}

/**
 * A4: overflow menu > "Restore from backup" on Screen 2. Reads the backup, then either writes it
 * straight away (nothing to choose) or waits for the conflict dialog. The plan stays here, so a
 * rotation keeps the dialog and its choices.
 */
class RestoreViewModel(
    private val prepare: suspend () -> RestorePlan,
    private val apply: suspend (plan: RestorePlan, useBackupFor: Set<String>) -> RestoreResult,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    private val _messages = Channel<RestoreMessage>(Channel.BUFFERED)
    val messages: Flow<RestoreMessage> = _messages.receiveAsFlow()

    private var plan: RestorePlan? = null

    fun restore() {
        if (_uiState.value.busy || plan != null) return
        _uiState.value = RestoreUiState(busy = true)
        run {
            val found = prepare()
            when {
                found.isEmpty -> {
                    _uiState.value = RestoreUiState()
                    _messages.send(RestoreMessage.NoBackup)
                }
                found.conflicts.isEmpty() -> write(found, emptySet())
                else -> {
                    plan = found
                    _uiState.value = RestoreUiState(conflicts = found.conflicts)
                }
            }
        }
    }

    /** The dialog's answer: [useBackupFor] holds the trip ids ticked for the backup version. */
    fun choose(useBackupFor: Set<String>) {
        val chosen = plan ?: return
        plan = null
        _uiState.value = RestoreUiState(busy = true)
        run { write(chosen, useBackupFor) }
    }

    /** The dialog was dismissed: nothing is written, not even the trips only in the backup. */
    fun cancel() {
        if (plan == null) return
        plan = null
        _uiState.value = RestoreUiState()
        _messages.trySend(RestoreMessage.Cancelled)
    }

    private suspend fun write(plan: RestorePlan, useBackupFor: Set<String>) {
        val result = apply(plan, useBackupFor)
        _uiState.value = RestoreUiState()
        _messages.send(RestoreMessage.Done(result))
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = RestoreUiState()
                _messages.send(
                    when (e) {
                        is IOException -> RestoreMessage.Offline
                        is AccountChangedException -> RestoreMessage.AccountChanged
                        else -> RestoreMessage.Failed
                    }
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val restore = appContainer().restoreRepository
                RestoreViewModel(restore::prepare, restore::apply)
            }
        }
    }
}
