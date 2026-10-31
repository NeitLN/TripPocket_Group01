package vn.edu.vlu.trippocket.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.edu.vlu.trippocket.BuildConfig
import vn.edu.vlu.trippocket.data.local.AppDatabase
import vn.edu.vlu.trippocket.data.local.TripChangeTracker
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.remote.firebase.FirebaseAuthRepository
import vn.edu.vlu.trippocket.data.remote.openmeteo.ForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.GeocodingApi
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.data.repository.DevAuthRepository
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.reminder.AlarmReminderScheduler
import vn.edu.vlu.trippocket.reminder.ReminderReconciler
import vn.edu.vlu.trippocket.reminder.ReminderScheduler
import vn.edu.vlu.trippocket.work.BackupScheduler

/**
 * Manual dependency injection: one instance of each repository for the whole app.
 * ViewModel factories read it through [vn.edu.vlu.trippocket.appContainer]; nothing else
 * creates repositories. Owner: Tiến; send the line to add for your repository.
 *
 * Firebase is not touched here, so the app and every test run without google-services.json.
 */
class AppContainer(context: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    /** Replaced by the WorkManager scheduler in A3. */
    val backupScheduler: BackupScheduler = BackupScheduler { }

    val tripWriter: TripWriter by lazy {
        TripWriter(database, TripChangeTracker(database.tripDao()), backupScheduler)
    }

    /**
     * Firebase Auth (A1) when the app was built with `app/google-services.json`, so Firebase
     * initialised itself at start-up. Without the file (CI, a fresh clone) the app keeps the
     * offline stand-in, always signed in as DevAuthRepository.DEV_UID.
     */
    val authRepository: AuthRepository =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseAuthRepository(FirebaseAuth.getInstance())
        } else {
            DevAuthRepository()
        }

    /** True when sign-in is the offline stand-in; the sample trips belong to it. */
    val usesDevAuth: Boolean get() = authRepository is DevAuthRepository

    val tripRepository: TripRepository by lazy {
        TripRepository(
            database.tripDao(),
            authRepository,
            tripWriter,
            lastUsedDay = { tripId -> itineraryRepository.lastUsedDayIndex(tripId) },
            syncReminders = { uid -> reminderReconciler.reconcile(uid) },
        )
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(database.tripMemberDao(), database.expenseDao(), tripWriter)
    }

    /** I1: the activities of a trip, in Room. */
    val itineraryRepository: ItineraryRepository by lazy {
        ItineraryRepository(database.activityDao(), tripWriter) { activityId ->
            reminderReconciler.syncActivity(activityId, authRepository.currentUid.value)
        }
    }

    /** Mock data for the module screens until they move to Room. */
    val sampleTripRepository = SampleTripRepository()
    val weatherRepository by lazy {
        WeatherRepository(
            geocodingRetrofit.create(GeocodingApi::class.java),
            forecastRetrofit.create(ForecastApi::class.java),
            database.weatherCacheDao(),
            database.savedPlaceDao(),
        )
    }

    /** W2: places saved to a trip, in Room. */
    val savedPlaceRepository: SavedPlaceRepository by lazy {
        SavedPlaceRepository(database.savedPlaceDao(), tripWriter)
    }

    /** Retrofit for https://geocoding-api.open-meteo.com/ (place search). */
    val geocodingRetrofit: Retrofit by lazy { retrofit("https://geocoding-api.open-meteo.com/") }

    /** Retrofit for https://api.open-meteo.com/ (daily forecast). */
    val forecastRetrofit: Retrofit by lazy { retrofit("https://api.open-meteo.com/") }

    /** I4: activity alarms on AlarmManager (exact when allowed, inexact otherwise). */
    val reminderScheduler: ReminderScheduler = AlarmReminderScheduler(context)

    /** I5: rebuilds every reminder alarm (boot, time zone, clock, sign-in/out, restore, trip dates). */
    val reminderReconciler: ReminderReconciler by lazy { ReminderReconciler(database.activityDao(), reminderScheduler) }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
