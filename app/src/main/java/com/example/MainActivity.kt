package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.CategoriesScreen
import com.example.ui.HomeScreen
import com.example.ui.MainViewModel
import com.example.ui.ManageAppsScreen
import com.example.ui.NotificationHistoryScreen
import com.example.ui.Screen
import com.example.ui.SettingsScreen
import com.example.ui.TemporaryMuteScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as NotificationMuterApp
        MainViewModel.Factory(app.repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermission()
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    // Handle back button when inside sub-screens
    BackHandler(enabled = currentScreen != Screen.HOME) {
        viewModel.navigateTo(Screen.HOME)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (currentScreen) {
            Screen.HOME -> HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.MANAGE_APPS -> ManageAppsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.CATEGORIES -> CategoriesScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.TEMPORARY_MUTE -> TemporaryMuteScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.NOTIFICATION_HISTORY -> NotificationHistoryScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
