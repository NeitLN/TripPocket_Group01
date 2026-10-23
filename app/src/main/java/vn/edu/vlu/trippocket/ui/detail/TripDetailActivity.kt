package vn.edu.vlu.trippocket.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityTripDetailBinding
import vn.edu.vlu.trippocket.ui.expenses.ExpensesFragment
import vn.edu.vlu.trippocket.ui.itinerary.ItineraryFragment
import vn.edu.vlu.trippocket.ui.weather.WeatherFragment
import vn.edu.vlu.trippocket.util.Format
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/**
 * Trip detail: one bottom-navigation tab per module
 * (Screen 3 Itinerary, Screen 4 Weather, Screen 5 Expenses).
 */
class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private val viewModel: TripDetailViewModel by viewModels { TripDetailViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // BottomNavigationView pads itself for the gesture/navigation bar.
        binding.root.applySystemBarPadding(bottom = false)

        if (viewModel.trip.value == null) {
            finish()
            return
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        // On rotation the FragmentManager restores the current tab by itself.
        if (savedInstanceState == null) show(R.id.nav_itinerary)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trip.filterNotNull().collect { trip ->
                    binding.toolbar.title = trip.name
                    binding.toolbar.subtitle = Format.dateRange(trip.startDate, trip.endDate)
                }
            }
        }
    }

    private fun show(menuId: Int) {
        val fragment: Fragment = when (menuId) {
            R.id.nav_weather -> WeatherFragment()
            R.id.nav_expenses -> ExpensesFragment()
            else -> ItineraryFragment()
        }
        // Each module's ViewModel reads the id back from its SavedStateHandle.
        fragment.arguments = bundleOf(ARG_TRIP_ID to viewModel.tripId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.detailContainer, fragment)
            .commit()
    }

    companion object {
        fun newIntent(context: Context, tripId: String): Intent =
            Intent(context, TripDetailActivity::class.java).putExtra(ARG_TRIP_ID, tripId)
    }
}
