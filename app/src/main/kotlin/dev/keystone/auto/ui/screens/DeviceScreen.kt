package dev.keystone.auto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.ConnectState
import dev.keystone.auto.link.ProjectorStatus
import dev.keystone.auto.ui.*

@Composable
fun DeviceScreen(vm: AppViewModel) {
    val device by vm.device.collectAsState()
    val connect by vm.connect.collectAsState()
    val info = (connect as? ConnectState.Connected)?.info
    val saved by vm.saved.collectAsState()
    var confirmReboot by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshDevice(); vm.refreshSleepTimer() }

    ScreenScaffold(
        title = "Projector", onBack = { vm.back() },
        actions = { HeaderIcon(Ico.Refresh, "Refresh") { vm.refreshDevice() } },
    ) {
        AppCard {
            Text(info?.displayName ?: "Projector", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Hint(info?.host ?: "")
            DividerLine()
            val st = device.status
            KeyValue("Android", st?.androidRelease?.takeIf { it.isNotBlank() } ?: info?.androidRelease ?: "—")
            KeyValue("Processor", st?.abi?.takeIf { it.isNotBlank() } ?: "—")
            if (st != null) { Spacer(Modifier.height(10.dp)); Meters(st) } else if (device.loading) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        if (info?.supported != false) {
        Spacer(Modifier.height(20.dp))
        SectionTitle("Keystone")
        AppCard {
            when (device.firmwareAutoKeystone) {
                true -> {
                    Banner("The projector's own Auto keystone is on. It stops the app from changing the keystone. Turn it off in the projector's own keystone settings.", BannerKind.Warning)
                }
                false -> Row { StatusPill("Auto keystone off", true); Spacer(Modifier.width(10.dp)); Hint("The app can adjust the picture.") }
                null -> Hint("Checking…")
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Saved keystones")
        AppCard {
            if (saved.isEmpty()) Hint("Moved the projector? Save the picture setup for each place (bedroom ceiling, living room table...) and switch with one tap.")
            saved.forEachIndexed { i, k ->
                if (i > 0) DividerLine()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(k.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { vm.applySavedKeystone(k) }) { Text("Apply") }
                    TextButton(onClick = { vm.deleteSavedKeystone(k) }) { Text("Delete") }
                }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Save the current keystone", { saveName = ""; saving = true })
        }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Power and memory")
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Screen off", { vm.power(AppViewModel.Power.Sleep) }, Modifier.weight(1f))
                SecondaryButton("Wake", { vm.power(AppViewModel.Power.Wake) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            val left by vm.sleepLeft.collectAsState()
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Hint(left?.let { "Screen turns off in ${(it + 59) / 60} min." } ?: "Turn the screen off by itself, even if you close the app.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { m -> SecondaryButton("$m min", { vm.setSleepTimer(m) }, Modifier.weight(1f)) }
            }
            if (left != null) { Spacer(Modifier.height(8.dp)); SecondaryButton("Cancel timer", { vm.setSleepTimer(null) }) }
            Spacer(Modifier.height(14.dp))
            SecondaryButton("Free up memory", { vm.freeMemory() })
            Hint("Stops background apps. The projector only has 1 GB of memory, so this can make everything feel faster.", Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Restart the projector", { confirmReboot = true })
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Help")
        AppCard {
            SecondaryButton("See the projector's screen", { vm.takeScreenshot() })
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Copy a diagnostics report", { vm.copyDiagnostics() })
            Hint("Connection speed, memory, temperature and settings in one block of text. It never includes the projector's address.", Modifier.padding(top = 6.dp))
        }

        if (device.customLauncher) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Recovery")
            AppCard {
                SecondaryButton("Restore the original home screen", { confirmRestore = true })
                Hint("Use this if the Home button misbehaves. It switches Home back to the projector's own launcher. Nothing is uninstalled.", Modifier.padding(top = 6.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Open on the projector")
        AppCard {
            ListRow(Ico.Wifi, "Wi-Fi") { vm.openProjectorSettings("android.settings.WIFI_SETTINGS") }
            DividerLine()
            ListRow(Ico.Sliders, "Display") { vm.openProjectorSettings("android.settings.DISPLAY_SETTINGS") }
            DividerLine()
            ListRow(Ico.Globe, "All settings") { vm.openProjectorSettings("android.settings.SETTINGS") }
        }
        Spacer(Modifier.height(8.dp))
    }

    val shot by vm.shot.collectAsState()
    shot?.let { img ->
        AlertDialog(
            onDismissRequest = { vm.shot.value = null },
            title = { Text("On the projector now") },
            text = { Image(img, "Screenshot of the projector", Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.takeScreenshot() }) { Text("Refresh") } },
            dismissButton = { TextButton(onClick = { vm.shot.value = null }) { Text("Close") } },
        )
    }

    if (saving) AlertDialog(
        onDismissRequest = { saving = false },
        title = { Text("Save this keystone") },
        text = { OutlinedTextField(saveName, { saveName = it }, singleLine = true, placeholder = { Text("e.g. Bedroom ceiling") }) },
        confirmButton = { TextButton(onClick = { saving = false; vm.saveCurrentKeystone(saveName) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
    )

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("Restore the original home screen?") },
        text = { Text("The Home button will open the projector's own launcher again, and the custom launcher's Home-key helper is switched off. You can undo this by reinstalling the launcher.") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restoreStockLauncher() }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
    )

    if (confirmReboot) AlertDialog(
        onDismissRequest = { confirmReboot = false },
        title = { Text("Restart the projector?") },
        text = { Text("It will switch off and start again. You'll need to reconnect afterwards.") },
        confirmButton = { TextButton(onClick = { confirmReboot = false; vm.power(AppViewModel.Power.Reboot) }) { Text("Restart") } },
        dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Meters(st: ProjectorStatus) {
    Meter("Memory", st.ramFreeMb, st.ramTotalMb)
    Meter("Storage", st.storageFreeMb, st.storageTotalMb)
}

@Composable
private fun Meter(label: String, free: Int, total: Int) {
    run {
        val used = if (total > 0) 1f - free.toFloat() / total else 0f
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${fmt(free)} free of ${fmt(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(6.dp))
            SmoothProgress(used)
        }
    }
}

private fun fmt(mb: Int) = if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"
