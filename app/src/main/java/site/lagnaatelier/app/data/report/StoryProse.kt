package site.lagnaatelier.app.data.report

import org.json.JSONObject
import kotlin.math.roundToInt

/*
 * The written pass over the report's prose, as lib/story-prose.ts does it on
 * the web: what is sent to POST /api/chart/story-prose, how its answer is read,
 * and how the written chapters are merged back into the engine's story.
 *
 * The engine decides every fact. The model may rewrite only the introduction,
 * the preface, and each chapter's opening and narrative; everything else on
 * the page (titles, support, signals, practices) stays the engine's.
 */

data class ProseChapter(val id: String, val opening: String, val narrative: List<String>)

data class StoryProse(val introduction: String, val preface: List<String>, val chapters: List<ProseChapter>)

/** One `progress` line of the route's NDJSON stream. */
data class ProseProgress(
    /** "planning" before the first word, then "writing". */
    val phase: String,
    val fraction: Double,
    val chaptersStarted: Int,
    val chapters: Int,
) {
    val planning: Boolean get() = phase != "writing"
}

object StoryProseCodec {
    /** What the web's Accept header asks for to get progress lines. */
    const val STREAM_TYPE = "application/x-ndjson"

    /**
     * The request body, `{"facts": ...}`, byte for byte what the web sends:
     * lib/story-prose.ts buildStoryProseFacts, through JSON.stringify.
     *
     * No reader's name and no report title; the route reads neither, and the
     * name was taken out of the prompt on purpose.
     */
    fun requestBody(story: PersonalStory): String = "{\"facts\":${factsJson(story)}}"

    /** JSON.stringify(buildStoryProseFacts(story)), keys in the web's order. */
    fun factsJson(story: PersonalStory): String = buildString {
        append('{')
        key("subtitle"); string(story.subtitle); append(',')
        key("atAGlance")
        array(story.atAGlance) { item ->
            append('{')
            key("label"); string(item.label); append(',')
            key("value"); string(item.value); append(',')
            key("context"); string(item.context)
            append('}')
        }
        append(',')
        key("centralThemes"); array(story.centralThemes) { string(it) }; append(',')
        key("chapters")
        array(story.chapters) { chapter ->
            append('{')
            key("id"); string(chapter.id); append(',')
            key("title"); string(chapter.title); append(',')
            key("eyebrow"); string(chapter.eyebrow); append(',')
            key("support"); string(chapter.support ?: "supported"); append(',')
            key("signals")
            array(chapter.signals) { signal ->
                append('{')
                key("label"); string(signal.label); append(',')
                key("value"); string(signal.value)
                append('}')
            }
            append(',')
            key("draft"); string(draft(chapter))
            append('}')
        }
        append('}')
    }

    /*
     * The opening and narrative folded into one blob. The engine's
     * narrative[0] is usually its opening repeated, so that copy is left out:
     * sent twice, the model reads it as emphasis.
     */
    internal fun draft(chapter: StoryChapter): String {
        val paragraphs = chapter.narrative.ifEmpty { listOf(chapter.body) }
        val opening = chapter.opening?.trim()
        val rest = paragraphs.map { it.trim() }.filter { it.isNotEmpty() && it != opening }
        return (listOf(opening) + rest).filter { !it.isNullOrEmpty() }.joinToString(" ")
    }

    /** The route's `prose`, checked as parseStoryProse does; null for anything that does not hold. */
    fun parseProse(value: Any?): StoryProse? {
        val raw = value as? JSONObject ?: return null
        val introduction = raw.opt("introduction") as? String ?: return null
        val preface = raw.optJSONArray("preface") ?: return null
        val chapters = raw.optJSONArray("chapters") ?: return null
        val written = chapters.objects().mapNotNull { row ->
            val id = row.opt("id") as? String ?: return@mapNotNull null
            val opening = row.opt("opening") as? String ?: return@mapNotNull null
            val narrative = row.optJSONArray("narrative") ?: return@mapNotNull null
            val paragraphs = narrative.strings().filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) null else ProseChapter(id, opening, paragraphs)
        }
        if (written.isEmpty()) return null
        return StoryProse(introduction, preface.strings(), written)
    }

    /**
     * Merge written prose into the story chapter by chapter, so a short answer
     * makes a mixed report rather than none: a chapter the model skipped keeps
     * the engine's text. (applyStoryProse on the web.)
     */
    fun apply(story: PersonalStory, prose: StoryProse): PersonalStory {
        val byId = prose.chapters.associateBy { it.id }
        return story.copy(
            introduction = prose.introduction.trim().ifEmpty { story.introduction },
            preface = prose.preface.ifEmpty { story.preface },
            chapters = story.chapters.map { chapter ->
                val written = byId[chapter.id] ?: return@map chapter
                chapter.copy(
                    opening = written.opening.trim().ifEmpty { chapter.opening },
                    narrative = written.narrative,
                )
            },
        )
    }

    /** What one line of the stream said. */
    sealed interface StreamLine {
        data class Progress(val progress: ProseProgress) : StreamLine
        data class Done(val prose: StoryProse?) : StreamLine
        data class Error(val message: String) : StreamLine
    }

    /** One NDJSON line, clamped as readStoryProseResponse clamps it; null for a blank or broken line. */
    fun parseLine(line: String): StreamLine? {
        if (line.isBlank()) return null
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        return when (json.optString("type")) {
            "progress" -> {
                val chapters = json.optDouble("chapters").let { if (it.isFinite()) it.toInt().coerceAtLeast(0) else 0 }
                val fraction = json.optDouble("fraction").let { if (it.isFinite()) it else 0.0 }.coerceIn(0.0, 1.0)
                val started = json.optDouble("chaptersStarted").let { if (it.isFinite()) it.toInt() else 0 }
                StreamLine.Progress(
                    ProseProgress(
                        phase = if (json.optString("phase") == "writing") "writing" else "planning",
                        fraction = fraction,
                        chaptersStarted = started.coerceIn(0, chapters),
                        chapters = chapters,
                    ),
                )
            }
            "done" -> StreamLine.Done(parseProse(json.opt("prose")))
            "error" -> StreamLine.Error(json.optString("message"))
            else -> null
        }
    }

    /** A plain JSON answer (a cache hit, or a route that did not stream): `{prose, cached}`. */
    fun parsePlain(body: String): StoryProse? =
        parseProse(runCatching { JSONObject(body) }.getOrNull()?.opt("prose"))

    private fun StringBuilder.key(name: String) {
        string(name)
        append(':')
    }

    private fun <T> StringBuilder.array(items: List<T>, each: StringBuilder.(T) -> Unit) {
        append('[')
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            each(item)
        }
        append(']')
    }

    /* JSON.stringify's escaping: quote, backslash, control characters and lone surrogates; nothing else. */
    private fun StringBuilder.string(value: String) {
        append('"')
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\b' -> append("\\b")
                c == '\u000C' -> append("\\f")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                    append(c).append(value[i + 1])
                    i++
                }
                c.isSurrogate() -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
            i++
        }
        append('"')
    }
}

/** The four steps, in order, as the generating screen lists them. */
enum class ReportStage { Calculating, Verifying, Writing, Typesetting }

/*
 * The bar's figure, by the web's rules (storyPercent in personal-story.tsx):
 * the quick steps hold fixed points, and writing -- nearly all of the wait --
 * runs from 8 to 92 as the route reports the share of words written.
 */
object ReportPercent {
    private const val WRITING_FROM = 8
    private const val WRITING_SPAN = 84

    fun of(stage: ReportStage, writing: ProseProgress?): Int = when (stage) {
        ReportStage.Calculating -> 2
        ReportStage.Verifying -> 6
        ReportStage.Writing -> (WRITING_FROM + (writing?.fraction ?: 0.0) * WRITING_SPAN).roundToInt()
        ReportStage.Typesetting -> 95
    }
}

/** The PDF's file name: the story title slugged as the web slugs it, "ananya-mehra-story.pdf". */
object ReportFiles {
    fun slug(value: String): String =
        value.lowercase(java.util.Locale.ROOT).trim()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "personal-story" }

    fun fileName(story: PersonalStory): String = "${slug(story.title)}.pdf"
}
