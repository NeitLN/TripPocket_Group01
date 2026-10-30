package vn.edu.vlu.trippocket.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import vn.edu.vlu.trippocket.R
import java.time.LocalDate

// Models of one module (owner: Tiến). Room entities live in data/local/entity.

enum class SyncStatus(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    SYNCED(R.string.sync_synced, R.drawable.ic_cloud_done),
    PENDING(R.string.sync_pending, R.drawable.ic_cloud_upload),
    FAILED(R.string.sync_failed, R.drawable.ic_cloud),
}

data class Trip(
    val tripId: String,
    val name: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val syncStatus: SyncStatus,
) {
    val dayCount: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
    fun dateOfDay(dayIndex: Int): LocalDate = startDate.plusDays(dayIndex.toLong())
}
