package vn.edu.vlu.trippocket.di

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.edu.vlu.trippocket.BuildConfig
import vn.edu.vlu.trippocket.data.local.AppDatabase
import vn.edu.vlu.trippocket.data.local.TripChangeTracker
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.data.repository.DevAuthRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.reminder.ReminderReconciler
import vn.edu.vlu.trippocket.reminder.ReminderScheduler
import vn.edu.vlu.trippocket.reminder.ScheduledReminder
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

    /** Replaced by the Firebase Auth repository in A1. */
    val authRepository: AuthRepository = DevAuthRepository()

    val tripRepository: TripRepository by lazy { TripRepository(database.tripDao(), authRepository) }

    /** Mock data for the module screens until they move to Room. */
    val sampleTripRepository = SampleTripRepository()
    val weatherRepository = WeatherRepository()

    /** Retrofit for https://geocoding-api.open-meteo.com/ (place search). */
    val geocodingRetrofit: Retrofit by lazy { retrofit("https://geocoding-api.open-meteo.com/") }

    /** Retrofit for https://api.open-meteo.com/ (daily forecast). */
    val forecastRetrofit: Retrofit by lazy { retrofit("https://api.open-meteo.com/") }

    /** Replaced by the AlarmManager scheduler in I4. */
    val reminderScheduler: ReminderScheduler = object : ReminderScheduler {
        override fun schedule(reminder: ScheduledReminder) = Unit
        override fun cancel(activityId: String) = Unit
    }

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
