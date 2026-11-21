package dev.keystone.auto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.ConnectState
import dev.keystone.auto.Screen
import dev.keystone.auto.ui.*

@Composable
fun ConnectScreen(vm: AppViewModel) {
    val state by vm.connect.collectAsState()
    val notice by vm.notice.collectAsState()
    var manual by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "ProjectorConnect",
        actions = {
            IconButton(onClick = { vm.go(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        },
    ) {
        ProjectorHero(Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Text("Your phone is now your projector's remote", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Hint("Control it like a mouse, type and speak, install apps and straighten the picture. All from your phone, no computer needed.")
        Spacer(Modifier.height(20.dp))

        notice?.let {
            Banner(it, BannerKind.Info)
            TextAction("Dismiss", { vm.notice.value = null })
            Spacer(Modifier.height(8.dp))
        }

        when (val st = state) {
            ConnectState.Idle -> {
                PrimaryButton("Find my projector", { vm.findProjector() })
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Enter IP address", { manual = true })
                Spacer(Modifier.height(16.dp))
                Hint("Your phone and projector must be on the same Wi-Fi, and network debugging (ADB) must be on in the projector.")
                AdbHelp()
            }

            is ConnectState.Scanning -> AppCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScanRadar(size = 170.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("Looking for your projector…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    SmoothProgress(if (st.total > 0) st.done.toFloat() / st.total else 0f)
                    Spacer(Modifier.height(8.dp))
                    Hint("Checked ${st.done} of ${st.total} addresses", center = true)
                    TextAction("Cancel", { vm.cancelScan() })
                }
            }

            is ConnectState.Connecting -> AppCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScanRadar(size = 150.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(st.step, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Hint(st.host, center = true)
                }
            }

            is ConnectState.Choose -> {
                SectionTitle("Pick your projector")
                st.devices.forEach { d ->
                    AppCard(Modifier.padding(bottom = 10.dp).clickable { vm.connectToChoice(d.host) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Hint(d.host)
                            }
                            StatusPill(if (d.supported) "Full support" else "Remote only", d.supported)
                        }
                        if (!d.supported && d.note != null) {
                            Spacer(Modifier.height(8.dp))
                            Hint(d.note)
                        }
                    }
                }
                TextAction("Scan again", { vm.findProjector() })
            }

            is ConnectState.Connected -> AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(st.info.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Hint("Android ${st.info.androidRelease}, ${st.info.host}")
                    }
                    StatusPill("Connected", true)
                }
                DividerLine()
                KeyValue("Picture area in use", "${(st.keystone.panelUse() * 100).toInt()}% of the panel")
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Open", { vm.go(Screen.Home) })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextAction("Scan again", { vm.findProjector() })
                    TextAction("Disconnect", { vm.disconnect() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is ConnectState.Failed -> {
                Banner(st.message, BannerKind.Error)
                st.hint?.let { Spacer(Modifier.height(10.dp)); Hint(it) }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", { vm.findProjector() })
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Enter IP address", { manual = true })
                AdbHelp()
            }
        }
    }

    if (manual) ManualIpDialog(onDismiss = { manual = false }, onConnect = { manual = false; vm.connectManually(it) })
}

/** A collapsible guide for the step where most first-time users get stuck. */
@Composable
private fun AdbHelp() {
    var open by remember { mutableStateOf(false) }
    TextAction(if (open) "Hide: how to turn on network debugging" else "How do I turn on network debugging?", { open = !open })
    if (open) AppCard {
        listOf(
            "1. On the projector open Settings → About (or System) and find the Build number.",
            "2. Press OK on it 7 times until it says you are a developer.",
            "3. Go back to Settings → Developer options.",
            "4. Switch on USB debugging, and ADB over network / Wireless debugging if you see it.",
            "5. Make sure the projector is on the same Wi-Fi as this phone, then tap Find my projector.",
            "6. If the projector asks \"Allow debugging?\", choose Always allow.",
        ).forEach { Hint(it, Modifier.padding(vertical = 3.dp)) }
        Spacer(Modifier.height(6.dp))
        Hint("Some projectors hide Developer options. A settings launcher app (for example \"Project Settings\") can open them.")
    }
}

@Composable
private fun ManualIpDialog(onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projector address") },
        text = {
            Column {
                Hint("Enter the projector's IP address (shown in its Wi-Fi settings).")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("e.g. 192.168.1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConnect(text) }, enabled = text.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
