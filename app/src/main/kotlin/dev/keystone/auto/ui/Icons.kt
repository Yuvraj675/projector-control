package dev.keystone.auto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset

/** The app's own line icons (24 x 24 grid, rounded strokes), so every icon shares one weight and no icon library is needed. */
enum class Ico(val d: String, val filled: Boolean = false) {
    Pointer("M6 3.5 V17.5 L9.6 14.2 L12.2 20.2 L14.6 19.1 L12 13.2 L17 12.8 Z"),
    Keyboard("M3 6.5 H21 V17.5 H3 Z M6.5 10 H6.6 M10 10 H10.1 M13.5 10 H13.6 M17 10 H17.1 M7 14 H17"),
    Apps("M4.5 4.5 H10 V10 H4.5 Z M14 4.5 H19.5 V10 H14 Z M4.5 14 H10 V19.5 H4.5 Z M14 14 H19.5 V19.5 H14 Z"),
    Install("M12 3.5 V14.5 M7.5 10.5 L12 15 L16.5 10.5 M4.5 19.5 H19.5"),
    Keystone("M8 5 H16 L20.5 19 H3.5 Z"),
    Projector("M3 8 H21 V17 H3 Z M17.2 12.5 A2.7 2.7 0 1 0 11.8 12.5 A2.7 2.7 0 1 0 17.2 12.5 M6.5 17 V19.5 M17.5 17 V19.5"),
    Mic("M12 3.5 A3 3 0 0 0 9 6.5 V11 A3 3 0 0 0 15 11 V6.5 A3 3 0 0 0 12 3.5 Z M5.5 11 A6.5 6.5 0 0 0 18.5 11 M12 17.5 V21"),
    Power("M12 3 V11.5 M6.6 6.8 A8 8 0 1 0 17.4 6.8"),
    VolUp("M4 9.5 H8 L13 5.5 V18.5 L8 14.5 H4 Z M16.5 9 A4.5 4.5 0 0 1 16.5 15 M19 6.5 A8 8 0 0 1 19 17.5"),
    VolDown("M4 9.5 H8 L13 5.5 V18.5 L8 14.5 H4 Z M16.5 9 A4.5 4.5 0 0 1 16.5 15"),
    Mute("M4 9.5 H8 L13 5.5 V18.5 L8 14.5 H4 Z M16.5 9.5 L21 14.5 M21 9.5 L16.5 14.5"),
    Home("M4 11 L12 4 L20 11 V20 H14.5 V14.5 H9.5 V20 H4 Z"),
    Back("M9 7 L4 12 L9 17 M4 12 H15 A5 5 0 0 1 20 17 V18"),
    Recents("M5.5 5.5 H18.5 V18.5 H5.5 Z"),
    Menu("M4 7 H20 M4 12 H20 M4 17 H20"),
    Play("M8 5 L18.5 12 L8 19 Z"),
    PlayPause("M5 5.5 L12 12 L5 18.5 Z M15 5.5 V18.5 M19.5 5.5 V18.5"),
    Up("M6 15 L12 9 L18 15"),
    Down("M6 9 L12 15 L18 9"),
    Left("M15 6 L9 12 L15 18"),
    Right("M9 6 L15 12 L9 18"),
    Sliders("M4 7 H13 M17 7 H20 M4 17 H7 M11 17 H20 M15 4.5 V9.5 M9 14.5 V19.5"),
    Refresh("M19.5 11 A7.5 7.5 0 1 0 17.3 16.3 M19.5 5 V11 H13.5"),
    Backspace("M9 5.5 H20.5 V18.5 H9 L3.5 12 Z M12.5 9.5 L17 14.5 M17 9.5 L12.5 14.5"),
    Enter("M19.5 5.5 V12 H5 M9 8 L5 12 L9 16"),
    Check("M5 12.5 L10 17.5 L19 7"),
    Link("M10 14 A4 4 0 0 0 15.7 14 L18.7 11 A4 4 0 0 0 13 5.3 L12 6.3 M14 10 A4 4 0 0 0 8.3 10 L5.3 13 A4 4 0 0 0 11 18.7 L12 17.7"),
    Search("M18.5 11 A7.5 7.5 0 1 0 3.5 11 A7.5 7.5 0 1 0 18.5 11 M16.5 16.5 L21 21"),
    Close("M6 6 L18 18 M18 6 L6 18"),
    Trash("M4.5 7 H19.5 M9 7 V4.5 H15 V7 M6.5 7 L7.5 20 H16.5 L17.5 7"),
    Stop("M6.5 6.5 H17.5 V17.5 H6.5 Z"),
    Crosshair("M12 3 V7 M12 17 V21 M3 12 H7 M17 12 H21 M15 12 A3 3 0 1 0 9 12 A3 3 0 1 0 15 12"),
    Touchpad("M4 5.5 H20 V18.5 H4 Z M9.5 15 H14.5"),
    Dpad("M9 3.5 H15 V9 H20.5 V15 H15 V20.5 H9 V15 H3.5 V9 H9 Z"),
    Chip("M5 7.5 H19 V16.5 H5 Z M9 7.5 V4.5 M12 7.5 V4.5 M15 7.5 V4.5 M9 16.5 V19.5 M12 16.5 V19.5 M15 16.5 V19.5"),
    Moon("M20 14.5 A8 8 0 1 1 9.5 4 A6.5 6.5 0 0 0 20 14.5 Z"),
    Warning("M12 4 L21 19.5 H3 Z M12 10 V14 M12 16.8 H12.1"),
    Clipboard("M8 5.5 H6 V20 H18 V5.5 H16 M9 3.5 H15 V7 H9 Z"),
    Globe("M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M3 12 H21 M12 3 C15 6.5 15 17.5 12 21 C9 17.5 9 6.5 12 3"),
    Wifi("M3.5 9.5 A12 12 0 0 1 20.5 9.5 M6.5 13 A8 8 0 0 1 17.5 13 M9.5 16.3 A4 4 0 0 1 14.5 16.3 M12 19.6 H12.1"),
    Chevron("M9.5 6 L15.5 12 L9.5 18"),
}

@Composable
fun Icon24(ico: Ico, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, strokeWidth: Float = 1.8f) {
    val path = remember(ico) { PathParser().parsePathString(ico.d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            drawPath(
                path, tint,
                style = if (ico.filled) Fill else Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Suppress("unused")
private val keepPath = Path()
