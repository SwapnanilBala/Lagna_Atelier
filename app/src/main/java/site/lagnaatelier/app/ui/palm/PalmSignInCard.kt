package site.lagnaatelier.app.ui.palm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.account.AccountSession
import site.lagnaatelier.app.ui.account.GoogleMark
import site.lagnaatelier.app.ui.account.SignInAction
import site.lagnaatelier.app.ui.account.SoonTag
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Palm reading needs an account, on the website and here: the routes answer
 * 401 to anyone signed out (app/api/palm-reading/route.ts). Saved readings
 * and the compare view are on the phone and stay open regardless; only a new
 * reading and questions about one ask for the account.
 */

/**
 * Whether the Palm tab shows the sign-in card instead of the camera.
 *
 * While the stored session is still being read ([AccountSession.Status.Checking])
 * the camera shows, so a signed-in reader does not see the card flash past;
 * only a 401 met in that moment ([sessionEnded]) brings the card up early.
 * Once the account says it is signed in, that is believed: a 401 on a call
 * that carried the token signs the account out by itself.
 */
internal fun palmNeedsSignIn(status: AccountSession.Status, sessionEnded: Boolean): Boolean = when (status) {
    AccountSession.Status.SignedIn, AccountSession.Status.SigningOut -> false
    AccountSession.Status.Checking -> sessionEnded
    AccountSession.Status.NotSetUp, AccountSession.Status.SignedOut, AccountSession.Status.SigningIn -> true
}

/** The card in the camera's place: why an account, and the account tab's own sign-in button. */
@Composable
internal fun PalmSignInCard(account: AccountSession, sessionEnded: Boolean) {
    val notSetUp = account.status == AccountSession.Status.NotSetUp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleMark()
        if (notSetUp) SoonTag(stringResource(R.string.accounts_not_set_up_tag))
    }
    Text(
        stringResource(R.string.palm_sign_in_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
    )
    Text(
        stringResource(if (notSetUp) R.string.palm_sign_in_not_set_up else R.string.palm_sign_in_copy),
        style = MaterialTheme.typography.bodyMedium,
        color = Muted,
    )
    if (sessionEnded && !notSetUp && account.message == null) Note(stringResource(R.string.palm_sign_in_ended), error = true)
    SignInAction(account)
}
