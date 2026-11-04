package site.lagnaatelier.app.ui.reading

import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.Sign

/*
 * Geometry for the North Indian (diamond) chart, after the web app's
 * lib/north-indian-chart.ts, in fractions of the square rather than its 400
 * units. Pure numbers, so it is tested on the JVM.
 *
 * The construction: a square, both diagonals, and the diamond joining the
 * midpoints of its sides. That cuts the square into four kites (houses 1, 4,
 * 7, 10 -- the angles) and eight triangles. The houses are fixed and run
 * anticlockwise from the top kite, which is always the ascendant; the signs
 * rotate, and each house shows the number of its sign (Aries = 1).
 *
 * A planet goes in the house its sign falls in, counted from the ascendant's
 * sign: whole signs, as the web's rasiPlacements does. Never by a planet's
 * `house`, which in this app is the bhava and under a cusp-based system can
 * name a house whose sign is not the planet's own.
 */

/** A point in the chart square, 0..1 on each axis, origin top-left. */
data class ChartPoint(val x: Float, val y: Float)

/** A box in the chart square, in the same fractions. */
data class ChartBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val centre: ChartPoint get() = ChartPoint(x + w / 2, y + h / 2)
}

object NorthIndianGeometry {
    val HOUSES = 1..12

    private const val H = 0.5f
    private const val Q = 0.25f

    private val TL = ChartPoint(0f, 0f)
    private val TR = ChartPoint(1f, 0f)
    private val BR = ChartPoint(1f, 1f)
    private val BL = ChartPoint(0f, 1f)
    private val TOP = ChartPoint(H, 0f)
    private val RIGHT = ChartPoint(1f, H)
    private val BOTTOM = ChartPoint(H, 1f)
    private val LEFT = ChartPoint(0f, H)
    private val CENTRE = ChartPoint(H, H)
    private val INNER_TL = ChartPoint(Q, Q)
    private val INNER_TR = ChartPoint(1 - Q, Q)
    private val INNER_BR = ChartPoint(1 - Q, 1 - Q)
    private val INNER_BL = ChartPoint(Q, 1 - Q)

    /** Each house's region, anticlockwise from the top kite. */
    val HOUSE_POLYGONS: Map<Int, List<ChartPoint>> = mapOf(
        1 to listOf(TOP, INNER_TR, CENTRE, INNER_TL),
        2 to listOf(TL, TOP, INNER_TL),
        3 to listOf(TL, INNER_TL, LEFT),
        4 to listOf(LEFT, INNER_TL, CENTRE, INNER_BL),
        5 to listOf(LEFT, INNER_BL, BL),
        6 to listOf(BL, INNER_BL, BOTTOM),
        7 to listOf(BOTTOM, INNER_BL, CENTRE, INNER_BR),
        8 to listOf(BOTTOM, INNER_BR, BR),
        9 to listOf(BR, INNER_BR, RIGHT),
        10 to listOf(RIGHT, INNER_BR, CENTRE, INNER_TR),
        11 to listOf(RIGHT, INNER_TR, TR),
        12 to listOf(TR, INNER_TR, TOP),
    )

    /* The web's offsets, 24 and 20 of its 400 units. */
    private const val KITE_NUMBER = 0.06f
    private const val EDGE_NUMBER = 0.05f

    /**
     * Where each house's sign number sits: just inside its inner corner, the
     * traditional spot, which clusters the angles' numbers round the centre.
     */
    val SIGN_NUMBER_POINTS: Map<Int, ChartPoint> = mapOf(
        1 to ChartPoint(H, H - KITE_NUMBER),
        4 to ChartPoint(H - KITE_NUMBER, H),
        7 to ChartPoint(H, H + KITE_NUMBER),
        10 to ChartPoint(H + KITE_NUMBER, H),
        2 to ChartPoint(Q, Q - EDGE_NUMBER),
        12 to ChartPoint(1 - Q, Q - EDGE_NUMBER),
        3 to ChartPoint(Q - EDGE_NUMBER, Q),
        11 to ChartPoint(1 - Q + EDGE_NUMBER, Q),
        5 to ChartPoint(Q - EDGE_NUMBER, 1 - Q),
        9 to ChartPoint(1 - Q + EDGE_NUMBER, 1 - Q),
        6 to ChartPoint(Q, 1 - Q + EDGE_NUMBER),
        8 to ChartPoint(1 - Q, 1 - Q + EDGE_NUMBER),
    )

    /*
     * The box a house's planets are written in: the largest comfortable
     * rectangle inside its region, clear of the sign number. The web's 112 x 76
     * for a kite; a triangle's box sits on its outer edge, 84 x 46, 6 in.
     */
    private const val KITE_W = 0.28f
    private const val KITE_H = 0.19f
    private const val EDGE_LONG = 0.21f
    private const val EDGE_SHORT = 0.115f
    private const val EDGE_INSET = 0.015f

    private fun kite(cx: Float, cy: Float) = ChartBox(cx - KITE_W / 2, cy - KITE_H / 2, KITE_W, KITE_H)

    val LABEL_BOXES: Map<Int, ChartBox> = mapOf(
        1 to kite(H, Q),
        4 to kite(Q, H),
        7 to kite(H, 1 - Q),
        10 to kite(1 - Q, H),
        2 to ChartBox(Q - EDGE_LONG / 2, EDGE_INSET, EDGE_LONG, EDGE_SHORT),
        12 to ChartBox(1 - Q - EDGE_LONG / 2, EDGE_INSET, EDGE_LONG, EDGE_SHORT),
        6 to ChartBox(Q - EDGE_LONG / 2, 1 - EDGE_INSET - EDGE_SHORT, EDGE_LONG, EDGE_SHORT),
        8 to ChartBox(1 - Q - EDGE_LONG / 2, 1 - EDGE_INSET - EDGE_SHORT, EDGE_LONG, EDGE_SHORT),
        3 to ChartBox(EDGE_INSET, Q - EDGE_LONG / 2, EDGE_SHORT, EDGE_LONG),
        5 to ChartBox(EDGE_INSET, 1 - Q - EDGE_LONG / 2, EDGE_SHORT, EDGE_LONG),
        11 to ChartBox(1 - EDGE_INSET - EDGE_SHORT, Q - EDGE_LONG / 2, EDGE_SHORT, EDGE_LONG),
        9 to ChartBox(1 - EDGE_INSET - EDGE_SHORT, 1 - Q - EDGE_LONG / 2, EDGE_SHORT, EDGE_LONG),
    )

    /** The sign on [house], counting whole signs from the ascendant's. */
    fun signForHouse(ascendant: Sign, house: Int): Sign =
        Sign.entries[(ascendant.ordinal + house - 1).mod(12)]

    /** The house [sign] falls in, counted from the ascendant's sign: the ascendant's own sign is 1. */
    fun houseOfSign(sign: Sign, ascendant: Sign): Int = (sign.ordinal - ascendant.ordinal).mod(12) + 1

    /** Aries = 1 ... Pisces = 12, the number written in the house. */
    fun signNumber(sign: Sign): Int = sign.ordinal + 1

    /** Each house's bodies, by sign from the ascendant, in the order given. */
    fun placementsByHouse(placements: List<Placement>, ascendant: Sign): Map<Int, List<Placement>> =
        HOUSES.associateWith { house -> placements.filter { houseOfSign(it.sign, ascendant) == house } }

    /** Points-in-polygon by ray casting; edges count as inside. */
    fun contains(polygon: List<ChartPoint>, point: ChartPoint): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
            val onEdge = kotlin.math.abs(cross) < 1e-6f &&
                point.x in minOf(a.x, b.x) - 1e-6f..maxOf(a.x, b.x) + 1e-6f &&
                point.y in minOf(a.y, b.y) - 1e-6f..maxOf(a.y, b.y) + 1e-6f
            if (onEdge) return true
            if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
