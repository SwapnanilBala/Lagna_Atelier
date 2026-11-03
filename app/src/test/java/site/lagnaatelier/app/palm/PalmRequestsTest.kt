package site.lagnaatelier.app.palm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.ApiProblem
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.palm.PalmApi
import site.lagnaatelier.app.data.palm.PalmCopyGuard
import site.lagnaatelier.app.data.palm.PalmHistoryCodec
import site.lagnaatelier.app.data.palm.PalmQa
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.data.palm.PalmReadingParser
import site.lagnaatelier.app.data.palm.PalmRequests
import site.lagnaatelier.app.data.palm.PalmWeek
import site.lagnaatelier.app.data.palm.QaRole
import site.lagnaatelier.app.data.palm.QaTurn
import site.lagnaatelier.app.data.palm.SavedPalmReading
import java.time.Instant

/* The request bodies, shaped as the web client sends them (palm-reading-panel.tsx, palm-qa-panel.tsx). */
class PalmRequestsTest {
    /* JSONObject.keySet is not in Android's org.json, which the test classpath compiles against. */
    private fun keysOf(json: JSONObject): Set<String> = json.keys().asSequence().toSet()

    private val reading = PalmReadingParser.parse(
        javaClass.classLoader!!.getResource("palm/reading-full.json")!!.readText(),
    )!!

    @Test
    fun theReadingBodyCarriesOnlyWhatIsChosen() {
        val plain = JSONObject(PalmRequests.readingBody("aGVsbG8=", "image/jpeg", false, PalmReader.Unspecified, null))
        assertEquals(setOf("image", "mediaType"), keysOf(plain))
        assertEquals("aGVsbG8=", plain.getString("image"))
        assertEquals("image/jpeg", plain.getString("mediaType"))

        val chart = PalmRequests.jyotishContext(SampleChart.chart)
        val full = JSONObject(PalmRequests.readingBody("aGVsbG8=", "image/jpeg", true, PalmReader.Woman, chart))
        assertEquals(setOf("image", "mediaType", "classicalMode", "reader", "jyotishContext"), keysOf(full))
        assertEquals(true, full.getBoolean("classicalMode"))
        assertEquals("woman", full.getString("reader"))
        assertEquals("man", JSONObject(PalmRequests.readingBody("x", "image/jpeg", false, PalmReader.Man, null)).getString("reader"))
    }

    @Test
    fun theChartContextFollowsTheWebsShape() {
        val context = PalmRequests.jyotishContext(SampleChart.chart, Instant.parse("2026-11-03T00:00:00Z"))
        val ascendant = context.getJSONObject("ascendant")
        assertEquals("Virgo", ascendant.getString("sign"))
        assertEquals(28.7507, ascendant.getDouble("degree"), 1e-9)
        /* Virgo 28°45' is sidereal 178.75°, in Chitra (173°20' to 186°40'). */
        assertEquals("Chitra", ascendant.getString("nakshatra"))
        assertEquals("Aquarius", context.getString("moonSign"))
        assertEquals("Shatabhisha", context.getString("moonNakshatra"))
        assertEquals("Gemini", context.getString("sunSign"))
        assertEquals("Mercury", context.getJSONObject("currentMahadasha").getString("lord"))
        /* To 29 June 2042: a little under 15.66 years. */
        assertEquals(15.65, context.getJSONObject("currentMahadasha").getDouble("remaining_years"), 0.02)
        assertEquals("Mercury", context.getJSONObject("currentAntardasha").getString("lord"))
        assertEquals(12.7, context.getJSONObject("currentAntardasha").getDouble("remaining_months"), 0.11)
        assertFalse(context.has("keyPlacements"))
    }

    @Test
    fun namesNakshatrasAtTheirEdges() {
        assertEquals("Ashwini", PalmRequests.nakshatraAt(0.0))
        assertEquals("Bharani", PalmRequests.nakshatraAt(13.34))
        assertEquals("Revati", PalmRequests.nakshatraAt(359.99))
        assertEquals("Ashwini", PalmRequests.nakshatraAt(360.0))
        assertEquals("Revati", PalmRequests.nakshatraAt(-0.01))
    }

    @Test
    fun theQuestionBodyReplaysThePriorThreadUnchanged() {
        val prior = listOf(
            QaTurn(QaRole.User, "Where is my work heading?"),
            QaTurn(QaRole.Assistant, "Your head line is long, which is why...", offTopic = false),
        )
        val body = JSONObject(
            PalmQa.requestBody(PalmReadingParser.toJson(reading), null, false, prior, "  And the next year?  "),
        )
        assertEquals(setOf("reading", "classicalMode", "history", "question"), keysOf(body))
        assertEquals(false, body.getBoolean("classicalMode"))
        assertEquals("And the next year?", body.getString("question"))
        val history = body.getJSONArray("history")
        assertEquals(2, history.length())
        assertEquals("user", history.getJSONObject(0).getString("role"))
        assertEquals("assistant", history.getJSONObject(1).getString("role"))
        assertEquals("Your head line is long, which is why...", history.getJSONObject(1).getString("content"))
        /* Only role and content go up: nothing else a turn carries on the phone. */
        assertEquals(setOf("role", "content"), keysOf(history.getJSONObject(1)))
        assertEquals(reading.overallSummary, body.getJSONObject("reading").getString("overall_summary"))
        assertFalse(body.getJSONObject("reading").has("health_and_vitality"))

        val withChart = JSONObject(
            PalmQa.requestBody(PalmReadingParser.toJson(reading), JSONObject().put("moonSign", "Aquarius"), true, emptyList(), "Q"),
        )
        assertEquals("Aquarius", withChart.getJSONObject("jyotishContext").getString("moonSign"))
        assertEquals(true, withChart.getBoolean("classicalMode"))
        assertEquals(0, withChart.getJSONArray("history").length())
    }

    @Test
    fun theThreadStopsAtSixQuestions() {
        val turns = mutableListOf<QaTurn>()
        repeat(PalmQa.MAX_QUESTIONS) { index ->
            assertFalse(PalmQa.atLimit(turns))
            assertEquals(PalmQa.MAX_QUESTIONS - index, PalmQa.questionsLeft(turns))
            turns += QaTurn(QaRole.User, "Question $index")
            turns += QaTurn(QaRole.Assistant, "Answer $index")
        }
        assertTrue(PalmQa.atLimit(turns))
        assertEquals(0, PalmQa.questionsLeft(turns))
        assertEquals(PalmQa.MAX_TURNS, turns.size)
        /* The sixth question went up with the ten turns before it. */
        val sixth = JSONObject(PalmQa.requestBody(JSONObject(), null, false, turns.dropLast(2), "Question 5"))
        assertEquals(10, sixth.getJSONArray("history").length())
    }

    @Test
    fun capsTheQuestionAtTheWebsLength() {
        val body = JSONObject(PalmQa.requestBody(JSONObject(), null, false, emptyList(), "q".repeat(900)))
        assertEquals(PalmQa.MAX_QUESTION_CHARS, body.getString("question").length)
    }

    @Test
    fun readsTheAnswer() {
        val answer = PalmQa.parseAnswer("""{"answer":"Your fate line firms up mid-palm.","offTopic":false}""")!!
        assertEquals("Your fate line firms up mid-palm.", answer.answer)
        assertFalse(answer.offTopic)
        assertTrue(PalmQa.parseAnswer("""{"answer":"I can only speak to this palm reading.","offTopic":true}""")!!.offTopic)
        assertNull(PalmQa.parseAnswer("""{"answer":""}"""))
        assertNull(PalmQa.parseAnswer("""{"error":{"message":"x"}}"""))
    }

    @Test
    fun sortsTheRoutesRefusals() {
        fun refusal(status: Int, body: String) = LagnaApi.apiException(status, body)
        assertTrue(PalmApi.isWeeklyCap(refusal(429, """{"error":{"code":"RATE_LIMITED","message":"x","details":{"scope":"weekly"}}}""")))
        /* Told by the scope, not the words: the English can change, the scope is the contract. */
        assertFalse(PalmApi.isWeeklyCap(refusal(429, """{"error":{"message":"this week","details":{"scope":"anonymous"}}}""")))
        assertFalse(PalmApi.isWeeklyCap(refusal(429, "")))
        assertFalse(PalmApi.isWeeklyCap(refusal(400, """{"error":{"message":"this week"}}""")))
        val daily = refusal(429, """{"error":{"code":"RATE_LIMITED","message":"Sign in to read another palm today.","details":{"scope":"anonymous"}}}""")
        assertEquals("RATE_LIMITED", daily.code)
        assertEquals("anonymous", daily.scope)
        assertEquals(ApiProblem.DailyLimit, daily.problem)
        assertEquals(ApiProblem.FullToday, refusal(429, """{"error":{"details":{"scope":"global"}}}""").problem)
        assertEquals(ApiProblem.TooFast, refusal(429, """{"detail":"Too many"}""").problem)
        assertEquals(ApiProblem.Unavailable, refusal(503, "").problem)
        assertEquals(listOf(1L, 3L), PalmWeek.withoutSend(listOf(1L, 2L, 3L), 2L))
        assertEquals(listOf(1L, 2L), PalmWeek.withoutSend(listOf(1L, 2L), 9L))
        assertEquals(listOf(5L), PalmWeek.withoutSend(listOf(5L, 5L), 5L))
        /* Refused before the model: not one of the week's two. */
        listOf(400, 413, 429, 503).forEach { assertFalse(PalmApi.counted(it)) }
        listOf(200, 500, 502, 504, null).forEach { assertTrue(PalmApi.counted(it)) }
    }

    @Test
    fun keepsDeathDiseaseAndDisabilityOffTheScreen() {
        val text = "Your life line is long. It does not speak to your lifespan. Your heart line shows a deadline-driven mind. " +
            "Watch for illness in later years. A stroke of luck is likely!"
        assertEquals("Your life line is long. Your heart line shows a deadline-driven mind.", PalmCopyGuard.clean(text))
        assertEquals("", PalmCopyGuard.clean("Nothing here suggests disability."))
        assertEquals("A careful hand.", PalmCopyGuard.clean("A careful hand."))
    }

    @Test
    fun savedReadingsRoundTripAndStopAtTwentyFive() {
        val record = SavedPalmReading("2b1c-4f", 1_793_600_000_000, PalmReader.Woman, true, JSONObject().put("moonSign", "Aquarius"), reading)
        val back = PalmHistoryCodec.fromJson(PalmHistoryCodec.toJson(record).toString())!!
        assertEquals(record.id, back.id)
        assertEquals(record.createdAtMillis, back.createdAtMillis)
        assertEquals(PalmReader.Woman, back.reader)
        assertTrue(back.classicalMode)
        assertEquals("Aquarius", back.jyotishContext!!.getString("moonSign"))
        assertEquals(reading, back.reading)
        assertNull(PalmHistoryCodec.fromJson("""{"id":"../../x","created_at":1,"reading":{"overall_summary":"x"}}"""))

        val many = (1..30).map { record.copy(id = "r$it", createdAtMillis = it.toLong()) }
        val (kept, dropped) = PalmHistoryCodec.trim(many.shuffled())
        assertEquals(25, kept.size)
        assertEquals("r30", kept.first().id)
        assertEquals((1..5).map { "r$it" }.toSet(), dropped.map { it.id }.toSet())
    }
}
