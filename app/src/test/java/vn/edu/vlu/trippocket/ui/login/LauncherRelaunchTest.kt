package vn.edu.vlu.trippocket.ui.login

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tapping the app icon while the app is open must not stack a second trip list (final check, 20/11). */
@RunWith(RobolectricTestRunner::class)
class LauncherRelaunchTest {

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Test
    fun theIconTappedWhileTheTaskIsAlive_isARelaunch() {
        assertTrue(isLauncherRelaunch(isTaskRoot = false, intent = launcherIntent()))
    }

    @Test
    fun aColdStartFromTheIcon_showsLoginAsUsual() {
        assertFalse(isLauncherRelaunch(isTaskRoot = true, intent = launcherIntent()))
    }

    @Test
    fun theLoginScreenOpenedAfterSignOut_isNotARelaunch() {
        assertFalse(isLauncherRelaunch(isTaskRoot = false, intent = Intent()))
        assertFalse(isLauncherRelaunch(isTaskRoot = false, intent = null))
    }
}
