package site.lagnaatelier.app.more

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.lifeareas.AskAnswer
import site.lagnaatelier.app.data.lifeareas.AskClassicsParser
import site.lagnaatelier.app.data.lifeareas.AskQuestion
import site.lagnaatelier.app.data.lifeareas.AskRefusal

/*
 * ask-strengths.json and ask-typed.json are real answers for the sample chart
 * (2026-11-03): the "strengths" question, and "What kind of work suits me?".
 */
class AskClassicsParserTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("lifeareas/$name")!!.readText()

    @Test
    fun readsWhichQuestionsTheBooksCanAnswerInTheAppsOrder() {
        val all = """{"available":["standing","career_year","love_year","money_year","health","marriage","strengths","children"]}"""
        assertEquals(AskQuestion.entries, AskClassicsParser.parseAvailable(all))
        assertEquals(
            listOf(AskQuestion.Health, AskQuestion.Children),
            AskClassicsParser.parseAvailable("""{"available":["children","palmistry","health"]}"""),
        )
        assertEquals(emptyList<AskQuestion>(), AskClassicsParser.parseAvailable("{}"))
    }

    @Test
    fun readsAReadyMadeAnswer() {
        val answer = AskClassicsParser.parseAnswer(fixture("ask-strengths.json"))
        assertTrue(answer is AskAnswer.Ready)
        val reading = (answer as AskAnswer.Ready).reading
        assertEquals(4, reading.sources.size)
        assertTrue(reading.paragraphs().first().first().text.startsWith("The Brihat Jataka holds"))
    }

    @Test
    fun readsATypedAnswerAndARefusal() {
        assertTrue(AskClassicsParser.parseAnswer(fixture("ask-typed.json")) is AskAnswer.Ready)
        assertEquals(
            AskAnswer.Refused(AskRefusal.Instructions),
            AskClassicsParser.parseAnswer("""{"refused":"instructions","reading":null,"cached":false}"""),
        )
        assertEquals(
            AskAnswer.Refused(AskRefusal.ForbiddenTopic),
            AskClassicsParser.parseAnswer("""{"refused":"forbidden_topic","reading":null,"cached":false}"""),
        )
        // A verdict this app has not met is still a refusal.
        assertEquals(
            AskAnswer.Refused(AskRefusal.NotAboutChart),
            AskClassicsParser.parseAnswer("""{"refused":"something_new","reading":null,"cached":false}"""),
        )
    }

    @Test
    fun treatsNoReadingAsTheBooksHavingNothing() {
        assertEquals(AskAnswer.Empty, AskClassicsParser.parseAnswer("""{"question":"children","reading":null,"cached":false}"""))
        assertEquals(AskAnswer.Empty, AskClassicsParser.parseAnswer("""{"refused":null,"reading":null,"cached":false}"""))
    }

    @Test
    fun cleansATypedQuestionAsTheSiteWill() {
        assertEquals("What kind of work suits me?", AskClassicsParser.clean("  What  kind of\nwork <suits> me?  "))
        assertEquals("a b c", AskClassicsParser.clean(" a \t b\n\nc "))
        assertEquals("x y", AskClassicsParser.clean("x<>y"))
        assertEquals(200, AskClassicsParser.clean("a".repeat(500)).length)
        assertFalse(AskClassicsParser.canAsk("  hi  "))
        assertTrue(AskClassicsParser.canAsk("Why?"))
    }

    @Test
    fun sendsOnlyTheCleanedWordsAndTheLanguage() {
        val body = JSONObject(AskClassicsParser.typedBody("  Will   I travel?  "))
        assertEquals("Will I travel?", body.getString("text"))
        assertEquals("en", body.getString("language"))
        assertEquals(2, body.length())
    }
}
