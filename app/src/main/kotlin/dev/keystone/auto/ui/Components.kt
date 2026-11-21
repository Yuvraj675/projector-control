package dev.keystone.auto.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.keystone.auto.ui.theme.appColors

/** True while a screen is shown inside the bottom tab bar: it hides the back arrow and leaves room for the bar. */
val LocalInTabs = compositionLocalOf { false }

/** Whether the phone should vibrate on taps (the Settings switch). */
val LocalHapticsOn = compositionLocalOf { true }

/** A press feedback for anything tappable. */
@Composable
fun rememberTap(): () -> Unit = rememberTick(LocalHapticsOn.current)

/** Quick, slightly bouncy: the one spring every press and move in the app uses. */
fun <T> pressSpring() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
fun <T> moveSpring() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/** A press that shrinks the thing a little and springs back. Returns the modifier and the interaction source to click with. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button, haptic: Boolean = true): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, pressSpring(), label = "press")
    val tap = rememberTap()
    return this.scale(scale).clickable(interactionSource = src, indication = null, enabled = enabled, role = role) { if (haptic) tap(); onClick() }
}

/** Flat, near-black (or paper) backdrop with the system bars handled. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars),
        content = content,
    )
}

private val STEP_NAMES = listOf("Photos", "Fixing", "Checking")

/** Where you are in the fix: three named stages, the finished ones filled, the current one named. [current] is 0 for Photos. */
@Composable
fun StepIndicator(current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        STEP_NAMES.forEachIndexed { i, name ->
            val fill by animateFloatAsState(if (i <= current) 1f else 0f, moveSpring(), label = "seg")
            val tint by animateColorAsState(if (i == current) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, label = "stept")
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, color = tint)
            }
        }
    }
}

/** A screen: a large left-aligned title (with a quiet back arrow when it isn't a tab), then the body. */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    step: Int? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (ColumnScope.() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inTabs = LocalInTabs.current
    AppBackground(modifier) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 6.dp)) {
                if (onBack != null && !inTabs) {
                    Box(Modifier.offset(x = (-10).dp).size(44.dp).clip(CircleShape).pressable(onBack), contentAlignment = Alignment.Center) {
                        Icon24(Ico.ArrowLeft, MaterialTheme.colorScheme.onBackground, size = 22.dp)
                    }
                } else Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    actions()
                }
                if (step != null) {
                    Spacer(Modifier.height(14.dp))
                    StepIndicator(step - 1)
                }
            }
            val bodyMod = Modifier.weight(1f).fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
            Column(bodyMod.padding(horizontal = 24.dp).padding(top = 18.dp, bottom = if (inTabs && bottomBar == null) 96.dp else 16.dp), content = content)
            if (bottomBar != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = if (inTabs) 96.dp else 16.dp, top = 4.dp), content = bottomBar)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val bg by animateColorAsState(if (enabled && !loading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, label = "pbg")
    Row(
        modifier.fillMaxWidth().height(58.dp).clip(CircleShape).background(bg)
            .pressable(onClick, enabled = enabled && !loading),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled && !loading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = fg)
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .pressable(onClick, enabled = enabled),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.heightIn(min = 44.dp).clip(CircleShape).pressable(onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** A raised surface: separated from the page by tone alone, no outline. */
@Composable
fun AppCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).padding(padding),
        content = content,
    )
}

enum class BannerKind { Info, Warning, Error, Success }

@Composable
fun Banner(text: String, kind: BannerKind = BannerKind.Info, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        BannerKind.Info -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
        BannerKind.Warning -> MaterialTheme.appColors.warning.copy(alpha = 0.14f) to MaterialTheme.appColors.warning
        BannerKind.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BannerKind.Success -> MaterialTheme.appColors.success.copy(alpha = 0.14f) to MaterialTheme.appColors.success
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (kind == BannerKind.Info) MaterialTheme.colorScheme.onSurface else fg)
    }
}

/** A small status chip: a quiet tinted pill, the colour carries the meaning. */
@Composable
fun StatusPill(text: String, ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> MaterialTheme.appColors.success
        false -> MaterialTheme.appColors.warning
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text, modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = color,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(bottom = 10.dp, top = 4.dp))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

/** A numbered instruction: a large light numeral, then the title and body. */
@Composable
fun NumberedStep(number: Int, title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text("$number", Modifier.width(34.dp), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A thin progress line that springs to its value. */
@Composable
fun SmoothProgress(fraction: Float, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), moveSpring(), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

/** Key/value line used in summaries. */
@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DividerLine() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Draws a soft glowing disc behind hero illustrations. */
@Composable
fun Glow(modifier: Modifier = Modifier, size: Dp = 220.dp) {
    val glow = MaterialTheme.appColors.glow
    Canvas(modifier.size(size)) {
        drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset(this.size.width / 2, this.size.height / 2), radius = this.size.minDimension / 2))
    }
}

/** Fades and lifts a block into place a beat after the screen appears; [index] sets the order. */
@Composable
fun Modifier.stagger(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60L + index * 55L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, moveSpring(), label = "stagger")
    return this.graphicsLayer { alpha = a.coerceIn(0f, 1f); translationY = (1f - a) * 36f }
}
