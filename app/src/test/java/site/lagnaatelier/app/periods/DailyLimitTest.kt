package site.lagnaatelier.app.periods

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import site.lagnaatelier.app.R
import site.lagnaatelier.app.account.FakeSite
import site.lagnaatelier.app.data.ApiProblem
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.periods.PeriodsApi

/*
 * A reading route's daily allowance, as the site refuses it: a 429 whose
 * details name the scope (app/api/chart/current-period/route.ts, the body as
 * lib/api-errors.ts writes it). Served by a stand-in site so LagnaApi's real
 * connection reads it; nothing here reaches lagnaatelier.site.
 */
class DailyLimitTest {
    private val site = FakeSite()
    private val periods = PeriodsApi(LagnaApi(site.baseUrl), site.baseUrl)

    @After
    fun close() = site.close()

    private fun refusal(scope: String, message: String) =
        """{"error":{"code":"RATE_LIMITED","message":"$message","details":{"retryAfterSeconds":3600,"scope":"$scope"}}}"""

    private fun refusedWith(status: Int, body: String): LagnaApi.ApiException {
        site.on("POST ${PeriodsApi.CURRENT_PERIOD_PATH}", status, body)
        try {
            runBlocking { periods.currentPeriod("""{"stack":[]}""") }
        } catch (e: LagnaApi.ApiException) {
            return e
        }
        fail("the route's refusal was not raised")
        throw AssertionError()
    }

    @Test
    fun aSignedOutReadersSpentAllowanceIsSaidAsTodaysReadings() {
        val error = refusedWith(429, refusal("anonymous", "Sign in to keep reading current-period interpretations today."))
        assertEquals(ApiProblem.DailyLimit, error.problem)
        assertEquals("anonymous", error.scope)
        /* The app's own words, never the site's "Sign in to keep reading…". */
        assertEquals(R.string.error_daily, ErrorText.of(error))
    }

    @Test
    fun aSignedInReadersSpentAllowanceIsTheSameMessage() {
        val error = refusedWith(429, refusal("caller", "Current-period readings are rate limited for today."))
        assertEquals(ApiProblem.DailyLimit, error.problem)
        assertEquals(R.string.error_daily, ErrorText.of(error))
    }

    @Test
    fun theSitesOwnCeilingIsToldApartFromTheReaders() {
        val error = refusedWith(429, refusal("global", "Current-period readings are rate limited for today."))
        assertEquals(ApiProblem.FullToday, error.problem)
        assertEquals(R.string.error_full_today, ErrorText.of(error))
    }

    @Test
    fun aBare429IsThePerMinuteLimitNotTheDay() {
        /* The firewall's empty 429, and the proxy's {detail}: wait a minute, not a day. */
        assertEquals(R.string.error_too_fast, ErrorText.of(refusedWith(429, "")))
        assertEquals(R.string.error_too_fast, ErrorText.of(refusedWith(429, """{"detail":"Rate limit exceeded. Try again in 12 seconds."}""")))
    }

    @Test
    fun aSpentAllowanceOffersNoTryAgainButAPassingFailureDoes() {
        val daily = ErrorText.of(refusedWith(429, refusal("anonymous", "x")))
        val full = ErrorText.of(refusedWith(429, refusal("global", "x")))
        val tooFast = ErrorText.of(refusedWith(429, ""))
        val down = ErrorText.of(refusedWith(503, """{"error":{"message":"down"}}"""))
        org.junit.Assert.assertFalse(ErrorText.offersRetry(daily))
        org.junit.Assert.assertFalse(ErrorText.offersRetry(full))
        org.junit.Assert.assertTrue(ErrorText.offersRetry(tooFast))
        org.junit.Assert.assertTrue(ErrorText.offersRetry(down))
    }

    @Test
    fun theRouteIsAskedOnceAndTheRefusalIsNotRetriedBehindTheReadersBack() {
        refusedWith(429, refusal("anonymous", "x"))
        assertEquals(1, site.callsTo("POST ${PeriodsApi.CURRENT_PERIOD_PATH}").size)
    }
}
