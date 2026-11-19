package vn.edu.vlu.trippocket.ui

import android.os.SystemClock
import android.view.View
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher

/** A string of the app under test (not of the test APK). */
fun string(@StringRes id: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

/**
 * Waits until a view matching [matcher] is on screen. Espresso waits for the main thread only;
 * the sample trips are written to Room in the background when the app starts, so the first
 * screen can need a moment before they appear.
 */
fun waitForView(matcher: Matcher<View>, timeoutMs: Long = 10_000) {
    val end = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        try {
            onView(matcher).check(matches(isDisplayed()))
            return
        } catch (e: Throwable) {
            if (SystemClock.uptimeMillis() > end) throw e
            SystemClock.sleep(200)
        }
    }
}

/** Two clicks in the same main-thread turn, before the first one's navigation has run. */
fun clickTwiceAtOnce(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isDisplayed()

    override fun getDescription(): String = "click twice before the screen changes"

    override fun perform(uiController: UiController, view: View) {
        view.performClick()
        view.performClick()
        uiController.loopMainThreadUntilIdle()
    }
}

/** A TextInputLayout that shows [text] as its error. */
fun hasInputError(text: String): Matcher<View> =
    object : BoundedMatcher<View, TextInputLayout>(TextInputLayout::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("TextInputLayout with error \"$text\"")
        }

        override fun matchesSafely(item: TextInputLayout): Boolean = item.error?.toString() == text
    }
