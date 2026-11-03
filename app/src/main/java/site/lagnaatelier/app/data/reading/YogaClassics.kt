package site.lagnaatelier.app.data.reading

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import java.io.IOException

/*
 * "From the classics" for the yoga section: what the Brihat Jataka says about
 * the yogas this chart has, cited verse by verse
 * (POST /api/chart/yoga-classics, app/api/chart/yoga-classics/route.ts).
 *
 * The route words the note with a model, so every answered call that is not
 * cached spends the site's daily allowance: the screen asks only when the
 * reader taps for it, and [YogaClassicsNotes] keeps the answer for the life of
 * the process. A chart none of whose yogas the book names gets `reading: null`,
 * which is an answer, costs nothing, and is not a failure.
 */

/** A passage the note cites, numbered in the order the note first cites it. */
data class ClassicalSource(
    val number: Int,
    /** The book's slug in the web's lib/knowledge/sources.ts. */
    val book: String,
    /** Chapter and verse, "13.9". */
    val ref: String,
    /** True for a translator's note on the verse rather than the verse itself. */
    val isNote: Boolean,
    val text: String,
)

/** A run of the note and the numbers of the passages it rests on (often none, for joining text). */
data class ClassicalSegment(val text: String, val sources: List<Int>)

data class ClassicalReading(val segments: List<ClassicalSegment>, val sources: List<ClassicalSource>) {
    /**
     * The note as paragraphs of cited pieces, for drawing. Citations arrive
     * per text block, and a block can end mid-paragraph or hold a paragraph
     * break, so paragraphs are cut at blank lines wherever they fall and a
     * block's numbers stay on its last piece: after the words they support.
     * The same cut as the web's readingParagraphs (lib/knowledge/classical-reading.ts).
     */
    fun paragraphs(): List<List<ClassicalSegment>> {
        val paragraphs = mutableListOf(mutableListOf<ClassicalSegment>())
        for (segment in segments) {
            val parts = segment.text.split(PARAGRAPH_BREAK)
            parts.forEachIndexed { index, part ->
                if (index > 0) paragraphs.add(mutableListOf())
                val last = index == parts.lastIndex
                paragraphs.last().add(ClassicalSegment(part, if (last) segment.sources else emptyList()))
            }
        }
        return paragraphs
            .map { pieces ->
                val trimmed = pieces.toMutableList()
                if (trimmed.isNotEmpty()) {
                    trimmed[0] = trimmed[0].copy(text = trimmed[0].text.trimStart())
                    trimmed[trimmed.lastIndex] = trimmed.last().copy(text = trimmed.last().text.trimEnd())
                }
                trimmed.filter { it.text.isNotEmpty() || it.sources.isNotEmpty() }
            }
            .filter { pieces -> pieces.any { it.text.isNotBlank() } }
    }

    private companion object {
        val PARAGRAPH_BREAK = Regex("\n\\s*\n")
    }
}

object YogaClassics {
    const val PATH = "/api/chart/yoga-classics"

    /** The route takes at most this many yogas (YOGA_CLASSICS_MAX_REQUEST). */
    const val MAX_YOGAS = 60

    /* What the route will accept in `planets`; anything else is dropped there, so it is not sent. */
    private val planets = setOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu", "Ascendant")

    /**
     * The request body, as the web's yogaClassicsRequest builds it: every
     * yoga in rank order (the route picks the first few the book speaks to),
     * as ids, planets and strengths only, so no free text reaches the prompt.
     * Null when there is nothing to ask about.
     */
    fun requestBody(yogas: List<Yoga>, language: String = "en"): String? {
        if (yogas.isEmpty()) return null
        val seen = mutableSetOf<String>()
        val rows = JSONArray()
        for (yoga in yogas) {
            if (rows.length() >= MAX_YOGAS) break
            if (!seen.add(yoga.id)) continue
            rows.put(
                JSONObject()
                    .put("id", yoga.id)
                    .put("planets", JSONArray(yoga.planets.filter { it in planets }.distinct()))
                    .put("strength", yoga.strength.code),
            )
        }
        return JSONObject().put("yogas", rows).put("language", language).toString()
    }

    /**
     * The route's answer: the note, or null when the book has nothing for
     * these yogas. A note that names no sources is refused, as the web
     * refuses it: an uncited note is exactly what this section must not print.
     */
    fun parse(json: String): ClassicalReading? {
        val reading = JSONObject(json).optJSONObject("reading") ?: return null
        val segments = reading.getJSONArray("segments").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.map { segment ->
                val numbers = segment.optJSONArray("sources")
                ClassicalSegment(
                    text = segment.optString("text"),
                    sources = numbers?.let { (0 until it.length()).map { i -> it.getInt(i) } }.orEmpty(),
                )
            }
        }
        val sources = reading.getJSONArray("sources").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.map { source ->
                ClassicalSource(
                    number = source.getInt("number"),
                    book = source.optString("book"),
                    ref = source.optString("ref"),
                    isNote = source.optString("kind") == "note",
                    text = source.optString("text"),
                )
            }
        }
        if (segments.isEmpty() || sources.isEmpty()) throw IOException("Classical note without its sources")
        return ClassicalReading(segments, sources)
    }

    /** Asks the route for [requestBody]'s note. Null is "the book has nothing here". */
    suspend fun fetch(api: LagnaApi, requestBody: String): ClassicalReading? =
        /* The route allows itself 60 s, a retry on a slower model included. */
        parse(api.postJson(PATH, requestBody, readTimeoutMs = 65_000))
}

/** Where one chart's classical note is, from not asked to answered. */
sealed interface NoteState {
    data object NotAsked : NoteState
    data object Asking : NoteState
    data class Ready(val reading: ClassicalReading) : NoteState

    /** The book has no verses on these yogas: an answer, not a failure. */
    data object Empty : NoteState
    data class Failed(@param:StringRes @get:StringRes val message: Int) : NoteState
}

/**
 * One chart's note. Compose state, so the card reads it directly; held in
 * the reading screen's YogaClassicsNotes rather than in the composition, so scrolling the card
 * away and back, or switching tabs, does not ask (and pay) again.
 */
class YogaClassicsNote(val requestBody: String) {
    var state by mutableStateOf<NoteState>(NoteState.NotAsked)
        private set

    /*
     * An answered or in-flight note is left alone; a failed one is asked
     * again only when the reader taps "Try again". Every failure is worded
     * by the app (ErrorText), including a reply the parser did not expect.
     */
    suspend fun ask(fetch: suspend (String) -> ClassicalReading?) {
        if (state is NoteState.Asking || state is NoteState.Ready || state is NoteState.Empty) return
        state = NoteState.Asking
        state = try {
            fetch(requestBody)?.let { NoteState.Ready(it) } ?: NoteState.Empty
        } catch (e: CancellationException) {
            state = NoteState.NotAsked
            throw e
        } catch (e: Exception) {
            NoteState.Failed(ErrorText.of(e))
        }
    }
}
