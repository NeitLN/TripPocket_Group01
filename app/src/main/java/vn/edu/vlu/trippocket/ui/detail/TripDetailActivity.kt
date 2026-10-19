package vn.edu.vlu.trippocket.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.MockData
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
    private lateinit var tripId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // BottomNavigationView pads itself for the gesture/navigation bar.
        binding.root.applySystemBarPadding(bottom = false)

        val trip = intent.getStringExtra(EXTRA_TRIP_ID)?.let(MockData::trip)
        if (trip == null) {
            finish()
            return
        }
        tripId = trip.tripId
        binding.toolbar.title = trip.name
        binding.toolbar.subtitle = Format.dateRange(trip.startDate, trip.endDate)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        // On rotation the FragmentManager restores the current tab by itself.
        if (savedInstanceState == null) show(R.id.nav_itinerary)
    }

    private fun show(menuId: Int) {
        val fragment: Fragment = when (menuId) {
            R.id.nav_weather -> WeatherFragment()
            R.id.nav_expenses -> ExpensesFragment()
            else -> ItineraryFragment()
        }
        fragment.arguments = Bundle().apply { putString(ARG_TRIP_ID, tripId) }
        supportFragmentManager.beginTransaction()
            .replace(R.id.detailContainer, fragment)
            .commit()
    }

    companion object {
        const val ARG_TRIP_ID = "trip_id"
        private const val EXTRA_TRIP_ID = "extra_trip_id"

        fun newIntent(context: Context, tripId: String): Intent =
            Intent(context, TripDetailActivity::class.java).putExtra(EXTRA_TRIP_ID, tripId)
    }
}

/** Trip id passed to every module fragment by [TripDetailActivity]. */
val Fragment.tripId: String
    get() = requireArguments().getString(TripDetailActivity.ARG_TRIP_ID).orEmpty()
