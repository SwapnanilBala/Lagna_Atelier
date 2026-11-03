package site.lagnaatelier.app.data.palm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Getting a photo ready for POST /api/palm-reading: upright (the camera app
 * writes the sensor's way up and an EXIF tag saying so), no larger than the
 * reading needs, a JPEG, under the route's limit, and Base64.
 *
 * The route refuses an image over 5 MB (MAX_IMAGE_BYTES, measured on the
 * decoded Base64) and anything that is not JPEG, PNG or WebP. A JPEG with a
 * 1600 px long edge is a few hundred kilobytes, so the limit here is only
 * ever met by a photo of noise; the encoder steps the quality down, then the
 * size, until it fits.
 */
object PalmImageMath {
    /** The long edge sent for reading. Enough for the creases; more only costs upload time. */
    const val UPLOAD_EDGE = 1600

    /** The long edge kept in the phone's history, as the web keeps (local-store.ts). */
    const val HISTORY_EDGE = 720

    /** The route's limit on the decoded image. */
    const val ROUTE_MAX_BYTES = 5 * 1024 * 1024

    /** What this app sends at most: under the route's limit with room to spare. */
    const val UPLOAD_MAX_BYTES = 4 * 1024 * 1024

    /** JPEG qualities tried in turn before the picture itself is made smaller. */
    val QUALITY_STEPS = listOf(88, 80, 72, 64, 56)

    /** How much an image of [width] x [height] is scaled so its long edge is at most [maxEdge]; never up. */
    fun scaleFor(width: Int, height: Int, maxEdge: Int): Double {
        val longEdge = max(width, height)
        if (longEdge <= 0) return 1.0
        return min(1.0, maxEdge.toDouble() / longEdge)
    }

    /** The size [width] x [height] is drawn at for [maxEdge]: rounded, and never below one pixel. */
    fun targetSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val scale = scaleFor(width, height, maxEdge)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /**
     * The largest power-of-two subsample that still leaves the long edge at
     * least [maxEdge], so the decoder does the coarse halving and the scaler
     * only the last step.
     */
    fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        val longEdge = max(width, height)
        var sample = 1
        while (longEdge / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** The length of [bytes] once Base64-encoded with padding. */
    fun base64Length(bytes: Int): Int = 4 * ceil(bytes / 3.0).toInt()

    /** The route's own estimate of the decoded size (estimateBase64Bytes). */
    fun routeEstimate(base64: String): Int {
        val normalised = base64.filterNot { it.isWhitespace() }
        val padding = when {
            normalised.endsWith("==") -> 2
            normalised.endsWith("=") -> 1
            else -> 0
        }
        return (normalised.length.toLong() * 3 / 4).toInt() - padding
    }

    fun fitsUpload(bytes: Int): Boolean = bytes in 1..UPLOAD_MAX_BYTES

    /** Degrees to turn and whether to mirror, for an EXIF orientation tag (1 to 8). */
    fun orientation(exif: Int): Pair<Int, Boolean> = when (exif) {
        2 -> 0 to true // flipped horizontally
        3 -> 180 to false
        4 -> 180 to true // flipped vertically
        5 -> 90 to true // transposed
        6 -> 90 to false
        7 -> 270 to true // transversed
        8 -> 270 to false
        else -> 0 to false
    }
}

/** A photo ready to send: an upright JPEG and its size. */
class PreparedPhoto(val jpeg: ByteArray, val width: Int, val height: Int) {
    val base64: String get() = Base64.getEncoder().encodeToString(jpeg)
    val mediaType: String get() = "image/jpeg"
}

object PalmImageLoader {
    class UnreadablePhoto : IOException("That photo could not be opened.")

    /** Reads [uri], turns it upright and encodes it for upload. */
    suspend fun prepare(context: Context, uri: Uri, maxEdge: Int = PalmImageMath.UPLOAD_EDGE): PreparedPhoto =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw UnreadablePhoto()
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadablePhoto()

            val exif = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val options = BitmapFactory.Options().apply {
                inSampleSize = PalmImageMath.sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw UnreadablePhoto()
            val upright = transform(decoded, exif, maxEdge)
            try {
                encode(upright)
            } finally {
                upright.recycle()
            }
        }

    /** A smaller copy of an already-upright JPEG, for the phone's history. */
    fun shrink(jpeg: ByteArray, maxEdge: Int = PalmImageMath.HISTORY_EDGE, quality: Int = 82): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = PalmImageMath.sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: throw UnreadablePhoto()
        val scaled = transform(decoded, ExifInterface.ORIENTATION_NORMAL, maxEdge)
        return try {
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        } finally {
            scaled.recycle()
        }
    }

    /* Scale to the long edge and apply the EXIF turn in one pass. Recycles [source] when it makes a new bitmap. */
    private fun transform(source: Bitmap, exif: Int, maxEdge: Int): Bitmap {
        val (degrees, mirror) = PalmImageMath.orientation(exif)
        val scale = PalmImageMath.scaleFor(source.width, source.height, maxEdge).toFloat()
        if (degrees == 0 && !mirror && scale == 1f) return source
        /* Turn, then mirror: the order the EXIF orientations are defined in.
           createBitmap moves the result back to the origin. */
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (degrees != 0) postRotate(degrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (out !== source) source.recycle()
        return out
    }

    /* The first quality that fits; if none does, three quarters the size and again. */
    private fun encode(bitmap: Bitmap): PreparedPhoto {
        var current = bitmap
        try {
            repeat(4) {
                for (quality in PalmImageMath.QUALITY_STEPS) {
                    val bytes = ByteArrayOutputStream().also { current.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                        .toByteArray()
                    if (PalmImageMath.fitsUpload(bytes.size)) return PreparedPhoto(bytes, current.width, current.height)
                }
                val (w, h) = PalmImageMath.targetSize(current.width, current.height, max(current.width, current.height) * 3 / 4)
                val smaller = Bitmap.createScaledBitmap(current, w, h, true)
                if (current !== bitmap) current.recycle()
                current = smaller
            }
        } finally {
            if (current !== bitmap) current.recycle()
        }
        throw IOException("That photo is too large to send. Try another.")
    }
}
