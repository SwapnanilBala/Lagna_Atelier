package site.lagnaatelier.app.palm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.palm.PalmImageMath
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.data.palm.PalmRequests
import java.util.Base64

class PalmImageMathTest {
    @Test
    fun scalesTheLongEdgeDownAndNeverUp() {
        assertEquals(0.4, PalmImageMath.scaleFor(4000, 3000, 1600), 1e-9)
        assertEquals(0.4, PalmImageMath.scaleFor(3000, 4000, 1600), 1e-9)
        assertEquals(1.0, PalmImageMath.scaleFor(1200, 900, 1600), 1e-9)
        assertEquals(1.0, PalmImageMath.scaleFor(0, 0, 1600), 1e-9)
    }

    @Test
    fun targetSizeKeepsTheShape() {
        assertEquals(1600 to 1200, PalmImageMath.targetSize(4000, 3000, 1600))
        assertEquals(1200 to 1600, PalmImageMath.targetSize(3024, 4032, 1600))
        assertEquals(720 to 540, PalmImageMath.targetSize(1600, 1200, PalmImageMath.HISTORY_EDGE))
        assertEquals(1 to 1600, PalmImageMath.targetSize(1, 5000, 1600))
    }

    @Test
    fun subsamplesInPowersOfTwoWithoutGoingUnderTheEdge() {
        assertEquals(1, PalmImageMath.sampleSizeFor(1600, 1200, 1600))
        assertEquals(1, PalmImageMath.sampleSizeFor(3000, 2000, 1600))
        assertEquals(2, PalmImageMath.sampleSizeFor(4032, 3024, 1600))
        assertEquals(4, PalmImageMath.sampleSizeFor(8000, 6000, 1600))
        for (edge in listOf(1700, 3300, 4032, 6400, 12000)) {
            assertTrue(edge / PalmImageMath.sampleSizeFor(edge, edge, 1600) >= 1600)
        }
    }

    @Test
    fun theUploadLimitSitsUnderTheRoutes() {
        assertTrue(PalmImageMath.UPLOAD_MAX_BYTES < PalmImageMath.ROUTE_MAX_BYTES)
        assertTrue(PalmImageMath.fitsUpload(PalmImageMath.UPLOAD_MAX_BYTES))
        assertFalse(PalmImageMath.fitsUpload(PalmImageMath.UPLOAD_MAX_BYTES + 1))
        assertFalse(PalmImageMath.fitsUpload(0))
    }

    @Test
    fun theRoutesEstimateOfTheBase64MatchesTheBytes() {
        for (size in listOf(1, 2, 3, 1000, 4 * 1024 * 1024)) {
            val encoded = Base64.getEncoder().encodeToString(ByteArray(size))
            assertEquals(PalmImageMath.base64Length(size), encoded.length)
            assertEquals(size, PalmImageMath.routeEstimate(encoded))
        }
        /* The largest upload is still under what the route refuses. */
        val largest = Base64.getEncoder().encodeToString(ByteArray(PalmImageMath.UPLOAD_MAX_BYTES))
        assertTrue(PalmImageMath.routeEstimate(largest) <= PalmImageMath.ROUTE_MAX_BYTES)
    }

    @Test
    fun readsTheExifOrientations() {
        assertEquals(0 to false, PalmImageMath.orientation(1))
        assertEquals(0 to false, PalmImageMath.orientation(0))
        assertEquals(0 to true, PalmImageMath.orientation(2))
        assertEquals(180 to false, PalmImageMath.orientation(3))
        assertEquals(180 to true, PalmImageMath.orientation(4))
        assertEquals(90 to true, PalmImageMath.orientation(5))
        assertEquals(90 to false, PalmImageMath.orientation(6))
        assertEquals(270 to true, PalmImageMath.orientation(7))
        assertEquals(270 to false, PalmImageMath.orientation(8))
    }

    @Test
    fun theLargestUploadFitsVercelsBodyLimitOnceEncoded() {
        val body = PalmRequests.readingBody(
            Base64.getEncoder().encodeToString(ByteArray(PalmImageMath.UPLOAD_MAX_BYTES)),
            "image/jpeg",
            classicalMode = true,
            reader = PalmReader.Woman,
            jyotishContext = null,
        )
        /* Vercel refuses a function body over 4.5 MB before the route sees it. */
        assertTrue(body.toByteArray(Charsets.UTF_8).size < 4_500_000)
    }

    @Test
    fun theSizePassIsJudgedByItsSizesNotItsReturnValue() {
        /* A readable photo: the stream opened and the decoder filled in its size. */
        assertTrue(PalmImageMath.boundsReadable(streamOpened = true, width = 1200, height = 1600))
        /* No stream (a revoked or missing URI), or a file that is not a picture. */
        assertFalse(PalmImageMath.boundsReadable(streamOpened = false, width = 0, height = 0))
        assertFalse(PalmImageMath.boundsReadable(streamOpened = true, width = -1, height = -1))
        assertFalse(PalmImageMath.boundsReadable(streamOpened = true, width = 1200, height = 0))
    }
}
