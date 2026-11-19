package dev.keystone.auto.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.AppsState
import dev.keystone.auto.InstallState
import dev.keystone.auto.Screen
import dev.keystone.auto.link.ProjectorApp
import dev.keystone.auto.ui.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(vm: AppViewModel) {
    val state by vm.apps.collectAsState()
    val pickApk = rememberApkPicker(vm)
    var menu by remember { mutableStateOf<ProjectorApp?>(null) }

    ScreenScaffold(
        title = "Apps", onBack = { vm.back() }, scroll = false,
        actions = {
            HeaderIcon(Ico.Refresh, "Refresh the list") { vm.loadApps() }
            HeaderIcon(Ico.Install, "Install an app from a file", pickApk)
        },
    ) {
        when (val s = state) {
            AppsState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            is AppsState.Failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Banner(s.message, BannerKind.Error); Spacer(Modifier.height(12.dp)); PrimaryButton("Try again", { vm.loadApps() })
            }
            is AppsState.Loaded -> {
                val mine = s.apps.filter { it.thirdParty }
                val system = s.apps.filter { !it.thirdParty }
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mine.isNotEmpty()) item(span = { GridItemSpan(3) }) { SectionTitle("Installed") }
                    items(mine, key = { it.packageName }) { AppTile(it, { vm.launchApp(it) }, { menu = it }) }
                    if (system.isNotEmpty()) item(span = { GridItemSpan(3) }) { SectionTitle("Projector") }
                    items(system, key = { it.packageName }) { AppTile(it, { vm.launchApp(it) }, { menu = it }) }
                    item(span = { GridItemSpan(3) }) { Hint("Tap to open. Press and hold for more.", center = true, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }
    }

    menu?.let { app ->
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(app.label) },
            text = { Hint(app.packageName) },
            confirmButton = { TextButton(onClick = { vm.launchApp(app); menu = null }) { Text("Open") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.stopApp(app); menu = null }) { Text("Stop") }
                    if (app.thirdParty) TextButton(onClick = { vm.uninstallApp(app); menu = null }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: ProjectorApp, onOpen: () -> Unit, onMore: () -> Unit) {
    val hue = remember(app.packageName) { (app.packageName.hashCode().toUInt() % 360u).toFloat() }
    val tone = Color.hsv(hue, 0.45f, 0.92f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onOpen, onLongClick = onMore).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(tone.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tone)
        }
        Spacer(Modifier.height(6.dp))
        Text(app.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun InstallScreen(vm: AppViewModel) {
    val state by vm.install.collectAsState()
    val pickApk = rememberApkPicker(vm)
    ScreenScaffold(title = "Install app", onBack = { vm.back() }) {
        Spacer(Modifier.height(24.dp))
        when (val s = state) {
            InstallState.Idle -> {
                Hint("Choose an app file on your phone. It is sent to the projector over Wi-Fi and installed.", center = true)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Choose a file", pickApk)
            }
            is InstallState.Working -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp), strokeWidth = 5.dp)
                Spacer(Modifier.height(24.dp))
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Hint(s.step, center = true)
                Spacer(Modifier.height(18.dp))
                Hint("Large apps can take a minute. Keep the projector on.", center = true)
            }
            is InstallState.Done -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val tone = if (s.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Box(Modifier.size(84.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon24(if (s.ok) Ico.Check else Ico.Warning, tone, size = 40.dp, strokeWidth = 2.2f)
                }
                Spacer(Modifier.height(20.dp))
                Text(if (s.ok) "Installed" else "Couldn't install", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(s.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (!s.ok) { Spacer(Modifier.height(14.dp)); Banner(s.message, BannerKind.Error) }
                Spacer(Modifier.height(28.dp))
                if (s.ok) {
                    PrimaryButton("See my apps", { vm.loadApps(); vm.go(Screen.Apps) })
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Install another", pickApk)
                } else {
                    PrimaryButton("Choose another file", pickApk)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Back to Home", { vm.go(Screen.Home) })
                }
            }
        }
    }
}
