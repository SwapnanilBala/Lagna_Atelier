package site.lagnaatelier.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.ui.theme.CardShadow
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.GoldenGlow
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The handful of pieces every Liquid screen is built from.
 */

/**
 * The one coloured thing on a screen: three soft patches of golden-hour light.
 *
 * Blurred on Android 12 and later. On 9 to 11 `blur` does nothing, and the
 * gradients' transparent edges carry the softness on their own.
 */
@Composable
fun Glow(modifier: Modifier = Modifier, colors: List<Color> = GoldenGlow) {
    Box(
        modifier
            .blur(44.dp, BlurredEdgeTreatment.Unbounded)
            .drawBehind {
                val spots = listOf(
                    Triple(0.35f, 0.40f, 0.46f),
                    Triple(0.65f, 0.55f, 0.42f),
                    Triple(0.50f, 0.76f, 0.36f),
                )
                spots.forEachIndexed { index, (x, y, r) ->
                    val center = Offset(size.width * x, size.height * y)
                    val radius = size.minDimension * r
                    val color = colors[index % colors.size]
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, color.copy(alpha = 0f)),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            },
    )
}

/** The main action: a black pill with an arrow in a white circle. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = LiquidIcons.ArrowForward,
) {
    Row(
        modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = CardWhite, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(34.dp).background(CardWhite, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
        }
    }
}

/** A quiet secondary action or label: white, hairline border, fully round. */
@Composable
fun GhostChip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = modifier
        .clip(CircleShape)
        .background(CardWhite)
        .border(1.dp, Hairline, CircleShape)
    Box(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = Ink))
    }
}

private val FloatShape = RoundedCornerShape(20.dp)

/** A white card lifted off the canvas by a soft tinted shadow. */
@Composable
fun FloatCard(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(18.dp, FloatShape, ambientColor = CardShadow, spotColor = CardShadow)
            .background(CardWhite, FloatShape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** A white card that sits flat, edged with a hairline. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(CardWhite, FloatShape)
            .border(1.dp, Hairline, FloatShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The small grey label at the top of a card. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Muted)
}

/** A black disc with a white tick. */
@Composable
fun TickBadge(size: Dp = 22.dp) {
    Box(Modifier.size(size).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
        Icon(LiquidIcons.Check, contentDescription = null, tint = CardWhite, modifier = Modifier.size(size * 0.56f))
    }
}

/** Two or three options in a soft track; the selected one is a black pill. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) Ink else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) CardWhite else Muted,
                )
            }
        }
    }
}
