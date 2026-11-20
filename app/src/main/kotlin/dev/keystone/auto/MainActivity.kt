package dev.keystone.auto

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.keystone.auto.link.RemoteKey
import dev.keystone.auto.ui.AppRoot
import dev.keystone.auto.ui.theme.ProjectorConnectTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            }
            ProjectorConnectTheme(dark) { AppRoot(vm, finish = { finish() }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** On the remote screens the phone's volume buttons control the projector's volume. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = vm.screen.value
        if (s == Screen.Remote || s == Screen.Keyboard) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { vm.press(RemoteKey.VOLUME_UP); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { vm.press(RemoteKey.VOLUME_DOWN); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A link shared from another app opens on the projector. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        if (type.startsWith("video/") || type.startsWith("audio/") || type.startsWith("image/")) {
            @Suppress("DEPRECATION")
            val uri = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            uri?.let { vm.sendFile(it, type) }
            return
        }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.openLink(it) }
    }
}
