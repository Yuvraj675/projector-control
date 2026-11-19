package dev.keystone.auto.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.core.Offsets
import dev.keystone.auto.data.Feedback
import dev.keystone.auto.ui.*
import dev.keystone.auto.ui.theme.appColors

@Composable
fun WorkingScreen(vm: AppViewModel) {
    val work by vm.work.collectAsState()
    ScreenScaffold(title = "Fixing the keystone", step = 2, scroll = false) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScanRadar(size = 190.dp)
            Spacer(Modifier.height(24.dp))
            Text(work.message.ifBlank { "Working…" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            SmoothProgress(work.fraction, Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            Hint("Keep the projector and the wall as they are.", center = true)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun VerifyScreen(vm: AppViewModel) {
    val v by vm.verify.collectAsState()
    val state = v ?: return
    val quality = when {
        state.combined.spread < 6 -> "Excellent" to true
        state.combined.spread < 14 -> "Good" to true
        else -> "Photos varied a lot" to false
    }
    ScreenScaffold(
        title = "Does it look right?", step = 3,
        bottomBar = {
            PrimaryButton("Looks correct", { vm.looksCorrect() })
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Not quite", { vm.notRight() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextAction("Undo", { vm.undo() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextAction("Peek at normal screen", { vm.peekHome() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextAction("Show pattern", { vm.showPatternAgain() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        Text("The new keystone is on the projector.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Hint("Look at the wall. The picture's border should be straight, its corners square, and the edges parallel to the wall and ceiling lines. Judge it from where you normally sit.")
        Spacer(Modifier.height(16.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Attempt ${state.round}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                StatusPill(quality.first, quality.second)
            }
            DividerLine()
            KeyValue("Picture area in use", "${(state.applied.panelUse() * 100).toInt()}% of the panel")
            KeyValue("Target shape", "%.2f : 1".format(state.params.aspect))
            KeyValue("Photos used", "${state.combined.used.size} of ${state.combined.used.size + state.combined.dropped.size}")
        }
        if (state.round >= 3) {
            Spacer(Modifier.height(12.dp))
            Banner("Still not right? Retaking the photos from a steady spot can help more than more adjustments.", BannerKind.Warning)
        }
    }
}

@Composable
fun FeedbackScreen(vm: AppViewModel) {
    ScreenScaffold(
        title = "What looks off?", step = 3, onBack = { vm.back() },
        bottomBar = { SecondaryButton("Retake the photos", { vm.retakePhotos() }) },
    ) {
        Hint("Pick the closest match. The app adjusts, applies it and asks again. If it overshoots, pick the opposite option and it takes a smaller step.")
        Spacer(Modifier.height(16.dp))
        Feedback.entries.forEach { fb ->
            AppCard(Modifier.padding(bottom = 10.dp).clickable { vm.reject(fb) }) {
                Text(fb.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Hint(
                    when (fb) {
                        Feedback.RIGHT_EDGE_SHORT -> "The right side of the picture is shorter than the left."
                        Feedback.LEFT_EDGE_SHORT -> "The left side of the picture is shorter than the right."
                        Feedback.RIGHT_SIDE_HIGH -> "The top edge rises from left to right."
                        Feedback.LEFT_SIDE_HIGH -> "The top edge falls from left to right."
                        Feedback.TOO_WIDE -> "The picture could be taller: it looks squashed."
                        Feedback.TOO_TALL -> "The picture looks stretched upwards."
                    },
                )
            }
        }
    }
}

@Composable
fun ResultPhotoScreen(vm: AppViewModel) {
    ScreenScaffold(
        title = "One last photo?", step = 3, onBack = { vm.back() },
        bottomBar = {
            PrimaryButton("Take a photo of the result", { vm.startResultPhoto() })
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Skip", { vm.skipResultPhoto() })
        },
    ) {
        Text("Great, it looks right.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Hint("Optional: a photo of the corrected picture shows the app exactly what \"right\" looks like for your room. It uses that to get closer the next time. Stand in the same spot as before.")
        Spacer(Modifier.height(16.dp))
        FramingDiagram()
    }
}

@Composable
private fun CheckBadge() {
    val ok = MaterialTheme.appColors.success
    Canvas(Modifier.size(96.dp)) {
        drawCircle(ok.copy(alpha = 0.18f))
        drawCircle(ok, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
        val p = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.53f); lineTo(size.width * 0.45f, size.height * 0.69f); lineTo(size.width * 0.74f, size.height * 0.35f)
        }
        drawPath(p, ok, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun DoneScreen(vm: AppViewModel) {
    val d by vm.done.collectAsState()
    val done = d ?: return
    ScreenScaffold(
        title = "All done", step = 3,
        bottomBar = {
            PrimaryButton("Finish and go back to the projector's home screen", { vm.goHome() })
            TextAction("Undo and restore the previous keystone", { vm.undo() }, Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CheckBadge()
            Spacer(Modifier.height(14.dp))
            Text("Keystone fixed", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Hint("The projector keeps this setting. Rerun the app any time the projector is moved.", center = true)
        }
        Spacer(Modifier.height(20.dp))
        AppCard {
            Text("What changed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            DividerLine()
            CornerTable(done.before, done.after)
            DividerLine()
            KeyValue("Picture area in use", "${(done.before.panelUse() * 100).toInt()}% → ${(done.after.panelUse() * 100).toInt()}% of the panel")
        }
        Spacer(Modifier.height(14.dp))
        AppCard {
            Text("What the app learned", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            DividerLine()
            KeyValue("Preferred shape", "%.2f : 1".format(done.learned.aspect))
            KeyValue("Your viewing angle", "%+.1f°".format(done.learned.yawDeg))
            KeyValue("Level correction", "%+.1f°".format(done.learned.rollBiasDeg))
            KeyValue("Accepted results so far", "${done.acceptedTotal}")
            Spacer(Modifier.height(6.dp))
            Hint(if (done.resultPhotoUsed) "Your result photo was used, so this was learned from a direct measurement." else "Learned from your photos and your approval. A result photo next time makes it more precise.")
        }
    }
}

/** The eight corner values before and after, in a compact two-row-per-corner table. */
@Composable
private fun CornerTable(before: Offsets, after: Offsets) {
    val names = listOf("Top-left" to (0 to 1), "Top-right" to (2 to 3), "Bottom-left" to (4 to 5), "Bottom-right" to (6 to 7))
    val b = before.toMap().values.toList()
    val a = after.toMap().values.toList()
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Corner", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Before (x, y)", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("After (x, y)", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    names.forEach { (name, idx) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(name, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("${b[idx.first]}, ${b[idx.second]}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${a[idx.first]}, ${a[idx.second]}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
