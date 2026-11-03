package site.lagnaatelier.app.data.palm

import org.json.JSONArray
import org.json.JSONObject

/*
 * A palm reading as POST /api/palm-reading returns it, rebuilt here from the
 * documented shape the same way the route rebuilds it on its side
 * (lib/palm-readings/reading-shape.ts on the web): only the documented keys
 * survive, every value has the type its field documents, every string is
 * capped, and a value outside its set is dropped. The response is model
 * output over a photo nobody checked, so none of it is trusted.
 *
 * Deliberately left out: health_and_vitality. The app never shows it, and a
 * reading it never shows is not kept on the phone or sent back with a
 * follow-up question either.
 */

enum class LineKey(val json: String) {
    Heart("heart_line"),
    Head("head_line"),
    Life("life_line"),
    Fate("fate_line"),
}

data class PalmLine(val description: String, val interpretation: String, val strength: String)

data class LineConfidence(val visibility: String, val confidence: Double)

/** A point on a traced line: fractions of the whole photo, origin top-left. */
data class PalmPoint(val x: Float, val y: Float)

data class ImageQuality(val rating: String, val issues: List<String>, val reliable: Boolean, val notes: String)

data class Correlation(val palmIndicator: String, val chartFactor: String, val reinforcement: String, val reading: String)

data class JyotishCorrelation(val summary: String, val correlations: List<Correlation>)

data class DashaIndicator(val indicator: String, val relevance: String, val timingNote: String?)

data class DashaRelevance(val summary: String, val indicators: List<DashaIndicator>)

data class SanskritTerm(val term: String, val meaning: String, val observation: String)

data class ClassicalNotes(val framework: String, val terms: List<SanskritTerm>)

data class Trajectory(
    val currentPhase: String,
    val nearFuture: String,
    val longTermPath: String,
    val challenges: String,
    val opportunities: String,
)

data class Career(val naturalTalents: String, val careerDirection: String, val purposeAlignment: String)

data class Relationships(val emotionalState: String, val relationshipDynamics: String, val connectionStyle: String)

data class Mounts(val prominent: List<String>, val interpretation: String)

data class Fingers(val observation: String, val interpretation: String)

data class Markings(val observed: List<String>, val interpretation: String)

data class PalmReading(
    val overallSummary: String,
    val dominantHandNote: String,
    val lines: Map<LineKey, PalmLine>,
    val trajectory: Trajectory,
    val career: Career,
    val relationships: Relationships,
    val mounts: Mounts,
    val fingers: Fingers,
    val markings: Markings,
    val guidance: String,
    val imageQuality: ImageQuality? = null,
    val lineConfidence: Map<LineKey, LineConfidence> = emptyMap(),
    val lineCoordinates: Map<LineKey, List<PalmPoint>> = emptyMap(),
    val jyotishCorrelation: JyotishCorrelation? = null,
    val dashaRelevance: DashaRelevance? = null,
    val classicalNotes: ClassicalNotes? = null,
    val servedBy: String? = null,
)

object PalmReadingParser {
    /* The route's own caps (reading-shape.ts). */
    const val MAX_PROSE_CHARS = 4000
    const val MAX_LABEL_CHARS = 200
    const val MAX_LIST_ITEMS = 12
    const val MAX_POINTS = 12

    private val STRENGTHS = listOf("strong", "moderate", "faint", "absent")
    private val VISIBILITIES = listOf("clear", "partial", "faint", "not_detected")
    private val CONFIDENCES = listOf("high", "medium", "low")
    private val RATINGS = listOf("excellent", "good", "marginal", "poor")
    private val REINFORCEMENTS = listOf("strong", "moderate", "contradictory", "neutral")
    private val SERVERS = listOf("anthropic", "openai")
    private val SPACES = Regex("\\s+")

    /** The reading in [body], or null when it is not a palm reading (not an object, or no summary). */
    fun parse(body: String): PalmReading? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return parse(json)
    }

    fun parse(value: JSONObject): PalmReading? {
        val overall = text(value.opt("overall_summary"))
        if (overall.isEmpty()) return null
        val lines = objectOf(value.opt("lines"))
        val trajectory = objectOf(value.opt("life_trajectory"))
        val career = objectOf(value.opt("career_and_purpose"))
        val relationships = objectOf(value.opt("relationships_and_emotional"))
        val mounts = objectOf(value.opt("mounts"))
        val fingers = objectOf(value.opt("fingers"))
        val markings = objectOf(value.opt("special_markings"))

        return PalmReading(
            overallSummary = overall,
            dominantHandNote = text(value.opt("dominant_hand_note")),
            lines = LineKey.entries.associateWith { line(lines.opt(it.json)) },
            trajectory = Trajectory(
                currentPhase = text(trajectory.opt("current_phase")),
                nearFuture = text(trajectory.opt("near_future")),
                longTermPath = text(trajectory.opt("long_term_path")),
                challenges = text(trajectory.opt("challenges")),
                opportunities = text(trajectory.opt("opportunities")),
            ),
            career = Career(
                naturalTalents = text(career.opt("natural_talents")),
                careerDirection = text(career.opt("career_direction")),
                purposeAlignment = text(career.opt("purpose_alignment")),
            ),
            relationships = Relationships(
                emotionalState = text(relationships.opt("emotional_state")),
                relationshipDynamics = text(relationships.opt("relationship_dynamics")),
                connectionStyle = text(relationships.opt("connection_style")),
            ),
            mounts = Mounts(labels(mounts.opt("prominent")), text(mounts.opt("interpretation"))),
            fingers = Fingers(text(fingers.opt("observation")), text(fingers.opt("interpretation"))),
            markings = Markings(labels(markings.opt("observed")), text(markings.opt("interpretation"))),
            guidance = text(value.opt("guidance")),
            imageQuality = imageQuality(value.opt("image_quality")),
            lineConfidence = lineConfidence(value.opt("line_confidence")),
            lineCoordinates = lineCoordinates(value.opt("line_coordinates")),
            jyotishCorrelation = jyotishCorrelation(value.opt("jyotish_correlation")),
            dashaRelevance = dashaRelevance(value.opt("dasha_relevance")),
            classicalNotes = classicalNotes(value.opt("classical_framework_notes")),
            servedBy = oneOf(value.opt("served_by"), SERVERS),
        )
    }

    /**
     * The reading in the route's own shape, for the phone's history and for
     * the `reading` a follow-up question carries. Parsing this gives back the
     * same reading.
     */
    fun toJson(reading: PalmReading): JSONObject = JSONObject().apply {
        put("overall_summary", reading.overallSummary)
        put("dominant_hand_note", reading.dominantHandNote)
        put("lines", JSONObject().apply {
            reading.lines.forEach { (key, line) ->
                put(key.json, JSONObject().apply {
                    put("description", line.description)
                    put("interpretation", line.interpretation)
                    put("strength", line.strength)
                })
            }
        })
        put("life_trajectory", JSONObject().apply {
            put("current_phase", reading.trajectory.currentPhase)
            put("near_future", reading.trajectory.nearFuture)
            put("long_term_path", reading.trajectory.longTermPath)
            put("challenges", reading.trajectory.challenges)
            put("opportunities", reading.trajectory.opportunities)
        })
        put("career_and_purpose", JSONObject().apply {
            put("natural_talents", reading.career.naturalTalents)
            put("career_direction", reading.career.careerDirection)
            put("purpose_alignment", reading.career.purposeAlignment)
        })
        put("relationships_and_emotional", JSONObject().apply {
            put("emotional_state", reading.relationships.emotionalState)
            put("relationship_dynamics", reading.relationships.relationshipDynamics)
            put("connection_style", reading.relationships.connectionStyle)
        })
        put("mounts", JSONObject().apply {
            put("prominent", JSONArray(reading.mounts.prominent))
            put("interpretation", reading.mounts.interpretation)
        })
        put("fingers", JSONObject().apply {
            put("observation", reading.fingers.observation)
            put("interpretation", reading.fingers.interpretation)
        })
        put("special_markings", JSONObject().apply {
            put("observed", JSONArray(reading.markings.observed))
            put("interpretation", reading.markings.interpretation)
        })
        put("guidance", reading.guidance)
        reading.imageQuality?.let {
            put("image_quality", JSONObject().apply {
                put("rating", it.rating)
                put("issues", JSONArray(it.issues))
                put("reliable_for_reading", it.reliable)
                put("notes", it.notes)
            })
        }
        if (reading.lineConfidence.isNotEmpty()) {
            put("line_confidence", JSONObject().apply {
                reading.lineConfidence.forEach { (key, entry) ->
                    put(key.json, JSONObject().put("visibility", entry.visibility).put("confidence", entry.confidence))
                }
            })
        }
        if (reading.lineCoordinates.isNotEmpty()) {
            put("line_coordinates", JSONObject().apply {
                reading.lineCoordinates.forEach { (key, points) ->
                    put(key.json, JSONArray(points.map { JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble()) }))
                }
            })
        }
        reading.jyotishCorrelation?.let { block ->
            put("jyotish_correlation", JSONObject().apply {
                put("summary", block.summary)
                put("correlations", JSONArray(block.correlations.map {
                    JSONObject()
                        .put("palm_indicator", it.palmIndicator)
                        .put("chart_factor", it.chartFactor)
                        .put("reinforcement", it.reinforcement)
                        .put("reading", it.reading)
                }))
            })
        }
        reading.dashaRelevance?.let { block ->
            put("dasha_relevance", JSONObject().apply {
                put("active_period_summary", block.summary)
                put("relevant_palm_indicators", JSONArray(block.indicators.map {
                    JSONObject().apply {
                        put("indicator", it.indicator)
                        put("relevance_to_dasha", it.relevance)
                        it.timingNote?.let { note -> put("timing_note", note) }
                    }
                }))
            })
        }
        reading.classicalNotes?.let { block ->
            put("classical_framework_notes", JSONObject().apply {
                put("framework", block.framework)
                put("sanskrit_terms", JSONArray(block.terms.map {
                    JSONObject().put("term", it.term).put("meaning", it.meaning).put("observation", it.observation)
                }))
            })
        }
        reading.servedBy?.let { put("served_by", it) }
    }

    /* ── the shaping helpers, after reading-shape.ts ── */

    private fun objectOf(value: Any?): JSONObject = value as? JSONObject ?: JSONObject()

    /** A string cut at a word boundary under [max], or "" for anything that is not a string. */
    internal fun text(value: Any?, max: Int = MAX_PROSE_CHARS): String {
        if (value !is String) return ""
        val trimmed = value.trim()
        if (trimmed.length <= max) return trimmed
        val cut = trimmed.substring(0, max)
        val space = cut.lastIndexOf(' ')
        return (if (space > max * 0.8) cut.substring(0, space) else cut).trimEnd()
    }

    /** One of [set], forgiving case and spaces, or null. */
    private fun oneOf(value: Any?, set: List<String>): String? {
        if (value !is String) return null
        val normalised = value.trim().lowercase().replace(SPACES, "_")
        return set.firstOrNull { it == normalised }
    }

    private fun labels(value: Any?): List<String> {
        val array = value as? JSONArray ?: return emptyList()
        return (0 until array.length())
            .map { text(array.opt(it), MAX_LABEL_CHARS) }
            .filter { it.isNotEmpty() }
            .take(MAX_LIST_ITEMS)
    }

    private fun <T> entries(value: Any?, shape: (JSONObject) -> T?): List<T> {
        val array = value as? JSONArray ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.opt(it) as? JSONObject }
            .mapNotNull(shape)
            .take(MAX_LIST_ITEMS)
    }

    private fun unit(value: Any?): Double? {
        val number = (value as? Number)?.toDouble() ?: return null
        if (number.isNaN() || number.isInfinite()) return null
        return number.coerceIn(0.0, 1.0)
    }

    private fun line(value: Any?): PalmLine {
        val raw = objectOf(value)
        return PalmLine(
            description = text(raw.opt("description")),
            interpretation = text(raw.opt("interpretation")),
            /* A value outside the scale is not one to print, so it reads as the middle of it. */
            strength = oneOf(raw.opt("strength"), STRENGTHS) ?: "moderate",
        )
    }

    private fun imageQuality(value: Any?): ImageQuality? {
        val raw = value as? JSONObject ?: return null
        val rating = oneOf(raw.opt("rating"), RATINGS) ?: return null
        val reliable = raw.opt("reliable_for_reading")
        return ImageQuality(
            rating = rating,
            issues = labels(raw.opt("issues")),
            /* The prompt's own rule when the flag is missing: unreliable only when poor. */
            reliable = if (reliable is Boolean) reliable else rating != "poor",
            notes = text(raw.opt("notes")),
        )
    }

    private fun lineConfidence(value: Any?): Map<LineKey, LineConfidence> {
        val raw = value as? JSONObject ?: return emptyMap()
        val out = linkedMapOf<LineKey, LineConfidence>()
        for (key in LineKey.entries) {
            val entry = objectOf(raw.opt(key.json))
            val visibility = oneOf(entry.opt("visibility"), VISIBILITIES)
            val confidence = unit(entry.opt("confidence"))
            if (visibility != null && confidence != null) out[key] = LineConfidence(visibility, confidence)
        }
        return out
    }

    private fun lineCoordinates(value: Any?): Map<LineKey, List<PalmPoint>> {
        val raw = value as? JSONObject ?: return emptyMap()
        val out = linkedMapOf<LineKey, List<PalmPoint>>()
        for (key in LineKey.entries) {
            val array = raw.opt(key.json) as? JSONArray ?: continue
            val points = (0 until array.length())
                .mapNotNull { array.opt(it) as? JSONObject }
                .mapNotNull { point ->
                    val x = unit(point.opt("x"))
                    val y = unit(point.opt("y"))
                    if (x != null && y != null) PalmPoint(x.toFloat(), y.toFloat()) else null
                }
                .take(MAX_POINTS)
            if (points.isNotEmpty()) out[key] = points
        }
        return out
    }

    private fun jyotishCorrelation(value: Any?): JyotishCorrelation? {
        val raw = value as? JSONObject ?: return null
        return JyotishCorrelation(
            summary = text(raw.opt("summary")),
            correlations = entries(raw.opt("correlations")) { entry ->
                val reinforcement = oneOf(entry.opt("reinforcement"), REINFORCEMENTS) ?: return@entries null
                Correlation(
                    palmIndicator = text(entry.opt("palm_indicator"), MAX_LABEL_CHARS),
                    chartFactor = text(entry.opt("chart_factor"), MAX_LABEL_CHARS),
                    reinforcement = reinforcement,
                    reading = text(entry.opt("reading")),
                )
            },
        )
    }

    private fun dashaRelevance(value: Any?): DashaRelevance? {
        val raw = value as? JSONObject ?: return null
        return DashaRelevance(
            summary = text(raw.opt("active_period_summary")),
            indicators = entries(raw.opt("relevant_palm_indicators")) { entry ->
                DashaIndicator(
                    indicator = text(entry.opt("indicator"), MAX_LABEL_CHARS),
                    relevance = text(entry.opt("relevance_to_dasha")),
                    timingNote = text(entry.opt("timing_note"), MAX_LABEL_CHARS).ifEmpty { null },
                )
            },
        )
    }

    private fun classicalNotes(value: Any?): ClassicalNotes? {
        val raw = value as? JSONObject ?: return null
        return ClassicalNotes(
            framework = text(raw.opt("framework"), MAX_LABEL_CHARS),
            terms = entries(raw.opt("sanskrit_terms")) { entry ->
                SanskritTerm(
                    term = text(entry.opt("term"), MAX_LABEL_CHARS),
                    meaning = text(entry.opt("meaning"), MAX_LABEL_CHARS),
                    observation = text(entry.opt("observation")),
                )
            },
        )
    }
}
