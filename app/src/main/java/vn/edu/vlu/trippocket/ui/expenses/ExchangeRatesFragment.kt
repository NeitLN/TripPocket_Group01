package vn.edu.vlu.trippocket.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentExchangeRatesBinding

/**
 * Foreign currency exchange rates screen (Milestone 4).
 * Displays live exchange rates for 12 common travel currencies converted to VND.
 */
class ExchangeRatesFragment : Fragment() {

    private var _binding: FragmentExchangeRatesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExchangeRatesViewModel by viewModels { ExchangeRatesViewModel.Factory }
    private val adapter = CurrencyAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExchangeRatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCurrencies.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCurrencies.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.networkError.btnNetworkRetry.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: ExchangeRatesUiState) {
        when (state) {
            is ExchangeRatesUiState.Loading -> {
                binding.progressBar.isVisible = !binding.swipeRefresh.isRefreshing
                binding.networkError.root.isVisible = false
                binding.tvEmpty.isVisible = false
                if (!binding.swipeRefresh.isRefreshing) {
                    binding.rvCurrencies.isVisible = false
                    binding.tvLastUpdated.isVisible = false
                }
            }
            is ExchangeRatesUiState.Success -> {
                binding.progressBar.isVisible = false
                binding.swipeRefresh.isRefreshing = state.isRefreshing
                binding.networkError.root.isVisible = false
                binding.rvCurrencies.isVisible = state.rates.items.isNotEmpty()
                binding.tvEmpty.isVisible = state.rates.items.isEmpty()
                binding.tvLastUpdated.isVisible = state.rates.lastUpdatedUtc.isNotBlank()
                if (state.rates.lastUpdatedUtc.isNotBlank()) {
                    binding.tvLastUpdated.text = getString(
                        R.string.exchange_rates_updated,
                        state.rates.lastUpdatedUtc,
                    )
                }
                adapter.submitList(state.rates.items)
            }
            is ExchangeRatesUiState.Error -> {
                binding.progressBar.isVisible = false
                binding.swipeRefresh.isRefreshing = false
                binding.networkError.root.isVisible = true
                binding.networkError.tvNetworkError.setText(state.error.messageRes)
                binding.rvCurrencies.isVisible = false
                binding.tvEmpty.isVisible = false
                binding.tvLastUpdated.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
