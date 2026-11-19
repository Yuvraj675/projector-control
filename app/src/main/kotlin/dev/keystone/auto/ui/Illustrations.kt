package dev.keystone.auto.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.keystone.auto.ui.theme.appColors

/** An animated radar used while scanning the Wi-Fi for the projector. */
@Composable
fun ScanRadar(modifier: Modifier = Modifier, size: Dp = 200.dp, active: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outline
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        for (i in 1..3) drawCircle(line.copy(alpha = 0.5f), r * i / 3f, c, style = Stroke(1.5.dp.toPx()))
        drawLine(line.copy(alpha = 0.35f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(line.copy(alpha = 0.35f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        if (active) {
            drawCircle(primary.copy(alpha = (1f - pulse) * 0.35f), r * pulse, c)
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, primary.copy(alpha = 0.55f)), center = c),
                startAngle = sweep - 70f, sweepAngle = 70f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
        }
        drawCircle(primary, 7.dp.toPx(), c)
        drawCircle(primary.copy(alpha = 0.3f), 14.dp.toPx(), c)
    }
}

/** Hero illustration: a projector throwing a (slightly skewed) picture that the app straightens. */
@Composable
fun ProjectorHero(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().aspectRatio(2.1f)) {
        val w = size.width
        val h = size.height
        // wall
        drawRoundRect(surface.copy(alpha = 0.55f), Offset(w * 0.34f, h * 0.06f), Size(w * 0.62f, h * 0.84f), CornerRadius(18.dp.toPx()))
        // skewed ghost picture
        val ghost = Path().apply {
            moveTo(w * 0.47f, h * 0.2f); lineTo(w * 0.88f, h * 0.12f); lineTo(w * 0.9f, h * 0.8f); lineTo(w * 0.44f, h * 0.72f); close()
        }
        drawPath(ghost, ink.copy(alpha = 0.25f))
        drawPath(ghost, ink.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        // corrected rectangle
        drawRoundRect(primary.copy(alpha = 0.9f), Offset(w * 0.5f, h * 0.27f), Size(w * 0.36f, h * 0.42f), CornerRadius(8.dp.toPx()))
        // beam
        val beam = Path().apply { moveTo(w * 0.17f, h * 0.7f); lineTo(w * 0.47f, h * 0.2f); lineTo(w * 0.44f, h * 0.72f); close() }
        drawPath(beam, Brush.horizontalGradient(listOf(primary.copy(alpha = 0.45f), primary.copy(alpha = 0.0f)), startX = w * 0.17f, endX = w * 0.46f))
        // projector
        drawRoundRect(outline, Offset(w * 0.04f, h * 0.62f), Size(w * 0.17f, h * 0.2f), CornerRadius(10.dp.toPx()))
        drawCircle(primary, h * 0.055f, Offset(w * 0.185f, h * 0.72f))
        drawCircle(Color.White.copy(alpha = 0.9f), h * 0.02f, Offset(w * 0.185f, h * 0.72f))
        // marker dots on the skewed corners
        listOf(Offset(w * 0.47f, h * 0.2f), Offset(w * 0.88f, h * 0.12f), Offset(w * 0.9f, h * 0.8f), Offset(w * 0.44f, h * 0.72f))
            .forEach { drawCircle(Color.White, 4.5.dp.toPx(), it) }
    }
}

private fun DrawScope.dashed() = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))

/** Top-down diagram: the wall, the picture, and the phone held parallel to the wall in front of its middle. */
@Composable
fun PlacementDiagram(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.appColors.warning
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().aspectRatio(1.7f)) {
        val w = size.width
        val h = size.height
        // wall
        drawRoundRect(ink.copy(alpha = 0.5f), Offset(w * 0.05f, h * 0.08f), Size(w * 0.9f, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
        // picture on the wall
        drawRoundRect(primary, Offset(w * 0.28f, h * 0.08f - 2.dp.toPx()), Size(w * 0.44f, 14.dp.toPx()), CornerRadius(4.dp.toPx()))
        // view cone from the phone to the picture edges
        val px = w * 0.5f
        val py = h * 0.76f
        val cone = Path().apply { moveTo(px, py); lineTo(w * 0.28f, h * 0.08f + 12.dp.toPx()); lineTo(w * 0.72f, h * 0.08f + 12.dp.toPx()); close() }
        drawPath(cone, primary.copy(alpha = 0.14f))
        drawLine(primary.copy(alpha = 0.5f), Offset(px, py), Offset(w * 0.28f, h * 0.08f + 12.dp.toPx()), 1.5.dp.toPx(), pathEffect = dashed())
        drawLine(primary.copy(alpha = 0.5f), Offset(px, py), Offset(w * 0.72f, h * 0.08f + 12.dp.toPx()), 1.5.dp.toPx(), pathEffect = dashed())
        // centre line (stand in front of the middle)
        drawLine(warn, Offset(px, h * 0.08f + 16.dp.toPx()), Offset(px, py - 16.dp.toPx()), 2.dp.toPx(), pathEffect = dashed())
        // phone: a wide, thin rectangle parallel to the wall
        drawRoundRect(surface, Offset(px - 30.dp.toPx(), py - 5.dp.toPx()), Size(60.dp.toPx(), 12.dp.toPx()), CornerRadius(5.dp.toPx()))
        drawRoundRect(ink, Offset(px - 30.dp.toPx(), py - 5.dp.toPx()), Size(60.dp.toPx(), 12.dp.toPx()), CornerRadius(5.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        drawCircle(primary, 3.dp.toPx(), Offset(px, py - 5.dp.toPx()))
        // parallel marks
        listOf(-1f, 1f).forEach { s ->
            val x = px + s * 46.dp.toPx()
            drawLine(warn, Offset(x, h * 0.08f + 14.dp.toPx()), Offset(x, py - 2.dp.toPx()), 1.dp.toPx(), pathEffect = dashed())
        }
        // projector, off to the side
        drawRoundRect(ink.copy(alpha = 0.8f), Offset(w * 0.8f, h * 0.7f), Size(w * 0.1f, h * 0.12f), CornerRadius(6.dp.toPx()))
    }
}

/** What the camera should see: the whole picture with all four circles inside, with some margin. */
@Composable
fun FramingDiagram(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val ok = MaterialTheme.appColors.success
    Box(modifier.fillMaxWidth().aspectRatio(1.8f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.8f)) {
            val w = size.width
            val h = size.height
            // phone screen
            drawRoundRect(ink.copy(alpha = 0.18f), Offset(w * 0.04f, h * 0.04f), Size(w * 0.92f, h * 0.92f), CornerRadius(22.dp.toPx()))
            drawRoundRect(ink, Offset(w * 0.04f, h * 0.04f), Size(w * 0.92f, h * 0.92f), CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
            // the projected picture, with margin on every side
            val l = w * 0.2f; val t = h * 0.2f; val r = w * 0.8f; val b = h * 0.8f
            drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(l, t), Size(r - l, b - t), CornerRadius(4.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(l, t), Size(r - l, b - t), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
            // the four circles
            val dots = listOf(Offset(l + (r - l) * 0.075f, t + (b - t) * 0.075f), Offset(l + (r - l) * 0.925f, t + (b - t) * 0.075f),
                Offset(l + (r - l) * 0.85f, t + (b - t) * 0.925f), Offset(l + (r - l) * 0.075f, t + (b - t) * 0.925f))
            dots.forEach { drawCircle(Color.White, 8.dp.toPx(), it); drawCircle(ok, 12.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
            // margin brackets in the phone corners
            val m = 18.dp.toPx(); val s = 3.dp.toPx()
            fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(primary, Offset(x, y), Offset(x + dx * m, y), s, StrokeCap.Round)
                drawLine(primary, Offset(x, y), Offset(x, y + dy * m), s, StrokeCap.Round)
            }
            bracket(w * 0.09f, h * 0.1f, 1f, 1f); bracket(w * 0.91f, h * 0.1f, -1f, 1f)
            bracket(w * 0.09f, h * 0.9f, 1f, -1f); bracket(w * 0.91f, h * 0.9f, -1f, -1f)
        }
    }
}

/** A tilt gauge: a bubble that should sit in the green centre. */
@Composable
fun LevelDiagram(modifier: Modifier = Modifier) {
    val ok = MaterialTheme.appColors.success
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().aspectRatio(5f)) {
        val w = size.width
        val h = size.height
        val y = h / 2
        drawRoundRect(ink.copy(alpha = 0.18f), Offset(w * 0.06f, y - 14.dp.toPx()), Size(w * 0.88f, 28.dp.toPx()), CornerRadius(14.dp.toPx()))
        drawRoundRect(ok.copy(alpha = 0.28f), Offset(w * 0.5f - 26.dp.toPx(), y - 14.dp.toPx()), Size(52.dp.toPx(), 28.dp.toPx()), CornerRadius(14.dp.toPx()))
        drawLine(ink, Offset(w * 0.5f - 26.dp.toPx(), y - 14.dp.toPx()), Offset(w * 0.5f - 26.dp.toPx(), y + 14.dp.toPx()), 2.dp.toPx())
        drawLine(ink, Offset(w * 0.5f + 26.dp.toPx(), y - 14.dp.toPx()), Offset(w * 0.5f + 26.dp.toPx(), y + 14.dp.toPx()), 2.dp.toPx())
        drawCircle(ok, 11.dp.toPx(), Offset(w * 0.5f, y))
        drawCircle(Color.White.copy(alpha = 0.5f), 4.dp.toPx(), Offset(w * 0.5f - 3.dp.toPx(), y - 3.dp.toPx()))
    }
}
