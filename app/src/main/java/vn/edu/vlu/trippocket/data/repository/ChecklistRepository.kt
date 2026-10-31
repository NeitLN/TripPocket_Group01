package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.local.dao.ChecklistDao
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.model.ChecklistItem
import vn.edu.vlu.trippocket.domain.Limits
import java.util.UUID

enum class ChecklistProblem { BLANK, TOO_LONG, NOT_FOUND }

class InvalidChecklistItemException(val problem: ChecklistProblem) : IllegalArgumentException(problem.name)

/**
 * The trip's packing list (I2). Every change goes through [TripWriter], so it marks the trip for
 * backup exactly once; a change that does nothing (same title, same tick) writes nothing.
 * The 100-item cap is checked again inside the transaction (proposal 6.2).
 */
class ChecklistRepository(
    private val checklistDao: ChecklistDao,
    private val tripWriter: TripWriter,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** In the order the items were added. */
    fun observe(tripId: String): Flow<List<ChecklistItem>> =
        checklistDao.observeByTrip(tripId).map { rows -> rows.map(::toModel) }

    /** Returns the new item's id. Throws [InvalidChecklistItemException] or LimitExceededException. */
    suspend fun add(tripId: String, title: String): String {
        val clean = validTitle(title)
        val id = newId()
        tripWriter.write(tripId) {
            Limits.check(checklistDao.countByTrip(tripId), Limits.MAX_CHECKLIST_ITEMS)
            checklistDao.insert(ChecklistItemEntity(id, tripId, clean, isDone = false))
        }
        return id
    }

    suspend fun rename(tripId: String, itemId: String, title: String) {
        val clean = validTitle(title)
        if (owned(tripId, itemId).title == clean) return
        tripWriter.write(tripId) { checklistDao.update(owned(tripId, itemId).copy(title = clean)) }
    }

    suspend fun setDone(tripId: String, itemId: String, done: Boolean) {
        if (owned(tripId, itemId).isDone == done) return
        tripWriter.write(tripId) { checklistDao.update(owned(tripId, itemId).copy(isDone = done)) }
    }

    /** Returns what was deleted, so the screen can offer Undo with [restore]. */
    suspend fun delete(tripId: String, itemId: String): ChecklistItem =
        tripWriter.write(tripId) {
            val row = owned(tripId, itemId)
            checklistDao.delete(row)
            toModel(row)
        }

    /** Puts a deleted item back with the same id (Undo). The cap still applies. */
    suspend fun restore(item: ChecklistItem) {
        tripWriter.write(item.tripId) {
            Limits.check(checklistDao.countByTrip(item.tripId), Limits.MAX_CHECKLIST_ITEMS)
            checklistDao.insert(ChecklistItemEntity(item.itemId, item.tripId, item.title, item.isDone))
        }
    }

    private fun validTitle(title: String): String {
        val clean = title.trim()
        return when {
            clean.isEmpty() -> throw InvalidChecklistItemException(ChecklistProblem.BLANK)
            clean.length > Limits.MAX_TEXT -> throw InvalidChecklistItemException(ChecklistProblem.TOO_LONG)
            else -> clean
        }
    }

    /** The row, only if it belongs to [tripId]. */
    private suspend fun owned(tripId: String, itemId: String): ChecklistItemEntity =
        checklistDao.get(itemId)?.takeIf { it.tripId == tripId }
            ?: throw InvalidChecklistItemException(ChecklistProblem.NOT_FOUND)

    private fun toModel(row: ChecklistItemEntity) = ChecklistItem(row.itemId, row.tripId, row.title, row.isDone)
}
