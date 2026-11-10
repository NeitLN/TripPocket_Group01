package vn.edu.vlu.trippocket

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.data.repository.DevAuthRepository
import vn.edu.vlu.trippocket.di.AppContainer

class TripPocketApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Work that must outlive a screen, such as seeding or scheduling. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Sample trips belong to the offline stand-in account only; a real Firebase account
        // starts with an empty list, like a real user.
        if (BuildConfig.DEBUG && container.usesDevAuth) {
            appScope.launch {
                val now = System.currentTimeMillis()
                container.tripRepository.seedSampleTrips(DevAuthRepository.DEV_UID, now)
                container.expenseRepository.seedSampleExpenses(now)
                container.itineraryRepository.seedSampleActivities()
            }
        }
    }
}

/** The app's container, for ViewModel factories: `initializer { X(appContainer().xRepository) }`. */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as TripPocketApp).container
