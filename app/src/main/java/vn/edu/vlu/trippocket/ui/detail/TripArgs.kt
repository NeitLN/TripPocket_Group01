package vn.edu.vlu.trippocket.ui.detail

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras

/**
 * Key of the trip id in TripDetailActivity's intent extras and in the SavedStateHandle
 * of every module ViewModel.
 */
const val ARG_TRIP_ID = "trip_id"

/**
 * `by viewModels`, plus the trip id of the hosting TripDetailActivity in the ViewModel's
 * SavedStateHandle under [ARG_TRIP_ID]. Use it for every ViewModel inside the trip detail:
 *
 * ```
 * private val viewModel: ItineraryViewModel by tripViewModels { ItineraryViewModel.Factory }
 * ```
 */
inline fun <reified VM : ViewModel> Fragment.tripViewModels(
    noinline factory: () -> ViewModelProvider.Factory,
): Lazy<VM> {
    val detail = activityViewModels<TripDetailViewModel> { TripDetailViewModel.Factory }
    return viewModels(
        extrasProducer = {
            MutableCreationExtras(defaultViewModelCreationExtras).apply {
                val args = Bundle(arguments ?: Bundle()).apply { putString(ARG_TRIP_ID, detail.value.tripId) }
                set(DEFAULT_ARGS_KEY, args)
            }
        },
        factoryProducer = factory,
    )
}
