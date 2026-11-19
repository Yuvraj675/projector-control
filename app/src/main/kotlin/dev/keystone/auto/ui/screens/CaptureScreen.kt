package dev.keystone.auto.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.core.elevationDeg
import dev.keystone.auto.core.rollDeg
import dev.keystone.auto.core.upInSensorFrame
import dev.keystone.auto.platform.CameraMeta
import dev.keystone.auto.platform.CameraRig
import dev.keystone.auto.platform.GravityTracker
import dev.keystone.auto.ui.*
import dev.keystone.auto.ui.theme.appColors
import dev.keystone.auto.vision.DetectResult
import dev.keystone.auto.vision.GrayImage
import dev.keystone.auto.vision.MarkerDetector
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What the live camera feed and the sensors currently say. Written from background threads, read by Compose. */
private class LiveState {
    var cameraReady by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var markersOk by mutableStateOf(false)
    var elev by mutableDoubleStateOf(0.0)
    var roll by mutableDoubleStateOf(0.0)
    var steady by mutableStateOf(false)
    val levelOk get() = abs(elev) <= LEVEL_LIMIT && abs(roll) <= LEVEL_LIMIT

    private var lastDetect = 0L
    private var lastLevel = 0L

    fun onFrame(img: GrayImage) {
        val now = System.currentTimeMillis()
        if (now - lastDetect < 200) return
        lastDetect = now
        val r = MarkerDetector.detect(img)
        markersOk = r is DetectResult.Found && wellPlaced(r, img.w, img.h)
    }

    private fun wellPlaced(r: DetectResult.Found, w: Int, h: Int): Boolean {
        val xs = r.points.map { it.x }
        val ys = r.points.map { it.y }
        val width = (xs.max() - xs.min()) / w
        val edge = 0.03
        return width in 0.25..0.95 && xs.min() > edge * w && xs.max() < (1 - edge) * w && ys.min() > edge * h && ys.max() < (1 - edge) * h
    }

    fun refresh(g: GravityTracker, meta: CameraMeta?) {
        val now = System.currentTimeMillis()
        if (now - lastLevel < 60) return
        lastLevel = now
        val grav = g.average(8) ?: return
        val up = upInSensorFrame(grav, meta?.sensorOrientation ?: 90)
        elev = elevationDeg(up)
        roll = rollDeg(up)
        steady = g.steady()
    }

    companion object {
        const val LEVEL_LIMIT = 1.5
    }
}

@Composable
fun CaptureScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // The level reading and the framing guide are easiest sideways.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it; asked = true }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        PermissionView(
            asked = asked,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
            onBack = { vm.back() },
        )
        return
    }
    CameraContent(vm)
}

@Composable
private fun CameraContent(vm: AppViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val session by vm.session.collectAsState()
    val processing by vm.processing.collectAsState()
    val notice by vm.notice.collectAsState()
    val settings by vm.settings.collectAsState()
    val resultMode by vm.resultMode.collectAsState()

    val rig = remember { CameraRig(context) }
    val gravity = remember { GravityTracker(context) }
    val live = remember { LiveState() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    var shooting by remember { mutableStateOf(false) }
    var cooldownUntil by remember { mutableLongStateOf(0L) }
    val autoCapture by rememberUpdatedState(settings.autoCapture)

    DisposableEffect(owner) {
        gravity.onSample = { live.refresh(gravity, rig.meta) }
        gravity.start()
        rig.bind(
            owner, previewView, settings.exposureStops,
            onFrame = { live.onFrame(it) },
            onReady = { live.cameraReady = true; if (it == null) live.error = "This phone doesn't report its camera details, so accuracy may suffer." },
            onError = { live.error = it },
        )
        onDispose { gravity.stop(); rig.release() }
    }

    fun shoot() {
        if (shooting || processing) return
        val meta = rig.meta
        if (meta == null) { vm.notice.value = "The camera's details aren't available yet. Wait a moment and try again."; return }
        val g = gravity.average()
        if (g == null) { vm.notice.value = "The phone's tilt sensor isn't responding."; return }
        shooting = true
        cooldownUntil = System.currentTimeMillis() + 2500
        vm.notice.value = null
        val file = File(context.cacheDir, "cap_${System.nanoTime()}.jpg")
        rig.takePhoto(
            file,
            onSaved = { shooting = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.onPhoto(file, g, meta) },
            onError = { shooting = false; vm.notice.value = "The photo failed: $it" },
        )
    }

    // Take the photo by itself once the circles are in view, the phone is level and it has been still for a moment.
    LaunchedEffect(Unit) {
        var readySince = 0L
        while (true) {
            delay(100)
            val now = System.currentTimeMillis()
            val ready = live.cameraReady && live.markersOk && live.levelOk && live.steady && !shooting && !vm.processing.value
            if (ready) {
                if (readySince == 0L) readySince = now
                if (autoCapture && now - readySince > 1200 && now > cooldownUntil) { readySince = 0L; shoot() }
            } else readySince = 0L
        }
    }

    val items = session?.items.orEmpty()
    val target = if (resultMode) 1 else session?.target ?: 4
    val have = if (resultMode) 0 else session?.usableCount ?: 0
    val last = items.lastOrNull()
    val allGood = live.markersOk && live.levelOk && live.steady

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // framing guide
        val guide = if (allGood) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val l = w * 0.14f; val r = w * 0.86f; val t = h * 0.14f; val b = h * 0.86f
            val len = 36.dp.toPx(); val sw = 4.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(guide, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
                drawLine(guide, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
            }
            corner(l, t, 1f, 1f); corner(r, t, -1f, 1f); corner(l, b, 1f, -1f); corner(r, b, -1f, -1f)
            drawLine(guide.copy(alpha = 0.7f), Offset(w / 2 - 22.dp.toPx(), h / 2), Offset(w / 2 + 22.dp.toPx(), h / 2), 2.dp.toPx())
            drawLine(guide.copy(alpha = 0.7f), Offset(w / 2, h / 2 - 22.dp.toPx()), Offset(w / 2, h / 2 + 22.dp.toPx()), 2.dp.toPx())
        }

        // top: back, title, status
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 12.dp).padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(
                    if (resultMode) "Photo of the result" else "Photo ${minOf(have + 1, target)} of $target",
                    color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                StatusPill("4 circles", live.markersOk)
                Spacer(Modifier.width(8.dp))
                StatusPill("Level ${"%+.1f".format(live.elev)}° ${"%+.1f".format(live.roll)}°", live.levelOk)
                Spacer(Modifier.width(8.dp))
                StatusPill("Steady", live.steady)
            }
        }

        // messages
        Column(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars).padding(top = 64.dp).padding(horizontal = 80.dp)) {
            live.error?.let { Banner(it, BannerKind.Error) }
            notice?.let { Banner(it, BannerKind.Info) }
            if (notice == null && last != null && live.error == null) {
                last.problem?.let { Banner("Last photo: $it", BannerKind.Error) } ?: last.warning?.let { Banner("Last photo: $it", BannerKind.Warning) }
            }
        }

        // bottom: instruction, thumbnails, shutter
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val instruction = when {
                !live.cameraReady -> "Starting the camera…"
                processing -> "Analysing the photo…"
                !live.markersOk -> "Aim at the picture: all four white circles in view, with a little margin"
                !live.levelOk -> "Level the phone: tilt ${"%+.1f".format(live.elev)}° up/down, roll ${"%+.1f".format(live.roll)}°"
                !live.steady -> "Hold still…"
                autoCapture -> "Perfect. Taking the photo…"
                else -> "Perfect. Tap the shutter"
            }
            Text(
                instruction, color = if (allGood) MaterialTheme.colorScheme.primary else Color.White,
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    TextAction("Show pattern again", { vm.showPatternAgain() }, color = Color.White)
                    TextAction(if (autoCapture) "Auto-capture: on" else "Auto-capture: off", { vm.updateSettings(settings.copy(autoCapture = !settings.autoCapture)) }, color = Color.White.copy(alpha = 0.8f))
                }
                Shutter(enabled = live.cameraReady && !processing && !shooting, ready = allGood, onClick = { shoot() })
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    items.takeLast(4).forEach { item ->
                        Box(
                            Modifier.padding(start = 6.dp).size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                                .border(2.dp, if (item.usable) MaterialTheme.appColors.success else MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                                .clickable(enabled = !processing) { vm.removeCapture(item.id) },
                        ) {
                            item.thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) Hint("Tap a small photo to remove it", Modifier.padding(top = 4.dp), center = true)
        }

        if (processing) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Analysing…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, ready: Boolean, onClick: () -> Unit) {
    val ring = if (ready) MaterialTheme.colorScheme.primary else Color.White
    Box(
        Modifier.size(78.dp).clip(CircleShape).border(4.dp, ring.copy(alpha = if (enabled) 1f else 0.4f), CircleShape).padding(8.dp)
            .clip(CircleShape).background(ring.copy(alpha = if (enabled) 0.95f else 0.3f)).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun PermissionView(asked: Boolean, onRequest: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = "Camera access", onBack = onBack) {
        AppCard {
            Text("AutoKeystone needs the camera", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Hint("It photographs the test pattern on the wall to measure the projector's keystone. Photos are analysed on your phone and are never uploaded or kept.")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Allow camera", onRequest)
            if (asked) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Open app settings", onSettings)
            }
        }
    }
}
