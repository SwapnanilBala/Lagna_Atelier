package site.lagnaatelier.app.ui.palm

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import site.lagnaatelier.app.data.palm.PalmCaptureFiles
import androidx.lifecycle.ViewModelProvider
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.chartKey
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi

/*
 * The Palm tab: capture, reading and history, as a small state machine of
 * its own under the shared top bar. Back from the reading or the history
 * returns to the capture card; back from there leaves the tab as usual.
 *
 * Palm works without a chart. [details] is null for the sample chart, and
 * then no chart facts are sent with the reading: the sample is somebody else's.
 */
@Composable
fun PalmTab(chart: ChartSummary, details: BirthDetails?, api: LagnaApi, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as ComponentActivity
    val model = remember(activity) { ViewModelProvider(activity)[PalmModel::class.java] }
    val context = LocalContext.current
    val hasChart = details != null

    LaunchedEffect(Unit) { model.refreshAllowance() }
    /* The sample chart is somebody else's, so it presets nothing. */
    val chartKey = details?.let(::chartKey)
    LaunchedEffect(chartKey, details?.sex) { model.presetReader(chartKey, details?.sex) }

    /* Where the camera app writes, kept across the process being let go while it is open. */
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        cameraUri = null
        /* The camera's full-size file is deleted once read, or now if there is nothing to read. */
        if (ok && uri != null) model.onPhoto(Uri.parse(uri), fromCamera = true) else model.discardCapture()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.onPhoto(uri)
    }

    fun launchCamera() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            model.cameraUnavailable()
            return
        }
        val uri = runCatching {
            /* Earlier captures are already gone: each is deleted as soon as it has been read. */
            FileProvider.getUriForFile(context, "${context.packageName}.files", PalmCaptureFiles.newCapture(context))
        }.getOrNull()
        if (uri == null) {
            model.cameraUnavailable()
            return
        }
        cameraUri = uri.toString()
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            model.cameraUnavailable()
        }
    }

    fun choosePhoto() {
        try {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            model.captureError = context.getString(R.string.palm_error_photo)
        }
    }

    BackHandler(enabled = model.view != PalmView.Capture) { model.view = PalmView.Capture }

    Box(modifier.fillMaxSize()) {
        val shown = model.shown
        when {
            model.view == PalmView.Reading && shown != null -> PalmReadingView(model, shown, onAsk = { model.ask(api, it) })
            model.view == PalmView.History -> PalmHistoryView(model)
            model.view == PalmView.Compare -> PalmCompareView(model)
            else -> PalmCaptureView(
                model = model,
                hasChart = hasChart,
                onTakePhoto = ::launchCamera,
                onChoosePhoto = ::choosePhoto,
                onAnalyse = { model.analyse(api, chart, hasRealChart = hasChart) },
            )
        }
    }
}
