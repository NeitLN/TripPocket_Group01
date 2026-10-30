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
        if (BuildConfig.DEBUG) {
            appScope.launch {
                container.tripRepository.seedSampleTrips(DevAuthRepository.DEV_UID, System.currentTimeMillis())
            }
        }
    }
}

/** The app's container, for ViewModel factories: `initializer { X(appContainer().xRepository) }`. */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as TripPocketApp).container
