package site.lagnaatelier.app.look

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import site.lagnaatelier.app.account.FakeSite
import site.lagnaatelier.app.data.AppDates
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.ReadingLanguage
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.lifeareas.AskQuestion
import site.lagnaatelier.app.data.lifeareas.LifeAreaKey
import site.lagnaatelier.app.data.lifeareas.MoreReadingsApi
import site.lagnaatelier.app.data.periods.CurrentPeriod
import site.lagnaatelier.app.data.periods.DashaReading
import site.lagnaatelier.app.data.periods.NatalParser
import site.lagnaatelier.app.data.periods.PeriodsApi
import site.lagnaatelier.app.data.reading.Yoga
import site.lagnaatelier.app.data.reading.YogaClassics
import site.lagnaatelier.app.data.reading.YogaFamily
import site.lagnaatelier.app.data.reading.YogaStrength
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.timing.RunningPeriods
import site.lagnaatelier.app.data.timing.TimingQueries
import site.lagnaatelier.app.data.vargas.Varga
import site.lagnaatelier.app.data.vargas.VargaPosition
import site.lagnaatelier.app.ui.more.MoreHolders
import site.lagnaatelier.app.ui.timing.TimingHolders
import java.net.URLDecoder
import java.time.LocalDate
import java.util.Locale

/*
 * The reader's language on every website call that takes one, against a
 * stand-in site on 127.0.0.1, so what is checked is what LagnaApi actually
 * sends; and the keys the app files written readings under, which must part
 * two languages.
 */
class ReadingLanguageTest {
    private val site = FakeSite()
    private val api = LagnaApi(site.baseUrl)
    private val details = TimingQueries.sampleDetails
    private val natal = NatalParser.parse(javaClass.classLoader!!.getResource("chart-ananya-mehra.json")!!.readText())

    @After
    fun close() {
        site.close()
        AppDates.locale = Locale.ENGLISH
    }

    private fun query(call: FakeSite.Call): Map<String, String> =
        call.query.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
            val (key, value) = it.split("=", limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    private fun bodyLanguage(route: String): String? =
        JSONObject(site.callsTo(route).single().body).optString("language").ifEmpty { null }

    /* What the call parses is not the point here; a minimal answer may not satisfy it. */
    private fun quietly(call: suspend () -> Unit) = runBlocking { runCatching { call() } }

    @Test
    fun theAppsLanguageIsOneTheRoutesTakeOrEnglish() {
        AppLanguage.SUPPORTED.forEach { tag -> assertEquals(tag, ReadingLanguage.of(Locale.forLanguageTag(tag))) }
        assertEquals("hi", ReadingLanguage.of(Locale.forLanguageTag("hi-IN")))
        assertEquals("en", ReadingLanguage.of(Locale.JAPANESE))
        AppDates.locale = Locale.GERMAN
        assertEquals("de", ReadingLanguage.current)
    }

    @Test
    fun lifeAreaNotesAndAnswersAskInTheLanguage() {
        site.on("GET ${MoreReadingsApi.AREA_CLASSICS}", body = """{"readings":{}}""")
        site.on("GET ${MoreReadingsApi.ASK_CLASSICS}", body = """{"reading":null}""")
        site.on("POST ${MoreReadingsApi.ASK_CLASSICS}", body = """{"reading":null}""")
        site.on("GET ${MoreReadingsApi.DOMAIN_BRIEF}", body = """{"briefs":{}}""")
        val routes = MoreReadingsApi(api, "hi")

        quietly { routes.areaClassics(details) }
        quietly { routes.ask(details, AskQuestion.entries.first()) }
        quietly { routes.askTyped(details, "Will my work move abroad?") }
        quietly { routes.briefs(details, LifeAreaKey.entries.first()) }

        assertEquals("hi", query(site.callsTo("GET ${MoreReadingsApi.AREA_CLASSICS}").single())["language"])
        assertEquals("hi", query(site.callsTo("GET ${MoreReadingsApi.ASK_CLASSICS}").single())["language"])
        assertEquals("hi", bodyLanguage("POST ${MoreReadingsApi.ASK_CLASSICS}"))
        /* The birth details stay in the query of the typed question, as on GET. */
        assertEquals(details.name, query(site.callsTo("POST ${MoreReadingsApi.ASK_CLASSICS}").single())["name"])
        /* domain-brief writes English only and takes no language. */
        assertFalse("language" in query(site.callsTo("GET ${MoreReadingsApi.DOMAIN_BRIEF}").single()))
    }

    @Test
    fun vargaNotesAskInTheLanguage() {
        site.on("POST /api/chart/varga-commentary", body = """{"notes":{}}""")
        val d9 = Varga(9, "D9", "", false, listOf(VargaPosition("Ascendant", Sign.Virgo, Sign.Virgo)))
        quietly { MoreReadingsApi(api, "bn").vargaNotes(listOf(d9)) }
        assertEquals("bn", bodyLanguage("POST /api/chart/varga-commentary"))
    }

    @Test
    fun periodReadingsAndLifeChaptersAskInTheLanguage() {
        site.on("POST ${PeriodsApi.DASHA_READING_PATH}", body = """{"reading":null}""")
        site.on("POST ${PeriodsApi.LIFE_SHIFTS_PATH}", body = """{"readings":[]}""")
        site.on("POST ${PeriodsApi.CURRENT_PERIOD_PATH}", body = """{"reading":"Ein Absatz."}""")
        val periods = PeriodsApi(api, site.baseUrl)
        val running = RunningPeriods(natal.stack[0], natal.stack[1], natal.stack.getOrNull(2), emptyList())

        quietly { periods.dashaReading(details, DashaReading.runningChains(running)[1], "de") }
        quietly { periods.lifeShifts(details, listOf("saturn-return-2019-11-29T13:56:18.960Z"), "de") {} }
        val paragraph = runBlocking {
            periods.currentPeriod(CurrentPeriod.requestBody(natal, 40.0, "de")!!)
        }

        assertEquals("de", bodyLanguage("POST ${PeriodsApi.DASHA_READING_PATH}"))
        assertEquals("de", bodyLanguage("POST ${PeriodsApi.LIFE_SHIFTS_PATH}"))
        assertEquals("de", bodyLanguage("POST ${PeriodsApi.CURRENT_PERIOD_PATH}"))
        assertEquals("Ein Absatz.", paragraph)
    }

    @Test
    fun theYogaNoteAsksInTheLanguageAndIsKeptPerLanguage() {
        site.on("POST ${YogaClassics.PATH}", body = """{"reading":null}""")
        val yogas = listOf(
            Yoga(
                id = "gaja_kesari", name = "Gaja Kesari", sanskrit = null, family = YogaFamily.Benefic,
                strength = YogaStrength.Moderate, weight = 69, planets = listOf("Moon", "Jupiter"), description = "", effects = "",
            ),
        )
        val french = YogaClassics.requestBody(yogas, "fr")!!
        quietly { YogaClassics.fetch(api, french) }
        assertEquals("fr", bodyLanguage("POST ${YogaClassics.PATH}"))
        /* The note is filed under its request body (YogaClassicsNotes), so two languages are two notes. */
        assertNotEquals(YogaClassics.requestBody(yogas, "en"), french)
    }

    @Test
    fun heldReadingsAreKeyedByLanguage() {
        val today = LocalDate.of(2026, 11, 4)
        assertNotEquals(MoreHolders.key(details, "en"), MoreHolders.key(details, "hi"))
        assertEquals(MoreHolders.key(details, "hi"), MoreHolders.key(details.copy(), "hi"))
        assertNotEquals(TimingHolders.key(details, today, "en"), TimingHolders.key(details, today, "de"))
        assertNotEquals(TimingHolders.key(details, today, "de"), TimingHolders.key(details, today.plusDays(1), "de"))
    }

    @Test
    fun theReportsRoutesAreEnglishOnly() {
        assertEquals("en", ReportApi.WRITTEN_IN)
        assertNull(ReportApi.storyReportQuery(details)["language"])
    }
}
