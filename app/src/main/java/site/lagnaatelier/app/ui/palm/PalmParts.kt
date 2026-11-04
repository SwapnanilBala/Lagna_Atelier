package site.lagnaatelier.app.ui.palm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.AppDates
import site.lagnaatelier.app.data.palm.LineKey
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.data.palm.PalmReading
import site.lagnaatelier.app.data.palm.PalmWeek
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Canvas as CanvasColor
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.GeistMono
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.MidTone
import site.lagnaatelier.app.ui.theme.Muted
import site.lagnaatelier.app.ui.theme.PhotoGround
import java.time.Instant
import java.time.ZoneId

/* Pieces the three palm views share. Copied in look from ui/reading, not imported from it. */

/** Room under the content for the floating nav pill. */
internal val NavRoom = 140.dp

/** Room for the nav pill and the palm dock above it. */
internal val DockRoom = 210.dp

internal fun dayLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(AppDates.formatter("EEE d MMM yyyy", "EEEdMMMyyyy"))

@Composable
internal fun handLabel(reader: PalmReader): String = stringResource(
    when (reader) {
        PalmReader.Woman -> R.string.palm_hand_left
        PalmReader.Man -> R.string.palm_hand_right
        PalmReader.Unspecified -> R.string.palm_hand_writing
    },
)

@Composable
internal fun qualityLabel(rating: String): String = stringResource(
    when (rating) {
        "excellent" -> R.string.palm_quality_excellent
        "good" -> R.string.palm_quality_good
        "marginal" -> R.string.palm_quality_marginal
        else -> R.string.palm_quality_poor
    },
)

internal fun lineName(key: LineKey): Int = when (key) {
    LineKey.Heart -> R.string.palm_line_heart
    LineKey.Head -> R.string.palm_line_head
    LineKey.Life -> R.string.palm_line_life
    LineKey.Fate -> R.string.palm_line_fate
}

/** A sub-view's heading: a round back button, a title and a line under it. */
@Composable
internal fun PalmHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    val backLabel = stringResource(R.string.palm_back)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CardWhite)
                .border(1.dp, Hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LiquidIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
internal fun PanelHead(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
        trailing()
    }
}

/** A label chip that is not a control: black when [on], hairline when not. */
@Composable
internal fun InkChip(text: String, on: Boolean = true) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (on) Ink else CardWhite)
            .border(1.dp, if (on) Ink else Hairline, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = if (on) CardWhite else Ink, maxLines = 1)
    }
}

/** The view's actions, floating above the nav pill at the bottom right. */
@Composable
internal fun BoxScope.PalmDock(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            /* The nav pill is 60dp tall, 8dp off the bottom, with 10dp between. */
            .padding(start = 16.dp, end = 16.dp, bottom = 78.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** "2 readings left this week", with the week's dots. */
@Composable
internal fun AllowancePanel(remaining: Int, nextOpening: String, onHistory: (() -> Unit)?, historyCount: Int) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$remaining", style = LiquidType.figure.copy(fontSize = 46.sp, lineHeight = 46.sp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pluralStringResource(R.plurals.palm_allowance_left, remaining), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (remaining > 0) stringResource(R.string.palm_allowance_note)
                    else stringResource(R.string.palm_allowance_opens, nextOpening),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                    color = Muted,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(PalmWeek.READINGS_PER_WEEK) { index ->
                    val left = index >= PalmWeek.READINGS_PER_WEEK - remaining
                    Box(Modifier.size(width = 18.dp, height = 4.dp).background(if (left) Ink else Hairline, RoundedCornerShape(4.dp)))
                }
            }
            if (onHistory != null) {
                GhostChip(
                    if (historyCount > 0) stringResource(R.string.palm_history_link, historyCount)
                    else stringResource(R.string.palm_history_link_empty),
                    onClick = onHistory,
                )
            }
        }
    }
}

/** A hairline track with a black fill, as on the reading screen. */
@Composable
internal fun ProgressLine(fraction: Float) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp)) {
        val x = maxWidth * fraction.coerceIn(0f, 1f)
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).background(Hairline, RoundedCornerShape(2.dp)))
        Box(Modifier.align(Alignment.CenterStart).size(width = x, height = 2.dp).background(Ink, RoundedCornerShape(2.dp)))
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((x - 5.dp).roundToPx(), 0) }
                .size(10.dp)
                .background(Ink, CircleShape)
                .border(2.dp, CardWhite, CircleShape),
        )
    }
}

/** The finder's four corner marks. */
@Composable
internal fun FinderCorners(modifier: Modifier = Modifier, inset: Dp = 12.dp, length: Dp = 22.dp) {
    Canvas(modifier) {
        val i = inset.toPx()
        val l = length.toPx()
        val w = 2.dp.toPx()
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val path = Path().apply {
                moveTo(x, y + dy * l)
                lineTo(x, y + dy * 6.dp.toPx())
                quadraticTo(x, y, x + dx * 6.dp.toPx(), y)
                lineTo(x + dx * l, y)
            }
            drawPath(path, Ink, style = stroke)
        }
        corner(i, i, 1f, 1f)
        corner(size.width - i, i, -1f, 1f)
        corner(i, size.height - i, 1f, -1f)
        corner(size.width - i, size.height - i, -1f, -1f)
    }
}

/* The outline of a hand the finder shows before there is a photo: the mock's
   six rounded bars, in its 160 x 200 frame. */
@Composable
internal fun HandOutline(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width / 160f, size.height / 200f)
        val ox = (size.width - 160f * s) / 2
        val oy = (size.height - 200f * s) / 2
        val bars = listOf(
            floatArrayOf(42f, 88f, 86f, 100f, 30f),
            floatArrayOf(44f, 30f, 18f, 74f, 9f),
            floatArrayOf(65f, 16f, 19f, 86f, 9.5f),
            floatArrayOf(87f, 24f, 18f, 78f, 9f),
            floatArrayOf(108f, 46f, 16f, 60f, 8f),
        )
        val outline = Stroke(width = 5f * s, join = StrokeJoin.Round)
        val edge = MidTone
        fun bar(b: FloatArray, fill: Boolean) {
            val topLeft = Offset(ox + b[0] * s, oy + b[1] * s)
            val sz = Size(b[2] * s, b[3] * s)
            val r = CornerRadius(b[4] * s)
            if (fill) drawRoundRect(CanvasColor, topLeft, sz, r) else drawRoundRect(edge, topLeft, sz, r, style = outline)
        }
        val thumb = floatArrayOf(20f, 96f, 18f, 62f, 9f)
        for (fill in listOf(false, true)) {
            bars.forEach { bar(it, fill) }
            rotate(-36f, pivot = Offset(ox + 38f * s, oy + 150f * s)) { bar(thumb, fill) }
        }
    }
}

/**
 * The photo with the reading's lines drawn over it. The points are
 * fractions of the whole photo, origin top-left, so the overlay is laid on a
 * box of exactly the photo's shape.
 */
@Composable
internal fun TracedPhoto(photo: ImageBitmap?, reading: PalmReading, modifier: Modifier = Modifier, height: Dp = 250.dp) {
    val description = stringResource(R.string.palm_traced_description)
    val labels = mapOf(
        LineKey.Heart to stringResource(R.string.palm_trace_heart),
        LineKey.Head to stringResource(R.string.palm_trace_head),
        LineKey.Life to stringResource(R.string.palm_trace_life),
        LineKey.Fate to stringResource(R.string.palm_trace_fate),
    )
    val measurer = rememberTextMeasurer()
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp)).background(PhotoGround),
        contentAlignment = Alignment.Center,
    ) {
        if (photo == null) return@Box
        val ratio = photo.width.toFloat() / photo.height.coerceAtLeast(1)
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .semantics { contentDescription = description },
        ) {
            Image(photo, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.matchParentSize()) {
                val labelStyle = TextStyle(fontFamily = GeistMono, fontSize = 10.sp, color = Ink, background = CardWhite.copy(alpha = 0.85f))
                reading.lineCoordinates.forEach { (key, points) ->
                    if (points.isEmpty()) return@forEach
                    val mapped = points.map { Offset(it.x * size.width, it.y * size.height) }
                    val path = Path().apply {
                        moveTo(mapped[0].x, mapped[0].y)
                        if (mapped.size == 2) {
                            lineTo(mapped[1].x, mapped[1].y)
                        } else {
                            /* Through the midpoints, so the trace curves like a crease rather than kinks. */
                            for (index in 1 until mapped.size - 1) {
                                val mid = Offset((mapped[index].x + mapped[index + 1].x) / 2, (mapped[index].y + mapped[index + 1].y) / 2)
                                quadraticTo(mapped[index].x, mapped[index].y, mid.x, mid.y)
                            }
                            lineTo(mapped.last().x, mapped.last().y)
                        }
                    }
                    val visibility = reading.lineConfidence[key]?.visibility
                    val dashed = visibility == "partial" || visibility == "faint"
                    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null
                    drawPath(path, CardWhite.copy(alpha = 0.85f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, Ink, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
                    val label = measurer.measure(labels.getValue(key), labelStyle)
                    val anchor = mapped.last()
                    val x = (anchor.x + 4.dp.toPx()).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
                    val y = (anchor.y - label.size.height / 2f).coerceIn(0f, (size.height - label.size.height).coerceAtLeast(0f))
                    drawText(label, topLeft = Offset(x, y))
                }
            }
        }
    }
}

/** A saved photo, small and rounded, or the warm ground when there is none. */
@Composable
internal fun Thumb(photo: ImageBitmap?, width: Dp, height: Dp) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(12.dp)).background(PhotoGround)) {
        if (photo != null) {
            Image(photo, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** An error or note line in the site's words, muted unless it is an error. */
@Composable
internal fun Note(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
        color = if (error) Ink else Muted,
    )
}
