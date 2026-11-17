package vn.edu.vlu.trippocket.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle distance between two points on Earth (Milestone 4, Nearby sights).
 *
 * Plain Kotlin so it can be used from a repository and tested without Android. Used to sort the
 * sights of Wikipedia by how far they are from the trip's saved place.
 */
object GeoDistance {

    /** Mean Earth radius, in metres. */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Haversine: metres between ([lat1], [lon1]) and ([lat2], [lon2]), in degrees. */
    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1).toRadians()
        val dLon = (lon2 - lon1).toRadians()
        val a = sin(dLat / 2).let { it * it } +
            cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }
}

private fun Double.toRadians(): Double = Math.toRadians(this)
