package dev.keystone.auto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.keystone.auto.ui.PrimaryButton
import dev.keystone.auto.ui.ScreenScaffold
import dev.keystone.auto.ui.theme.AutoKeystoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            }
            AutoKeystoneTheme(dark) {
                ScreenScaffold(title = "AutoKeystone", step = 0) {
                    Text("Theme check")
                    PrimaryButton("Continue", onClick = {})
                }
            }
        }
    }
}
