package site.lagnaatelier.app.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.reading.signName
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.GeistMono
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Pieces the Saved charts screen and the Account tab share.
 */

/** The mock's extra line icons, drawn the way LiquidIcons draws its own. */
internal object AccountIcons {
    val Plus: ImageVector by lazy { stroke("plus", "M12 5v14M5 12h14") }
    val Bin: ImageVector by lazy {
        stroke("bin", "M4.5 7h15M10 11v6M14 11v6M6.5 7l.8 11.2a1.5 1.5 0 0 0 1.5 1.3h6.4a1.5 1.5 0 0 0 1.5-1.3L17.5 7M9.5 7V5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v2")
    }
    val External: ImageVector by lazy {
        stroke("external", "M14 5h5v5M19 5l-8 8M18 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h4")
    }
    val ChevronDown: ImageVector by lazy { stroke("chevron_down", "M6 9l6 6 6-6") }
    val ChevronUp: ImageVector by lazy { stroke("chevron_up", "M6 15l6-6 6 6") }

    private fun stroke(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}

/** "New Delhi", or the place's fuller label when the city is blank. */
internal fun BirthDetails.placeShort(): String = place.city.ifBlank { place.label }

/** The birth time as the list prints it: "14:30", or the part of the day for an estimate. */
@Composable
internal fun BirthDetails.timeLabel(): String = when (accuracy) {
    TimeAccuracy.Exact -> exactTime.orEmpty()
    TimeAccuracy.Morning -> stringResource(R.string.saved_time_morning)
    TimeAccuracy.Afternoon -> stringResource(R.string.saved_time_afternoon)
    TimeAccuracy.Evening -> stringResource(R.string.saved_time_evening)
    TimeAccuracy.Unknown -> stringResource(R.string.saved_time_unknown)
}

/** "Lahiri · whole sign", or the site's own name for a tradition the app does not list. */
internal fun SavedChart.traditionText(): String = traditionLabel(details.engineId) ?: tradition

@Composable
internal fun SavedChart.risingDegree(): String =
    ascendantDegree?.let(::formatDegree) ?: stringResource(R.string.saved_missing_value)

/** A round initials disc: ink for the chart in front, pale for the rest. */
@Composable
internal fun InitialsDisc(name: String, dark: Boolean) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (dark) Ink else Canvas)
            .then(if (dark) Modifier else Modifier.border(1.dp, Hairline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = if (dark) CardWhite else Ink,
            maxLines = 1,
        )
    }
}

/** A small round button with a line icon, hairline-edged, as the mock's icon-btn. */
@Composable
internal fun IconCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).background(CardWhite, CircleShape).border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * One saved chart in a list: initials, name, place and date, rising sign.
 * Tap opens it; [onDelete], when given, adds the bin button and long-press.
 */
@Composable
internal fun SavedChartRow(
    chart: SavedChart,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)? = null,
    darkDisc: Boolean = false,
    showDegree: Boolean = true,
) {
    val name = chart.details.name
    val openLabel = stringResource(R.string.saved_open_chart, name)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = openLabel,
                onLongClick = onDelete,
                onClick = onOpen,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InitialsDisc(name, dark = darkDisc)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${chart.details.placeShort()} · ${chart.details.date.dayMonthYear()}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signName(chart.ascendant),
                style = if (showDegree) MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)
                else MaterialTheme.typography.labelMedium.copy(color = Muted),
            )
            if (showDegree) {
                Text(chart.risingDegree(), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        if (onDelete != null) {
            IconCircle(AccountIcons.Bin, stringResource(R.string.saved_delete_button, name), onDelete)
        }
    }
}

/** The inline check before a chart is deleted. */
@Composable
internal fun DeleteConfirm(name: String, onCancel: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Canvas, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.saved_delete_question, name),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
            )
            Text(
                stringResource(R.string.saved_delete_detail),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostChip(stringResource(R.string.saved_cancel), onClick = onCancel)
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Ink)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.saved_delete), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = CardWhite)
            }
        }
    }
}

/** The four-colour ring of the mock's Google mark, on a white disc. */
@Composable
internal fun GoogleMark() {
    Box(
        Modifier.size(34.dp).background(CardWhite, CircleShape).border(1.dp, Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val ring = size.minDimension * 0.25f
            val inset = ring / 2
            val arc = Size(size.width - ring, size.height - ring)
            /* Clockwise from twelve o'clock, as the mock's conic gradient. */
            listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4))
                .forEachIndexed { index, color ->
                    drawArc(
                        color = color,
                        startAngle = -90f + index * 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(width = ring),
                    )
                }
        }
    }
}

/** The "COMING SOON" tag: canvas-grey pill, mono capitals. */
@Composable
internal fun SoonTag(text: String) {
    Box(
        Modifier.clip(CircleShape).background(Canvas).padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = GeistMono, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
            color = Muted,
        )
    }
}
