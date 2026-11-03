package site.lagnaatelier.app.ui.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.AppDates
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.report.ReportPercent
import site.lagnaatelier.app.data.report.ReportStage
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.CardShadow
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.GeistMono
import site.lagnaatelier.app.ui.theme.GlowHoney
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.Muted
import java.io.File

private val ReadyDate get() = AppDates.formatter("EEE d MMM yyyy", "EEEdMMMyyyy")

/* The report's own paper, as in the PDF; the app's glow stays outside it. */
private val Paper = Color(0xFFF7F4EE)
private val PaperInk = Color(0xFF171915)

// ── Generating ──────────────────────────────────────────────────────────

@Composable
internal fun GeneratingScreen(state: ReportState.Working, onStop: () -> Unit) {
    val story = state.story
    val chapters = story?.chapters.orEmpty()
    val percent = ReportPercent.of(state.stage, state.writing)
    val planning = state.stage == ReportStage.Writing && (state.writing?.planning ?: true)
    val started = state.writing?.chaptersStarted ?: 0
    val subtitle = if (story == null) {
        stringResource(R.string.report_subtitle_preparing, state.name)
    } else {
        stringResource(R.string.report_subtitle_working, state.name, pluralStringResource(R.plurals.report_chapters, chapters.size, chapters.size))
    }
    val detail = when (state.stage) {
        ReportStage.Calculating -> stringResource(R.string.report_step_calculating)
        ReportStage.Verifying -> stringResource(R.string.report_step_verifying)
        ReportStage.Writing -> when {
            planning -> stringResource(R.string.report_detail_planning)
            started > 0 -> stringResource(R.string.report_detail_chapter, started, state.writing?.chapters ?: chapters.size)
            else -> stringResource(R.string.report_detail_introduction)
        }
        ReportStage.Typesetting -> stringResource(R.string.report_step_typesetting)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ReportTopBar(stringResource(R.string.report_title), subtitle, onBack = onStop)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                Glow(Modifier.offset(x = (-40).dp, y = 10.dp).requiredSize(width = 400.dp, height = 300.dp))
                FloatCard(Modifier.fillMaxWidth(), spacing = 14.dp, padding = 16.dp) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            CardLabel(stringResource(R.string.report_generating_label))
                            Spacer(Modifier.height(4.dp))
                            Text(detail, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 23.sp))
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text(percent.toString(), style = LiquidType.figure.copy(fontSize = 46.sp, lineHeight = 44.sp))
                            Text(
                                stringResource(R.string.report_percent_sign),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                                modifier = Modifier.padding(top = 3.dp, start = 1.dp),
                            )
                        }
                    }
                    val progressLabel = stringResource(R.string.report_progress_description, percent)
                    ProgressLine(
                        fraction = percent / 100f,
                        pulsing = planning,
                        modifier = Modifier.semantics {
                            contentDescription = progressLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val verification = story?.verification
                        StepRow(
                            stringResource(R.string.report_step_calculating),
                            stepState(ReportStage.Calculating, state.stage),
                        )
                        StepRow(
                            stringResource(R.string.report_step_verifying),
                            stepState(ReportStage.Verifying, state.stage),
                            trailing = verification?.let { stringResource(R.string.report_step_checks, it.passedCount, it.totalCount) },
                        )
                        val total = state.writing?.chapters?.takeIf { it > 0 } ?: chapters.size
                        StepRow(
                            stringResource(R.string.report_step_writing),
                            stepState(ReportStage.Writing, state.stage),
                            trailing = when {
                                state.stage == ReportStage.Writing && started > 0 -> stringResource(R.string.report_step_chapters, started, total)
                                state.stage == ReportStage.Typesetting && total > 0 -> stringResource(R.string.report_step_chapters, total, total)
                                else -> null
                            },
                        )
                        StepRow(
                            stringResource(R.string.report_step_typesetting),
                            stepState(ReportStage.Typesetting, state.stage),
                        )
                    }
                    Text(
                        stringResource(R.string.report_note),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                        color = Muted,
                    )
                }
            }

            if (chapters.isNotEmpty()) {
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.report_chapters_title), style = MaterialTheme.typography.titleMedium)
                        CardLabel(stringResource(R.string.report_chapters_label))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chapters.forEachIndexed { index, chapter ->
                            val mark = when {
                                state.stage == ReportStage.Typesetting -> Mark.Done
                                state.stage != ReportStage.Writing || started == 0 -> Mark.Todo
                                index < started - 1 -> Mark.Done
                                index == started - 1 -> Mark.Now
                                else -> Mark.Todo
                            }
                            ChapterRow(index + 1, chapter.title, mark)
                        }
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 4.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GhostChip(stringResource(R.string.report_stop), onClick = onStop)
        }
    }
}

private enum class StepState { Done, Live, Waiting }

private fun stepState(step: ReportStage, current: ReportStage): StepState = when {
    step.ordinal < current.ordinal -> StepState.Done
    step == current -> StepState.Live
    else -> StepState.Waiting
}

@Composable
private fun StepRow(label: String, state: StepState, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Done -> TickBadge()
                StepState.Live -> CircularProgressIndicator(Modifier.size(20.dp), color = Ink, trackColor = Hairline, strokeWidth = 2.dp)
                StepState.Waiting -> Box(Modifier.size(20.dp).border(1.5.dp, Hairline, CircleShape))
            }
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            color = if (state == StepState.Waiting) Muted else Ink,
        )
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = Muted)
    }
}

private enum class Mark { Done, Now, Todo }

@Composable
private fun ChapterRow(number: Int, title: String, mark: Mark) {
    val doneLabel = stringResource(R.string.report_chapter_done)
    val nowLabel = stringResource(R.string.report_chapter_now)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            when (mark) {
                Mark.Done -> contentDescription = "$number. $title. $doneLabel"
                Mark.Now -> contentDescription = "$number. $title. $nowLabel"
                Mark.Todo -> Unit
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            number.toString().padStart(2, '0'),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 17.5.sp,
                fontWeight = if (mark == Mark.Now) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (mark == Mark.Now) Ink else Muted,
        )
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            when (mark) {
                Mark.Done -> Icon(LiquidIcons.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(14.dp))
                Mark.Now -> {
                    Box(Modifier.size(16.dp).background(GlowHoney.copy(alpha = 0.6f), CircleShape))
                    Box(Modifier.size(8.dp).background(Ink, CircleShape))
                }
                Mark.Todo -> Unit
            }
        }
    }
}

/* The mock's line: a hairline track, an ink fill, and a ringed knob at the head. While the model plans, the knob breathes. */
@Composable
private fun ProgressLine(fraction: Float, pulsing: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(600), label = "report-progress")
    val transition = rememberInfiniteTransition(label = "report-planning")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "report-planning-knob",
    )
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2
        val track = 2.dp.toPx()
        val knob = 7.dp.toPx()
        val usable = size.width - 2 * knob
        val x = knob + usable * shown
        drawLine(Hairline, Offset(knob, y), Offset(size.width - knob, y), strokeWidth = track, cap = StrokeCap.Round)
        drawLine(Ink, Offset(knob, y), Offset(x, y), strokeWidth = track, cap = StrokeCap.Round)
        val alpha = if (pulsing) breath else 1f
        drawCircle(Ink.copy(alpha = alpha), radius = knob, center = Offset(x, y))
        drawCircle(CardWhite, radius = knob - 1.dp.toPx(), center = Offset(x, y))
        drawCircle(Ink.copy(alpha = alpha), radius = knob - 4.dp.toPx(), center = Offset(x, y))
    }
}

// ── Ready ───────────────────────────────────────────────────────────────

@Composable
internal fun ReadyScreen(state: ReportState.Ready, chart: ChartSummary, onBack: () -> Unit, onMakeAgain: () -> Unit) {
    val context = LocalContext.current
    val story = state.story
    val verification = story.verification
    val chapters = pluralStringResource(R.plurals.report_chapters, story.chapters.size, story.chapters.size)
    val cover = remember(state.cover) { state.cover?.asImageBitmap() }
    val noViewer = stringResource(R.string.report_no_viewer)
    val chooserTitle = stringResource(R.string.report_share_chooser)
    val subject = stringResource(R.string.report_share_subject, chart.name)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ReportTopBar(
            stringResource(R.string.report_title),
            stringResource(R.string.report_subtitle_ready, state.preparedOn.format(ReadyDate)),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Glow(Modifier.requiredSize(width = 380.dp, height = 300.dp))
                val coverShape = RoundedCornerShape(6.dp)
                val description = stringResource(R.string.report_cover_description, chart.name)
                Box(
                    Modifier
                        .width(196.dp)
                        .aspectRatio(210f / 297f)
                        .shadow(18.dp, coverShape, ambientColor = CardShadow, spotColor = CardShadow)
                        .clip(coverShape)
                        .background(Paper)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        Image(cover, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(
                            chart.name.uppercase(),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.03.em),
                            color = PaperInk,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.report_ready_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.045).em),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        R.string.report_ready_meta,
                        chapters,
                        stringResource(if (verification.isVerified) R.string.report_ready_verified else R.string.report_ready_qualified),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(stringResource(R.string.report_open), onClick = { openPdf(context, state.file, noViewer) })
                GhostChip(stringResource(R.string.report_share), onClick = { sharePdf(context, state.file, chooserTitle, subject) })
            }

            (state.prose as? ReportApi.ProseResult.Unavailable)?.let { unavailable ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.report_plain_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (unavailable.reason) {
                                ReportApi.ProseFailure.DailyLimit -> R.string.report_plain_limit
                                ReportApi.ProseFailure.TimedOut -> R.string.report_plain_timeout
                                ReportApi.ProseFailure.Offline -> R.string.report_plain_offline
                                ReportApi.ProseFailure.Declined -> R.string.report_plain_declined
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                    if (unavailable.reason != ReportApi.ProseFailure.DailyLimit) {
                        GhostChip(stringResource(R.string.report_make_again), onClick = onMakeAgain)
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 34.dp, height = 40.dp)
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.report_file_kind),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Medium),
                            color = Muted,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.file.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = GeistMono, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                            color = Ink,
                        )
                        CardLabel(stringResource(R.string.report_file_where))
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 1.dp)) { TickBadge() }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (verification.warningCount > 0) R.string.report_checks_qualified else R.string.report_checks_passed,
                                verification.passedCount,
                                verification.totalCount,
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                        )
                        if (verification.summary.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(verification.summary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = Muted)
                        }
                    }
                }
            }
        }
    }
}

private fun reportUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

private fun openPdf(context: Context, file: File, noViewer: String) {
    val uri = reportUri(context, file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, noViewer, Toast.LENGTH_LONG).show()
    }
}

private fun sharePdf(context: Context, file: File, chooserTitle: String, subject: String) {
    val uri = reportUri(context, file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    /* The clip carries the grant through the chooser to the app picked from it. */
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

// ── Failed ──────────────────────────────────────────────────────────────

@Composable
internal fun FailedScreen(state: ReportState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    val message = when (state.reason) {
        ReportState.Failed.Reason.Verification -> stringResource(R.string.report_failed_verification)
        ReportState.Failed.Reason.Pdf -> stringResource(R.string.report_failed_pdf)
        ReportState.Failed.Reason.Network -> stringResource(state.message ?: R.string.report_failed_generic)
    }
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Glow(Modifier.requiredSize(width = 420.dp, height = 380.dp))
        FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 20.dp) {
            Text(stringResource(R.string.report_failed_title), style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(stringResource(R.string.report_retry), onClick = onRetry)
                GhostChip(stringResource(R.string.report_close), onClick = onClose)
            }
        }
    }
}

// ── Shared ──────────────────────────────────────────────────────────────

@Composable
private fun ReportTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    val backLabel = stringResource(R.string.report_back)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
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
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
