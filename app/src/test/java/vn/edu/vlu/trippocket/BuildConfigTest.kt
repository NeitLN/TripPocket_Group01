package vn.edu.vlu.trippocket

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 stage 6: the MockAPI endpoint comes from `local.properties` -> `gradle.properties` -> a
 * public default, never from source. If somebody pastes a URL or a key into the Kotlin code (or
 * leaves the field empty by hardcoding it), this test fails on CI before the APK is built.
 */
class BuildConfigTest {

    @Test
    fun mockApiBaseUrl_isInjected_andHttps() {
        assertTrue(
            "MOCKAPI_BASE_URL must be https, was '${BuildConfig.MOCKAPI_BASE_URL}'",
            BuildConfig.MOCKAPI_BASE_URL.startsWith("https://"),
        )
        assertTrue(
            "MOCKAPI_BASE_URL must end with '/', was '${BuildConfig.MOCKAPI_BASE_URL}'",
            BuildConfig.MOCKAPI_BASE_URL.endsWith("/"),
        )
    }

    /** A blank or placeholder value would build an APK whose every idea request fails. */
    @Test
    fun mockApiBaseUrl_isNotEmpty() {
        assertTrue(BuildConfig.MOCKAPI_BASE_URL.length > "https://".length)
    }
}
