package site.lagnaatelier.app.data.palm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/*
 * Palm readings saved on this phone, the app's version of the web's
 * lib/palm-readings/local-store.ts: never on the server, at most 25, the
 * oldest falling off, and each photo kept at 720 px on its long edge.
 *
 * One reading is two files in filesDir/palm_readings: <id>.json and <id>.jpg.
 */

data class SavedPalmReading(
    val id: String,
    val createdAtMillis: Long,
    val reader: PalmReader,
    val classicalMode: Boolean,
    val jyotishContext: JSONObject?,
    val reading: PalmReading,
)

object PalmHistoryCodec {
    fun toJson(record: SavedPalmReading): JSONObject = JSONObject().apply {
        put("id", record.id)
        put("created_at", record.createdAtMillis)
        put("reader", record.reader.apiValue)
        put("classical_mode", record.classicalMode)
        record.jyotishContext?.let { put("jyotish_context", it) }
        put("reading", PalmReadingParser.toJson(record.reading))
    }

    /** A saved record, or null for a file that is not one. The reading is shaped again on the way in. */
    fun fromJson(text: String): SavedPalmReading? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val id = (json.opt("id") as? String)?.takeIf { SAFE_ID.matches(it) } ?: return null
        val reading = (json.opt("reading") as? JSONObject)?.let(PalmReadingParser::parse) ?: return null
        val created = (json.opt("created_at") as? Number)?.toLong() ?: return null
        return SavedPalmReading(
            id = id,
            createdAtMillis = created,
            reader = PalmReader.from(json.opt("reader") as? String),
            classicalMode = json.opt("classical_mode") == true,
            jyotishContext = json.opt("jyotish_context") as? JSONObject,
            reading = reading,
        )
    }

    /** Newest first, at most [max]; returns (kept, dropped). */
    fun trim(records: List<SavedPalmReading>, max: Int = PalmHistoryStore.MAX_READINGS): Pair<List<SavedPalmReading>, List<SavedPalmReading>> {
        val sorted = records.sortedByDescending { it.createdAtMillis }
        return sorted.take(max) to sorted.drop(max)
    }

    /* Ids are UUIDs written here; anything else is not used as a file name. */
    private val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
}

class PalmHistoryStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, DIR)

    fun photoFile(id: String): File = File(dir, "$id.jpg")

    suspend fun list(): List<SavedPalmReading> = withContext(Dispatchers.IO) {
        val records = dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { PalmHistoryCodec.fromJson(file.readText()) }.getOrNull() }
        val (kept, dropped) = PalmHistoryCodec.trim(records)
        dropped.forEach { deleteFiles(it.id) }
        kept
    }

    /** Saves a reading with its photo, shrunk for keeping; returns the record. */
    suspend fun save(
        reading: PalmReading,
        photoJpeg: ByteArray?,
        reader: PalmReader,
        classicalMode: Boolean,
        jyotishContext: JSONObject?,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): SavedPalmReading = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val record = SavedPalmReading(
            id = UUID.randomUUID().toString(),
            createdAtMillis = createdAtMillis,
            reader = reader,
            classicalMode = classicalMode,
            jyotishContext = jyotishContext,
            reading = reading,
        )
        photoJpeg?.let { photoFile(record.id).writeBytes(PalmImageLoader.shrink(it)) }
        File(dir, "${record.id}.json").writeText(PalmHistoryCodec.toJson(record).toString())
        list()
        record
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { deleteFiles(id) }

    private fun deleteFiles(id: String) {
        File(dir, "$id.json").delete()
        photoFile(id).delete()
    }

    companion object {
        const val MAX_READINGS = 25
        private const val DIR = "palm_readings"
    }
}
