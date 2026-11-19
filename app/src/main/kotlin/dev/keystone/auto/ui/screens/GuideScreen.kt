package dev.keystone.auto.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.keystone.auto.AppViewModel
import dev.keystone.auto.ui.*

private const val PAGES = 3

@Composable
fun GuideScreen(vm: AppViewModel, initialPage: Int = 0) {
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    val settings by vm.settings.collectAsState()

    ScreenScaffold(
        title = "How to take the photos",
        step = 1,
        onBack = { if (page > 0) page-- else vm.back() },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(PAGES) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == page) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                }
            }
            if (page < PAGES - 1) {
                PrimaryButton("Next", { page++ })
                TextAction("Skip the guide", { page = PAGES - 1 }, Modifier.align(Alignment.CenterHorizontally))
            } else {
                PrimaryButton("I'm ready: show the pattern", { vm.startCapture() })
            }
        },
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 4 } + fadeOut())
            },
            label = "guide",
        ) { p ->
            Column(Modifier.fillMaxWidth()) {
                when (p) {
                    0 -> {
                        ProjectorHero()
                        Spacer(Modifier.height(12.dp))
                        Text("Get ready", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        NumberedStep(1, "Dim the room", "The test pattern is bright. A darker room gives crisp circles and a more accurate result.")
                        NumberedStep(2, "Leave the projector where it is", "Don't move or tilt it during the process. Everything is measured from where it sits now.")
                        NumberedStep(3, "Aim at a flat wall", "A flat, plain wall or screen works best. Curtains, corners or furniture in the way add error.")
                        NumberedStep(4, "We'll take ${settings.captureCount} photos", "Averaging a few photos cancels out hand shake. It takes about a minute.")
                    }

                    1 -> {
                        PlacementDiagram()
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendItem(MaterialTheme.colorScheme.primary, "Picture on the wall")
                            LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, "You + phone")
                            LegendItem(MaterialTheme.colorScheme.secondary, "Projector")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Where to stand", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        NumberedStep(1, "In front of the middle of the picture", "Stand on the centre line, facing the wall straight on. The closer to square, the better.")
                        NumberedStep(2, "Far enough back to fit it all", "About 2 to 3 metres from the wall, so the whole picture fits with some margin on every side.")
                        NumberedStep(3, "Same spot every time", "Stay in one place for all the photos. If you later repeat this, stand in the same place.")
                    }

                    else -> {
                        FramingDiagram()
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            StatusPill("4 circles", true)
                            StatusPill("Tilt -0.4°  Roll +0.6°", true)
                            StatusPill("Steady", true)
                        }
                        Spacer(Modifier.height(4.dp))
                        Hint("These three chips turn green when the shot is ready.", center = true)
                        Spacer(Modifier.height(12.dp))
                        Text("How to hold the phone", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        NumberedStep(1, "Sideways, parallel to the wall", "Hold it like you're taking a flat photo of a poster. Don't angle it left or right.")
                        NumberedStep(2, "Keep the tilt chip green", "It turns green when the phone is level. Aim the camera at the middle of the picture, with the phone upright.")
                        NumberedStep(3, "All four white circles in view", "Leave a little space around the picture. Don't zoom.")
                        NumberedStep(4, "Hold still", "When everything shows ready, the app takes the photo by itself. Lower the phone and raise it again between photos.")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
