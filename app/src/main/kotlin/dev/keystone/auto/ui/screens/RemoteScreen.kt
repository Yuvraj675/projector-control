package dev.keystone.auto.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.RemoteState
import dev.keystone.auto.core.PadMotion
import dev.keystone.auto.link.Evdev
import dev.keystone.auto.link.RemoteKey
import dev.keystone.auto.platform.GyroPointer
import dev.keystone.auto.ui.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max

private val MODES = listOf(Segment(Ico.Touchpad, "Touchpad"), Segment(Ico.Crosshair, "Air"), Segment(Ico.Dpad, "D-pad"))

@Composable
fun RemoteScreen(vm: AppViewModel, initialMode: Int = 0, autoStart: Boolean = true) {
    val state by vm.remoteState.collectAsState()
    val settings by vm.settings.collectAsState()
    var mode by rememberSaveable { mutableIntStateOf(initialMode) }
    val tick = rememberTick(settings.haptics)
    var typing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoStart) vm.startRemote() }

    ScreenScaffold(
        title = "Remote", onBack = { vm.back() }, scroll = false,
        actions = {
            HeaderIcon(Ico.Keyboard, if (typing) "Hide keyboard" else "Type on the projector") { tick(); typing = !typing }
            HeaderIcon(Ico.Power, "Projector screen off") { vm.press(RemoteKey.POWER) }
        },
    ) {
        Segmented(MODES, mode, { mode = it; tick() })
        Spacer(Modifier.height(14.dp))
        if (typing) {
            TypingBar(vm, tick) { typing = false }
            Spacer(Modifier.height(10.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                0 -> TouchPad(vm, Modifier.fillMaxSize())
                1 -> AirPad(vm, Modifier.fillMaxSize())
                else -> DPad(vm, Modifier.fillMaxSize())
            }
            when (val s = state) {
                RemoteState.Ready -> Unit
                RemoteState.Idle, RemoteState.Starting -> Overlay { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text("Connecting your phone as a mouse…", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium) }
                is RemoteState.Failed -> Overlay {
                    Text(s.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Reconnect", { vm.startRemote() }, Modifier.width(200.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Ico.Back, "Back", { vm.press(RemoteKey.BACK) }, tick = tick)
            RoundKey(Ico.Home, "Home", { vm.press(RemoteKey.HOME) }, tick = tick)
            RoundKey(Ico.Recents, "Recent apps", { vm.press(RemoteKey.APP_SWITCH) }, tick = tick)
            RoundKey(Ico.Menu, "Menu", { vm.press(RemoteKey.MENU) }, tick = tick)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Ico.VolDown, "Volume down", { vm.press(RemoteKey.VOLUME_DOWN) }, repeat = true, tick = tick)
            RoundKey(Ico.Mute, "Mute", { vm.press(RemoteKey.MUTE) }, tick = tick)
            RoundKey(Ico.VolUp, "Volume up", { vm.press(RemoteKey.VOLUME_UP) }, repeat = true, tick = tick)
            RoundKey(Ico.PlayPause, "Play or pause", { vm.press(RemoteKey.PLAY_PAUSE) }, prominent = true, tick = tick)
        }
    }
}

/**
 * A keyboard inside the remote: opens the phone keyboard and sends every letter to the projector as it is typed.
 * The field always starts with an invisible marker so a Backspace on an empty field is still noticed.
 */
@Composable
private fun TypingBar(vm: AppViewModel, tick: () -> Unit, onClose: () -> Unit) {
    val marker = "\u200B"
    var value by remember { mutableStateOf(marker) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); kb?.show() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                when {
                    !new.startsWith(marker) -> { vm.press(RemoteKey.BACKSPACE); tick(); value = marker }
                    else -> {
                        val typed = new.removePrefix(marker)
                        val old = value.removePrefix(marker)
                        if (typed.length > old.length) { vm.typeText(typed.substring(old.length)); tick() }
                        else if (typed.length < old.length) { repeat(old.length - typed.length) { vm.press(RemoteKey.BACKSPACE) }; tick() }
                        // keep the field short: only the last few characters matter for the diff
                        value = if (typed.length > 24) marker else new
                    }
                }
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Typing on the projector…") },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { vm.press(RemoteKey.ENTER); tick(); value = marker }),
        )
        Spacer(Modifier.width(8.dp))
        RoundKey(Ico.Enter, "Enter", { vm.press(RemoteKey.ENTER); value = marker }, size = 48.dp, prominent = true, tick = tick)
        Spacer(Modifier.width(4.dp))
        HeaderIcon(Ico.Check, "Done typing") { kb?.hide(); onClose() }
    }
}

@Composable
private fun Overlay(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** The dotted, softly lit surface behind every control. */
@Composable
private fun PadSurface(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val dot = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center = center, radius = size.maxDimension * 0.6f), radius = size.maxDimension, center = center)
            val step = 26.dp.toPx()
            var y = step
            while (y < size.height) { var x = step; while (x < size.width) { drawCircle(dot, 1.6.dp.toPx(), Offset(x, y)); x += step }; y += step }
        }
        content()
    }
}

private const val TAP_MS = 320L
private const val LONG_MS = 380L
private const val SLOP_PX = 14f

/** Swipe to move, tap to click, two fingers to scroll, two-finger tap for Back, press-and-hold to drag. */
@Composable
private fun TouchPad(vm: AppViewModel, modifier: Modifier) {
    val settings by vm.settings.collectAsState()
    val s by rememberUpdatedState(settings)
    val tick = rememberTick(settings.haptics)
    val tickNow by rememberUpdatedState(tick)
    PadSurface(modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startT = down.uptimeMillis
            var travelled = 0f
            var maxFingers = 1
            var scrollAcc = 0f
            var longPressed = false
            var endT = startT
            while (true) {
                val waiting = !longPressed && travelled < SLOP_PX && maxFingers == 1
                val ev = withTimeoutOrNull(if (waiting) max(1L, LONG_MS - (endT - startT)) else Long.MAX_VALUE) { awaitPointerEvent() }
                if (ev == null) { longPressed = true; vm.buttonDown(); tickNow(); continue }
                val pressed = ev.changes.filter { it.pressed }
                endT = ev.changes.maxOf { it.uptimeMillis }
                if (pressed.isEmpty()) { ev.changes.forEach { it.consume() }; break }
                maxFingers = max(maxFingers, pressed.size)
                if (pressed.size >= 2) {
                    val dy = pressed.map { it.positionChange().y }.average().toFloat()
                    travelled += abs(dy)
                    vm.scrollBy((if (s.naturalScroll) dy else -dy) / 24.0)
                    scrollAcc += abs(dy)
                    if (scrollAcc >= 40f) { scrollAcc = 0f; tickNow() }
                } else {
                    val c = pressed[0]
                    val d = c.positionChange()
                    val dt = max(1L, c.uptimeMillis - c.previousUptimeMillis)
                    val speed = d.getDistance() / dt * 1000f
                    travelled += d.getDistance()
                    if (longPressed || travelled >= SLOP_PX) {
                        val g = PadMotion.gain(speed.toDouble(), s.pointerSpeed)
                        vm.pointerMove(d.x * g, d.y * g)
                    }
                }
                ev.changes.forEach { it.consume() }
            }
            if (longPressed) { vm.buttonUp(); tickNow() }
            else if (travelled < SLOP_PX && endT - startT < TAP_MS) { tickNow(); vm.click(if (maxFingers >= 2) Evdev.BTN_RIGHT else Evdev.BTN_LEFT) }
        }
    }) {
        Column(Modifier.align(Alignment.BottomCenter).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Swipe to move, tap to click", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Two fingers scroll, two-finger tap goes back", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

/** Point the phone like a Magic Remote: hold the big button to aim, tap it to click. */
@Composable
private fun AirPad(vm: AppViewModel, modifier: Modifier) {
    val context: Context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val gyro = remember { GyroPointer(context) }
    val speed by rememberUpdatedState(settings.airSpeed)
    val tick = rememberTick(settings.haptics)
    var aiming by remember { mutableStateOf(false) }
    DisposableEffect(gyro) {
        gyro.onMove = { dx, dy -> vm.pointerMove(dx, dy) }
        onDispose { gyro.stop() }
    }
    PadSurface(modifier) {
        if (!gyro.available) {
            Text("This phone has no gyroscope, so the air mouse isn't available. Use the touchpad instead.", Modifier.align(Alignment.Center).padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@PadSurface
        }
        val primary = MaterialTheme.colorScheme.primary
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(210.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(primary.copy(alpha = if (aiming) 0.45f else 0.22f), primary.copy(alpha = 0.06f))))
                    .border(2.dp, primary.copy(alpha = if (aiming) 0.95f else 0.5f), CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            gyro.speed = speed
                            gyro.start(); aiming = true; tick()
                            var travelled = 0f
                            var last = down.position
                            var upT = down.uptimeMillis
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.first()
                                travelled += (c.position - last).getDistance(); last = c.position
                                upT = c.uptimeMillis
                                c.consume()
                                if (!c.pressed) break
                            }
                            gyro.stop(); aiming = false
                            if (upT - down.uptimeMillis < TAP_MS && travelled < 40f) { tick(); vm.click() }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon24(Ico.Crosshair, primary, size = 56.dp, strokeWidth = 1.5f)
            }
            Spacer(Modifier.height(18.dp))
            Text(if (aiming) "Aiming…" else "Hold to aim, tap to click", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Point the top of the phone at the screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundKey(Ico.Up, "Scroll up", { vm.scrollBy(1.0) }, size = 48.dp, repeat = true, tick = tick)
            RoundKey(Ico.Down, "Scroll down", { vm.scrollBy(-1.0) }, size = 48.dp, repeat = true, tick = tick)
        }
    }
}

/** Arrow keys and OK, for apps that are driven by a D-pad. Hold an arrow to repeat it. */
@Composable
private fun DPad(vm: AppViewModel, modifier: Modifier) {
    val settings by vm.settings.collectAsState()
    val tick = rememberTick(settings.haptics)
    PadSurface(modifier) {
        Box(Modifier.align(Alignment.Center).size(270.dp)) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            RoundKey(Ico.Up, "Up", { vm.press(RemoteKey.UP) }, Modifier.align(Alignment.TopCenter).padding(top = 14.dp), size = 62.dp, repeat = true, tick = tick)
            RoundKey(Ico.Down, "Down", { vm.press(RemoteKey.DOWN) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), size = 62.dp, repeat = true, tick = tick)
            RoundKey(Ico.Left, "Left", { vm.press(RemoteKey.LEFT) }, Modifier.align(Alignment.CenterStart).padding(start = 14.dp), size = 62.dp, repeat = true, tick = tick)
            RoundKey(Ico.Right, "Right", { vm.press(RemoteKey.RIGHT) }, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), size = 62.dp, repeat = true, tick = tick)
            RoundKey(Ico.Check, "OK", { vm.press(RemoteKey.OK) }, Modifier.align(Alignment.Center), size = 92.dp, prominent = true, tick = tick)
        }
    }
}
