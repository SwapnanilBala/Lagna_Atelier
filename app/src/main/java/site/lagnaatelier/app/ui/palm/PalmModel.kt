package site.lagnaatelier.app.ui.palm

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.AppDates
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.palm.PalmAllowanceStore
import site.lagnaatelier.app.data.palm.PalmApi
import site.lagnaatelier.app.data.palm.PalmCaptureFiles
import site.lagnaatelier.app.data.palm.PalmHistoryStore
import site.lagnaatelier.app.data.palm.PalmPendingStore
import site.lagnaatelier.app.data.palm.PalmImageLoader
import site.lagnaatelier.app.data.palm.PalmQa
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.data.palm.PalmReading
import site.lagnaatelier.app.data.palm.PalmReadingParser
import site.lagnaatelier.app.data.palm.PalmRequests
import site.lagnaatelier.app.data.palm.PalmWeek
import site.lagnaatelier.app.data.palm.PhotoRefusal
import site.lagnaatelier.app.data.palm.ReadingRefusal
import site.lagnaatelier.app.data.palm.PreparedPhoto
import site.lagnaatelier.app.data.palm.QaRole
import site.lagnaatelier.app.data.palm.QaTurn
import site.lagnaatelier.app.data.palm.SavedPalmReading
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

/** Which of the tab's three views is showing. */
enum class PalmView { Capture, Reading, History, Compare }

/** A reading on screen: one just read, or one opened from the history. */
class ShownReading(
    val reading: PalmReading,
    val photo: ImageBitmap?,
    /** The photo as sent, kept until the reading is saved. Null for a saved reading. */
    val photoJpeg: ByteArray?,
    val createdAtMillis: Long,
    val reader: PalmReader,
    val classicalMode: Boolean,
    val jyotishContext: JSONObject?,
    savedId: String?,
) {
    var savedId by mutableStateOf(savedId)
}

/*
 * The tab's state, scoped to the activity so it outlives rotation and a trip
 * to another tab: a reading takes about a minute and is one of the week's
 * two, so losing it to a turned phone is not an option. Created through
 * ViewModelProvider, since lifecycle-viewmodel-compose is not on the
 * classpath.
 */
/** "Whose hand" for a chart's sex at birth: a woman's or a man's, or not said. */
fun readerFor(sex: BirthSex?): PalmReader = when (sex) {
    BirthSex.Female -> PalmReader.Woman
    BirthSex.Male -> PalmReader.Man
    null -> PalmReader.Unspecified
}

class PalmModel(application: Application) : AndroidViewModel(application) {
    private val allowanceStore = PalmAllowanceStore(application)
    private val history = PalmHistoryStore(application)
    private val pending = PalmPendingStore(application)
    private fun text(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    var view by mutableStateOf(PalmView.Capture)

    var reader by mutableStateOf(PalmReader.Unspecified)

    /* The chart (and its sex at birth) "whose hand" was last preset from. */
    private var presetFrom: String? = null

    /**
     * Presets "whose hand" from the chart's sex at birth, once per chart, so a
     * choice the reader then makes by hand stands until another chart opens.
     */
    fun presetReader(chart: String?, sex: BirthSex?) {
        val from = chart?.let { "$it|${sex?.apiValue.orEmpty()}" }
        if (from == presetFrom) return
        presetFrom = from
        reader = readerFor(sex)
    }
    var classicalMode by mutableStateOf(false)

    var photo by mutableStateOf<PreparedPhoto?>(null)
        private set
    var photoBitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set
    var captureError by mutableStateOf<String?>(null)

    /** When the reading in flight was sent (elapsedRealtime), or null. */
    var analysingSince by mutableStateOf<Long?>(null)
        private set

    /* Read off the main thread in init; until then the panel shows the full week. */
    var remaining by mutableIntStateOf(PalmWeek.READINGS_PER_WEEK)
        private set

    var shown by mutableStateOf<ShownReading?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    val turns = mutableStateListOf<QaTurn>()
    var draft by mutableStateOf("")
    var asking by mutableStateOf(false)
        private set
    var qaError by mutableStateOf<String?>(null)
        private set

    /**
     * The site answered 401: whatever the phone thought, it is not signed in
     * there. Shows the sign-in card until the account says it is signed in
     * again ([signedIn]).
     */
    var sessionEnded by mutableStateOf(false)
        private set

    fun signedIn() {
        sessionEnded = false
    }

    var saved by mutableStateOf<List<SavedPalmReading>>(emptyList())
        private set
    var historyLoaded by mutableStateOf(false)
        private set

    init {
        refreshAllowance()
        refreshHistory()
        restorePending()
        /* Captures stranded by an app closed with the camera open; see PalmCaptureFiles. */
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PalmCaptureFiles.clearStale(PalmCaptureFiles.dir(getApplication())) }
        }
    }

    /** The camera was cancelled or failed: its empty or partial file goes now. */
    fun discardCapture() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PalmCaptureFiles.clearAll(PalmCaptureFiles.dir(getApplication())) }
        }
    }

    fun refreshAllowance() {
        viewModelScope.launch {
            remaining = withContext(Dispatchers.IO) { allowanceStore.remaining() }
        }
    }

    /* A reading read before the process was let go, and not saved: back on screen as it was. */
    private fun restorePending() {
        viewModelScope.launch {
            val (record, jpeg) = runCatching { pending.read() }.getOrNull() ?: return@launch
            if (shown != null) return@launch
            val bitmap = jpeg?.let { withContext(Dispatchers.IO) { runCatching { decode(it) }.getOrNull() } }
            show(
                ShownReading(
                    reading = record.reading,
                    photo = bitmap,
                    photoJpeg = jpeg,
                    createdAtMillis = record.createdAtMillis,
                    reader = record.reader,
                    classicalMode = record.classicalMode,
                    jyotishContext = record.jyotishContext,
                    savedId = null,
                ),
            )
        }
    }

    fun cameraUnavailable() {
        captureError = text(R.string.palm_error_camera)
    }

    /**
     * Prepares the upload copy of [uri]. For a [fromCamera] photo the camera's
     * full-size original (EXIF and GPS included) is deleted as soon as it has
     * been read, whether or not preparing it worked. A photo chosen from the
     * gallery is the reader's own file and is never touched.
     */
    fun onPhoto(uri: Uri, fromCamera: Boolean = false) {
        captureError = null
        preparing = true
        viewModelScope.launch {
            try {
                val prepared = PalmImageLoader.prepare(getApplication(), uri)
                photoBitmap = withContext(Dispatchers.IO) { decode(prepared.jpeg) }
                photo = prepared
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                captureError = text(R.string.palm_error_photo)
            } finally {
                preparing = false
                if (fromCamera) {
                    /* NonCancellable: the original must go even if this work was cancelled. */
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { PalmCaptureFiles.clearAll(PalmCaptureFiles.dir(getApplication())) }
                    }
                }
            }
        }
    }

    fun analyse(api: LagnaApi, chart: ChartSummary, hasRealChart: Boolean) {
        val prepared = photo ?: return
        if (analysingSince != null) return
        captureError = null
        analysingSince = SystemClock.elapsedRealtime()
        val reader = reader
        val classical = classicalMode
        /* Only from a chart the reader cast: the sample is somebody else's. */
        val jyotish = if (hasRealChart) PalmRequests.jyotishContext(chart) else null
        val bitmap = photoBitmap
        viewModelScope.launch {
            var token: Long? = null
            /* A refusal before the model ran: the server did not count it, so neither does the phone. */
            fun rollBack() {
                token?.let { allowanceStore.rollBack(it) }
                token = null
            }
            try {
                val left = withContext(Dispatchers.IO) { allowanceStore.remaining() }
                if (left <= 0) {
                    captureError = weeklyMessage()
                    return@launch
                }
                val body = withContext(Dispatchers.Default) {
                    PalmRequests.readingBody(prepared.base64, prepared.mediaType, classical, reader, jyotish)
                }
                /* Counted before it goes: killed mid-reading, the phone still knows it was spent. */
                token = withContext(Dispatchers.IO) { allowanceStore.recordSend() }
                val reading = PalmApi(api).read(body)
                if (reading == null) {
                    captureError = text(R.string.palm_error_not_reading)
                } else {
                    val shownReading = ShownReading(
                        reading = reading,
                        photo = bitmap,
                        photoJpeg = prepared.jpeg,
                        createdAtMillis = System.currentTimeMillis(),
                        reader = reader,
                        classicalMode = classical,
                        jyotishContext = jyotish,
                        savedId = null,
                    )
                    /* On disk before it is on screen: it has been spent, so it must not be lost. */
                    withContext(NonCancellable) { runCatching { pending.write(shownReading.asRecord(), prepared.jpeg) } }
                    show(shownReading)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LagnaApi.ApiException) {
                if (!PalmApi.counted(e.status)) rollBack()
                captureError = when (val refusal = PalmApi.readingRefusal(e)) {
                    ReadingRefusal.SignIn -> {
                        sessionEnded = true
                        null
                    }
                    ReadingRefusal.Weekly -> {
                        allowanceStore.recordWeeklyCap()
                        weeklyMessage()
                    }
                    /* The photo is kept: the reader may want to see what was turned away before taking another. */
                    is ReadingRefusal.Photo -> refusedMessage(refusal.refusal)
                    ReadingRefusal.Other -> text(ErrorText.of(e))
                }
            } catch (_: SocketTimeoutException) {
                captureError = text(R.string.palm_error_timeout)
            } catch (_: UnknownHostException) {
                /* Never reached the site. */
                rollBack()
                captureError = text(R.string.palm_error_offline)
            } catch (_: ConnectException) {
                rollBack()
                captureError = text(R.string.palm_error_offline)
            } catch (_: IOException) {
                /* Dropped mid-answer: the server may well have spent it, so it stays counted. */
                captureError = text(R.string.palm_error_generic)
            } catch (_: Exception) {
                captureError = text(R.string.palm_error_generic)
            } catch (_: OutOfMemoryError) {
                captureError = text(R.string.palm_error_generic)
            } finally {
                analysingSince = null
                refreshAllowance()
            }
        }
    }

    /** A refused photo's words, and that it cost none of the week's readings. */
    private fun refusedMessage(refusal: PhotoRefusal): String =
        "${text(refusal.message)} ${text(R.string.palm_refused_not_counted)}"

    private fun weeklyMessage(): String {
        val opens = nextOpening()
        return text(R.string.palm_error_weekly, PalmWeek.READINGS_PER_WEEK, opens)
    }

    /** The Monday the week's readings open again, for the allowance panel. */
    fun nextOpening(): String = PalmWeek.nextWeekStart(Instant.now()).format(AppDates.formatter("EEEE d MMMM", "EEEEdMMMM"))

    private fun show(reading: ShownReading) {
        shown = reading
        turns.clear()
        draft = ""
        qaError = null
        saveError = null
        view = PalmView.Reading
    }

    /** Back to the capture card with nothing chosen. An unsaved reading is let go, as the reader chose. */
    fun newReading() {
        viewModelScope.launch { runCatching { pending.clear() } }
        shown = null
        photo = null
        photoBitmap = null
        captureError = null
        turns.clear()
        draft = ""
        qaError = null
        view = PalmView.Capture
    }

    fun save() {
        val current = shown ?: return
        if (current.savedId != null || saving) return
        saving = true
        saveError = null
        viewModelScope.launch {
            try {
                val record = history.save(
                    reading = current.reading,
                    photoJpeg = current.photoJpeg,
                    reader = current.reader,
                    classicalMode = current.classicalMode,
                    jyotishContext = current.jyotishContext,
                    createdAtMillis = current.createdAtMillis,
                )
                current.savedId = record.id
                runCatching { pending.clear() }
                saved = history.list()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                saveError = text(R.string.palm_error_save)
            } finally {
                saving = false
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            saved = runCatching { history.list() }.getOrDefault(emptyList())
            historyLoaded = true
        }
    }

    fun openHistory() {
        refreshHistory()
        refreshAllowance()
        view = PalmView.History
    }

    fun openSaved(record: SavedPalmReading) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decode(history.photoFile(record.id).readBytes()) }.getOrNull()
            }
            show(
                ShownReading(
                    reading = record.reading,
                    photo = bitmap,
                    photoJpeg = null,
                    createdAtMillis = record.createdAtMillis,
                    reader = record.reader,
                    classicalMode = record.classicalMode,
                    jyotishContext = record.jyotishContext,
                    savedId = record.id,
                ),
            )
        }
    }

    fun delete(record: SavedPalmReading) {
        viewModelScope.launch {
            runCatching { history.delete(record.id) }
            if (shown?.savedId == record.id) shown?.savedId = null
            saved = runCatching { history.list() }.getOrDefault(emptyList())
        }
    }

    /** The saved photo's thumbnail, decoded small. */
    suspend fun thumbnail(record: SavedPalmReading): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = history.photoFile(record.id)
        if (!file.exists()) return@withContext null
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        runCatching { BitmapFactory.decodeFile(file.path, options)?.asImageBitmap() }.getOrNull()
    }

    fun ask(api: LagnaApi, rawQuestion: String) {
        val current = shown ?: return
        val question = rawQuestion.trim().take(PalmQa.MAX_QUESTION_CHARS)
        if (question.isEmpty() || asking || PalmQa.atLimit(turns)) return
        qaError = null
        draft = ""
        asking = true
        /* The thread as it stood before this question; the server appends the question itself. */
        val prior = turns.toList()
        turns.add(QaTurn(QaRole.User, question))
        viewModelScope.launch {
            try {
                val body = PalmQa.requestBody(
                    reading = PalmReadingParser.toJson(current.reading),
                    jyotishContext = current.jyotishContext,
                    classicalMode = current.classicalMode,
                    history = prior,
                    question = question,
                )
                val answer = PalmApi(api).ask(body) ?: throw IOException("no answer")
                if (shown === current) turns.add(QaTurn(QaRole.Assistant, answer.answer, answer.offTopic))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shown === current) {
                    /* Take the question back out, as the web does: unanswered, it reads as ignored. */
                    if (turns.lastOrNull()?.role == QaRole.User) turns.removeAt(turns.lastIndex)
                    draft = question
                    val signIn = e is LagnaApi.ApiException && PalmApi.needsSignIn(e)
                    if (signIn) sessionEnded = true
                    qaError = text(
                        when {
                            signIn -> R.string.palm_sign_in_ask
                            e is LagnaApi.ApiException -> ErrorText.of(e)
                            else -> R.string.palm_qa_error
                        },
                    )
                }
            } finally {
                asking = false
            }
        }
    }

    private fun ShownReading.asRecord() = SavedPalmReading(
        id = "pending",
        createdAtMillis = createdAtMillis,
        reader = reader,
        classicalMode = classicalMode,
        jyotishContext = jyotishContext,
        reading = reading,
    )

    private fun decode(jpeg: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
}
