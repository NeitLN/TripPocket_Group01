package vn.edu.vlu.trippocket.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.appContainer
import vn.edu.vlu.trippocket.data.model.ExchangeRates
import vn.edu.vlu.trippocket.data.remote.ApiResult
import vn.edu.vlu.trippocket.data.repository.ExchangeRateRepository
import vn.edu.vlu.trippocket.domain.ApiError

sealed interface ExchangeRatesUiState {
    data object Loading : ExchangeRatesUiState
    data class Success(
        val rates: ExchangeRates,
        val isRefreshing: Boolean = false,
    ) : ExchangeRatesUiState
    data class Error(val error: ApiError) : ExchangeRatesUiState
}

class ExchangeRatesViewModel(
    private val repository: ExchangeRateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExchangeRatesUiState>(ExchangeRatesUiState.Loading)
    val uiState: StateFlow<ExchangeRatesUiState> = _uiState.asStateFlow()

    init {
        loadRates()
    }

    fun loadRates() {
        _uiState.value = ExchangeRatesUiState.Loading
        viewModelScope.launch {
            fetchRates(isRefresh = false)
        }
    }

    fun retry() {
        loadRates()
    }

    fun refresh() {
        val current = _uiState.value
        if (current is ExchangeRatesUiState.Success) {
            _uiState.value = current.copy(isRefreshing = true)
        } else {
            _uiState.value = ExchangeRatesUiState.Loading
        }
        viewModelScope.launch {
            fetchRates(isRefresh = true)
        }
    }

    private suspend fun fetchRates(isRefresh: Boolean) {
        when (val result = repository.getExchangeRates()) {
            is ApiResult.Success -> {
                _uiState.value = ExchangeRatesUiState.Success(result.data, isRefreshing = false)
            }
            is ApiResult.Failure -> {
                _uiState.value = ExchangeRatesUiState.Error(result.error)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExchangeRatesViewModel(appContainer().exchangeRateRepository)
            }
        }
    }
}
