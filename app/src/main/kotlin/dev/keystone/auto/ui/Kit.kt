package dev.keystone.auto.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A light tick for presses, honouring the haptics setting. */
@Composable
fun rememberTick(enabled: Boolean = true): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(enabled, h) { { if (enabled) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** A bento tile for the home screen. [accent] tiles carry the app colour, the rest are quiet surfaces. */
@Composable
fun Tile(
    title: String,
    subtitle: String,
    ico: Ico,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    big: Boolean = false,
    compact: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "tile")
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(28.dp)
    val bg = if (accent) Brush.linearGradient(listOf(primary.copy(alpha = 0.34f), primary.copy(alpha = 0.10f)))
    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
    Box(
        modifier.scale(scale).clip(shape).background(bg)
            .border(BorderStroke(1.dp, if (accent) primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" }
            .padding(if (big || compact) 18.dp else 14.dp),
    ) {
        val chip = if (accent) primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant
        if (compact) {
            // icon on the left, text on the right: for tiles that are too short to stack them
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(chip), contentAlignment = Alignment.Center) { Icon24(ico, MaterialTheme.colorScheme.onSurface, size = 22.dp) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            return@Box
        }
        Box(Modifier.size(if (big) 52.dp else 42.dp).clip(CircleShape).background(chip), contentAlignment = Alignment.Center) {
            Icon24(ico, if (accent) primary else MaterialTheme.colorScheme.onSurface, size = if (big) 28.dp else 22.dp)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A round remote button. It answers on touch-down (like a hardware remote) and, when [repeat] is set, keeps firing while held.
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
    val scale by animateFloatAsState(if (down) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "key")
    val primary = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(
        when {
            down -> primary.copy(alpha = 0.32f)
            prominent -> primary.copy(alpha = 0.20f)
            else -> MaterialTheme.colorScheme.surface
        }, label = "keyfill",
    )
    val border = if (prominent || down) primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.size(size).scale(scale).clip(CircleShape).background(fill).border(1.dp, border, CircleShape)
            .semantics { contentDescription = label; role = Role.Button }
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    down = true
                    tick(); onPress()
                    coroutineScope {
                        val job = if (repeat) launch { delay(380); while (true) { onPress(); delay(110) } } else null
                        tryAwaitRelease()
                        job?.cancel()
                    }
                    down = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon24(ico, if (prominent || down) primary else MaterialTheme.colorScheme.onSurface, size = size * 0.4f) }
}

data class Segment(val ico: Ico, val label: String)

/** A pill-shaped switch between a few modes. */
@Composable
fun Segmented(options: List<Segment>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val bg by animateColorAsState(if (on) primary.copy(alpha = 0.20f) else androidx.compose.ui.graphics.Color.Transparent, label = "seg")
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon24(o.ico, if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(o.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A small icon-only button in the screen header. */
@Composable
fun HeaderIcon(ico: Ico, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon24(ico, MaterialTheme.colorScheme.onBackground) }
}

/** A row in a list card: icon, title, optional subtitle, chevron. */
@Composable
fun ListRow(ico: Ico, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon24(ico, MaterialTheme.colorScheme.onSurface, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon24(Ico.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
    }
}

/** A short message that floats over any screen and goes away by itself. */
@Composable
fun ToastHost(vm: AppViewModel, modifier: Modifier = Modifier) {
    val text by vm.toast.collectAsState()
    LaunchedEffect(text) {
        if (text != null) { delay(3200); vm.toast.value = null }
    }
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 24.dp), contentAlignment = Alignment.BottomCenter) {
        text?.let {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.94f)).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.background) }
        }
    }
}
