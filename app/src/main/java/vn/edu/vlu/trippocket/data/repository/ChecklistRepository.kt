package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.local.dao.ChecklistDao
import vn.edu.vlu.trippocket.data.local.entity.ChecklistItemEntity
import vn.edu.vlu.trippocket.data.model.ChecklistItem
import vn.edu.vlu.trippocket.domain.Limits
import java.util.UUID

enum class ChecklistProblem { BLANK, TOO_LONG, NOT_FOUND }

class InvalidChecklistItemException(val problem: ChecklistProblem) : IllegalArgumentException(problem.name)

/** A deleted item and where it stood, so Undo puts it back in the same place. */
data class DeletedChecklistItem(val item: ChecklistItem, val position: Long)

/**
 * The trip's packing list (I2). Every change goes through [TripWriter], so it marks the trip for
 * backup exactly once; a change that does nothing (same title, same tick) writes nothing.
 * The 100-item cap is checked again inside the transaction (proposal 6.2).
 *
 * Changes run one at a time ([mutex]): the "does it change anything" check and the write must
 * see the same row, or a quick tick-untick could read the old state twice and drop the untick.
 */
class ChecklistRepository(
    private val checklistDao: ChecklistDao,
    private val tripWriter: TripWriter,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    /** In the order the items were added. */
    fun observe(tripId: String): Flow<List<ChecklistItem>> =
        checklistDao.observeByTrip(tripId).map { rows -> rows.map(::toModel) }

    /** Returns the new item's id. Throws [InvalidChecklistItemException] or LimitExceededException. */
    suspend fun add(tripId: String, title: String): String {
        val clean = validTitle(title)
        val id = newId()
        mutex.withLock {
            tripWriter.write(tripId) {
                Limits.check(checklistDao.countByTrip(tripId), Limits.MAX_CHECKLIST_ITEMS)
                checklistDao.insert(ChecklistItemEntity(id, tripId, clean, isDone = false))
            }
        }
        return id
    }

    suspend fun rename(tripId: String, itemId: String, title: String) {
        val clean = validTitle(title)
        mutex.withLock {
            val row = owned(tripId, itemId)
            if (row.title == clean) return
            tripWriter.write(tripId) { checklistDao.update(row.copy(title = clean)) }
        }
    }

    suspend fun setDone(tripId: String, itemId: String, done: Boolean) {
        mutex.withLock {
            val row = owned(tripId, itemId)
            if (row.isDone == done) return
            tripWriter.write(tripId) { checklistDao.update(row.copy(isDone = done)) }
        }
    }

    /** Returns what was deleted and where, so the screen can offer Undo with [restore]. */
    suspend fun delete(tripId: String, itemId: String): DeletedChecklistItem = mutex.withLock {
        tripWriter.write(tripId) {
            val row = owned(tripId, itemId)
            val position = checklistDao.positionOf(itemId) ?: throw InvalidChecklistItemException(ChecklistProblem.NOT_FOUND)
            checklistDao.delete(row)
            DeletedChecklistItem(toModel(row), position)
        }
    }

    /**
     * Puts a deleted item back with the same id at the same place (Undo). If a new item took that
     * place meanwhile, it goes to the end instead. The cap still applies.
     */
    suspend fun restore(deleted: DeletedChecklistItem) {
        val item = deleted.item
        mutex.withLock {
            tripWriter.write(item.tripId) {
                Limits.check(checklistDao.countByTrip(item.tripId), Limits.MAX_CHECKLIST_ITEMS)
                // Check first instead of catching the constraint error: a failed statement inside
                // Room's nested transaction would silently roll back the whole write.
                if (checklistDao.countAt(deleted.position) == 0) {
                    checklistDao.insertAt(deleted.position, item.itemId, item.tripId, item.title, item.isDone)
                } else {
                    checklistDao.insert(ChecklistItemEntity(item.itemId, item.tripId, item.title, item.isDone))
                }
            }
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
