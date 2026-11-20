package dev.keystone.auto.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.MirrorState
import dev.keystone.auto.core.Panel
import dev.keystone.auto.link.RemoteKey
import dev.keystone.auto.platform.MirrorDecoder
import dev.keystone.auto.ui.*
import kotlin.math.roundToInt

private const val STREAM_W = 960
private const val STREAM_H = 540

/**
 * The projector's screen on the phone. Touching it touches the projector (a virtual touch screen), keystone corrected.
 * The stream only runs while this screen is in front: leaving it or putting the app in the background stops the encoder.
 */
@Composable
fun MirrorScreen(vm: AppViewModel) {
    val state by vm.mirror.collectAsState()
    val sharp by vm.mirrorSharp.collectAsState()
    val decoder = remember { MirrorDecoder(STREAM_W, STREAM_H) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    fun begin() {
        val s = surface ?: return
        decoder.start(s)
        vm.startRemote()
        vm.startMirror { u, k -> decoder.feed(u, k) }
    }
    fun end() { decoder.stop(); vm.stopMirror() }

    // The decoder needs the surface before the first frames arrive, so start both together.
    LaunchedEffect(surface) { begin() }
    // A different stream size needs a fresh decoder.
    var firstSharp by remember { mutableStateOf(true) }
    LaunchedEffect(sharp) { if (firstSharp) firstSharp = false else surface?.let { decoder.start(it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) end()
            if (e == Lifecycle.Event.ON_START) begin()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); decoder.stop(); surface?.release(); vm.stopMirror() }
    }

    // Landscape: turn the phone's screen sideways and hide the system bars; always put both back on the way out.
    DisposableEffect(landscape) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (landscape) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = landscape) { landscape = false }

    val picture: @Composable (Modifier) -> Unit = { modifier ->
        Picture(vm, state, modifier, onSurface = { surface = it }, onSurfaceGone = { decoder.stop(); surface = null },
            onRetry = { end(); begin() })
    }

    if (landscape) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            picture(Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(16f / 9f))
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).clip(RoundedCornerShape(26.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)).padding(vertical = 6.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RoundKey(Ico.Back, "Back", { vm.press(RemoteKey.BACK) }, size = 46.dp)
                RoundKey(Ico.Home, "Home", { vm.press(RemoteKey.HOME) }, size = 46.dp)
                RoundKey(Ico.Recents, "Recent apps", { vm.press(RemoteKey.APP_SWITCH) }, size = 46.dp)
                RoundKey(Ico.Keyboard, "Type", { landscape = false; vm.go(dev.keystone.auto.Screen.Keyboard) }, size = 46.dp)
                RoundKey(Ico.Close, "Exit landscape", { landscape = false }, size = 46.dp)
            }
        }
    } else ScreenScaffold(title = "Projector screen", onBack = { vm.back() }, scroll = false) {
        picture(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(10.dp))
        Hint("Tap, drag and pinch on the picture to use the projector like a touch screen.", center = true)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Landscape", { landscape = true }, Modifier.weight(1f))
            SecondaryButton(if (sharp) "Sharp" else "Smooth", { vm.setMirrorSharp(!sharp) }, Modifier.weight(1f))
        }
        Hint("Smooth keeps the projector's load low. Sharp looks better but makes it work harder while the picture moves.", Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Ico.Back, "Back", { vm.press(RemoteKey.BACK) })
            RoundKey(Ico.Home, "Home", { vm.press(RemoteKey.HOME) })
            RoundKey(Ico.Recents, "Recent apps", { vm.press(RemoteKey.APP_SWITCH) })
            RoundKey(Ico.Keyboard, "Type", { vm.go(dev.keystone.auto.Screen.Keyboard) })
        }
    }
}

@Composable
private fun Picture(
    vm: AppViewModel, state: MirrorState, modifier: Modifier,
    onSurface: (Surface) -> Unit, onSurfaceGone: () -> Unit, onRetry: () -> Unit,
) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
        val quad = (state as? MirrorState.Live)?.quad
        val quadNow by rememberUpdatedState(quad)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    tv.keepScreenOn = true
                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { onSurface(Surface(st)) }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { onSurfaceGone(); return true }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                    tv.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ -> applyQuad(v as TextureView, quadNow, r - l, b - t) }
                }
            },
            // The stream shows the picture after the keystone warp; stretch its four corners back to the full view so the
            // phone shows the projector's real screen and a touch lands exactly where it looks.
            update = { tv -> applyQuad(tv, quad, tv.width, tv.height) },
        )
        Box(Modifier.fillMaxSize().touchToProjector(vm, enabled = state is MirrorState.Live))
        when (state) {
            is MirrorState.Live -> Unit
            MirrorState.Idle, MirrorState.Starting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("Starting the projector's screen…", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            is MirrorState.Failed -> Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Try again", onRetry, Modifier.width(180.dp))
            }
        }
    }
}

/** Maps the warped picture's corners (fractions of the stream) onto the whole view. */
private fun applyQuad(tv: TextureView, quad: List<Pair<Float, Float>>?, w: Int, h: Int) {
    if (w <= 0 || h <= 0) return
    if (quad == null || quad.size != 4) { tv.setTransform(null); return }
    val src = FloatArray(8) { i -> if (i % 2 == 0) quad[i / 2].first * w else quad[i / 2].second * h }
    val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
    val m = Matrix()
    if (m.setPolyToPoly(src, 0, dst, 0, 4)) tv.setTransform(m) else tv.setTransform(null)
}

/** Up to two fingers become two touches on the projector's virtual touch screen (1280x720). */
private fun Modifier.touchToProjector(vm: AppViewModel, enabled: Boolean): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitPointerEventScope {
        val slots = HashMap<PointerId, Int>()
        fun px(p: Offset) = (p.x / size.width * (Panel.W - 1)).roundToInt() to (p.y / size.height * (Panel.H - 1)).roundToInt()
        while (true) {
            val event = awaitPointerEvent()
            for (c in event.changes) {
                val (x, y) = px(c.position)
                when {
                    c.changedToDown() -> {
                        val slot = (0 until 2).firstOrNull { it !in slots.values }
                        if (slot != null) { slots[c.id] = slot; vm.touchDown(slot, x, y) }
                        c.consume()
                    }
                    c.changedToUp() -> { slots.remove(c.id)?.let { vm.touchUp(it) }; c.consume() }
                    c.pressed && c.positionChanged() -> { slots[c.id]?.let { vm.touchMove(it, x, y) }; c.consume() }
                }
            }
        }
    }
}
