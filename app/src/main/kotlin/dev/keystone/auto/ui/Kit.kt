package dev.keystone.auto.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.platform.Haptics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A vibration for presses, honouring the haptics setting. Goes straight to the phone's vibrator. */
@Composable
fun rememberTick(enabled: Boolean = true, kind: Haptics.Kind = Haptics.Kind.Click): () -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(enabled, ctx, kind) { { if (enabled) Haptics.play(ctx, kind) } }
}

/**
 * A round remote button. It answers on touch-down (like a hardware remote) and, when [repeat] is set, keeps firing while held.
 * Flat: a tonal disc, the accent only on the prominent key and while pressed.
 */
@Composable
fun RoundKey(
    ico: Ico,
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    repeat: Boolean = false,
    prominent: Boolean = false,
    tick: () -> Unit = {},
) {
    var down by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (down) 0.88f else 1f, pressSpring(), label = "key")
    val primary = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(
        when {
            down && prominent -> primary.copy(alpha = 0.85f)
            down -> MaterialTheme.colorScheme.outline
            prominent -> primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }, label = "keyfill",
    )
    val fg = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(CircleShape).background(fill)
            .semantics { contentDescription = label; role = Role.Button }
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    down = true
                    tick(); onPress()
                    coroutineScope {
                        val job = if (repeat) launch { delay(380); while (true) { onPress(); tick(); delay(110) } } else null
                        tryAwaitRelease()
                        job?.cancel()
                    }
                    down = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon24(ico, fg, size = size * 0.4f) }
}

data class Segment(val ico: Ico, val label: String)

/** A pill switch between a few modes. The highlight slides to the chosen one with a spring. */
@Composable
fun Segmented(options: List<Segment>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tap = rememberTap()
    BoxWithConstraints(modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(4.dp)) {
        val each = maxWidth / options.size
        val x = animateDpAsState(each * selected, moveSpring(), label = "seg")
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.value.roundToPx(), 0) }.width(each).height(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                val tint by animateColorAsState(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "segtint")
                Row(
                    Modifier.weight(1f).height(44.dp).clip(CircleShape)
                        .clickable(role = Role.Tab) { if (i != selected) { tap(); onSelect(i) } }
                        .semantics { this.selected = on },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon24(o.ico, tint, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(o.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}

/** A small icon-only button in the screen header. */
@Composable
fun HeaderIcon(ico: Ico, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).pressable(onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon24(ico, MaterialTheme.colorScheme.onBackground) }
}

/** A row in a list: icon, title, optional subtitle, chevron. No chips, no outlines. */
@Composable
fun ListRow(ico: Ico, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable(onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon24(ico, MaterialTheme.colorScheme.onSurface, size = 24.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon24(Ico.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
    }
}

data class TabItem(val ico: Ico, val label: String)

/**
 * The floating tab bar: one rounded slab, the selected tab grows a label and an accent capsule that slides between tabs.
 */
@Composable
fun FloatingTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tap = rememberTap()
    BoxWithConstraints(
        modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp).fillMaxWidth().height(68.dp)
            .clip(RoundedCornerShape(34.dp)).background(MaterialTheme.colorScheme.surface).padding(6.dp),
    ) {
        val each = maxWidth / tabs.size
        val x = animateDpAsState(each * selected, moveSpring(), label = "tab")
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.value.roundToPx(), 0) }.width(each).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                val tint by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabtint")
                val lift = animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = 380f), label = "lift")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(role = Role.Tab) { if (!on) { tap(); onSelect(i) } }
                        .semantics { this.selected = on; contentDescription = t.label },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon24(t.ico, tint, size = 22.dp, modifier = Modifier.graphicsLayer { val l = lift.value; translationY = (3f * (1f - l) - 3f * l) * density; scaleX = 1f + 0.12f * l; scaleY = 1f + 0.12f * l })
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** A short message that floats over any screen and goes away by itself. */
@Composable
fun ToastHost(vm: AppViewModel, modifier: Modifier = Modifier) {
    val text by vm.toast.collectAsState()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text != null) { shown = text; delay(3200); vm.toast.value = null }
    }
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 24.dp, vertical = 28.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(text != null, enter = slideInVertically(moveSpring()) { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
            Text(
                shown.orEmpty(), Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.onBackground).padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.background,
            )
        }
    }
}
