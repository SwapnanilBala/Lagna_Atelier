package site.lagnaatelier.app.ui.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.TodaySky
import site.lagnaatelier.app.data.weekdayDayMonth
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.reading.signName
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.GeistMono
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LagnaAtelierTheme
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.Muted

@Composable
fun LandingScreen(
    today: TodaySky?,
    todayUnavailable: Boolean,
    onStart: () -> Unit,
    onOpenSaved: () -> Unit,
    onSignIn: () -> Unit,
    /** The signed-in account's name or address; null when signed out. */
    signedInAs: String? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                )
                GhostChip(stringResource(R.string.landing_language))
            }

            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.landing_headline), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.landing_copy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
                PillButton(stringResource(R.string.landing_start), onClick = onStart)
            }

            CardCluster(today, todayUnavailable)

            ProofRow(Modifier.padding(horizontal = 20.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoogleSignIn(signedInAs, onSignIn)
            GhostChip(stringResource(R.string.landing_saved), onClick = onOpenSaved)
        }
    }
}

@Composable
private fun MoonDisc() {
    Box(Modifier.size(28.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
        Text("☽", color = CardWhite, fontSize = 15.sp)
    }
}

/* Three cards over the glow, overlapping the way the reference's do. */
@Composable
private fun CardCluster(today: TodaySky?, todayUnavailable: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 18.dp),
    ) {
        Glow(Modifier.fillMaxSize().padding(top = 6.dp))

        FloatCard(
            Modifier.width(178.dp).offset(y = 22.dp),
            spacing = 6.dp,
        ) {
            if (today != null) {
                CardLabel(stringResource(R.string.landing_today_label, today.date.weekdayDayMonth()))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoonDisc()
                    Text(
                        stringResource(R.string.landing_today_moon, signName(today.moonSign)),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp),
                    )
                }
                Text(
                    stringResource(R.string.landing_today_detail, today.moonNakshatra, signName(today.sunSign)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            } else {
                /* Never a stale sky: until today's has loaded, the card says so. */
                CardLabel(stringResource(R.string.landing_today_plain))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoonDisc()
                    Text(
                        stringResource(if (todayUnavailable) R.string.landing_today_unavailable else R.string.landing_today_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }

        FloatCard(
            Modifier.align(Alignment.TopEnd).width(168.dp).offset(y = 112.dp),
            spacing = 10.dp,
        ) {
            Text(stringResource(R.string.landing_traditions_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.landing_traditions_copy),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                color = Muted,
            )
            CardLabel(stringResource(R.string.landing_traditions_label))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(stringResource(R.string.landing_traditions_count), style = LiquidType.figure.copy(fontSize = 38.sp))
                TraditionCoins()
            }
        }

        FloatCard(
            Modifier.width(170.dp).offset(x = 16.dp, y = 188.dp),
            spacing = 4.dp,
            padding = 12.dp,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardLabel(stringResource(R.string.landing_now_label))
                TickBadge(18.dp)
            }
            Text(
                stringResource(R.string.landing_now_copy),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
            )
        }
    }
}

/*
 * Discs that overlap, like the reference's coin row and avatar stack. Each
 * disc is placed by offset inside a box exactly as wide as the stack, so the
 * overlap is real in the layout and nothing beside it is pushed or clipped.
 */
@Composable
private fun OverlapStack(
    labels: List<String>,
    size: Dp,
    step: Dp,
    modifier: Modifier = Modifier,
    disc: @Composable (String) -> Unit,
) {
    Box(modifier.width(size + step * (labels.size - 1)).height(size)) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.offset(x = step * index).size(size), contentAlignment = Alignment.Center) {
                disc(label)
            }
        }
    }
}

/* The reference's coin row: here, the first five traditions' initials. */
@Composable
private fun TraditionCoins() {
    val description = stringResource(R.string.landing_traditions_initials_description)
    OverlapStack(
        listOf("La", "Ra", "KP", "FB", "Pu"),
        size = 22.dp,
        step = 16.dp,
        modifier = Modifier.semantics { contentDescription = description },
    ) { initials ->
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Ink)
                .border(1.5.dp, CardWhite, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = CardWhite, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontFamily = GeistMono))
        }
    }
}

/* The avatar stack, made factual: planet glyphs and a number the app stands behind. */
@Composable
private fun ProofRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverlapStack(listOf("☉", "☽", "♃", "♄"), size = 28.dp, step = 20.dp) { glyph ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(1.dp, Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, fontSize = 14.sp, color = Ink)
            }
        }
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.landing_proof))
                withStyle(SpanStyle(color = Muted)) { append(stringResource(R.string.landing_proof_tail)) }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        )
    }
}

@Composable
private fun GoogleSignIn(signedInAs: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4), Color(0xFFEA4335)),
                    ),
                    shape = CircleShape,
                ),
        )
        Text(
            signedInAs?.let { stringResource(R.string.accounts_landing_signed_in, it) }
                ?: stringResource(R.string.landing_google),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun LandingPreview() {
    LagnaAtelierTheme {
        LandingScreen(SampleChart.today, todayUnavailable = false, onStart = {}, onOpenSaved = {}, onSignIn = {})
    }
}
