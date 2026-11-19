package dev.keystone.auto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var confirmReboot by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshDevice() }

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

        Spacer(Modifier.height(20.dp))
        SectionTitle("Keystone")
        AppCard {
            when (device.firmwareAutoKeystone) {
                true -> {
                    Banner("The projector's own Auto keystone is on. It stops the app from changing the keystone.", BannerKind.Warning)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Switch it off", { vm.switchFirmwareAutoOff() })
                }
                false -> Row { StatusPill("Auto keystone off", true); Spacer(Modifier.width(10.dp)); Hint("The app can adjust the picture.") }
                null -> Hint("Checking…")
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
            SecondaryButton("Free up memory", { vm.freeMemory() })
            Hint("Stops background apps. The projector only has 1 GB of memory, so this can make everything feel faster.", Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Restart the projector", { confirmReboot = true })
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
