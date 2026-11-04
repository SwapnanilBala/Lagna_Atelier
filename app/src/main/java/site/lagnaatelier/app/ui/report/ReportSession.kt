package site.lagnaatelier.app.ui.report

import android.app.Application
import androidx.annotation.StringRes
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.report.PersonalStory
import site.lagnaatelier.app.data.report.ProseProgress
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.report.ReportCover
import site.lagnaatelier.app.data.report.ReportFiles
import site.lagnaatelier.app.data.report.ReportPdf
import site.lagnaatelier.app.data.report.ReportStage
import site.lagnaatelier.app.data.report.StoryProseCodec
import site.lagnaatelier.app.ui.reading.signNameRes
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the report is, from the first call to the finished file. */
sealed interface ReportState {
    data object Idle : ReportState

    data class Working(
        val stage: ReportStage,
        val name: String,
        /** Null until the story route answers. */
        val story: PersonalStory? = null,
        /** The prose route's latest progress line, while it writes. */
        val writing: ProseProgress? = null,
    ) : ReportState

    data class Ready(
        val story: PersonalStory,
        val file: File,
        val pages: Int,
        val prose: ReportApi.ProseResult,
        /** Page 1, rendered back from the file; null if the renderer could not. */
        val cover: Bitmap?,
        val preparedOn: LocalDate,
    ) : ReportState

    data class Failed(val reason: Reason, @param:StringRes @get:StringRes val message: Int? = null) : ReportState {
        enum class Reason { Network, Verification, Pdf }
    }
}

/*
 * The report's work, held in a ViewModel so it survives recomposition and
 * rotation: a two-minute job should not restart because the phone turned.
 * The overlay reads [state]; [start] is a no-op while a report is in hand.
 */
class ReportSession(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf<ReportState>(ReportState.Idle)
        private set

    private var job: Job? = null

    fun start(chart: ChartSummary, details: BirthDetails?, api: LagnaApi) {
        if (job?.isActive == true || state is ReportState.Ready) return
        val birth = details ?: SAMPLE_DETAILS
        val reportApi = ReportApi(api)
        state = ReportState.Working(ReportStage.Calculating, chart.name)
        job = viewModelScope.launch { run(chart, birth, reportApi) }
    }

    /** "Stop writing", back, or Close: drops whatever is in flight and the finished state. */
    fun reset() {
        job?.cancel()
        job = null
        state = ReportState.Idle
    }

    /** Try again after a failure, or make it again for the written version. */
    fun retry(chart: ChartSummary, details: BirthDetails?, api: LagnaApi) {
        reset()
        start(chart, details, api)
    }

    private suspend fun run(chart: ChartSummary, details: BirthDetails, reportApi: ReportApi) {
        val report = try {
            reportApi.storyReport(details)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            /* Worded by the app: a failed consistency check is a 5xx with the site's own English. */
            state = ReportState.Failed(ReportState.Failed.Reason.Network, ErrorText.of(error))
            return
        } catch (error: OutOfMemoryError) {
            state = ReportState.Failed(ReportState.Failed.Reason.Network, R.string.error_generic)
            return
        }
        var story = report.story
        if (story.verification.isFailed) {
            state = ReportState.Failed(ReportState.Failed.Reason.Verification)
            return
        }
        state = ReportState.Working(ReportStage.Verifying, chart.name, story)

        state = ReportState.Working(ReportStage.Writing, chart.name, story)
        val prose = withTimeoutOrNull(PROSE_DEADLINE_MS) {
            reportApi.storyProse(story) { line ->
                (state as? ReportState.Working)?.let {
                    if (it.stage == ReportStage.Writing) state = it.copy(writing = it.writing?.advancedBy(line) ?: line)
                }
            }
        } ?: ReportApi.ProseResult.Unavailable(ReportApi.ProseFailure.TimedOut, null)
        if (prose is ReportApi.ProseResult.Written) story = StoryProseCodec.apply(story, prose.prose)

        state = ReportState.Working(ReportStage.Typesetting, chart.name, story)
        val preparedOn = preparedDate(report.generatedAtUtc)
        val app = getApplication<Application>()
        val cover = ReportCover(
            name = chart.name,
            /* In the same language as the rest of the PDF, which ReportPdf takes from this context. */
            ascendant = app.getString(signNameRes(chart.ascendant)),
            place = details.place.label,
            preparedOn = preparedOn.format(PREPARED_FORMAT),
        )
        state = try {
            val (file, pages, thumbnail) = withContext(Dispatchers.IO) { typeset(app, story, cover) }
            ReportState.Ready(story, file, pages, prose, thumbnail, preparedOn)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ReportState.Failed(ReportState.Failed.Reason.Pdf)
        } catch (error: OutOfMemoryError) {
            ReportState.Failed(ReportState.Failed.Reason.Pdf)
        }
    }

    private fun typeset(app: Application, story: PersonalStory, cover: ReportCover): Triple<File, Int, Bitmap?> {
        val folder = File(app.cacheDir, REPORTS_FOLDER).apply { mkdirs() }
        val name = ReportFiles.fileName(story)
        /* Only the latest report is kept; older ones are in whatever the reader shared them to. */
        folder.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        val partial = File(folder, "$name.part")
        val pages = ReportPdf(app).write(story, cover, partial)
        val file = File(folder, name)
        if (file.exists()) file.delete()
        if (!partial.renameTo(file)) throw IOException("Could not move the finished PDF into place.")
        return Triple(file, pages, runCatching { renderCover(file) }.getOrNull())
    }

    private fun renderCover(file: File): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val width = COVER_WIDTH_PX
                    val height = width * page.height / page.width
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    /* The renderer draws onto what is there; a PDF page with no fill would come out transparent. */
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    private fun preparedDate(generatedAtUtc: String): LocalDate =
        runCatching { Instant.parse(generatedAtUtc).atZone(ZoneId.systemDefault()).toLocalDate() }
            .getOrElse { LocalDate.now() }

    companion object {
        /** The FileProvider's cache path (res/xml/file_paths.xml). */
        const val REPORTS_FOLDER = "reports"

        /* The route's own ceiling is 300 s; past this the PDF goes ahead with the engine's text. */
        private const val PROSE_DEADLINE_MS = 330_000L
        private const val COVER_WIDTH_PX = 600

        private val PREPARED_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

        /** The sample chart's birth details, for a report on the sample (details are null then). */
        val SAMPLE_DETAILS = BirthDetails(
            name = "Ananya Mehra",
            date = LocalDate.of(1990, 6, 15),
            exactTime = "14:30",
            accuracy = TimeAccuracy.Exact,
            place = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330),
            engineId = "lahiri_classic",
        )
    }
}
