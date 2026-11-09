package vn.edu.vlu.trippocket.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Milestone 4: the haversine distance used to sort the nearby sights by distance. */
class GeoDistanceTest {

    @Test
    fun theSamePoint_isZeroMetres() {
        assertEquals(0.0, GeoDistance.meters(11.9404, 108.4583, 11.9404, 108.4583), 0.000001)
    }

    @Test
    fun oneDegreeOfLatitude_isAbout111Kilometres() {
        // 1 degree of latitude on a 6 371 km sphere: 6 371 000 * PI / 180.
        assertEquals(111_194.9, GeoDistance.meters(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun aPointJustNorthOfDaLat_isAbout111MetresAway() {
        assertEquals(111.2, GeoDistance.meters(11.9404, 108.4583, 11.9414, 108.4583), 0.5)
    }

    @Test
    fun daLatToHoXuanHuongLake_isAbout1Point5Kilometres() {
        // Ho Xuan Huong lake, west of the city centre.
        assertEquals(1_519.2, GeoDistance.meters(11.9404, 108.4583, 11.9417, 108.4444), 2.0)
    }

    @Test
    fun theDistanceIsTheSameInBothDirections() {
        val there = GeoDistance.meters(11.9404, 108.4583, 45.5231, -122.6765)
        val back = GeoDistance.meters(45.5231, -122.6765, 11.9404, 108.4583)
        assertEquals(there, back, 0.000001)
    }
}
