package dev.keystone.auto.ui.screens

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
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

/** The projector's screen on the phone. Touching it touches the projector (a virtual touch screen), keystone corrected. */
@Composable
fun MirrorScreen(vm: AppViewModel) {
    val state by vm.mirror.collectAsState()
    val decoder = remember { MirrorDecoder(STREAM_W, STREAM_H) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var started by remember { mutableStateOf(false) }

    // The decoder needs the surface before the first frames arrive, so start both together.
    LaunchedEffect(surface) {
        val s = surface ?: return@LaunchedEffect
        decoder.start(s)
        if (!started) { started = true; vm.startRemote(); vm.startMirror { u, k -> decoder.feed(u, k) } }
    }
    DisposableEffect(Unit) { onDispose { decoder.stop(); surface?.release(); vm.stopMirror() } }

    ScreenScaffold(title = "Projector screen", onBack = { vm.back() }, scroll = false) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val quad = (state as? MirrorState.Live)?.quad
            val quadNow by rememberUpdatedState(quad)
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surface = Surface(st) }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { decoder.stop(); surface = null; return true }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                        tv.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ -> applyQuad(v as TextureView, quadNow, r - l, b - t) }
                    }
                },
                update = { tv ->
                    // The stream shows the picture after the keystone warp; stretch its four corners back to the full view so the
                    // phone shows the projector's real screen and a touch lands exactly where it looks.
                    applyQuad(tv, quad, tv.width, tv.height)
                },
            )
            Box(Modifier.fillMaxSize().touchToProjector(vm, enabled = state is MirrorState.Live))
            when (val s = state) {
                is MirrorState.Live -> Unit
                MirrorState.Idle, MirrorState.Starting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("Starting the projector's screen…", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                is MirrorState.Failed -> Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Try again", { vm.stopMirror(); started = false; surface?.let { decoder.start(it); started = true; vm.startMirror { u, k -> decoder.feed(u, k) } } }, Modifier.width(180.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Hint("Tap, drag and pinch on the picture to use the projector like a touch screen.", center = true)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Ico.Back, "Back", { vm.press(RemoteKey.BACK) })
            RoundKey(Ico.Home, "Home", { vm.press(RemoteKey.HOME) })
            RoundKey(Ico.Recents, "Recent apps", { vm.press(RemoteKey.APP_SWITCH) })
            RoundKey(Ico.Keyboard, "Type", { vm.go(dev.keystone.auto.Screen.Keyboard) })
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
