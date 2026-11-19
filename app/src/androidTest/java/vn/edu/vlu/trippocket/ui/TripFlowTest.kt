package vn.edu.vlu.trippocket.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.ui.trips.MainActivity

/**
 * A2 on a device: the trip list and the new-trip form. Runs on a debug build without
 * google-services.json (CI), where the offline stand-in account owns the sample trips.
 */
@RunWith(AndroidJUnit4::class)
class TripFlowTest {

    private val dalat = MockData.trips.first { it.tripId == MockData.TRIP_DALAT }

    @Test
    fun theSampleTrips_areListed_andOneOpensOnItsItinerary() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withText(dalat.name))

            onView(withText(dalat.name)).perform(click())

            waitForView(withText("Sleeper bus to Đà Lạt"))
        }
    }

    @Test
    fun savingAnEmptyTrip_showsWhatIsMissing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForView(withText(dalat.name))
            onView(withId(R.id.fabNewTrip)).perform(click())

            onView(withId(R.id.btnSave)).perform(click())

            onView(withId(R.id.tilName)).check(matches(hasInputError(string(R.string.trip_error_name_empty))))
            onView(withId(R.id.tilDates)).check(matches(hasInputError(string(R.string.trip_error_dates_missing))))
        }
    }

    @Test
    fun whatWasTyped_staysAfterTheScreenIsRecreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForView(withText(dalat.name))
            onView(withId(R.id.fabNewTrip)).perform(click())
            onView(withId(R.id.etName)).perform(replaceText("Espresso trip"))
            onView(withId(R.id.etDestination)).perform(replaceText("Huế"), closeSoftKeyboard())

            scenario.recreate() // what rotating the phone does

            onView(withId(R.id.etName)).check(matches(withText("Espresso trip")))
            onView(withId(R.id.etDestination)).check(matches(withText("Huế")))
        }
    }
}
