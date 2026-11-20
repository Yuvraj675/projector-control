package dev.keystone.auto.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.RemoteState
import dev.keystone.auto.link.RemoteKey
import dev.keystone.auto.ui.*

/** Types what changed between two versions of the text: backspaces for what was removed, then the new tail. */
private fun sendDiff(vm: AppViewModel, old: String, new: String) {
    var i = 0
    while (i < old.length && i < new.length && old[i] == new[i]) i++
    repeat(old.length - i) { vm.press(RemoteKey.BACKSPACE) }
    if (new.length > i) vm.typeText(new.substring(i))
}

@Composable
fun KeyboardScreen(vm: AppViewModel, autoStart: Boolean = true) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val state by vm.remoteState.collectAsState()
    val tick = rememberTick(settings.haptics)
    var text by rememberSaveable { mutableStateOf("") }
    var live by rememberSaveable { mutableStateOf(true) }
    var link by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (autoStart) vm.startRemote() }

    fun change(new: String) { if (live) sendDiff(vm, text, new); text = new }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) change(if (text.isBlank()) heard else "$text $heard")
    }
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what to type on the projector")
        try { voice.launch(i) } catch (_: ActivityNotFoundException) { vm.toast.value = "This phone has no speech recognition service" }
    }

    ScreenScaffold(title = "Type & speak", onBack = { vm.back() }) {
        if (state is RemoteState.Failed) {
            Banner((state as RemoteState.Failed).message, BannerKind.Error)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Reconnect", { vm.startRemote() })
            Spacer(Modifier.height(14.dp))
        }

        AppCard {
            OutlinedTextField(
                value = text, onValueChange = ::change, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                placeholder = { Text(if (live) "Everything you type appears on the projector" else "Type here, then send it") },
                keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Type as I go", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Hint(if (live) "Each letter is sent right away." else "Nothing is sent until you tap Send.")
                }
                Switch(live, { live = it })
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Type my phone's clipboard", { clipboardText(context)?.let { vm.typeText(it) } ?: run { vm.toast.value = "The clipboard is empty" } })
            if (!live) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Send", { vm.typeText(text); text = "" }, Modifier.weight(1f), enabled = text.isNotEmpty())
                    SecondaryButton("Send + Enter", { vm.typeText(text); vm.press(RemoteKey.ENTER); text = "" }, Modifier.weight(1f), enabled = text.isNotEmpty())
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { tick(); listen() },
                contentAlignment = Alignment.Center,
            ) { Icon24(Ico.Mic, MaterialTheme.colorScheme.primary, size = 30.dp) }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Speak", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Hint("Say it and it's typed on the projector.")
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Ico.Backspace, "Backspace", { vm.press(RemoteKey.BACKSPACE); if (text.isNotEmpty()) text = text.dropLast(1) }, size = 52.dp, repeat = true, tick = tick)
            RoundKey(Ico.Enter, "Enter", { vm.press(RemoteKey.ENTER) }, size = 52.dp, prominent = true, tick = tick)
            RoundKey(Ico.Left, "Left", { vm.press(RemoteKey.LEFT) }, size = 52.dp, repeat = true, tick = tick)
            RoundKey(Ico.Up, "Up", { vm.press(RemoteKey.UP) }, size = 52.dp, repeat = true, tick = tick)
            RoundKey(Ico.Down, "Down", { vm.press(RemoteKey.DOWN) }, size = 52.dp, repeat = true, tick = tick)
            RoundKey(Ico.Right, "Right", { vm.press(RemoteKey.RIGHT) }, size = 52.dp, repeat = true, tick = tick)
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("Open on the projector")
        AppCard {
            OutlinedTextField(
                value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a link, or search YouTube") }, shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false),
                trailingIcon = {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { link = clipboardText(context) ?: link }, contentAlignment = Alignment.Center) {
                        Icon24(Ico.Clipboard, MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Open link", { vm.openLink(link) }, Modifier.weight(1f), enabled = link.isNotBlank())
                SecondaryButton("Search YouTube", { vm.searchYoutube(link.trim()) }, Modifier.weight(1f), enabled = link.isNotBlank())
            }
        }
        Spacer(Modifier.height(10.dp))
        Hint("Typing uses an English keyboard layout: letters, digits and common symbols.", center = true)
    }
}

private fun clipboardText(context: Context): String? =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
