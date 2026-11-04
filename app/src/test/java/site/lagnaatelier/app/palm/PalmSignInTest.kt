package site.lagnaatelier.app.palm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.account.AccountSession.Status
import site.lagnaatelier.app.data.palm.PalmApi
import site.lagnaatelier.app.data.palm.PhotoRefusal
import site.lagnaatelier.app.data.palm.ReadingRefusal
import site.lagnaatelier.app.ui.palm.palmNeedsSignIn
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/*
 * Palm reading needs an account (the site answers 401 to anyone signed out),
 * and the route's photo check turns some photos away with a 422 that names
 * why. Both are refusals before the model ran, so neither is one of the
 * week's two.
 */
class PalmSignInTest {
    private fun refusal(status: Int, body: String) = LagnaApi.apiException(status, body)

    private val signIn = refusal(
        401,
        """{"error":{"code":"UNAUTHORIZED","message":"Sign in to read your palm.","details":{"scope":"sign_in"}}}""",
    )

    private fun photo(reason: String) = refusal(
        422,
        """{"error":{"code":"VALIDATION_FAILED","message":"x","details":{"reason":"$reason"}}}""",
    )

    @Test
    fun aSignedOutReadingAsksForTheAccountAndCostsNothing() {
        assertEquals("sign_in", signIn.scope)
        assertTrue(PalmApi.needsSignIn(signIn))
        assertEquals(ReadingRefusal.SignIn, PalmApi.readingRefusal(signIn))
        assertFalse(PalmApi.counted(401))
        /* Told by the status, not the words or the scope. */
        assertEquals(ReadingRefusal.SignIn, PalmApi.readingRefusal(refusal(401, "")))
        assertFalse(PalmApi.needsSignIn(refusal(403, "")))
        assertFalse(PalmApi.needsSignIn(refusal(429, """{"error":{"details":{"scope":"weekly"}}}""")))
    }

    @Test
    fun sortsThePhotoChecksThreeReasons() {
        assertEquals("not_a_palm", photo("not_a_palm").reason)
        assertEquals(ReadingRefusal.Photo(PhotoRefusal.NotAPalm), PalmApi.readingRefusal(photo("not_a_palm")))
        assertEquals(ReadingRefusal.Photo(PhotoRefusal.BackOfHand), PalmApi.readingRefusal(photo("back_of_hand")))
        assertEquals(ReadingRefusal.Photo(PhotoRefusal.Unclear), PalmApi.readingRefusal(photo("unclear")))
        assertFalse(PalmApi.counted(422))
    }

    @Test
    fun leavesOtherRefusalsToTheSharedWording() {
        /* A reason this build does not know, or the shape on another status or code, is not a photo refusal. */
        assertNull(PalmApi.photoRefusal(photo("too_small")))
        assertNull(PalmApi.photoRefusal(refusal(422, """{"error":{"code":"VALIDATION_FAILED"}}""")))
        assertNull(PalmApi.photoRefusal(refusal(422, """{"error":{"code":"OTHER","details":{"reason":"unclear"}}}""")))
        assertNull(PalmApi.photoRefusal(refusal(400, """{"error":{"code":"VALIDATION_FAILED","details":{"reason":"unclear"}}}""")))
        assertEquals(ReadingRefusal.Other, PalmApi.readingRefusal(photo("too_small")))
        assertEquals(ReadingRefusal.Other, PalmApi.readingRefusal(refusal(502, "")))
        assertEquals(
            ReadingRefusal.Weekly,
            PalmApi.readingRefusal(refusal(429, """{"error":{"code":"RATE_LIMITED","details":{"scope":"weekly"}}}""")),
        )
    }

    @Test
    fun showsTheSignInCardOnlyWithoutAnAccount() {
        assertTrue(palmNeedsSignIn(Status.SignedOut, sessionEnded = false))
        assertTrue(palmNeedsSignIn(Status.SigningIn, sessionEnded = false))
        /* No client id in the build: the card says so instead of offering a button. */
        assertTrue(palmNeedsSignIn(Status.NotSetUp, sessionEnded = false))
        assertFalse(palmNeedsSignIn(Status.SignedIn, sessionEnded = false))
        assertFalse(palmNeedsSignIn(Status.SigningOut, sessionEnded = false))
        /* Still reading the stored session: the camera, unless the site has just said 401. */
        assertFalse(palmNeedsSignIn(Status.Checking, sessionEnded = false))
        assertTrue(palmNeedsSignIn(Status.Checking, sessionEnded = true))
        /* A 401 on a call that carried the token signs the account out, which brings the card. */
        assertTrue(palmNeedsSignIn(Status.SignedOut, sessionEnded = true))
    }

    @Test
    fun wordsEachRefusalPlainly() {
        val english = File("src/main/res/values/strings_palm_sign_in.xml").let { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
                /* As Android reads it: \' is an apostrophe. */
                .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'") }
        }
        assertTrue(english.getValue("palm_refused_not_a_palm").startsWith("That doesn't look like a palm"))
        assertTrue(english.getValue("palm_refused_back_of_hand").startsWith("That looks like the back of a hand — turn it over"))
        assertTrue(english.getValue("palm_refused_unclear").startsWith("The photo is too dark or blurred to read"))
        assertTrue(english.getValue("palm_refused_not_counted").contains("didn't use one of this week's readings"))
        assertEquals(
            listOf(R.string.palm_refused_not_a_palm, R.string.palm_refused_back_of_hand, R.string.palm_refused_unclear),
            PhotoRefusal.entries.map { it.message },
        )
    }
}
