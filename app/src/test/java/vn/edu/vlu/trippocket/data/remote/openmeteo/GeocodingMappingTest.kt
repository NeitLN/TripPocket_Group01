package vn.edu.vlu.trippocket.data.remote.openmeteo

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0: the real Open-Meteo geocoding JSON maps to [vn.edu.vlu.trippocket.data.model.Place]. */
class GeocodingMappingTest {

    private val gson = Gson()

    private fun parse(file: String): GeocodingResponse =
        gson.fromJson(readResource(file), GeocodingResponse::class.java)

    @Test
    fun daLatSearch_mapsNameRegionCountryAndCoordinates() {
        val places = parse("geocoding_da_lat.json").toPlaces()

        assertEquals(2, places.size)
        val dalat = places.first()
        assertEquals("Da Lat", dalat.name)
        assertEquals("Lam Dong", dalat.region)
        assertEquals("Vietnam", dalat.country)
        assertEquals(11.94646, dalat.latitude, 0.0)
        assertEquals(108.44193, dalat.longitude, 0.0)
    }

    @Test
    fun emptySearch_hasNoResults_andIsNotAnError() {
        val response = parse("geocoding_empty.json")

        assertEquals(null, response.results)
        assertTrue(response.toPlaces().isEmpty())
    }

    @Test
    fun brokenRows_areDropped_andTheRestSurvives() {
        val json = """
            {
              "results": [
                {"name": "Nowhere", "latitude": 1.5, "longitude": 2.5},
                {"name": "No coordinates", "latitude": null, "longitude": null},
                {"latitude": 3.5, "longitude": 4.5},
                {"name": "  ", "latitude": 5.5, "longitude": 6.5},
                {"name": "Also fine", "latitude": 7.5, "longitude": 8.5, "admin1": "Somewhere", "country": "Vietnam"}
              ]
            }
        """.trimIndent()

        val places = gson.fromJson(json, GeocodingResponse::class.java).toPlaces()

        assertEquals(listOf("Nowhere", "Also fine"), places.map { it.name })
        assertEquals("", places.first().region)
        assertEquals("", places.first().country)
        assertEquals("Somewhere", places.last().region)
        assertEquals("Vietnam", places.last().country)
    }

    private fun readResource(file: String): String =
        requireNotNull(javaClass.getResource("/openmeteo/$file")) { "Missing test resource $file" }.readText()
}
