package site.lagnaatelier.app.data

import androidx.annotation.StringRes
import site.lagnaatelier.app.R
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/*
 * What kind of failure a call met, told from the status and the allowance
 * scope the site names (lib/llm-budget.ts: global, caller, anonymous,
 * weekly), never from the site's English. Each one has the app's own
 * wording: the site's text can promise things the app cannot do, such as
 * "Sign in to read another palm today" to a phone that has no sign-in.
 */
enum class ApiProblem(@param:StringRes @get:StringRes val message: Int) {
    /** The per-minute limit or the firewall: a later try gets past it. */
    TooFast(R.string.error_too_fast),

    /** This caller's allowance for today (signed out or signed in alike). */
    DailyLimit(R.string.error_daily),

    /** The site's allowance for everyone today. */
    FullToday(R.string.error_full_today),

    /** This week's palm readings. The palm tab words this one itself, with the date. */
    WeeklyLimit(R.string.error_weekly),

    /** The site would not take what was sent. */
    NotAccepted(R.string.error_not_accepted),

    /** The site is down or could not finish. */
    Unavailable(R.string.error_unavailable),

    Other(R.string.error_generic);

    companion object {
        fun of(status: Int?, scope: String?): ApiProblem = when {
            scope == "weekly" -> WeeklyLimit
            scope == "anonymous" || scope == "caller" -> DailyLimit
            scope == "global" -> FullToday
            status == 429 -> TooFast
            status != null && status in 400..499 -> NotAccepted
            status != null && status in 500..599 -> Unavailable
            else -> Other
        }
    }
}

/*
 * The one mapping from a failure to what the reader is told, for every
 * feature. A parser's or a PDF writer's own message is a developer's
 * sentence and never reaches the screen.
 */
object ErrorText {
    @StringRes
    fun of(error: Throwable): Int = when (error) {
        is LagnaApi.ApiException -> error.problem.message
        is LagnaApi.ResponseTooLarge -> R.string.error_generic
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> R.string.error_offline
        is SocketTimeoutException -> R.string.error_unavailable
        is IOException -> R.string.error_offline
        else -> R.string.error_generic
    }

    /**
     * Whether a failure worded [message] is worth a "Try again". A spent
     * allowance only clears tomorrow, so it is said as such with no button
     * that would just be refused again (as the timing tab already does).
     */
    fun offersRetry(@StringRes message: Int): Boolean =
        message != R.string.error_daily && message != R.string.error_full_today
}
