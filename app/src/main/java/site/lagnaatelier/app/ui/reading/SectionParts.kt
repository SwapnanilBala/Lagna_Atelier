package site.lagnaatelier.app.ui.reading

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Small pieces the reading's longer sections share. Kept here rather than
 * reusing ReadingScreen's private PanelHead, so the sections can be added
 * without reshaping that file.
 */

/** A section's title, an optional grey line under it, and something on its right. */
@Composable
internal fun SectionHead(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 1.dp))
            }
        }
        trailing()
    }
}

/** A small grey text link: "See all 20", "Show". */
@Composable
internal fun SectionLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/** "See all N" under a list cut short, "See fewer" once it is open; nothing when there is no more. */
@Composable
internal fun SeeAllLink(total: Int, shown: Int, expanded: Boolean, onToggle: () -> Unit) {
    if (total <= shown && !expanded) return
    SectionLink(
        if (expanded) stringResource(R.string.sections_see_fewer) else stringResource(R.string.sections_see_all, total),
        onClick = onToggle,
    )
}

/** The grey small print under a table or a list: legends, caveats. */
@Composable
internal fun Legend(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = modifier)
}

/** The grey label over a block of a reading: "Why this reading", "What it means". */
@Composable
internal fun BlockLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Muted)
}

/**
 * A row that opens and closes what is under it, with a chevron that turns.
 * The row's whole width is the target, and it reads as expanded or collapsed.
 */
@Composable
internal fun DisclosureRow(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val state = stringResource(if (expanded) R.string.sections_hide else R.string.sections_show)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        Icon(
            LiquidIcons.Chevron,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(16.dp).rotate(turn),
        )
    }
}

/** A small hairline tag: a house group, a family name. */
@Composable
internal fun Tag(text: String, strong: Boolean = false) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (strong) Ink else CardWhite)
            .border(1.dp, if (strong) Ink else Hairline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (strong) CardWhite else Muted)
    }
}

/** A row's mark: a black disc with the rank for a top finding, a hollow dot for the rest. */
@Composable
internal fun RankMark(rank: Int) {
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (rank > 0) {
            Box(Modifier.size(22.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                Text("$rank", style = MaterialTheme.typography.labelSmall, color = CardWhite)
            }
        } else {
            Box(Modifier.size(8.dp).border(1.5.dp, Muted, CircleShape))
        }
    }
}
