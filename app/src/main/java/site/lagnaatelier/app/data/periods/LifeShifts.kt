package site.lagnaatelier.app.data.periods

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.AppDates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The life chapters: the website's major life shifts
 * (lib/engines/major-shifts-engine.ts), ported line for line, because
 * POST /api/chart/life-shifts takes the chapters' ids and rebuilds them on the
 * server from the birth details: an id the server's own engine does not
 * produce is dropped, so the app's ids must come out byte for byte the same.
 * An id is the kind and the pivot as JavaScript's toISOString writes it
 * ("saturn-return-2019-11-29T13:56:18.960Z"), and the pivots are millisecond
 * arithmetic on doubles, done here in the same order as the engine does it.
 *
 * The chapters are the Maha Dasha openings and the Saturn, Jupiter and nodal
 * returns; the five nearest to now are kept, in date order. The engine's
 * English narrative is what a chapter shows until its written reading lands,
 * and instead of it when none does.
 *
 * The label, the theme and the evidence are the engine's English too, which
 * is what an id and the tests compare against; the screen prints them in the
 * app's language from [LifeShift.kind], [LifeShift.occurrence] and
 * [LifeShift.facts] (ui/timing/LifeChapterText.kt), as the web's
 * life-shift-labels.ts does for the label and the theme.
 */

enum class ShiftKind(val apiValue: String) {
    Mahadasha("mahadasha"),
    SaturnReturn("saturn-return"),
    JupiterReturn("jupiter-return"),
    NodalReturn("nodal-return"),
}

enum class ShiftStatus { Past, Active, Upcoming }

data class LifeShift(
    val index: Int,
    val kind: ShiftKind,
    /** The engine's label: "Saturn return (first)", "Mercury mahadasha begins". */
    val label: String,
    val planet: String,
    val pivot: Instant,
    val windowStart: Instant,
    val windowEnd: Instant,
    val status: ShiftStatus,
    val ageAtPivot: Int,
    val theme: String,
    val narrative: String,
    val evidence: String,
    /** Which return this is, 1 for the first; 0 for a Maha Dasha opening. */
    val occurrence: Int = 0,
    /** [evidence]'s parts, in its order, for printing in another language. */
    val facts: List<ShiftFact> = emptyList(),
) {
    /** The id the route files the chapter's reading under (lifeShiftId). */
    val id: String get() = "${kind.apiValue}-${LifeShifts.isoString(pivot)}"
}

/** One part of a chapter's evidence line, as the engine words it in English. */
sealed interface ShiftFact {
    /** "Mahadasha lord: Saturn". */
    data class Lord(val planet: String) : ShiftFact

    /** "Lasts ~19 years". */
    data class Lasts(val years: Double) : ShiftFact

    /** "Cycle: ~29.5 years". */
    data class Cycle(val years: Double) : ShiftFact

    /** "Natal Saturn: Leo / H5". */
    data class Natal(val planet: String, val sign: String, val house: Int) : ShiftFact

    /** A node's own placement on the nodal return: "Rahu: Leo / H5". */
    data class Node(val planet: String, val sign: String, val house: Int) : ShiftFact
}

object LifeShifts {
    private const val DAY_MS = 86_400_000.0
    private const val YEAR_DAYS = 365.25
    private const val SATURN_RETURN_YEARS = 29.4571
    private const val JUPITER_RETURN_YEARS = 11.862
    private const val NODAL_RETURN_YEARS = 18.6129

    /** The most chapters the engine produces, and so the most one call takes. */
    const val MAX = 5

    private val PLANET_THEMES = mapOf(
        "Sun" to "authority, identity, and visibility",
        "Moon" to "emotional foundations, family, and inner safety",
        "Mars" to "courage, conflict, and decisive action",
        "Mercury" to "communication, study, and adaptive thinking",
        "Jupiter" to "expansion, faith, teaching, and ethical growth",
        "Venus" to "love, partnership, money, and pleasure",
        "Saturn" to "discipline, responsibility, and karmic reckoning",
        "Rahu" to "ambition, hunger, and unfamiliar terrain",
        "Ketu" to "release, withdrawal, and inward mastery",
    )

    private val PLANET_TONE = mapOf(
        "Sun" to "a re-set of who is in charge of your life",
        "Moon" to "a domestic and emotional re-tuning",
        "Mars" to "an action and conflict cycle that re-draws your boundaries",
        "Mercury" to "a communication, learning, and commerce reset",
        "Jupiter" to "an expansion window where teachers, study, or travel widen the field",
        "Venus" to "a relationship, money, and aesthetic recalibration",
        "Saturn" to "a long, structural rebuild that asks for maturity",
        "Rahu" to "an ambition spike that pulls you into unfamiliar rooms",
        "Ketu" to "a stripping-back where attachments quietly fall away",
    )

    private val ORDINALS = listOf("first", "second", "third", "fourth")

    /* toISOString: always milliseconds, always UTC, always "Z". */
    private val ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    fun isoString(instant: Instant): String = ISO.format(instant)

    /** Every chapter for [natal], as the engine would compute it at [now]. Empty when the birth instant is unknown. */
    fun compute(natal: NatalChart, now: Instant = Instant.now()): List<LifeShift> {
        val birth = birthMs(natal) ?: return emptyList()
        val candidates = mutableListOf<Candidate>()

        for (period in natal.mahaPeriods) {
            if (period.isPartial) continue
            val pivot = dayMs(period.start)
            if (abs(diffYears(pivot, birth)) < 0.5) continue
            candidates.add(mahadasha(period, pivot, birth, natal))
        }
        for (n in 1..3) candidates.add(planetaryReturn(ShiftKind.SaturnReturn, birth, n, natal))
        for (n in 1..8) candidates.add(planetaryReturn(ShiftKind.JupiterReturn, birth, n, natal))
        for (n in 1..5) candidates.add(planetaryReturn(ShiftKind.NodalReturn, birth, n, natal))

        /* The first of a kind on a given day stands. */
        val deduped = LinkedHashMap<String, Candidate>()
        for (candidate in candidates) {
            deduped.putIfAbsent("${candidate.kind.apiValue}:${isoString(candidate.pivot).take(10)}", candidate)
        }

        val nowMs = now.toEpochMilli()
        val picked = deduped.values
            .sortedBy { abs(it.pivot.toEpochMilli() - nowMs) }
            .take(MAX)
            .sortedBy { it.pivot.toEpochMilli() }

        return picked.mapIndexed { index, candidate ->
            LifeShift(
                index = index + 1,
                kind = candidate.kind,
                label = candidate.label,
                planet = candidate.planet,
                pivot = candidate.pivot,
                windowStart = candidate.windowStart,
                windowEnd = candidate.windowEnd,
                status = when {
                    nowMs < candidate.windowStart.toEpochMilli() -> ShiftStatus.Upcoming
                    nowMs > candidate.windowEnd.toEpochMilli() -> ShiftStatus.Past
                    else -> ShiftStatus.Active
                },
                ageAtPivot = candidate.ageAtPivot,
                theme = candidate.theme,
                narrative = candidate.narrative,
                evidence = candidate.evidence,
                occurrence = candidate.occurrence,
                facts = candidate.facts,
            )
        }
    }

    /**
     * What /insights/life-shifts puts on the page, and so asks readings for:
     * the active and upcoming chapters (two at most) on top, or the last
     * chapter when none is ahead, then every past one. A third chapter ahead
     * is not shown, as on the web.
     */
    data class Page(val featured: List<LifeShift>, val past: List<LifeShift>) {
        val shown: List<LifeShift> get() = featured + past
    }

    fun page(shifts: List<LifeShift>): Page {
        val forward = shifts.filter { it.status != ShiftStatus.Past }.take(2)
        val featured = forward.ifEmpty { shifts.takeLast(1) }
        val featuredIds = featured.map { it.id }.toSet()
        return Page(featured, shifts.filter { it.status == ShiftStatus.Past && it.id !in featuredIds })
    }

    /** The body the route reads: the chapters' ids, the depth the life chapters page asks for, and the language. */
    fun requestBody(ids: List<String>, language: String = "en"): String = JSONObject()
        .put("ids", JSONArray(ids.take(MAX)))
        .put("depth", "compact")
        .put("language", language)
        .toString()

    /** The readings in a JSON answer ({readings, cached}) or one NDJSON `readings` line, by chapter id. */
    fun parseReadings(json: JSONObject): Map<String, ClassicalReading> {
        val readings = json.optJSONArray("readings") ?: return emptyMap()
        return readings.objects().mapNotNull { entry ->
            val id = entry.opt("id") as? String ?: return@mapNotNull null
            val reading = ClassicalReading.parse(entry.optJSONObject("reading")) ?: return@mapNotNull null
            if (reading.isUsable) id to reading else null
        }.toMap()
    }

    /** One line of the stream: readings, the end, or the route saying nothing could be written. */
    sealed interface StreamLine {
        data class Readings(val readings: Map<String, ClassicalReading>) : StreamLine
        data object Done : StreamLine
        data object Failed : StreamLine
    }

    fun parseLine(line: String): StreamLine? {
        if (line.isBlank()) return null
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        return when (json.optString("type")) {
            "readings" -> StreamLine.Readings(parseReadings(json))
            "done" -> StreamLine.Done
            "error" -> StreamLine.Failed
            else -> null
        }
    }

    /* ── The engine ─────────────────────────────────────────────────────── */

    private data class Candidate(
        val kind: ShiftKind,
        val label: String,
        val planet: String,
        val pivot: Instant,
        val windowStart: Instant,
        val windowEnd: Instant,
        val ageAtPivot: Int,
        val theme: String,
        val narrative: String,
        val evidence: String,
        val occurrence: Int,
        val facts: List<ShiftFact>,
    )

    /*
     * The audit writes the birth instant without an offset ("1990-06-15T09:00");
     * the engine reads it as UTC, which it is. The local time is the fallback,
     * read the same way, then the first period's start.
     */
    internal fun birthMs(natal: NatalChart): Double? {
        (natal.birthUtcIso ?: natal.birthLocalIso)?.let { value ->
            parseUtc(value)?.let { return it.toEpochMilli().toDouble() }
        }
        val first = natal.mahaPeriods.firstOrNull() ?: return null
        return dayMs(first.sequenceStart ?: first.start)
    }

    private fun parseUtc(value: String): Instant? = runCatching {
        when {
            !value.contains('T') -> LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()
            value.endsWith("Z") || value.endsWith("z") || ZONED.containsMatchIn(value) -> OffsetDateTime.parse(value).toInstant()
            else -> LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
        }
    }.getOrNull()

    private val ZONED = Regex("[+-]\\d{2}:?\\d{2}$")

    private fun dayMs(date: LocalDate): Double = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toDouble()

    private fun diffYears(a: Double, b: Double): Double = (a - b) / (YEAR_DAYS * DAY_MS)

    /* new Date(ms) truncates a fractional millisecond toward zero; toLong does the same. */
    private fun instant(ms: Double): Instant = Instant.ofEpochMilli(ms.toLong())

    /* Math.round: halves go up. */
    private fun jsRound(value: Double): Long = Math.floor(value + 0.5).roundToLong()

    private fun tag(planet: String, natal: NatalChart): String =
        natal.planet(planet)?.let { "${it.sign} / H${it.house}" }.orEmpty()

    /* The same placement as [tag], kept apart; null where [tag] is empty. */
    private fun natalFact(planet: String, natal: NatalChart): ShiftFact.Natal? =
        natal.planet(planet)?.let { ShiftFact.Natal(planet, it.sign, it.house) }

    private fun nodeFact(planet: String, natal: NatalChart): ShiftFact.Node? =
        natal.planet(planet)?.let { ShiftFact.Node(planet, it.sign, it.house) }

    private fun mahadasha(period: MahaPeriod, pivot: Double, birth: Double, natal: NatalChart): Candidate {
        val planet = period.planet
        val age = maxOf(0L, jsRound(diffYears(pivot, birth))).toInt()
        val placement = tag(planet, natal)
        val tone = PLANET_TONE[planet] ?: "a $planet cycle that re-orders priorities"
        val theme = PLANET_THEMES[planet] ?: "a new chapter"
        val where = if (placement.isNotEmpty()) " ($placement)" else ""
        return Candidate(
            kind = ShiftKind.Mahadasha,
            label = "$planet mahadasha begins",
            planet = planet,
            pivot = instant(pivot),
            windowStart = instant(pivot - 270 * DAY_MS),
            windowEnd = instant(pivot + 270 * DAY_MS),
            ageAtPivot = age,
            theme = theme,
            narrative = "The $planet mahadasha opens around age $age — $tone. Its themes are $theme, and they tend to " +
                "surface most clearly in the area where $planet sits in your chart$where. The 12 to 18 months around " +
                "the start are usually where the new chapter announces itself.",
            evidence = listOf(
                "Mahadasha lord: $planet",
                "Lasts ~${jsNumber(period.years)} years",
                if (placement.isNotEmpty()) "Natal $planet: $placement" else "",
            ).filter { it.isNotEmpty() }.joinToString(" | "),
            occurrence = 0,
            facts = listOfNotNull(ShiftFact.Lord(planet), ShiftFact.Lasts(period.years), natalFact(planet, natal)),
        )
    }

    private fun planetaryReturn(kind: ShiftKind, birth: Double, occurrence: Int, natal: NatalChart): Candidate {
        val ordinal = ORDINALS.getOrNull(occurrence - 1) ?: "${occurrence}th"
        val years = when (kind) {
            ShiftKind.SaturnReturn -> SATURN_RETURN_YEARS
            ShiftKind.JupiterReturn -> JUPITER_RETURN_YEARS
            else -> NODAL_RETURN_YEARS
        } * occurrence
        /* addYears: base + years * YEAR_DAYS * DAY_MS, multiplied left to right. */
        val pivot = instant(birth + years * YEAR_DAYS * DAY_MS).toEpochMilli().toDouble()
        val age = jsRound(diffYears(pivot, birth))
        val planet: String
        val label: String
        val theme: String
        val halfWidth: Int
        val narrative: String
        val evidence: String
        val facts: List<ShiftFact>
        when (kind) {
            ShiftKind.SaturnReturn -> {
                planet = "Saturn"
                label = "Saturn return ($ordinal)"
                theme = "structure, responsibility, and the cost of choices"
                halfWidth = 365
                val placement = tag("Saturn", natal)
                val where = if (placement.isNotEmpty()) " ($placement)" else ""
                narrative = "Saturn comes back to its birth position around age $age. This is the classic life pivot: " +
                    "commitments harden, false structures crack, and the area where Saturn sits in your chart$where " +
                    "demands a more honest version. Expect a 9 to 18 month rebuild, not an event."
                evidence = listOf("Cycle: ~29.5 years", if (placement.isNotEmpty()) "Natal Saturn: $placement" else "")
                    .filter { it.isNotEmpty() }.joinToString(" | ")
                facts = listOfNotNull(ShiftFact.Cycle(29.5), natalFact("Saturn", natal))
            }
            ShiftKind.JupiterReturn -> {
                planet = "Jupiter"
                label = "Jupiter return ($ordinal)"
                theme = "expansion, meaning, and a wider field of play"
                halfWidth = 240
                val placement = tag("Jupiter", natal)
                val where = if (placement.isNotEmpty()) " ($placement)" else ""
                narrative = "Jupiter completes its 12-year cycle near age $age. Doors that felt locked tend to open; " +
                    "teachers, study, travel, or new ethical frames arrive. Where Jupiter sits in your chart$where is " +
                    "the room that gets enlarged."
                evidence = listOf("Cycle: ~11.9 years", if (placement.isNotEmpty()) "Natal Jupiter: $placement" else "")
                    .filter { it.isNotEmpty() }.joinToString(" | ")
                facts = listOfNotNull(ShiftFact.Cycle(11.9), natalFact("Jupiter", natal))
            }
            else -> {
                planet = "Rahu / Ketu"
                label = "Nodal return ($ordinal)"
                theme = "fate axis — what you reach for and what you release"
                halfWidth = 270
                val rahu = tag("Rahu", natal)
                val ketu = tag("Ketu", natal)
                narrative = "The lunar nodes return to their natal axis around age $age. This is when the soul " +
                    "direction asserts itself — Rahu's hunger${if (rahu.isNotEmpty()) " ($rahu)" else ""} pulls you " +
                    "forward while Ketu${if (ketu.isNotEmpty()) " ($ketu)" else ""} loosens what is finished. " +
                    "Decisions made here often look obvious in hindsight."
                evidence = listOf(
                    "Cycle: ~18.6 years",
                    if (rahu.isNotEmpty()) "Rahu: $rahu" else "",
                    if (ketu.isNotEmpty()) "Ketu: $ketu" else "",
                ).filter { it.isNotEmpty() }.joinToString(" | ")
                facts = listOfNotNull(ShiftFact.Cycle(18.6), nodeFact("Rahu", natal), nodeFact("Ketu", natal))
            }
        }
        return Candidate(
            kind = kind,
            label = label,
            planet = planet,
            pivot = instant(pivot),
            windowStart = instant(pivot - halfWidth * DAY_MS),
            windowEnd = instant(pivot + halfWidth * DAY_MS),
            ageAtPivot = maxOf(0L, age).toInt(),
            theme = theme,
            narrative = narrative,
            evidence = evidence,
            occurrence = occurrence,
            facts = facts,
        )
    }

    /* A number as JavaScript prints it in a template: 17, not 17.0. */
    private fun jsNumber(value: Double): String =
        if (value == Math.floor(value) && abs(value) < 1e15) value.toLong().toString() else value.toString()

    /* ── Dates on the cards ──────────────────────────────────────────────── */

    private fun monthYear(locale: Locale) = AppDates.formatter("MMMM yyyy", "MMMMyyyy", locale).withZone(ZoneOffset.UTC)
    private fun shortMonthYear(locale: Locale) = AppDates.formatter("MMM yyyy", "MMMyyyy", locale).withZone(ZoneOffset.UTC)

    /** "September 2027" in English, in UTC as the engine counts (formatShiftPivot); the app's language otherwise. */
    fun pivotLabel(shift: LifeShift, locale: Locale = AppDates.locale): String = monthYear(locale).format(shift.pivot)

    /** "Dec 2026 → Jun 2028" (formatShiftWindow). */
    fun windowLabel(shift: LifeShift, locale: Locale = AppDates.locale): String {
        val format = shortMonthYear(locale)
        return "${format.format(shift.windowStart)} → ${format.format(shift.windowEnd)}"
    }
}
