package vn.edu.vlu.trippocket.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.edu.vlu.trippocket.data.local.TripWriter
import vn.edu.vlu.trippocket.data.local.dao.SavedPlaceDao
import vn.edu.vlu.trippocket.data.local.entity.SavedPlaceEntity
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.data.model.SavedPlace
import vn.edu.vlu.trippocket.domain.Limits
import java.util.UUID

/** Why a saved place was not written; the whole write is rolled back. */
class InvalidPlaceException(val problem: PlaceProblem) : IllegalArgumentException("Invalid place: $problem")

enum class PlaceProblem { BLANK_NAME, NAME_TOO_LONG, NOTE_TOO_LONG, NOT_FOUND }

/**
 * W2 (Minh, Weather module of Duy Anh): the places saved to a trip, in Room (proposal 3.3).
 * Each place has a UUID, so two places with the same name at different coordinates stay
 * separate. At most 30 per trip; name up to 80 characters, note up to 300. Every write goes
 * through TripWriter, so it is part of the trip's backup.
 *
 * Search results and forecasts stay in WeatherRepository (W1, W3). Saving a search result
 * here and then loading its forecast is the WeatherViewModel's job.
 */
class SavedPlaceRepository(
    private val dao: SavedPlaceDao,
    private val tripWriter: TripWriter,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** In the order they were saved. */
    fun observePlaces(tripId: String): Flow<List<SavedPlace>> =
        dao.observeByTrip(tripId).map { rows -> rows.map(::toModel) }

    /**
     * Saves a search result to the trip and returns its id. Saving the same coordinates again
     * returns the existing place instead of a copy. Names from the API longer than 80
     * characters are shortened. Throws [vn.edu.vlu.trippocket.domain.LimitExceededException]
     * for the 31st place.
     */
    suspend fun savePlace(tripId: String, place: Place): String {
        // Saving the same coordinates again is not a change: no new row, no backup.
        dao.findAt(tripId, place.latitude, place.longitude)?.let { return it.placeId }
        val name = place.name.trim().take(Limits.MAX_TEXT)
        if (name.isEmpty()) throw InvalidPlaceException(PlaceProblem.BLANK_NAME)
        return tripWriter.write(tripId) {
            // Checked again inside the transaction: two quick taps that both passed the check
            // above must still end with one row (this rare path costs one revision).
            dao.findAt(tripId, place.latitude, place.longitude)?.let { return@write it.placeId }
            Limits.check(dao.countByTrip(tripId), Limits.MAX_PLACES)
            val id = newId()
            dao.insert(
                SavedPlaceEntity(
                    placeId = id,
                    tripId = tripId,
                    name = name,
                    country = place.country.trim().take(Limits.MAX_TEXT),
                    latitude = place.latitude,
                    longitude = place.longitude,
                    note = "",
                )
            )
            id
        }
    }

    suspend fun renamePlace(tripId: String, placeId: String, name: String) {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> throw InvalidPlaceException(PlaceProblem.BLANK_NAME)
            trimmed.length > Limits.MAX_TEXT -> throw InvalidPlaceException(PlaceProblem.NAME_TOO_LONG)
        }
        edit(tripId, placeId) { it.copy(name = trimmed) }
    }

    suspend fun updateNote(tripId: String, placeId: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.length > Limits.MAX_NOTE) throw InvalidPlaceException(PlaceProblem.NOTE_TOO_LONG)
        edit(tripId, placeId) { it.copy(note = trimmed) }
    }

    /** Its cached forecast is removed with it. */
    suspend fun deletePlace(tripId: String, placeId: String) {
        tripWriter.write(tripId) {
            if (dao.deleteById(tripId, placeId) == 0) throw InvalidPlaceException(PlaceProblem.NOT_FOUND)
        }
    }

    private suspend fun edit(tripId: String, placeId: String, change: (SavedPlaceEntity) -> SavedPlaceEntity) {
        tripWriter.write(tripId) {
            val row = dao.get(tripId, placeId) ?: throw InvalidPlaceException(PlaceProblem.NOT_FOUND)
            dao.update(change(row))
        }
    }

    private fun toModel(row: SavedPlaceEntity) = SavedPlace(
        placeId = row.placeId,
        tripId = row.tripId,
        name = row.name,
        country = row.country,
        latitude = row.latitude,
        longitude = row.longitude,
        note = row.note,
    )
}
