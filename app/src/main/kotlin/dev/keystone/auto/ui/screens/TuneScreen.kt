package dev.keystone.auto.ui.screens

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.TuneState
import dev.keystone.auto.core.Offsets
import dev.keystone.auto.core.Panel
import dev.keystone.auto.core.Pt
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

/**
 * Manual keystone: the projector's panel (1280x720) with the picture's outline and a dot on each corner. Drag a dot to move
 * that corner live; tap one to select it and nudge it with the arrows. Dragging is relative (the dot follows the finger's
 * movement, not its position), so a corner never jumps when it is picked up.
 */
@Composable
fun TuneScreen(vm: AppViewModel) {
    val state by vm.tune.collectAsState()
    val offsets by vm.tuneOffsets.collectAsState()
    LaunchedEffect(Unit) { vm.openTune() }
    TuneContent(state, offsets, onMove = { vm.tuneTo(it) }, onRetry = { vm.openTune() }, onBack = { vm.back() })
}

/** The whole screen as a pure function of its state (so it can be drawn in a snapshot test without a projector). */
@Composable
fun TuneContent(state: TuneState, offsets: Offsets, onMove: (Offsets) -> Unit, onRetry: () -> Unit, onBack: () -> Unit, initiallySelected: Int? = null) {
    val ready = state as? TuneState.Ready

    ScreenScaffold(
        title = "Fine-tune keystone", onBack = onBack, scroll = false,
        bottomBar = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Reset", { ready?.let { onMove(it.start) } }, Modifier.weight(1f), enabled = ready != null)
                PrimaryButton("Done", onBack, Modifier.weight(1f))
            }
        },
    ) {
        var selected by rememberSaveable { mutableStateOf(initiallySelected) }
        var step by rememberSaveable { mutableIntStateOf(1) }

        PanelView(offsets, selected, enabled = ready != null, state = state,
            onSelect = { selected = it }, onMove = onMove, onRetry = onRetry)
        Spacer(Modifier.height(14.dp))

        val sel = selected
        if (sel == null) {
            Hint(
                if (ready != null) "Drag a dot to move that corner of the picture. Tap a dot to nudge it a pixel at a time."
                else "Moves follow your finger on the projector as you drag.",
                center = true,
            )
        } else {
            Text(Panel.cornerNames[sel], Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text(cornerReadout(offsets, sel), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            StepPicker(step) { step = it }
            Spacer(Modifier.height(8.dp))
            val tap = rememberTap()
            fun nudge(dx: Int, dy: Int) { if (ready != null) onMove(offsets.nudged(sel, (dx * step).toDouble(), (dy * step).toDouble())) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RoundKey(Ico.Up, "Move up", { nudge(0, -1) }, size = 56.dp, repeat = true, tick = tap)
                Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    RoundKey(Ico.Left, "Move left", { nudge(-1, 0) }, size = 56.dp, repeat = true, tick = tap)
                    RoundKey(Ico.Right, "Move right", { nudge(1, 0) }, size = 56.dp, repeat = true, tick = tap)
                }
                RoundKey(Ico.Down, "Move down", { nudge(0, 1) }, size = 56.dp, repeat = true, tick = tap)
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

@Composable
private fun PanelView(
    offsets: Offsets, selected: Int?, enabled: Boolean, state: TuneState,
    onSelect: (Int?) -> Unit, onMove: (Offsets) -> Unit, onRetry: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val zone = MaterialTheme.colorScheme.outline
    val tap = rememberTap()
    val offsetsNow by rememberUpdatedState(offsets)
    val enabledNow by rememberUpdatedState(enabled)
    val selectedNow by rememberUpdatedState(selected)

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    val pad = 22.dp.toPx()
                    val hit = 40.dp.toPx()
                    fun toCanvas(p: Pt) = Offset(pad + (p.x / Panel.W * (size.width - 2 * pad)).toFloat(), pad + (p.y / Panel.H * (size.height - 2 * pad)).toFloat())
                    fun nearest(at: Offset): Int? = offsetsNow.corners().withIndex()
                        .map { (i, c) -> i to (toCanvas(c) - at).getDistance() }.filter { it.second <= hit }.minByOrNull { it.second }?.first
                    detectTapGestures { at -> if (enabledNow) { val i = nearest(at); if (i != selectedNow) tap(); onSelect(i) } }
                }
                .pointerInput(Unit) {
                    val pad = 22.dp.toPx()
                    val hit = 40.dp.toPx()
                    fun toCanvas(p: Pt) = Offset(pad + (p.x / Panel.W * (size.width - 2 * pad)).toFloat(), pad + (p.y / Panel.H * (size.height - 2 * pad)).toFloat())
                    var corner = -1
                    var pos = Pt(0.0, 0.0)
                    detectDragGestures(
                        onDragStart = { at ->
                            corner = -1
                            if (!enabledNow) return@detectDragGestures
                            val c = offsetsNow.corners().withIndex().map { (i, c) -> i to (toCanvas(c) - at).getDistance() }.filter { it.second <= hit }.minByOrNull { it.second }?.first
                            if (c != null) { corner = c; pos = offsetsNow.corners()[c]; if (selectedNow != c) tap(); onSelect(c) }
                        },
                        onDragEnd = { corner = -1 },
                        onDragCancel = { corner = -1 },
                    ) { change, amount ->
                        if (corner < 0) return@detectDragGestures
                        change.consume()
                        // the finger's movement, in panel pixels; the unrounded position is kept so slow drags are not lost to rounding
                        val box = Panel.cornerBox(corner)
                        pos = Pt(
                            (pos.x + amount.x / (size.width - 2 * pad) * Panel.W).coerceIn(box.left, box.right),
                            (pos.y + amount.y / (size.height - 2 * pad) * Panel.H).coerceIn(box.top, box.bottom),
                        )
                        onMove(offsetsNow.withCorner(corner, pos))
                    }
                },
        ) {
            val pad = 22.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            fun at(p: Pt) = Offset(pad + (p.x / Panel.W * w).toFloat(), pad + (p.y / Panel.H * h).toFloat())
            // the panel
            drawRoundRect(surface, Offset.Zero, size, CornerRadius(14.dp.toPx()))
            drawRect(zone.copy(alpha = 0.35f), Offset(pad, pad), Size(w, h), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            // each corner's reach (the area the projector accepts)
            for (i in 0..3) {
                val b = Panel.cornerBox(i)
                val a = at(Pt(b.left, b.top)); val z = at(Pt(b.right, b.bottom))
                drawRect(zone.copy(alpha = if (i == selected) 0.22f else 0.10f), a, Size(z.x - a.x, z.y - a.y))
            }
            // the picture as it is now
            val c = offsetsNow.corners().map { at(it) }
            val path = Path().apply { moveTo(c[0].x, c[0].y); for (k in 1..3) lineTo(c[k].x, c[k].y); close() }
            drawPath(path, primary.copy(alpha = 0.16f), style = Fill)
            drawPath(path, primary, style = Stroke(2.dp.toPx()))
            // the dots
            c.forEachIndexed { i, p ->
                val on = i == selected
                if (on) drawCircle(primary.copy(alpha = 0.25f), 20.dp.toPx(), p)
                drawCircle(if (enabled) primary else zone, if (on) 11.dp.toPx() else 9.dp.toPx(), p)
                drawCircle(Color.White.copy(alpha = if (enabled) 0.9f else 0.4f), 3.5.dp.toPx(), p)
            }
        }
        when (state) {
            TuneState.Starting, TuneState.Idle -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)).padding(16.dp)) {
                CircularProgressIndicator(color = primary)
                Spacer(Modifier.height(8.dp))
                Text("Connecting to the keystone…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            is TuneState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)).padding(16.dp)) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Try again", onRetry, Modifier.width(180.dp))
            }
            is TuneState.Ready -> Unit
        }
    }
}
