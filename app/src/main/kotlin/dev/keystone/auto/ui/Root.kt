package dev.keystone.auto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.Screen
import dev.keystone.auto.link.LinkState
import dev.keystone.auto.ui.screens.*

@Composable
fun AppRoot(vm: AppViewModel, finish: () -> Unit) {
    val screen by vm.screen.collectAsState()
    val busy by vm.busy.collectAsState()

    BackHandler { if (!vm.back()) finish() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Connect -> ConnectScreen(vm)
                Screen.Home -> HomeScreen(vm)
                Screen.Remote -> RemoteScreen(vm)
                Screen.Keyboard -> KeyboardScreen(vm)
                Screen.Apps -> AppsScreen(vm)
                Screen.Install -> InstallScreen(vm)
                Screen.Mirror -> MirrorScreen(vm)
                Screen.Device -> DeviceScreen(vm)
                Screen.Guide -> GuideScreen(vm)
                Screen.Capture -> CaptureScreen(vm)
                Screen.Working -> WorkingScreen(vm)
                Screen.Verify -> VerifyScreen(vm)
                Screen.Feedback -> FeedbackScreen(vm)
                Screen.ResultPhoto -> ResultPhotoScreen(vm)
                Screen.Done -> DoneScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                Screen.History -> HistoryScreen(vm)
                Screen.Log -> LogScreen(vm)
            }
        }
        busy?.let { BusyOverlay(it) }
        val link by vm.link.collectAsState()
        if (link == LinkState.Reconnecting && screen != Screen.Connect) {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                StatusPill("Reconnecting to the projector…", false)
            }
        }
        ToastHost(vm)
    }
}

@Composable
fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = true) { }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
