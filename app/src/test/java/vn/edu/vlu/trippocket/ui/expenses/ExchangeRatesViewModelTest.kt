package vn.edu.vlu.trippocket.ui.expenses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateApi
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateResponse
import vn.edu.vlu.trippocket.data.repository.ExchangeRateRepository
import vn.edu.vlu.trippocket.domain.ApiError
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRatesViewModelTest {

    private class FakeExchangeRateApi : ExchangeRateApi {
        var response: ExchangeRateResponse? = null
        var error: Exception? = null

        override suspend fun getLatestRates(base: String): ExchangeRateResponse {
            error?.let { throw it }
            return response ?: error("No response configured")
        }
    }

    private val fakeApi = FakeExchangeRateApi()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ExchangeRateRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = ExchangeRateRepository(fakeApi, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleSuccessResponse() = ExchangeRateResponse(
        result = "success",
        timeLastUpdateUtc = "Tue, 05 Nov 2026 00:02:31 +0000",
        baseCode = "USD",
        rates = mapOf(
            "USD" to 1.0,
            "VND" to 25611.0,
            "EUR" to 0.92,
        ),
    )

    @Test
    fun init_loadsRatesSuccessfully() = runTest {
        fakeApi.response = sampleSuccessResponse()

        val viewModel = ExchangeRatesViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is ExchangeRatesUiState.Success)
        val successState = state as ExchangeRatesUiState.Success
        assertEquals(2, successState.rates.items.size)
        assertEquals("Tue, 05 Nov 2026 00:02:31 +0000", successState.rates.lastUpdatedUtc)
    }

    @Test
    fun init_fails_emitsErrorState() = runTest {
        fakeApi.error = IOException("No internet connection")

        val viewModel = ExchangeRatesViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(ExchangeRatesUiState.Error(ApiError.OFFLINE), state)
    }

    @Test
    fun retry_reloadsAfterError() = runTest {
        fakeApi.error = IOException("Offline")
        val viewModel = ExchangeRatesViewModel(repository)
        assertEquals(ExchangeRatesUiState.Error(ApiError.OFFLINE), viewModel.uiState.value)

        // Clear error and provide response
        fakeApi.error = null
        fakeApi.response = sampleSuccessResponse()

        viewModel.retry()

        val state = viewModel.uiState.value
        assertTrue(state is ExchangeRatesUiState.Success)
        assertEquals(2, (state as ExchangeRatesUiState.Success).rates.items.size)
    }

    @Test
    fun refresh_updatesState() = runTest {
        fakeApi.response = sampleSuccessResponse()
        val viewModel = ExchangeRatesViewModel(repository)
        assertTrue(viewModel.uiState.value is ExchangeRatesUiState.Success)

        // Update response for refresh
        fakeApi.response = sampleSuccessResponse().copy(
            timeLastUpdateUtc = "Wed, 06 Nov 2026 00:02:31 +0000",
        )

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue(state is ExchangeRatesUiState.Success)
        val successState = state as ExchangeRatesUiState.Success
        assertEquals("Wed, 06 Nov 2026 00:02:31 +0000", successState.rates.lastUpdatedUtc)
    }

    @Test
    fun aFailedRefresh_keepsTheRatesOnScreen_andStopsTheSpinner() = runTest {
        fakeApi.response = sampleSuccessResponse()
        val viewModel = ExchangeRatesViewModel(repository)
        val before = viewModel.uiState.value as ExchangeRatesUiState.Success

        // Pull to refresh, then the network drops.
        fakeApi.response = null
        fakeApi.error = IOException("Offline")
        viewModel.refresh()

        val after = viewModel.uiState.value as ExchangeRatesUiState.Success
        assertEquals(before.rates, after.rates)
        assertFalse(after.isRefreshing)
        assertEquals(ApiError.OFFLINE, after.refreshError)
    }

    @Test
    fun retryFromTheRefreshErrorBanner_keepsTheList_andFetchesAgain() = runTest {
        fakeApi.response = sampleSuccessResponse()
        val viewModel = ExchangeRatesViewModel(repository)
        val before = (viewModel.uiState.value as ExchangeRatesUiState.Success).rates

        // Pull to refresh fails once: the list stays and the banner offers Retry.
        fakeApi.response = null
        fakeApi.error = IOException("Offline")
        viewModel.refresh()
        assertEquals(
            ApiError.OFFLINE,
            (viewModel.uiState.value as ExchangeRatesUiState.Success).refreshError,
        )

        // Retry from the banner goes through refresh(), not back to the Loading screen.
        fakeApi.error = null
        fakeApi.response = sampleSuccessResponse().copy(
            timeLastUpdateUtc = "Wed, 06 Nov 2026 00:02:31 +0000",
        )
        viewModel.retry()

        val after = viewModel.uiState.value as ExchangeRatesUiState.Success
        assertEquals(before.items, after.rates.items)
        assertNull(after.refreshError)
        assertFalse(after.isRefreshing)
        assertEquals("Wed, 06 Nov 2026 00:02:31 +0000", after.rates.lastUpdatedUtc)
    }

    @Test
    fun aSuccessfulRefresh_clearsThePreviousRefreshError() = runTest {
        fakeApi.response = sampleSuccessResponse()
        val viewModel = ExchangeRatesViewModel(repository)

        fakeApi.response = null
        fakeApi.error = IOException("Offline")
        viewModel.refresh()
        assertEquals(
            ApiError.OFFLINE,
            (viewModel.uiState.value as ExchangeRatesUiState.Success).refreshError,
        )

        fakeApi.error = null
        fakeApi.response = sampleSuccessResponse().copy(
            timeLastUpdateUtc = "Wed, 06 Nov 2026 00:02:31 +0000",
        )
        viewModel.refresh()

        val state = viewModel.uiState.value as ExchangeRatesUiState.Success
        assertNull(state.refreshError)
        assertEquals("Wed, 06 Nov 2026 00:02:31 +0000", state.rates.lastUpdatedUtc)
    }
}
