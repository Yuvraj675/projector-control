package dev.keystone.auto.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.keystone.auto.CaptureItem
import dev.keystone.auto.ui.screens.CaptureOverlay
import dev.keystone.auto.ui.theme.ProjectorConnectTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The capture overlay in landscape, over a plain stand-in for the camera preview. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
class CaptureSnapshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @Composable () -> Unit) {
        rule.setContent { ProjectorConnectTheme(true) { Box(Modifier.fillMaxSize().background(Color(0xFF2A2F3A))) { content() } } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val w = view.width.takeIf { it > 0 } ?: 2673
        val h = view.height.takeIf { it > 0 } ?: 1233
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("build/ui-snapshots/$name.png").also { it.parentFile?.mkdirs() }.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun item(id: Int, ok: Boolean) = CaptureItem(id, null, null, 0.0, 0.0, if (ok) null else "Couldn't find all four circles.", null)

    private fun overlay(markers: Boolean, level: Boolean, steady: Boolean, instruction: String, items: List<CaptureItem> = emptyList(),
                        notice: String? = null, processing: Boolean = false, result: Boolean = false) = @Composable {
        CaptureOverlay(
            title = if (result) "Photo of the result" else "Photo ${items.size + 1} of 4", markersOk = markers, levelOk = level, steady = steady,
            elev = if (level) -0.4 else 4.2, roll = if (level) 0.6 else -3.1, instruction = instruction, cameraError = null, notice = notice,
            items = items, autoCapture = true, resultMode = result, processing = processing, shutterEnabled = !processing,
            onBack = {}, onShowPattern = {}, onSkipResult = {}, onToggleAuto = {}, onShutter = {}, onRemove = {},
        )
    }

    @Test fun searching() = snap("20_capture_searching", overlay(false, false, false, "Aim at the picture: all four white circles in view, with a little margin"))

    @Test fun tilted() = snap("21_capture_tilted", overlay(true, false, true, "Level the phone: tilt +4.2° up/down, roll -3.1°"))

    @Test fun ready() = snap("22_capture_ready", overlay(true, true, true, "Perfect. Taking the photo…", items = listOf(item(1, true), item(2, true))))

    @Test fun problem() = snap("23_capture_problem", overlay(true, true, false, "Hold still…", items = listOf(item(1, true), item(2, false))))

    @Test fun resultMode() = snap("24_capture_result", overlay(true, true, true, "Perfect. Taking the photo…", result = true))
}
