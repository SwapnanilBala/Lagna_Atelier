package site.lagnaatelier.app.data.palm

import android.content.Context
import java.io.File

/*
 * The camera app's full-size photo, in cache/palm (res/xml/file_paths.xml).
 *
 * It carries whatever the camera wrote into it, GPS position included, so it
 * lives only as long as it takes to make the upload copy: that copy is
 * re-encoded (no EXIF) and held in memory, and the original is deleted the
 * moment it has been read, or as soon as the camera is cancelled.
 *
 * A capture can still be stranded if the app is closed while the camera is
 * open. Those are swept once they are an hour old -- never sooner, because a
 * photo the camera is about to hand back after the process restarted must
 * survive until it is read.
 */
object PalmCaptureFiles {
    /** Must match the cache-path in res/xml/file_paths.xml. */
    private const val DIR = "palm"

    const val STALE_AFTER_MS = 60 * 60 * 1000L

    fun dir(context: Context): File = File(context.cacheDir, DIR)

    /** A new, empty file for the camera to write into. */
    fun newCapture(context: Context, now: Long = System.currentTimeMillis()): File =
        File(dir(context).apply { mkdirs() }, "capture-$now.jpg")

    /** Deletes every capture. Returns how many files went. */
    fun clearAll(dir: File): Int = dir.listFiles().orEmpty().count { it.isFile && it.delete() }

    /** Deletes captures older than [STALE_AFTER_MS]. Returns how many files went. */
    fun clearStale(dir: File, now: Long = System.currentTimeMillis()): Int =
        dir.listFiles().orEmpty().count { it.isFile && now - it.lastModified() > STALE_AFTER_MS && it.delete() }
}
