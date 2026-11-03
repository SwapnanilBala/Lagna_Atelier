package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import kotlin.math.roundToInt

/* The mock's --grey: even days in the week chart, and the outline of quiet ones. */
internal val BarGrey = Color(0xFFC9CDD6)

/*
 * A hairline track with a black fill and a ringed dot at today. Copied from
 * the reading screen's private ProgressLine so this feature does not edit it.
 */
@Composable
internal fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val percent = (fraction * 100).roundToInt()
    val description = stringResource(R.string.timing_percent_through, percent)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        val x = maxWidth * fraction
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).background(Hairline, RoundedCornerShape(2.dp)))
        Box(Modifier.align(Alignment.CenterStart).size(width = x, height = 2.dp).background(Ink, RoundedCornerShape(2.dp)))
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = x - 6.dp)
                .size(12.dp)
                .background(Ink, CircleShape)
                .border(3.dp, CardWhite, CircleShape)
                .border(1.dp, Ink, CircleShape),
        )
    }
}

/** A panel's title with something on its right: a switch, a count, a link. */
@Composable
internal fun PanelHead(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
        trailing()
    }
}

/** A small grey text link: "See all 6", "See fewer". */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** The grey mono line under a value: dates, degrees, orbs. */
@Composable
internal fun Meta(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/**
 * What a panel shows when it has nothing yet: a quiet label while loading,
 * and on failure the site's message (or the offline line) with "Try again".
 */
@Composable
internal fun <T> LoadState(
    state: Load<T>,
    loadingLabel: String,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is Load.Ready -> content(state.value)
        is Load.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    state.offline -> stringResource(R.string.timing_offline)
                    state.message != null -> state.message
                    else -> stringResource(R.string.timing_unreadable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            GhostChip(stringResource(R.string.timing_retry), onClick = onRetry)
        }
        Load.Idle, Load.Loading -> CardLabel(loadingLabel, Modifier.padding(vertical = 6.dp))
    }
}

/** A flat tile on the canvas grey: label, value, mono line. */
@Composable
internal fun RowScope.FlatTile(label: String, value: String, meta: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .background(Canvas, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CardLabel(label)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Meta(meta)
    }
}

/** A heading and the sentence under it. */
@Composable
internal fun Lead(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
        }
        if (body.isNotBlank()) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}
