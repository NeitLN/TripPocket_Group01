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
import vn.edu.vlu.trippocket.data.remote.exchangerate.ExchangeRateApi
import vn.edu.vlu.trippocket.data.remote.mockapi.TripIdeaApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.ForecastApi
import vn.edu.vlu.trippocket.data.remote.openmeteo.GeocodingApi
import vn.edu.vlu.trippocket.data.remote.wikipedia.PageSummaryApi
import vn.edu.vlu.trippocket.data.repository.AuthRepository
import vn.edu.vlu.trippocket.data.repository.ChecklistRepository
import vn.edu.vlu.trippocket.data.repository.DevAuthRepository
import vn.edu.vlu.trippocket.data.repository.ExchangeRateRepository
import vn.edu.vlu.trippocket.data.repository.ExpenseRepository
import vn.edu.vlu.trippocket.data.repository.ItineraryRepository
import vn.edu.vlu.trippocket.data.repository.PlaceInfoRepository
import vn.edu.vlu.trippocket.data.repository.SampleTripRepository
import vn.edu.vlu.trippocket.data.repository.SavedPlaceRepository
import vn.edu.vlu.trippocket.data.repository.TripIdeaRepository
import vn.edu.vlu.trippocket.data.repository.TripRepository
import vn.edu.vlu.trippocket.data.repository.WeatherRepository
import vn.edu.vlu.trippocket.reminder.AlarmReminderScheduler
import vn.edu.vlu.trippocket.reminder.ReminderReconciler
import vn.edu.vlu.trippocket.reminder.ReminderScheduler
import vn.edu.vlu.trippocket.work.BackupScheduler
import vn.edu.vlu.trippocket.util.APP_USER_AGENT
import java.util.concurrent.TimeUnit

/**
 * Manual dependency injection: one instance of each repository for the whole app.
 * ViewModel factories read it through [vn.edu.vlu.trippocket.appContainer]; nothing else
 * creates repositories. Owner: Tiến; send the line to add for your repository.
 *
 * Firebase is not touched here, so the app and every test run without google-services.json.
 */
class AppContainer(context: Context) {

    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    /** Replaced by the WorkManager scheduler in A3. */
    val backupScheduler: BackupScheduler = BackupScheduler { }

    val tripWriter: TripWriter by lazy {
        TripWriter(database, TripChangeTracker(database.tripDao()), backupScheduler)
    }

    /**
     * True when the app was built without `app/google-services.json` (CI, a fresh clone), so
     * Firebase did not initialise and sign-in is the offline stand-in that owns the sample trips.
     */
    val usesDevAuth: Boolean by lazy { FirebaseApp.getApps(context).isEmpty() }

    /**
     * Firebase Auth (A1), created on first use like every Firebase service (docs/ARCHITECTURE.md).
     * The stand-in, always signed in as DevAuthRepository.DEV_UID, exists only in debug builds:
     * a release build without the Firebase config fails loudly instead of silently never
     * signing in or backing up.
     */
    val authRepository: AuthRepository by lazy {
        when {
            !usesDevAuth -> FirebaseAuthRepository(FirebaseAuth.getInstance())
            BuildConfig.DEBUG -> DevAuthRepository()
            else -> error("Release build without app/google-services.json: Firebase is not configured")
        }
    }

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
    val checklistRepository: ChecklistRepository by lazy {
        ChecklistRepository(database.checklistDao(), tripWriter)
    }

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

    /** M4: photos and a short introduction of a place, from Wikipedia. */
    val placeInfoRepository by lazy {
        PlaceInfoRepository(wikipediaRetrofit.create(PageSummaryApi::class.java))
    }

    /** Retrofit for https://geocoding-api.open-meteo.com/ (place search). */
    val geocodingRetrofit: Retrofit by lazy { retrofit("https://geocoding-api.open-meteo.com/") }

    /** Retrofit for https://api.open-meteo.com/ (daily forecast). */
    val forecastRetrofit: Retrofit by lazy { retrofit("https://api.open-meteo.com/") }

    /**
     * Retrofit for https://en.wikipedia.org/ (Milestone 4): GeoSearch for nearby sights (Bảo,
     * Itinerary) and the page summary with a photo for a place (Duy Anh, Weather). Each feature
     * creates its own interface from this instance.
     */
    val wikipediaRetrofit: Retrofit by lazy { retrofit("https://en.wikipedia.org/") }

    /** Retrofit for https://open.er-api.com/ (Milestone 4): exchange rates for expenses (Minh). */
    val exchangeRateRetrofit: Retrofit by lazy { retrofit("https://open.er-api.com/") }
    val exchangeRateRepository: ExchangeRateRepository by lazy { ExchangeRateRepository(exchangeRateRetrofit.create(ExchangeRateApi::class.java)) }

    /** I4: activity alarms on AlarmManager (exact when allowed, inexact otherwise). */
    val reminderScheduler: ReminderScheduler = AlarmReminderScheduler(context)

    /** I5: rebuilds every reminder alarm (boot, time zone, clock, sign-in/out, restore, trip dates). */
    val reminderReconciler: ReminderReconciler by lazy { ReminderReconciler(database.activityDao(), reminderScheduler) }

    /** Retrofit for the team's MockAPI.io project (Trip Ideas CRUD, Milestone 4). */
    val mockApiRetrofit: Retrofit by lazy { retrofit(BuildConfig.MOCKAPI_BASE_URL) }

    /** Trip Ideas on MockAPI.io (Milestone 4, Tiến). */
    val tripIdeaRepository: TripIdeaRepository by lazy {
        TripIdeaRepository(mockApiRetrofit.create(TripIdeaApi::class.java), authRepository)
    }

    /**
     * One OkHttp client for every API (Milestone 4): explicit timeouts so a dead network ends in
     * ApiError.TIMEOUT instead of a spinner that never stops, and a User-Agent naming the app
     * (Wikipedia's API policy asks for one).
     */
    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", APP_USER_AGENT)
                        .build()
                )
            }
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
