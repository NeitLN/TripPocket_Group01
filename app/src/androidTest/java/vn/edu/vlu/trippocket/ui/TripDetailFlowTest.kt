package vn.edu.vlu.trippocket.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.ui.trips.MainActivity

/** I1 and E2 on a device: adding an activity and an expense to the sample Đà Lạt trip. */
@RunWith(AndroidJUnit4::class)
class TripDetailFlowTest {

    @Before
    fun setUp() = clearTheScreen()

    private val dalat = MockData.trips.first { it.tripId == MockData.TRIP_DALAT }
    private val firstMember = MockData.membersOf(MockData.TRIP_DALAT).first().name

    /** "Day " as the dropdown writes it, from R.string.itinerary_day_simple ("Day %1$d"). */
    private fun dayLabelPrefix(): String = string(R.string.itinerary_day_simple).substringBefore("%")

    private fun openDalat(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForView(withText(dalat.name))
        onView(withText(dalat.name)).perform(click())
        waitForView(withId(R.id.fabAddActivity))
        return scenario
    }

    @Test
    fun aNewActivity_appearsInTheItinerary() {
        openDalat().use {
            onView(withId(R.id.fabAddActivity)).perform(click())
            // The form reads the trip from Room before it fills its fields; typing earlier would be
            // wiped by that fill. The day dropdown has a label only once loading is done.
            waitForView(allOf(withId(R.id.actvDay), withText(startsWith(dayLabelPrefix()))))
            onView(withId(R.id.etTitle)).perform(replaceText("Espresso stop"), closeSoftKeyboard())

            onView(withId(R.id.btnSave)).perform(scrollTo(), click())

            waitForView(withId(R.id.rvActivities))
            onView(withId(R.id.rvActivities)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText("Espresso stop")))
            )
            onView(withText("Espresso stop")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun aDoubleTapOnAdd_opensTheFormOnce() {
        openDalat().use {
            // Two taps that both land before the form replaces the itinerary. Espresso's
            // doubleClick() waits for the main thread between taps, so the second one hits the
            // form instead; clicking twice in one main-thread turn is what a fast finger does.
            // Before navigateOnce() the second click crashed with "action ... cannot be found";
            // now it is ignored and one Back returns to the itinerary.
            onView(withId(R.id.fabAddActivity)).perform(clickTwiceAtOnce())
            waitForView(withId(R.id.etTitle))

            onView(withId(R.id.etTitle)).perform(closeSoftKeyboard())
            pressBack()

            waitForView(withId(R.id.fabAddActivity))
        }
    }

    @Test
    fun aNewExpense_appearsInTheList() {
        openDalat().use {
            onView(withId(R.id.nav_expenses)).perform(click())
            waitForView(withId(R.id.fabAddExpense))
            onView(withId(R.id.fabAddExpense)).perform(click())
            // Same for the expense form: it reads the members first, then names the payer.
            waitForView(allOf(withId(R.id.actPayer), withText(firstMember)))
            onView(withId(R.id.etTitle)).perform(replaceText("Espresso coffee"))
            onView(withId(R.id.etAmount)).perform(replaceText("45000"), closeSoftKeyboard())

            onView(withId(R.id.btnSave)).perform(scrollTo(), click())

            waitForView(withId(R.id.rvExpenses))
            onView(withId(R.id.rvExpenses)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText("Espresso coffee")))
            )
            onView(withText("Espresso coffee")).check(matches(isDisplayed()))
        }
    }
}
