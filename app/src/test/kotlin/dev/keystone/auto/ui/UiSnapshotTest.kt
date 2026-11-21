package dev.keystone.auto.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.keystone.auto.*
import dev.keystone.auto.core.*
import dev.keystone.auto.data.HistoryEntry
import dev.keystone.auto.link.ProjectorInfo
import dev.keystone.auto.platform.AppLog
import dev.keystone.auto.ui.screens.*
import dev.keystone.auto.ui.theme.ProjectorConnectTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen that does not need the camera and writes PNGs to build/ui-snapshots, so the UI can be looked at
 * without a phone. Also catches crashes in composables (missing resources, bad state handling).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class UiSnapshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun vm() = AppViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun snap(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        rule.setContent { ProjectorConnectTheme(dark) { content() } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        // draw the real window content into a bitmap (captureToImage waits for a hardware draw that Robolectric never runs)
        val view = rule.activity.window.decorView
        val w = view.width.takeIf { it > 0 } ?: 1233
        val h = view.height.takeIf { it > 0 } ?: 2673
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val f = File("build/ui-snapshots/$name.png").also { it.parentFile?.mkdirs() }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SNAP ${f.path} ${bmp.width}x${bmp.height}")
    }

    private val info = ProjectorInfo("192.0.2.13", "HiDPTAndroid Hi3751V350", "Hisilicon", "12", 31, true, null)
    private val ks = Offsets(103.0, 36.0, 107.0, 7.0, 2.0, 56.0, 13.0, 74.0)
    private val before = Offsets(115.0, 31.0, 105.0, 5.0, 11.0, 13.0, 0.0, 8.0)

    @Test fun connectIdle() = snap("01_connect_idle") { ConnectScreen(vm()) }

    @Test fun connectScanning() { val v = vm(); v.connect.value = ConnectState.Scanning(120, 253); snap("02_connect_scanning") { ConnectScreen(v) } }

    @Test fun connectChoose() {
        val v = vm()
        v.connect.value = ConnectState.Choose(listOf(
            DeviceChoice("192.0.2.13", "Hisilicon HiDPTAndroid Hi3751V350", true, null),
            DeviceChoice("192.0.2.88", "192.0.2.88", false, "Could not talk to this device. If it asks to allow debugging, accept on its screen and scan again."),
        ))
        snap("03_connect_choose") { ConnectScreen(v) }
    }

    @Test fun connectConnected() { val v = vm(); v.connect.value = ConnectState.Connected(info, ks); snap("04_connect_connected") { ConnectScreen(v) } }

    @Test fun connectFailed() {
        val v = vm()
        v.connect.value = ConnectState.Failed("No projector found on this Wi-Fi.", "Switch the projector on, make sure it is on the same Wi-Fi as this phone and that network debugging (ADB) is enabled, then scan again.")
        snap("05_connect_failed") { ConnectScreen(v) }
    }

    @Test fun connectConnectedLight() { val v = vm(); v.connect.value = ConnectState.Connected(info, ks); snap("06_connect_connected_light", dark = false) { ConnectScreen(v) } }

    @Test fun guide() = snap("07_guide_1") { GuideScreen(vm()) }

    @Test fun guidePage2() = snap("07_guide_2") { GuideScreen(vm(), initialPage = 1) }

    @Test fun guidePage3() = snap("07_guide_3") { GuideScreen(vm(), initialPage = 2) }

    @Test fun working() { val v = vm(); v.work.value = Work("Applying to the projector…", 0.7f); snap("08_working") { WorkingScreen(v) } }

    private fun verifyState(round: Int) = VerifyState(
        before, ks, Combined(ks, listOf(0, 1, 2, 3), emptyList(), 5.0), Params(), round,
    )

    @Test fun verify() { val v = vm(); v.verify.value = verifyState(1); snap("09_verify") { VerifyScreen(v) } }

    @Test fun verifyThirdAttempt() { val v = vm(); v.verify.value = verifyState(3); snap("10_verify_round3") { VerifyScreen(v) } }

    @Test fun feedback() = snap("11_feedback") { FeedbackScreen(vm()) }

    @Test fun resultPhoto() = snap("12_result_photo") { ResultPhotoScreen(vm()) }

    @Test fun done() {
        val v = vm()
        v.done.value = DoneState(before, ks, Params(1.70, 6.0, -0.5), true, 3)
        snap("13_done") { DoneScreen(v) }
    }

    @Test fun settings() { val v = vm(); v.connect.value = ConnectState.Connected(info, ks); snap("14_settings") { SettingsScreen(v) } }

    @Test fun history() {
        val dir = ApplicationProvider.getApplicationContext<Application>().filesDir
        dev.keystone.auto.platform.Store(ApplicationProvider.getApplicationContext<Application>()).apply {
            clearHistory()
            addHistory(HistoryEntry(System.currentTimeMillis() - 3_600_000, "p", before, ks, Params(), 4, 5.0, "rejected:RIGHT_EDGE_SHORT"))
            addHistory(HistoryEntry(System.currentTimeMillis(), "p", before, ks, Params(1.70, 2.0, 0.0), 4, 4.2, "accepted", "with result photo"))
        }
        snap("15_history") { HistoryScreen(vm()) }
    }

    @Test fun log() {
        AppLog.clear(); AppLog.d("scan: network=LocalNet(ip=192.0.2.50, prefixLength=24)"); AppLog.d("scan: 253 hosts, ADB open on [192.0.2.13]"); AppLog.d("connected: Hisilicon HiDPTAndroid Hi3751V350")
        snap("16_log") { LogScreen(vm()) }
    }

    // ---- ProjectorConnect: home, remote, keyboard, apps, install, projector --------------------------------------
    private fun connectedVm(): AppViewModel = vm().also { it.connect.value = ConnectState.Connected(info, ks) }

    @Test fun home() = snap("20_home") { HomeScreen(connectedVm()) }

    @Test fun homeLight() = snap("20_home_light", dark = false) { HomeScreen(connectedVm()) }

    @Test fun remoteTouchpad() { val v = connectedVm(); v.remoteState.value = RemoteState.Ready; snap("21_remote_touchpad") { RemoteScreen(v, 0, autoStart = false) } }

    @Test fun remoteAir() { val v = connectedVm(); v.remoteState.value = RemoteState.Ready; snap("22_remote_air") { RemoteScreen(v, 1, autoStart = false) } }

    @Test fun remoteDpad() { val v = connectedVm(); v.remoteState.value = RemoteState.Ready; snap("23_remote_dpad") { RemoteScreen(v, 2, autoStart = false) } }

    @Test fun remoteConnecting() { val v = connectedVm(); v.remoteState.value = RemoteState.Starting; snap("24_remote_connecting") { RemoteScreen(v, 0, autoStart = false) } }

    @Test fun remoteFailed() { val v = connectedVm(); v.remoteState.value = RemoteState.Failed("The connection to the projector dropped."); snap("25_remote_failed") { RemoteScreen(v, 0, autoStart = false) } }

    @Test fun keyboard() { val v = connectedVm(); v.remoteState.value = RemoteState.Ready; snap("26_keyboard") { KeyboardScreen(v, autoStart = false) } }

    @Test fun apps() {
        val v = connectedVm()
        fun app(p: String, l: String, third: Boolean) = dev.keystone.auto.link.ProjectorApp(p, "$p/.Main", l, third, true)
        v.apps.value = AppsState.Loaded(listOf(
            app("com.google.android.youtube.tv", "YouTube", true), app("in.startv.hotstar", "JioHotstar", true), app("com.stremio.one", "Stremio", true),
            app("org.videolan.vlc", "VLC", true), app("org.localsend.localsend_app", "LocalSend", true), app("com.playfy.tv", "Playfy", true),
            app("com.android.vending", "Play Store", false), app("com.newlink.filemanager", "Files", false), app("com.newlink.cast", "Cast", false),
        ))
        snap("27_apps") { AppsScreen(v) }
    }

    @Test fun installWorking() { val v = connectedVm(); v.install.value = InstallState.Working("JioHotstar_26.10.apkm", "Sending part 2…"); snap("28_install_working") { InstallScreen(v) } }

    @Test fun installDone() { val v = connectedVm(); v.install.value = InstallState.Done("VLC-3.6.apk", true, "Installed."); snap("29_install_done") { InstallScreen(v) } }

    @Test fun installFailed() {
        val v = connectedVm()
        v.install.value = InstallState.Done("netflix-arm64.apk", false, "This app isn't built for the projector's 32-bit processor. Look for an armeabi-v7a (arm32) version.")
        snap("30_install_failed") { InstallScreen(v) }
    }

    @Test fun device() {
        val v = connectedVm()
        v.device.value = DeviceState(dev.keystone.auto.link.ProjectorStatus(972, 219, 3463, 2372, "HiDPTAndroid Hi3751V350", "12", "armeabi-v7a"), true)
        snap("31_device") { DeviceScreen(v) }
    }
}
