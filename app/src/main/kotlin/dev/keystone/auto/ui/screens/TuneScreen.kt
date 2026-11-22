package dev.keystone.auto.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.TuneState
import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Panel
import dev.keystone.auto.core.Pt
import dev.keystone.auto.core.TuneView
import dev.keystone.auto.ui.*

private val STEPS = listOf(1, 2, 5)

/** How far the corner is from the two panel edges it sits against, in words ("146 px from the left, 153 px from the top"). */
fun cornerReadout(o: Offsets, i: Int): String {
    val (h, hv, v, vv) = when (i) {
        0 -> listOf("left", o.x1, "top", o.y1)
        1 -> listOf("right", o.x2, "top", o.y2)
        2 -> listOf("right", o.x4, "bottom", o.y4)
        else -> listOf("left", o.x3, "bottom", o.y3)
    }
    return "${Math.round(hv as Double)} px from the $h, ${Math.round(vv as Double)} px from the $v"
}

@Composable
fun TuneScreen(vm: AppViewModel) {
    val state by vm.tune.collectAsState()
    val offsets by vm.tuneOffsets.collectAsState()
    LaunchedEffect(Unit) { vm.openTune() }
    TuneContent(state, offsets, onMove = { vm.tuneTo(it) }, onRetry = { vm.openTune() }, onBack = { vm.back() })
}

/**
 * Manual keystone, edited the way you judge it: as the picture looks on the wall.
 *
 * The keystone that is set when the screen opens is the base, so the picture starts as a plain rectangle showing the one
 * frame captured then. Dragging a corner warps that picture (the other three stay put) and the projector follows live; tap a
 * corner and the arrows move it up, down, left or right in the same view. A pure function of its state, so the snapshot
 * tests can draw it without a projector.
 */
@Composable
fun TuneContent(state: TuneState, offsets: Offsets, onMove: (Offsets) -> Unit, onRetry: () -> Unit, onBack: () -> Unit, initiallySelected: Int? = null) {
    val ready = state as? TuneState.Ready

    ScreenScaffold(
        title = "Fine-tune keystone", onBack = onBack, scroll = false,
        bottomBar = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Reset", { ready?.let { onMove(it.view.base) } }, Modifier.weight(1f), enabled = ready != null)
                PrimaryButton("Done", onBack, Modifier.weight(1f))
            }
        },
    ) {
        var selected by rememberSaveable { mutableStateOf(initiallySelected) }
        var step by rememberSaveable { mutableIntStateOf(1) }

        PanelView(state, offsets, selected, onSelect = { selected = it }, onMove = onMove, onRetry = onRetry)
        Spacer(Modifier.height(12.dp))

        val sel = selected
        if (sel == null || ready == null) {
            Hint(
                when {
                    ready == null -> "The projector's picture is captured once, then you move its corners and it follows live."
                    ready.image == null -> "Couldn't capture the picture, so a grid is shown. Drag a corner to warp it, or tap one to nudge it."
                    else -> "This is the picture as it looks now. Drag a corner to warp it, or tap one to nudge it with the arrows."
                },
                center = true,
            )
        } else {
            Text(Panel.cornerNames[sel], Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text(cornerReadout(offsets, sel), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            StepPicker(step) { step = it }
            Spacer(Modifier.height(8.dp))
            val tap = rememberTap()
            fun nudge(dx: Double, dy: Double) { onMove(ready.view.step(offsets, sel, Pt(dx, dy), step.toDouble())) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RoundKey(Ico.Up, "Move up", { nudge(0.0, -1.0) }, size = 56.dp, repeat = true, tick = tap)
                Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    RoundKey(Ico.Left, "Move left", { nudge(-1.0, 0.0) }, size = 56.dp, repeat = true, tick = tap)
                    RoundKey(Ico.Right, "Move right", { nudge(1.0, 0.0) }, size = 56.dp, repeat = true, tick = tap)
                }
                RoundKey(Ico.Down, "Move down", { nudge(0.0, 1.0) }, size = 56.dp, repeat = true, tick = tap)
            }
        }
    }
}

@Composable
private fun StepPicker(step: Int, onPick: (Int) -> Unit) {
    val tap = rememberTap()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Step", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        STEPS.forEach { s ->
            val on = s == step
            Box(
                Modifier.padding(horizontal = 3.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                    .clickable(role = Role.Tab) { if (!on) { tap(); onPick(s) } }
                    .semantics { this.selected = on; contentDescription = "$s pixel steps" }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("$s px", style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        }
    }
}

private val PAD = 22.dp

/** The editor: the captured picture warped onto the four dots, in the corrected view. */
@Composable
private fun PanelView(
    state: TuneState, offsets: Offsets, selected: Int?,
    onSelect: (Int?) -> Unit, onMove: (Offsets) -> Unit, onRetry: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val zone = MaterialTheme.colorScheme.outline
    val tap = rememberTap()
    val ready = state as? TuneState.Ready
    // Until the projector's own keystone is known, draw the neutral frame; it is replaced as soon as the screen is ready.
    val view = remember(ready?.view) { ready?.view ?: TuneView(Offsets.NONE) }
    val viewNow by rememberUpdatedState(view)
    val offsetsNow by rememberUpdatedState(offsets)
    val enabledNow by rememberUpdatedState(ready != null)
    val selectedNow by rememberUpdatedState(selected)
    val onSelectNow by rememberUpdatedState(onSelect)
    val onMoveNow by rememberUpdatedState(onMove)
    val bounds = view.bounds
    val bw = (bounds.right - bounds.left).toFloat()
    val bh = (bounds.bottom - bounds.top).toFloat()
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sc = (maxWidth - PAD * 2).value / bw                  // dp per view pixel
        val height = (bh * sc).dp + PAD * 2
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().testTag("tunePanel")
                    .pointerInput(Unit) {
                        val pad = PAD.toPx(); val hit = 40.dp.toPx()
                        // The view (and so the drawn scale) changes when the projector becomes ready, and this handler outlives
                        // that: always measure with the CURRENT view, never one captured when the handler started.
                        fun scale() = (size.width - 2 * pad) / (viewNow.bounds.right - viewNow.bounds.left).toFloat()
                        fun toCanvas(p: Pt) = Offset(pad + ((p.x - viewNow.bounds.left) * scale()).toFloat(), pad + ((p.y - viewNow.bounds.top) * scale()).toFloat())
                        detectTapGestures { at ->
                            if (!enabledNow) return@detectTapGestures
                            val i = viewNow.corners(offsetsNow).withIndex().map { (i, c) -> i to (toCanvas(c) - at).getDistance() }
                                .filter { it.second <= hit }.minByOrNull { it.second }?.first
                            if (i != selectedNow) tap()
                            onSelectNow(i)
                        }
                    }
                    .pointerInput(Unit) {
                        val pad = PAD.toPx(); val hit = 40.dp.toPx()
                        // The view (and so the drawn scale) changes when the projector becomes ready, and this handler outlives
                        // that: always measure with the CURRENT view, never one captured when the handler started.
                        fun scale() = (size.width - 2 * pad) / (viewNow.bounds.right - viewNow.bounds.left).toFloat()
                        fun toCanvas(p: Pt) = Offset(pad + ((p.x - viewNow.bounds.left) * scale()).toFloat(), pad + ((p.y - viewNow.bounds.top) * scale()).toFloat())
                        var corner = -1
                        var pos = Pt(0.0, 0.0)
                        detectDragGestures(
                            onDragStart = { at ->
                                corner = -1
                                if (!enabledNow) return@detectDragGestures
                                val vc = viewNow.corners(offsetsNow)
                                val c = vc.withIndex().map { (i, p) -> i to (toCanvas(p) - at).getDistance() }.filter { it.second <= hit }.minByOrNull { it.second }?.first
                                if (c != null) { corner = c; pos = vc[c]; if (selectedNow != c) tap(); onSelectNow(c) }
                            },
                            onDragEnd = { corner = -1 },
                            onDragCancel = { corner = -1 },
                        ) { change, amount ->
                            if (corner < 0) return@detectDragGestures
                            change.consume()
                            // The corner goes where the finger goes, in the corrected view: the dot moves by exactly the finger's
                            // movement (relative, so it never jumps when picked up), and the other three stay put on the wall.
                            val k = scale()
                            pos = Pt(pos.x + amount.x / k, pos.y + amount.y / k)
                            val next = viewNow.moveCorner(offsetsNow, corner, pos)
                            onMoveNow(next)
                            // at the limit the dot stops, and the finger has to come back before it moves again
                            val actual = viewNow.corners(next)[corner]
                            if (actual.dist(pos) > 1.5 / k) pos = actual
                        }
                    },
            ) {
                val pad = PAD.toPx()
                val k = (size.width - 2 * pad) / bw
                fun at(p: Pt) = Offset(pad + ((p.x - bounds.left) * k).toFloat(), pad + ((p.y - bounds.top) * k).toFloat())
                drawRoundRect(surface, Offset.Zero, size, CornerRadius(14.dp.toPx()))

                // how far each corner may go
                for (i in 0..3) {
                    val r = view.reach[i].map { at(it) }
                    val p = Path().apply { moveTo(r[0].x, r[0].y); for (j in 1..3) lineTo(r[j].x, r[j].y); close() }
                    drawPath(p, zone.copy(alpha = if (i == selected) 0.24f else 0.10f), style = Fill)
                }
                // where the picture started (the base): the full rectangle
                val f = Panel.frame.map { at(it) }
                val frame = Path().apply { moveTo(f[0].x, f[0].y); for (j in 1..3) lineTo(f[j].x, f[j].y); close() }
                drawPath(frame, zone.copy(alpha = 0.6f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))

                // the picture as it is now: the captured frame warped onto the four dots, or a tint when nothing was captured
                val vc = view.corners(offsets).map { at(it) }
                val quad = Path().apply { moveTo(vc[0].x, vc[0].y); for (j in 1..3) lineTo(vc[j].x, vc[j].y); close() }
                val img: Bitmap? = ready?.image
                if (img != null && ready != null) {
                    val sx = img.width / Panel.W.toFloat(); val sy = img.height / Panel.H.toFloat()
                    val src = ready.view.base.corners().flatMap { listOf(it.x.toFloat() * sx, it.y.toFloat() * sy) }.toFloatArray()
                    val dst = vc.flatMap { listOf(it.x, it.y) }.toFloatArray()
                    val m = Matrix()
                    if (m.setPolyToPoly(src, 0, dst, 0, 4)) {
                        drawIntoCanvas { c ->
                            val n = c.nativeCanvas
                            n.save(); n.clipPath(quad.asAndroidPath()); n.drawBitmap(img, m, paint); n.restore()
                        }
                    } else drawPath(quad, primary.copy(alpha = 0.16f), style = Fill)
                } else drawPath(quad, primary.copy(alpha = 0.16f), style = Fill)
                // a grid on the picture (straight lines stay straight under the warp), so the warp is easy to judge
                for (g in 1..3) {
                    val t = g / 4f
                    fun lerp(a: Offset, b: Offset, u: Float) = Offset(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u)
                    drawLine(Color.White.copy(alpha = 0.28f), lerp(vc[0], vc[1], t), lerp(vc[3], vc[2], t), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.28f), lerp(vc[0], vc[3], t), lerp(vc[1], vc[2], t), 1.dp.toPx())
                }
                drawPath(quad, primary, style = Stroke(2.dp.toPx()))

                // the dots
                vc.forEachIndexed { i, p ->
                    val on = i == selected
                    if (on) drawCircle(primary.copy(alpha = 0.25f), 20.dp.toPx(), p)
                    drawCircle(if (ready != null) primary else zone, if (on) 11.dp.toPx() else 9.dp.toPx(), p)
                    drawCircle(Color.White.copy(alpha = if (ready != null) 0.9f else 0.4f), 3.5.dp.toPx(), p)
                }
            }
            when (state) {
                is TuneState.Starting -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)).padding(16.dp)) {
                    CircularProgressIndicator(color = primary)
                    Spacer(Modifier.height(8.dp))
                    Text(state.step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                }
                TuneState.Idle -> Unit
                is TuneState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)).padding(16.dp)) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Try again", onRetry, Modifier.width(180.dp))
                }
                is TuneState.Ready -> Unit
            }
        }
    }
}
