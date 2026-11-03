package site.lagnaatelier.app.more

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.lifeareas.AskAnswer
import site.lagnaatelier.app.data.lifeareas.AskQuestion
import site.lagnaatelier.app.data.lifeareas.ClassicalReading
import site.lagnaatelier.app.data.lifeareas.LifeArea
import site.lagnaatelier.app.data.lifeareas.LifeAreaKey
import site.lagnaatelier.app.data.lifeareas.LifeAreasParser
import site.lagnaatelier.app.data.timing.TimingQueries
import site.lagnaatelier.app.data.vargas.Varga
import site.lagnaatelier.app.data.vargas.VargaPosition
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.ui.more.Fetch
import site.lagnaatelier.app.ui.more.MoreHolder
import site.lagnaatelier.app.ui.more.MoreSource
import java.io.IOException

/*
 * The holder decides how often the paid routes are called, so these count the
 * calls: each answer is asked for once per chart, a failed one again only on
 * request, and a typed question one at a time.
 */
class MoreHolderTest {
    private val areas = LifeAreasParser.parseAreas(
        javaClass.classLoader!!.getResource("lifeareas/life-domains.json")!!.readText(),
    )
    private val limit = LagnaApi.apiException(
        429,
        """{"error":{"code":"RATE_LIMITED","message":"Sign in to keep asking the classics today.","details":{"retryAfterSeconds":3600,"scope":"anonymous"}}}""",
    )

    private class Fake(private val areas: List<LifeArea>) : MoreSource {
        val calls = mutableListOf<String>()
        var failAreas: Exception? = null
        var askFailure: Exception? = null
        var typedGate: CompletableDeferred<AskAnswer>? = null

        override suspend fun lifeAreas(details: BirthDetails): List<LifeArea> {
            calls += "areas"
            failAreas?.let { throw it }
            return areas
        }

        override suspend fun briefs(details: BirthDetails, domain: LifeAreaKey): Map<LifeAreaKey, String> {
            calls += "briefs:${domain.apiKey}"
            return LifeAreaKey.entries.associateWith { "brief for ${it.apiKey}" }
        }

        override suspend fun areaClassics(details: BirthDetails): Map<LifeAreaKey, ClassicalReading> {
            calls += "classics"
            return emptyMap()
        }

        override suspend fun askAvailable(details: BirthDetails): List<AskQuestion> {
            calls += "available"
            return AskQuestion.entries
        }

        override suspend fun ask(details: BirthDetails, question: AskQuestion): AskAnswer {
            calls += "ask:${question.id}"
            askFailure?.let { throw it }
            return AskAnswer.Empty
        }

        override suspend fun askTyped(details: BirthDetails, text: String): AskAnswer {
            calls += "typed:$text"
            return typedGate?.await() ?: AskAnswer.Empty
        }

        override suspend fun vargas(details: BirthDetails): List<Varga> {
            calls += "vargas"
            return listOf(varga(9))
        }

        override suspend fun vargaNotes(vargas: List<Varga>): Map<Int, String> {
            calls += "notes:${vargas.map { it.division }}"
            return mapOf(9 to "A note.")
        }
    }

    private fun holder(fake: Fake) =
        MoreHolder(TimingQueries.sampleDetails, fake, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test
    fun loadsTheAreasThenEveryBriefOnceAndTheNotesAlongside() {
        val fake = Fake(areas)
        val holder = holder(fake)
        holder.openLifeAreas()
        holder.openLifeAreas()
        // The briefs name the first area; one answer carries all seven.
        assertEquals(listOf("areas", "briefs:love_life", "classics"), fake.calls)
        assertEquals(7, (holder.briefs as Fetch.Ready).value.size)
        assertEquals(areas, (holder.areas as Fetch.Ready).value)
    }

    @Test
    fun saysWhyTheAreasFailedAndAsksAgainOnlyOnRetry() {
        val fake = Fake(areas).apply { failAreas = IOException("offline") }
        val holder = holder(fake)
        holder.openLifeAreas()
        assertEquals(Fetch.Failed(R.string.error_offline), holder.areas)
        // No brief without the areas to rank.
        assertFalse(fake.calls.any { it.startsWith("briefs") })
        fake.failAreas = null
        holder.openLifeAreas(force = true)
        assertTrue(holder.areas is Fetch.Ready)
        assertEquals(1, fake.calls.count { it.startsWith("briefs") })
    }

    @Test
    fun asksAReadyMadeQuestionOnce() {
        val fake = Fake(areas)
        val holder = holder(fake)
        holder.ask(AskQuestion.Health)
        holder.ask(AskQuestion.Health)
        assertEquals(listOf("ask:health"), fake.calls)
        assertEquals(AskAnswer.Empty, holder.answers[AskQuestion.Health])
        assertEquals(AskQuestion.Health, holder.showing)
    }

    @Test
    fun wordsTheDaysLimitAsThePanelsOwnLineAndLetsAFailedQuestionBeAskedAgain() {
        val fake = Fake(areas).apply { askFailure = limit }
        val holder = holder(fake)
        holder.ask(AskQuestion.Children)
        assertEquals(AskAnswer.Failed(R.string.more_ask_limited), holder.answers[AskQuestion.Children])
        fake.askFailure = null
        holder.ask(AskQuestion.Children)
        assertEquals(AskAnswer.Empty, holder.answers[AskQuestion.Children])
        assertEquals(2, fake.calls.count { it == "ask:children" })
    }

    @Test
    fun sendsOneTypedQuestionAtATime() {
        val gate = CompletableDeferred<AskAnswer>()
        val fake = Fake(areas).apply { typedGate = gate }
        val holder = holder(fake)
        assertFalse(holder.askTyped(" hi "))
        assertTrue(holder.askTyped("  What   suits me?  "))
        assertFalse(holder.askTyped("And another?"))
        assertEquals("What suits me?" to AskAnswer.Pending, holder.typed)
        gate.complete(AskAnswer.Empty)
        assertEquals("What suits me?" to AskAnswer.Empty, holder.typed)
        assertTrue(holder.askTyped("And another?"))
        assertEquals(listOf("typed:What suits me?", "typed:And another?"), fake.calls)
    }

    @Test
    fun usesTheVargasTheChartWasCastWithAndAsksForTheNotesOnce() {
        val fake = Fake(areas)
        val holder = holder(fake)
        holder.openVargas(listOf(varga(1), varga(9)))
        holder.openVargas(listOf(varga(1), varga(9)))
        assertEquals(listOf("notes:[1, 9]"), fake.calls)
        assertEquals(mapOf(9 to "A note."), (holder.vargaNotes as Fetch.Ready).value)
    }

    @Test
    fun castsTheSampleAgainForItsVargas() {
        val fake = Fake(areas)
        val holder = holder(fake)
        holder.openVargas(emptyList())
        assertEquals(listOf("vargas", "notes:[9]"), fake.calls)
    }

    @Test
    fun saysTheDaysLimitPlainlyForEveryOtherRoute() {
        assertEquals(R.string.more_ask_limited, MoreHolder.askFailure(limit))
        val tooFast = LagnaApi.apiException(429, "")
        assertEquals(R.string.error_too_fast, MoreHolder.askFailure(tooFast))
    }
}

private fun varga(division: Int) = Varga(
    division = division,
    label = "D$division",
    description = "",
    extended = false,
    positions = listOf(VargaPosition("Ascendant", Sign.Virgo, Sign.Virgo)),
)
