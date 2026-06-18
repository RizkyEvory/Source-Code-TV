package com.example

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.SettingsManager
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.home.BackgroundColor
import com.example.ui.playlist.PlaylistManagerScreen
import com.example.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isAndroidTv(this)) {
            val intent = Intent(this, TvMainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()

        val app = application as M4DiTVApplication
        settingsManager = SettingsManager(this)
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(app, app.repository, settingsManager)
        )[HomeViewModel::class.java]

        setContent {
            var currentScreen by remember { mutableStateOf("home") }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BackgroundColor
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        "home" -> HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToSettings = { currentScreen = "settings" },
                            onNavigateToPlaylists = { currentScreen = "playlists" }
                        )
                        "playlists" -> PlaylistManagerScreen(
                            viewModel = homeViewModel,
                            onBack = { currentScreen = "home" }
                        )
                        "settings" -> SettingsScreen(
                            viewModel = homeViewModel,
                            settingsManager = settingsManager,
                            onBack = { currentScreen = "home" }
                        )
                    }
                }
            }
        }
    }

    private fun isAndroidTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanbackFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isTelevisionMode || hasLeanbackFeature
    }
}
