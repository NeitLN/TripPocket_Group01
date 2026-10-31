package vn.edu.vlu.trippocket.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.ActivityTripDetailBinding
import vn.edu.vlu.trippocket.ui.trips.TripFormDialogFragment
import vn.edu.vlu.trippocket.util.Format
import vn.edu.vlu.trippocket.util.applySystemBarPadding

/**
 * Trip detail: one bottom-navigation tab per module. Each tab is a nested graph owned by
 * its module (res/navigation/nav_<module>.xml), included in nav_trip_detail.xml.
 */
class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private val viewModel: TripDetailViewModel by viewModels { TripDetailViewModel.Factory }
    private lateinit var navController: NavController
    private var graphSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // BottomNavigationView pads itself for the gesture/navigation bar.
        binding.root.applySystemBarPadding(bottom = false)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        setUpTripMenu()

        navController = (supportFragmentManager.findFragmentById(R.id.detailContainer) as NavHostFragment).navController

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gate.collect { gate ->
                    when (gate) {
                        TripGate.Loading -> Unit
                        TripGate.NotFound -> finish()
                        is TripGate.Ready -> {
                            binding.toolbar.title = gate.trip.name
                            binding.toolbar.subtitle = Format.dateRange(gate.trip.startDate, gate.trip.endDate)
                            binding.toolbar.menu.setGroupVisible(0, true)
                            showTabs()
                        }
                    }
                }
            }
        }
    }

    /** A2: Edit trip opens the trip form; Delete trip asks first. Shown once the trip is Ready. */
    private fun setUpTripMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_trip_options)
        binding.toolbar.menu.setGroupVisible(0, false)
        binding.toolbar.setOnMenuItemClickListener { item ->
            val trip = (viewModel.gate.value as? TripGate.Ready)?.trip ?: return@setOnMenuItemClickListener false
            when (item.itemId) {
                R.id.action_edit_trip -> {
                    TripFormDialogFragment.show(supportFragmentManager, trip.tripId)
                    true
                }
                R.id.action_delete_trip -> {
                    ConfirmDeleteTripDialogFragment.newInstance(trip.name)
                        .show(supportFragmentManager, ConfirmDeleteTripDialogFragment.TAG)
                    true
                }
                else -> false
            }
        }
        supportFragmentManager.setFragmentResultListener(ConfirmDeleteTripDialogFragment.REQUEST_KEY, this) { _, bundle ->
            if (bundle.getBoolean(ConfirmDeleteTripDialogFragment.RESULT_CONFIRMED)) viewModel.deleteTrip()
        }
    }

    /**
     * The tabs only start once the trip is known to belong to the signed-in account. After
     * recreation, setting the graph again restores the saved back stack of every tab.
     */
    private fun showTabs() {
        if (graphSet) return
        graphSet = true
        navController.setGraph(R.navigation.nav_trip_detail)
        binding.bottomNav.setupWithNavController(navController)
    }

    /** Switches the bottom navigation to another tab, e.g. Itinerary's "Packing" button -> Checklist. */
    fun showTab(menuItemId: Int) {
        binding.bottomNav.selectedItemId = menuItemId
    }

    companion object {
        fun newIntent(context: Context, tripId: String): Intent =
            Intent(context, TripDetailActivity::class.java).putExtra(ARG_TRIP_ID, tripId)
    }
}
