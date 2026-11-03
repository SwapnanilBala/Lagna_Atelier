package site.lagnaatelier.app.data.reading

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.Sign
import kotlin.math.roundToInt

/*
 * The reading tab's longer sections, read from the same /api/chart response
 * the chart screen is built from: the planet table, house support, every
 * finding with its evidence, and the yogas.
 *
 * Kept apart from ChartSummary's own fields so the four sections can grow
 * without the summary every screen takes growing with them. Each section is
 * read on its own and a section the response does not carry (or carries in a
 * shape this parser does not know) comes back empty, never as a failed chart:
 * the rest of the reading is still worth showing.
 */
data class ReadingSections(
    val positions: PlanetTable? = null,
    val houseSupport: HouseSupport? = null,
    /** Every finding the rule engine matched, the chosen few first. */
    val findings: List<FullFinding> = emptyList(),
    /** Every yoga the chart has, strongest first. */
    val yogas: List<Yoga> = emptyList(),
)

// --- Planet positions ---------------------------------------------------------

/**
 * One graha's row. [signHouse] is counted by sign from the rising sign, as the
 * chart draws it; [bhava] is the house under the chart's house system, which
 * differs from it only outside Whole Sign.
 */
data class PlanetRow(
    val name: String,
    val sign: Sign,
    val degreeInSign: Double,
    val signHouse: Int,
    val bhava: Int,
    val retrograde: Boolean,
    val combust: Boolean,
)

/** [houseSystem] is null under Whole Sign, where the table needs only the one house column. */
data class PlanetTable(val rows: List<PlanetRow>, val houseSystem: HouseSystemName?) {
    val anyFlags: Boolean get() = rows.any { it.retrograde || it.combust }
}

/** The house systems besides Whole Sign, as the response's `house_system` names them. */
enum class HouseSystemName(val code: String) {
    Equal("equal"), Placidus("placidus"), Koch("koch"),
    Campanus("campanus"), Regiomontanus("regiomontanus"), Porphyry("porphyry");

    companion object {
        fun of(code: String): HouseSystemName? = entries.firstOrNull { it.code == code }
    }
}

// --- House support ----------------------------------------------------------

enum class SupportBand { Strong, Neutral, Weak }

/** The classical groupings a house belongs to (lib/engines/house-support-engine.ts HOUSE_GROUPS). */
enum class HouseGroup { Kendra, Panaphara, Apoklima, Trikona, Upachaya, Dusthana }

data class HouseRow(
    val house: Int,
    val sign: Sign,
    val bindus: Int,
    /** Bindus against the 28.1 average, as a whole percentage: 100 is exactly average. */
    val percent: Int,
    val band: SupportBand,
) {
    val groups: List<HouseGroup> get() = HouseSupport.GROUPS.getValue(house)
}

data class HouseSupport(val houses: List<HouseRow>) {
    val totalBindus: Int get() = houses.sumOf { it.bindus }

    /*
     * False when the house system puts one sign on two cusps and leaves
     * another with none: the twelve houses then do not add back to a clean
     * 337, and the total is reporting the house division as much as the chart.
     */
    val totalIsExact: Boolean get() = houses.map { it.sign }.toSet().size == 12

    val strongest: HouseRow get() = houses.maxBy { it.bindus }
    val weakest: HouseRow get() = houses.minBy { it.bindus }
    val aboveAverage: Int get() = houses.count { it.bindus > AVERAGE }

    companion object {
        /** Every chart's Sarvashtakavarga sums to this; the engine enforces it. */
        const val TOTAL = 337

        /** 337 / 12, the bindus a house holds at dead average. */
        const val AVERAGE = TOTAL / 12.0

        /*
         * Kendra, Panaphara and Apoklima split all twelve between them;
         * Trikona, Upachaya and Dusthana overlap them and each other on
         * purpose (the 6th really is both an Upachaya and a Dusthana).
         */
        val GROUPS: Map<Int, List<HouseGroup>> = mapOf(
            1 to listOf(HouseGroup.Kendra, HouseGroup.Trikona),
            2 to listOf(HouseGroup.Panaphara),
            3 to listOf(HouseGroup.Apoklima, HouseGroup.Upachaya),
            4 to listOf(HouseGroup.Kendra),
            5 to listOf(HouseGroup.Panaphara, HouseGroup.Trikona),
            6 to listOf(HouseGroup.Apoklima, HouseGroup.Upachaya, HouseGroup.Dusthana),
            7 to listOf(HouseGroup.Kendra),
            8 to listOf(HouseGroup.Panaphara, HouseGroup.Dusthana),
            9 to listOf(HouseGroup.Apoklima, HouseGroup.Trikona),
            10 to listOf(HouseGroup.Kendra, HouseGroup.Upachaya),
            11 to listOf(HouseGroup.Panaphara, HouseGroup.Upachaya),
            12 to listOf(HouseGroup.Apoklima, HouseGroup.Dusthana),
        )

        /* The thresholds the web's Ashtakavarga panel uses, so "strong" here is the same claim. */
        fun bandFor(bindus: Int): SupportBand = when {
            bindus >= 28 -> SupportBand.Strong
            bindus <= 25 -> SupportBand.Weak
            else -> SupportBand.Neutral
        }

        /**
         * Ashtakavarga scores signs, so each house takes the total of the sign
         * on it. The sign comes from the chart's own `houses`, not from counting
         * off the ascendant: the two disagree under Placidus and the other
         * quadrant systems, where a sign can hold two cusps or none.
         */
        fun of(sarvashtakavarga: List<Int>, houseSigns: Map<Int, Sign>): HouseSupport? {
            if (sarvashtakavarga.size != 12 || houseSigns.size != 12) return null
            val rows = (1..12).map { house ->
                val sign = houseSigns[house] ?: return null
                val bindus = sarvashtakavarga[sign.ordinal]
                HouseRow(house, sign, bindus, (bindus / AVERAGE * 100).roundToInt(), bandFor(bindus))
            }
            return HouseSupport(rows)
        }
    }
}

// --- Findings -----------------------------------------------------------------

/** The area of life a finding speaks to (the engine's RuleCategory). */
enum class FindingTheme(val code: String) {
    Core("core"), Career("career"), Love("love");

    companion object {
        fun of(code: String): FindingTheme? = entries.firstOrNull { it.code == code }
    }
}

/** One fact a finding rests on: "Rising sign: Virgo". */
data class Claim(val label: String, val value: String, val detail: String? = null)

data class FullFinding(
    val key: String,
    val theme: FindingTheme,
    val headline: String,
    val body: String,
    /** The honest counterweight, when the engine gives one. */
    val tension: String?,
    /** "Shows up in about 9 of every 100 charts". */
    val rarityLabel: String,
    val rare: Boolean,
    /** 1-based rank among the findings chosen for the page; 0 for the rest. */
    val rank: Int,
    val score: Double,
    val technicalNote: String,
    val claims: List<Claim>,
) {
    val isTop: Boolean get() = rank > 0
}

/** The full reading's filters: everything, the chosen few, or one area of life. */
enum class FindingFilter {
    All, Top, Core, Career, Love;

    fun matches(finding: FullFinding): Boolean = when (this) {
        All -> true
        Top -> finding.isTop
        Core -> finding.theme == FindingTheme.Core
        Career -> finding.theme == FindingTheme.Career
        Love -> finding.theme == FindingTheme.Love
    }

    companion object {
        /** The filters worth offering for [findings]: one that would list nothing is left out. */
        fun offered(findings: List<FullFinding>): List<FindingFilter> =
            entries.filter { filter -> findings.any(filter::matches) }
    }
}

// --- Yogas --------------------------------------------------------------------

/** The yoga families, in the order the web's reading room groups them. */
enum class YogaFamily(val code: String) {
    Mahapurusha("mahapurusha"), Wealth("wealth"), Benefic("benefic"),
    Viparita("viparita"), Nabhasa("nabhasa"), Challenging("challenging");

    companion object {
        fun of(code: String): YogaFamily? = entries.firstOrNull { it.code == code }
    }
}

enum class YogaStrength(val code: String) {
    Strong("strong"), Moderate("moderate"), Weak("weak");

    companion object {
        fun of(code: String): YogaStrength? = entries.firstOrNull { it.code == code }
    }
}

data class Yoga(
    val id: String,
    val name: String,
    /** Only when it says something the English name does not. */
    val sanskrit: String?,
    val family: YogaFamily,
    val strength: YogaStrength,
    /** The engine's weighting of strength and family; the panel's sort key, never shown. */
    val weight: Int,
    val planets: List<String>,
    /** What forms it in this chart. */
    val description: String,
    /** What it means. */
    val effects: String,
    val activationTiming: String? = null,
    val cancellation: String? = null,
    val source: String? = null,
)

// --- Parsing ------------------------------------------------------------------

object ReadingSectionsParser {
    private val grahas = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
    private val rareBands = setOf("rare", "very_rare")

    fun parse(json: String): ReadingSections = parse(JSONObject(json))

    /** [root] is the whole /api/chart response. */
    fun parse(root: JSONObject): ReadingSections {
        val chart = root.optJSONObject("chart") ?: return ReadingSections()
        return ReadingSections(
            positions = section { positions(chart) },
            houseSupport = section { houseSupport(root, chart) },
            findings = section { findings(chart) }.orEmpty(),
            yogas = section { yogas(chart) }.orEmpty(),
        )
    }

    /* One section's failure is that section's absence, not the chart's. */
    private inline fun <T> section(read: () -> T): T? = try {
        read()
    } catch (e: Exception) {
        null
    }

    private fun positions(chart: JSONObject): PlanetTable? {
        val ascendant = sign(chart.getJSONObject("ascendant").getString("sign")) ?: return null
        val planets = chart.getJSONArray("planets").objects()
        val rows = planets.mapNotNull { planet ->
            val name = planet.getString("name")
            val sign = sign(planet.getString("sign")) ?: return@mapNotNull null
            PlanetRow(
                name = name,
                sign = sign,
                degreeInSign = planet.getDouble("degree_in_sign"),
                signHouse = signHouse(sign, ascendant),
                bhava = planet.optInt("house", signHouse(sign, ascendant)),
                retrograde = planet.optBoolean("is_retrograde"),
                combust = planet.optBoolean("is_combust"),
            )
        }.sortedBy { grahas.indexOf(it.name).let { index -> if (index < 0) grahas.size else index } }
        if (rows.isEmpty()) return null
        return PlanetTable(rows, HouseSystemName.of(chart.optString("house_system")))
    }

    private fun houseSupport(root: JSONObject, chart: JSONObject): HouseSupport? {
        val sav = root.optJSONObject("ashtakavarga")?.optJSONArray("sarvashtakavarga") ?: return null
        val totals = (0 until sav.length()).map { sav.getInt(it) }
        val houseSigns = chart.getJSONArray("houses").objects().associate { house ->
            house.getInt("house_number") to (sign(house.getString("sign")) ?: return null)
        }
        return HouseSupport.of(totals, houseSigns)
    }

    /* In the order the web's full reading lists them (lib/rule-order.ts bySelectionRank). */
    private fun findings(chart: JSONObject): List<FullFinding> {
        val rules = chart.optJSONArray("deterministic_rules")?.objects().orEmpty()
        return rules.mapNotNull { finding(it) }.sortedWith(
            compareBy<FullFinding> { if (it.isTop) it.rank else Int.MAX_VALUE }.thenByDescending { it.score },
        )
    }

    private fun finding(rule: JSONObject): FullFinding? {
        val display = rule.optJSONObject("display") ?: return null
        val headline = display.optString("headline").ifBlank { return null }
        val theme = FindingTheme.of(rule.optString("category")) ?: return null
        val evidence = rule.optJSONObject("evidence")
        val rarity = evidence?.optJSONObject("rarity")
        val selection = rule.optJSONObject("selection")
        val selected = selection?.optBoolean("selected") == true
        return FullFinding(
            key = rule.optString("instance_key").ifBlank { rule.optString("id") },
            theme = theme,
            headline = headline,
            body = display.optString("body"),
            tension = display.text("tension"),
            rarityLabel = display.text("rarity_label") ?: rarity?.let { rarityLabel(it) }.orEmpty(),
            rare = rarity?.optString("band") in rareBands,
            rank = if (selected) selection!!.optInt("rank") else 0,
            score = selection?.optDouble("score", 0.0)?.takeIf { !it.isNaN() } ?: 0.0,
            technicalNote = evidence?.optString("technical_note").orEmpty(),
            claims = evidence?.optJSONArray("claims")?.objects().orEmpty().mapNotNull { claim ->
                val label = claim.text("label") ?: return@mapNotNull null
                Claim(label, claim.opt("value")?.toString().orEmpty(), claim.text("detail"))
            },
        )
    }

    /**
     * The sentence the engine prints for how common a finding is
     * (lib/rules/rarity.ts rarityLabel), for a response that leaves it out.
     */
    fun rarityLabel(rarity: JSONObject): String {
        if (rarity.optBoolean("low_confidence")) return "An unusual combination"
        val rate = rarity.optDouble("fire_rate", 0.0)
        val perHundred = (rate * 100).roundToInt()
        if (perHundred >= 100) return "Present in every chart"
        if (perHundred >= 1) return "Shows up in about $perHundred of every 100 charts"
        val perThousand = (rate * 1000).roundToInt()
        if (perThousand >= 1) return "Shows up in about $perThousand of every 1,000 charts"
        return "Shows up in fewer than 1 in 1,000 charts"
    }

    /* Strongest first, as the web's yoga panel and its classical note rank them (lib/knowledge/yoga-classics.ts). */
    private fun yogas(chart: JSONObject): List<Yoga> {
        val yogas = chart.optJSONArray("yogas")?.objects().orEmpty().mapNotNull { yoga(it) }
        return yogas.sortedWith(compareByDescending<Yoga> { it.weight }.thenBy { it.strength.ordinal })
    }

    private fun yoga(row: JSONObject): Yoga? {
        if (row.has("present") && !row.optBoolean("present")) return null
        val name = row.text("name") ?: return null
        val sanskrit = row.text("sanskrit")
        return Yoga(
            id = row.text("yoga_id") ?: return null,
            name = name,
            /* Generated records repeat the English name here; only a real Sanskrit name is kept. */
            sanskrit = sanskrit?.takeIf { it != name },
            family = YogaFamily.of(row.optString("category")) ?: return null,
            strength = YogaStrength.of(row.optString("strength")) ?: return null,
            weight = row.optInt("occurrence_chance"),
            planets = row.optJSONArray("involved_planets")?.strings().orEmpty(),
            description = row.optString("description"),
            effects = row.optString("effects"),
            activationTiming = row.text("activation_timing"),
            cancellation = row.text("cancellation"),
            source = row.text("source"),
        )
    }

    /** The house a sign falls in, counted from the rising sign: the rising sign's own is the 1st. */
    fun signHouse(sign: Sign, ascendant: Sign): Int = (sign.ordinal - ascendant.ordinal + 12) % 12 + 1

    private fun sign(name: String): Sign? = Sign.entries.firstOrNull { it.label == name }

    /* A string field, or null when it is missing, null or blank. */
    private fun JSONObject.text(key: String): String? =
        (opt(key) as? String)?.trim()?.ifBlank { null }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { opt(it) as? String }
}
