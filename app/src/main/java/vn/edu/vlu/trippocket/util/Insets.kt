package vn.edu.vlu.trippocket.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 35+ draws edge-to-edge, so the root layout pads itself by the
 * status bar, navigation bar and display cutout.
 *
 * @param bottom false when a child (BottomNavigationView) handles the bottom inset itself.
 */
fun View.applySystemBarPadding(bottom: Boolean = true) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(bars.left, bars.top, bars.right, if (bottom) bars.bottom else 0)
        if (bottom) WindowInsetsCompat.CONSUMED else insets
    }
}
