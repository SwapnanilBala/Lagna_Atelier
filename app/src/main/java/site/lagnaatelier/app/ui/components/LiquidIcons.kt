package site.lagnaatelier.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * The mock's line icons, from the same 24x24 SVG paths, so the app matches the
 * design without pulling in the whole material-icons-extended library. The
 * stroke is black and `Icon(tint = ...)` recolours it.
 */
object LiquidIcons {
    val ArrowForward: ImageVector by lazy { stroke("arrow_forward", "M5 12h14M13 6l6 6-6 6") }
    val ArrowBack: ImageVector by lazy { stroke("arrow_back", "M19 12H5M11 6l-6 6 6 6") }
    val ArrowDown: ImageVector by lazy { stroke("arrow_down", "M12 5v12M7 12l5 5 5-5") }
    val Check: ImageVector by lazy { stroke("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.6f) }
    val Chevron: ImageVector by lazy { stroke("chevron", "M9 6l6 6-6 6") }

    val Reading: ImageVector by lazy {
        stroke(
            "reading",
            circle(12f, 12f, 4f),
            "M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8",
        )
    }
    val Timing: ImageVector by lazy { stroke("timing", circle(12f, 12f, 8.5f), "M12 7v5l3.5 2") }
    val Palm: ImageVector by lazy {
        stroke(
            "palm",
            "M8 13V5.5a1.5 1.5 0 0 1 3 0V11m0-6.5a1.5 1.5 0 0 1 3 0V11m0-5a1.5 1.5 0 0 1 3 0v7.5" +
                "a7 7 0 0 1-7 7h-.6a6 6 0 0 1-4.9-2.6L3.6 14.6a1.6 1.6 0 0 1 2.6-1.8L8 15",
        )
    }
    val Account: ImageVector by lazy { stroke("account", circle(12f, 8.5f, 3.5f), "M5 20a7 7 0 0 1 14 0") }

    /* SVG has <circle>; path data does not, so a circle is two half-arcs. */
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun stroke(name: String, vararg paths: String, width: Float = 1.7f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
