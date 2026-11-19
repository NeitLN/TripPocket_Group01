package vn.edu.vlu.trippocket.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Schema v2 (A1): the last answer of each online API, so the screens still show something in
// Airplane Mode. Like weather_cache these rows are never backed up: they belong to the server
// and are fetched again after a restore.

/** One trip idea from MockAPI.io, in the order the server listed it. */
@Entity(tableName = "trip_idea_cache")
data class TripIdeaCacheEntity(
    @PrimaryKey val id: String,
    /** Position in the last list the server returned. */
    val position: Int,
    val title: String,
    val destination: String,
    val days: Int,
    val budgetVnd: Long,
    val coverUrl: String?,
    val summary: String,
    val author: String,
    val authorUid: String?,
    /** Epoch millis of the download that wrote this row. */
    val fetchedAt: Long,
)

/** The last rates of one base currency from ExchangeRate-API, kept as the JSON map it sent. */
@Entity(tableName = "exchange_rate_cache")
data class ExchangeRateCacheEntity(
    /** "USD": the API's base currency. */
    @PrimaryKey val baseCurrency: String,
    /** Currency code -> units per 1 base currency, e.g. {"VND": 25704.0, "EUR": 0.9}. */
    val ratesJson: String,
    /** As the API wrote it ("Fri, 20 Nov 2026 00:00:01 +0000"), shown under the list. */
    val lastUpdatedUtc: String,
    /** Epoch millis. */
    val fetchedAt: Long,
)

/**
 * The Wikipedia summary of one page title. [title] null records "Wikipedia has no article", so
 * an offline place without a photo does not show an error either.
 */
@Entity(tableName = "place_info_cache")
data class PlaceInfoCacheEntity(
    /** Page title as requested, with underscores ("Da_Lat"). */
    @PrimaryKey val page: String,
    val title: String?,
    val description: String?,
    val extract: String?,
    val imageUrl: String?,
    /** Epoch millis. */
    val fetchedAt: Long,
)
