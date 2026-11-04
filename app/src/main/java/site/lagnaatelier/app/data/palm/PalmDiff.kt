package site.lagnaatelier.app.data.palm

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * What changed between two saved palm readings, the app's version of the
 * web's lib/palm-readings/diff.ts. Pure: it reads two readings already on
 * the phone and never asks the site anything.
 *
 * Two small departures from the web, both kinder to model output: the
 * earlier reading is always A, so "newly visible" means newer; and marks
 * and mounts match ignoring case and spacing, so "Mount of Jupiter" and
 * "mount of  jupiter" are not reported as one lost and one new.
 */

enum class StrengthDirection { Stronger, Weaker, Unchanged }

data class LineStrengthChange(val line: LineKey, val from: String?, val to: String?, val direction: StrengthDirection)

data class LineVisibilityChange(val line: LineKey, val from: String?, val to: String?)

data class PalmDiff(
    /** The earlier reading. */
    val a: SavedPalmReading,
    /** The later one. */
    val b: SavedPalmReading,
    val daysApart: Int,
    /** All four major lines, in order, changed or not. */
    val lineStrengthChanges: List<LineStrengthChange>,
    /** Only the lines whose visibility differs. */
    val lineVisibilityChanges: List<LineVisibilityChange>,
    val aSummary: String,
    val bSummary: String,
    val newMarkings: List<String>,
    val lostMarkings: List<String>,
    val newMounts: List<String>,
    val lostMounts: List<String>,
) {
    val allLinesUnchanged: Boolean get() = lineStrengthChanges.all { it.direction == StrengthDirection.Unchanged }
    val markingsChanged: Boolean get() = newMarkings.isNotEmpty() || lostMarkings.isNotEmpty()
    val mountsChanged: Boolean get() = newMounts.isNotEmpty() || lostMounts.isNotEmpty()
}

object PalmDiffer {
    /* The web's STRENGTH_RANK. */
    private val RANK = mapOf("absent" to 0, "faint" to 1, "moderate" to 2, "strong" to 3)
    private const val DAY_MS = 24 * 60 * 60 * 1000.0
    private val SPACES = Regex("\\s+")

    /** Compares two readings in either order: the earlier becomes A. */
    fun diff(first: SavedPalmReading, second: SavedPalmReading): PalmDiff {
        val (a, b) = if (first.createdAtMillis <= second.createdAtMillis) first to second else second to first
        val ra = a.reading
        val rb = b.reading

        val strengths = LineKey.entries.map { line ->
            val from = ra.lines[line]?.strength
            val to = rb.lines[line]?.strength
            LineStrengthChange(line, from, to, direction(from, to))
        }
        val visibilities = LineKey.entries.mapNotNull { line ->
            val from = ra.lineConfidence[line]?.visibility
            val to = rb.lineConfidence[line]?.visibility
            if ((from != null || to != null) && from != to) LineVisibilityChange(line, from, to) else null
        }

        return PalmDiff(
            a = a,
            b = b,
            daysApart = (abs(b.createdAtMillis - a.createdAtMillis) / DAY_MS).roundToInt(),
            lineStrengthChanges = strengths,
            lineVisibilityChanges = visibilities,
            aSummary = ra.overallSummary,
            bSummary = rb.overallSummary,
            newMarkings = missingFrom(rb.markings.observed, ra.markings.observed),
            lostMarkings = missingFrom(ra.markings.observed, rb.markings.observed),
            newMounts = missingFrom(rb.mounts.prominent, ra.mounts.prominent),
            lostMounts = missingFrom(ra.mounts.prominent, rb.mounts.prominent),
        )
    }

    internal fun direction(from: String?, to: String?): StrengthDirection {
        val a = RANK[from] ?: return StrengthDirection.Unchanged
        val b = RANK[to] ?: return StrengthDirection.Unchanged
        return when {
            b > a -> StrengthDirection.Stronger
            b < a -> StrengthDirection.Weaker
            else -> StrengthDirection.Unchanged
        }
    }

    /** The entries of [values] not in [other], once each, in their order, as first written. */
    internal fun missingFrom(values: List<String>, other: List<String>): List<String> {
        val present = other.map(::key).filter { it.isNotEmpty() }.toSet()
        val seen = mutableSetOf<String>()
        return values.map { it.trim() }.filter { value ->
            val k = key(value)
            k.isNotEmpty() && k !in present && seen.add(k)
        }
    }

    private fun key(value: String) = value.trim().replace(SPACES, " ").lowercase()
}
