package site.lagnaatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.PlaceOffsets
import java.io.IOException
import java.time.LocalDate

class PlaceOffsetsTest {
    @Test
    fun worksTheOffsetOutForTheBirthSeason() {
        assertEquals(-240, PlaceOffsets.offsetMinutes("America/New_York", LocalDate.of(1985, 7, 4), "08:15"))
        assertEquals(-300, PlaceOffsets.offsetMinutes("America/New_York", LocalDate.of(1990, 1, 15), "08:15"))
        assertEquals(60, PlaceOffsets.offsetMinutes("Europe/London", LocalDate.of(1992, 7, 1), "18:30"))
        assertEquals(330, PlaceOffsets.offsetMinutes("Asia/Kolkata", LocalDate.of(1990, 6, 15), null))
        assertNull(PlaceOffsets.offsetMinutes("", LocalDate.of(1990, 6, 15), "12:00"))
        assertNull(PlaceOffsets.offsetMinutes("Not/AZone", LocalDate.of(1990, 6, 15), "12:00"))
    }

    @Test
    fun neverTakesAMissingOrImpossibleOffsetAsUtc() {
        val london = LagnaApi.PlaceSuggestion("London", "London, England", "England", "United Kingdom")
        val missing = LagnaApi.parseGeocode(
            """{"found":true,"lat":51.5,"lon":-0.12,"timeZoneId":"Europe/London"}""", london, LocalDate.of(1992, 7, 1), "18:30",
        )
        assertEquals(60, missing!!.timezoneOffsetMinutes)
        /* Forty hours is no zone's offset: the zone decides instead. */
        val wild = LagnaApi.parseGeocode(
            """{"found":true,"lat":51.5,"lon":-0.12,"timeZoneId":"Europe/London","timezoneOffsetMinutes":2400}""",
            london, LocalDate.of(1992, 7, 1), "12:00",
        )
        assertEquals(60, wild!!.timezoneOffsetMinutes)
        try {
            LagnaApi.parseGeocode("""{"found":true,"lat":51.5,"lon":-0.12,"timezoneOffsetMinutes":99999}""", london, null, "12:00")
            fail("A place with no usable offset must be refused")
        } catch (_: IOException) {
        }
        assertNull(LagnaApi.parseGeocode("""{"found":false}""", london, null, "12:00"))
    }

    @Test
    fun refusesAResponseOverItsCap() {
        assertEquals("abc", LagnaApi.readCapped("abc".byteInputStream(), 3))
        try {
            LagnaApi.readCapped(ByteArray(40_000).inputStream(), 32_000)
            fail("Over the cap must throw")
        } catch (_: LagnaApi.ResponseTooLarge) {
        }
        try {
            LagnaApi.capped(ByteArray(10).inputStream(), 8).readBytes()
            fail("Over the cap must throw")
        } catch (_: LagnaApi.ResponseTooLarge) {
        }
    }
}
