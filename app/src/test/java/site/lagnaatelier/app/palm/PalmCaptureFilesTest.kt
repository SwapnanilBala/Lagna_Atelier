package site.lagnaatelier.app.palm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import site.lagnaatelier.app.data.palm.PalmCaptureFiles
import java.io.File

/* The camera's full-size photo must not outlive the upload copy made from it. */
class PalmCaptureFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun capture(dir: File, name: String, modifiedAt: Long) =
        File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)); setLastModified(modifiedAt) }

    @Test
    fun clearAllRemovesEveryCapture() {
        val dir = folder.newFolder("palm")
        val now = 1_800_000_000_000L
        val a = capture(dir, "capture-1.jpg", now)
        val b = capture(dir, "capture-2.jpg", now - 10)
        assertEquals(2, PalmCaptureFiles.clearAll(dir))
        assertFalse(a.exists())
        assertFalse(b.exists())
    }

    @Test
    fun clearStaleKeepsACaptureTheCameraMayStillHandBack() {
        val dir = folder.newFolder("palm")
        val now = 1_800_000_000_000L
        val fresh = capture(dir, "capture-fresh.jpg", now - 5 * 60 * 1000L)
        val stranded = capture(dir, "capture-old.jpg", now - PalmCaptureFiles.STALE_AFTER_MS - 1)
        assertEquals(1, PalmCaptureFiles.clearStale(dir, now))
        assertTrue(fresh.exists())
        assertFalse(stranded.exists())
    }

    @Test
    fun aMissingOrEmptyFolderIsNotAnError() {
        val missing = File(folder.root, "never-made")
        assertEquals(0, PalmCaptureFiles.clearAll(missing))
        assertEquals(0, PalmCaptureFiles.clearStale(missing))
        assertEquals(0, PalmCaptureFiles.clearAll(folder.newFolder("empty")))
    }
}
