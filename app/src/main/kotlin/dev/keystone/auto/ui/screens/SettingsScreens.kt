package dev.keystone.auto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.ConnectState
import dev.keystone.auto.Screen
import dev.keystone.auto.platform.AppLog
import dev.keystone.auto.platform.Settings
import dev.keystone.auto.ui.*
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val connect by vm.connect.collectAsState()
    val hasOriginal by vm.hasOriginal.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }

    ScreenScaffold(title = "Settings", onBack = { vm.back() }) {
        SectionTitle("Remote")
        AppCard {
            Text("Touchpad speed: ${"%.1f".format(settings.pointerSpeed)}×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(value = settings.pointerSpeed.toFloat(), onValueChange = { vm.updateSettings(settings.copy(pointerSpeed = Math.round(it * 10) / 10.0)) }, valueRange = 0.4f..2.5f)
            DividerLine()
            Text("Air mouse speed: ${"%.1f".format(settings.airSpeed)}×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(value = settings.airSpeed.toFloat(), onValueChange = { vm.updateSettings(settings.copy(airSpeed = Math.round(it * 10) / 10.0)) }, valueRange = 0.4f..2.5f)
            DividerLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Natural scrolling", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Hint("The page follows your fingers, like on a phone.")
                }
                Switch(settings.naturalScroll, { vm.updateSettings(settings.copy(naturalScroll = it)) })
            }
            DividerLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vibrate on tap", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Hint("A light tick when you press a remote key.")
                }
                Switch(settings.haptics, { vm.updateSettings(settings.copy(haptics = it)) })
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Taking photos")
        AppCard {
            Text("Photos per session: ${settings.captureCount}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Hint("More photos average out hand shake but take longer.")
            Slider(
                value = settings.captureCount.toFloat(), onValueChange = { vm.updateSettings(settings.copy(captureCount = it.toInt())) },
                valueRange = Settings.MIN_CAPTURES.toFloat()..Settings.MAX_CAPTURES.toFloat(), steps = Settings.MAX_CAPTURES - Settings.MIN_CAPTURES - 1,
            )
            DividerLine()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-capture", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Hint("Take each photo by itself when the phone is level and still.")
                }
                Switch(settings.autoCapture, { vm.updateSettings(settings.copy(autoCapture = it)) })
            }
            DividerLine()
            Text("Exposure: ${"%+.1f".format(settings.exposureStops)} stops", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Hint("Darker keeps the circles crisp. Raise it only if the camera preview looks too dark to see the circles.")
            Slider(
                value = settings.exposureStops.toFloat(), onValueChange = { vm.updateSettings(settings.copy(exposureStops = Math.round(it * 2) / 2.0)) },
                valueRange = -3f..0f, steps = 5,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("What Auto keystone has learned")
        AppCard {
            KeyValue("Preferred shape", "%.2f : 1".format(profile.params.aspect))
            KeyValue("Viewing angle", "%+.1f°".format(profile.params.yawDeg))
            KeyValue("Level correction", "%+.1f°".format(profile.params.rollBiasDeg))
            KeyValue("Accepted / adjusted", "${profile.accepted} / ${profile.rejected}")
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Reset what it learned", { confirmReset = true })
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Projector")
        AppCard {
            val connected = connect as? ConnectState.Connected
            Text(connected?.info?.displayName ?: "Not connected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            connected?.let { Hint("${it.info.host} · picture uses ${(it.keystone.panelUse() * 100).toInt()}% of the panel") }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Restore the original keystone", { vm.restoreOriginal() }, enabled = connected != null && hasOriginal)
            Hint("Puts back the keystone the projector had the first time this app connected to it.", Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Forget this projector", { vm.forgetProjector() })
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Data and diagnostics")
        AppCard {
            Text("History", Modifier.fillMaxWidth().clickable { vm.go(Screen.History) }.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            DividerLine()
            Text("Activity log", Modifier.fillMaxWidth().clickable { vm.go(Screen.Log) }.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(20.dp))
        Hint("ProjectorConnect $version. Photos are analysed on your phone and are not uploaded or kept.", center = true)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset learned preferences?") },
            text = { Text("The app forgets your preferred shape and viewing angle and starts again from the defaults. Your history is kept.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetLearning() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val entries = remember { vm.history() }
    var confirmClear by remember { mutableStateOf(false) }
    ScreenScaffold(title = "History", onBack = { vm.back() }) {
        if (entries.isEmpty()) {
            Hint("Nothing here yet. Each run is recorded after you give your verdict.")
        } else {
            entries.forEach { e ->
                AppCard(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.timeMs)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        val ok = e.verdict == "accepted"
                        StatusPill(
                            when {
                                ok -> "Accepted"
                                e.verdict.startsWith("rejected") -> "Adjusted"
                                e.verdict == "undone" -> "Undone"
                                else -> "Restored"
                            },
                            if (ok) true else if (e.verdict.startsWith("rejected")) false else null,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Hint("${e.captures} photos · shape %.2f:1 · spread ±%.0f px".format(e.params.aspect, e.spreadPx))
                    Hint("Applied ${e.applied.toMap().values.joinToString(" ")}")
                    if (e.verdict.startsWith("rejected:")) Hint(e.verdict.removePrefix("rejected:").lowercase().replace('_', ' '))
                    if (e.note.isNotBlank()) Hint(e.note)
                }
            }
            TextAction("Clear history", { confirmClear = true }, color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the history?") },
            text = { Text("This only removes the list. What the app learned is kept.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory(); vm.go(Screen.Settings) }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun LogScreen(vm: AppViewModel) {
    val lines by AppLog.lines.collectAsState()
    val clipboard = LocalClipboardManager.current
    ScreenScaffold(
        title = "Activity log", onBack = { vm.go(Screen.Settings) },
        bottomBar = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Copy", { clipboard.setText(AnnotatedString(AppLog.text())) }, Modifier.weight(1f))
                SecondaryButton("Clear", { AppLog.clear() }, Modifier.weight(1f))
            }
        },
    ) {
        Hint("A record of what the app did, to help find problems. Nothing here leaves your phone unless you copy it.")
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(
                if (lines.isEmpty()) "(empty)" else lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
