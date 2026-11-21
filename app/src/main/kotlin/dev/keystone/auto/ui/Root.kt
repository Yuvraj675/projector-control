package dev.keystone.auto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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

private val TABS = listOf(
    Screen.Home to TabItem(Ico.Tools, "Home"),
    Screen.Remote to TabItem(Ico.Pointer, "Remote"),
    Screen.Keyboard to TabItem(Ico.Keyboard, "Type"),
    Screen.Apps to TabItem(Ico.Apps, "Apps"),
)

/** How deep a screen is: the tabs sit side by side, everything else is one level in. Decides which way a transition slides. */
private fun depth(s: Screen): Int = TABS.indexOfFirst { it.first == s }.let { if (it >= 0) it else 10 }

@Composable
fun AppRoot(vm: AppViewModel, finish: () -> Unit) {
    val screen by vm.screen.collectAsState()
    val busy by vm.busy.collectAsState()
    val settings by vm.settings.collectAsState()

    BackHandler { if (!vm.back()) finish() }

    CompositionLocalProvider(LocalHapticsOn provides settings.haptics) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // the four tabs live in one swipeable pager; every other screen is a normal destination
            val isTab = TABS.any { it.first == screen }
            AnimatedContent(
                targetState = if (isTab) Screen.Home else screen,
                transitionSpec = {
                    val dir = if (depth(targetState) >= depth(initialState)) 1 else -1
                    (slideInHorizontally(moveSpring()) { dir * it / 9 } + fadeIn(tween(240, delayMillis = 60))) togetherWith
                        (slideOutHorizontally(moveSpring()) { -dir * it / 9 } + fadeOut(tween(140)))
                },
                label = "screen",
            ) { s ->
                CompositionLocalProvider(LocalInTabs provides (s == Screen.Home)) {
                    when (s) {
                        Screen.Connect -> ConnectScreen(vm)
                        Screen.Home -> TabsHost(vm)
                        Screen.Remote, Screen.Keyboard, Screen.Apps -> Unit
                        Screen.Install -> InstallScreen(vm)
                        Screen.PhoneApps -> PhoneAppsScreen(vm)
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
            }

            val tabIndex = TABS.indexOfFirst { it.first == screen }
            Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars)) {
                AnimatedVisibility(
                    tabIndex >= 0,
                    enter = slideInVertically(moveSpring()) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(tween(120)),
                ) {
                    FloatingTabBar(TABS.map { it.second }, tabIndex.coerceAtLeast(0), { i ->
                        val target = TABS[i].first
                        if (target == Screen.Apps) vm.loadApps()
                        vm.go(target)
                    })
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
}

/** Home, Remote, Type and Apps side by side: swipe sideways or tap the bar. Touching the touchpad never swipes. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TabsHost(vm: AppViewModel) {
    val screen by vm.screen.collectAsState()
    val start = TABS.indexOfFirst { it.first == screen }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { TABS.size }
    val tap = rememberTap()
    // the tab bar (or the back button) changed the screen: slide the pager there
    LaunchedEffect(screen) {
        val i = TABS.indexOfFirst { it.first == screen }
        if (i >= 0 && i != pager.targetPage) pager.animateScrollToPage(i, animationSpec = moveSpring())
    }
    // the user swiped: tell the app which tab is now showing
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val target = TABS[page].first
            if (target != vm.screen.value && TABS.any { it.first == vm.screen.value }) {
                if (target == Screen.Apps) vm.loadApps()
                tap()
                vm.go(target)
            }
        }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondBoundsPageCount = 0, key = { it }) { page ->
        when (TABS[page].first) {
            Screen.Home -> HomeScreen(vm)
            Screen.Remote -> RemoteScreen(vm)
            Screen.Keyboard -> KeyboardScreen(vm)
            else -> AppsScreen(vm)
        }
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
