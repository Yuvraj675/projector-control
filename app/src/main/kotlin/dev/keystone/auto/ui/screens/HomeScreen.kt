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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(Modifier.weight(1f))
                HeaderIcon(Ico.Sliders, "Settings") { vm.go(Screen.Settings) }
            }
            Spacer(Modifier.height(14.dp))

            DeviceCard(name = info?.displayName ?: "Projector", detail = info?.host ?: "", onClick = { vm.go(Screen.Device) })
            Spacer(Modifier.height(14.dp))

            val apps by vm.apps.collectAsState()
            val pinned by vm.pinned.collectAsState()
            val quick = (apps as? AppsState.Loaded)?.apps?.filter { it.packageName in pinned }.orEmpty()
            if (quick.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { app -> Box(Modifier.width(84.dp)) { AppTile(app, { vm.launchApp(app) }, { vm.togglePin(app) }) } }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(Modifier.fillMaxWidth().height(212.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Remote", "Touchpad, air mouse, D-pad", Ico.Pointer, { vm.go(Screen.Remote) }, Modifier.weight(1.2f).fillMaxHeight(), accent = true, big = true)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Type & speak", "", Ico.Keyboard, { vm.go(Screen.Keyboard) }, Modifier.weight(1f).fillMaxWidth())
                    Tile("Apps", "", Ico.Apps, { vm.loadApps(); vm.go(Screen.Apps) }, Modifier.weight(1f).fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Send file", "Copy it and play it", Ico.Send, pickFile, Modifier.weight(1f).fillMaxHeight())
                Tile("Projector screen", "See it and touch it", Ico.Projector, { vm.go(Screen.Mirror) }, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Install app", "From an APK file", Ico.Install, pickApk, Modifier.weight(1f).fillMaxHeight())
                if (limited) Tile("Projector", "Memory, power, files", Ico.Projector, { vm.go(Screen.Device) }, Modifier.weight(1f).fillMaxHeight())
                else Tile("Auto keystone", "Straighten the picture", Ico.Keystone, { vm.go(Screen.Guide) }, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(12.dp))
            if (!limited) Tile("Projector", "Memory, power and settings", Ico.Projector, { vm.go(Screen.Device) }, Modifier.fillMaxWidth().height(84.dp), compact = true)

            Spacer(Modifier.height(18.dp))
            Hint("Tip: share a link, video, photo or song to ProjectorConnect from any app and it opens on the projector.", center = true)
        }
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon24(Ico.Projector, MaterialTheme.colorScheme.primary, size = 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text("ProjectorConnect", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun DeviceCard(name: String, detail: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(primary.copy(alpha = 0.16f)).border(1.dp, primary.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Icon24(Ico.Projector, primary, size = 26.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.appColors.success))
                Spacer(Modifier.width(8.dp))
                Text(if (detail.isNotBlank()) "$detail" else "Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Icon24(Ico.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
    }
}
