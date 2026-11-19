package vn.edu.vlu.trippocket.util

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController

/**
 * Navigates only while the current screen still owns [actionId]. A fast double tap sends two
 * clicks before the first navigation finishes: the second one finds the form already on top,
 * which has no such action, so it is ignored instead of crashing with "action not found" or
 * opening the form twice.
 */
fun NavController.navigateOnce(@IdRes actionId: Int, args: Bundle? = null) {
    if (currentDestination?.getAction(actionId) == null) return
    navigate(actionId, args)
}
