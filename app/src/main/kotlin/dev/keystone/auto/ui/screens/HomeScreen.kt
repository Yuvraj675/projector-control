package dev.keystone.auto.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.AppsState
import dev.keystone.auto.ConnectState
import dev.keystone.auto.Screen
import dev.keystone.auto.ui.*
import dev.keystone.auto.ui.theme.appColors

/** Opens the system file picker; whatever the user chooses is installed on the projector. */
@Composable
fun rememberApkPicker(vm: AppViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.installFrom(uri) }
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    val connect by vm.connect.collectAsState()
    val info = (connect as? ConnectState.Connected)?.info
    val pickApk = rememberApkPicker(vm)
    val sendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.sendFile(uri, null) }
    val pickFile = remember(sendLauncher) { { sendLauncher.launch(arrayOf("*/*")) } }
    val limited = info != null && !info.supported      // not a Hisilicon projector: no keystone

    AppBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 104.dp)) {
            Row(Modifier.fillMaxWidth().stagger(0), verticalAlignment = Alignment.CenterVertically) {
                val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                Text(
                    when { hour < 5 -> "Late night viewing"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; hour < 22 -> "Movie night?"; else -> "Late night viewing" },
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                HeaderIcon(Ico.Sliders, "Settings") { vm.go(Screen.Settings) }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().stagger(1).clip(RoundedCornerShape(24.dp)).pressable({ vm.go(Screen.Device) }).padding(vertical = 4.dp)) {
                Text(info?.displayName ?: "Projector", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill("Connected", true)
                    Spacer(Modifier.width(10.dp))
                    Text(info?.host.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(26.dp))

            val apps by vm.apps.collectAsState()
            val pinned by vm.pinned.collectAsState()
            val icons by vm.icons.collectAsState()
            val quick = (apps as? AppsState.Loaded)?.apps?.filter { it.packageName in pinned }.orEmpty()
            if (quick.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().stagger(2).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    quick.forEach { app -> Box(Modifier.width(84.dp)) { AppTile(app, { vm.launchApp(app) }, { vm.togglePin(app) }, icons[app.packageName]) } }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (!limited) {
                val primary = MaterialTheme.colorScheme.primary
                Row(
                    Modifier.fillMaxWidth().stagger(3).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                        .pressable({ vm.go(Screen.Guide) }).padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto keystone", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text("Straighten a skewed picture using your phone's camera", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                    Spacer(Modifier.width(16.dp))
                    Icon24(Ico.Keystone, primary, size = 40.dp, strokeWidth = 1.4f)
                }
                Spacer(Modifier.height(14.dp))
            }

            Column(Modifier.fillMaxWidth().stagger(4).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 8.dp)) {
                ListRow(Ico.Projector, "Projector screen", "See it on your phone and touch it") { vm.go(Screen.Mirror) }
                ListRow(Ico.Send, "Send a file", "Copy it to the projector and play it", pickFile)
                ListRow(Ico.Install, "Install an app", "Send one from this phone, or pick an APK") { vm.go(Screen.Install) }
                ListRow(Ico.Chip, "Projector", "Memory, power and settings") { vm.go(Screen.Device) }
            }

        }
    }
}
