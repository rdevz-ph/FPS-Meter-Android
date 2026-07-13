package com.rdevzph.fpsmeter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.ui.screen.MainScreen
import com.rdevzph.fpsmeter.ui.theme.FpsMeterTheme
import com.rdevzph.fpsmeter.viewmodel.FpsViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FpsViewModel

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled by viewmodel check */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        var keepSplash = true
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannel()

        viewModel = ViewModelProvider(
            this,
            FpsViewModel.Factory(packageManager)
        ).get(FpsViewModel::class.java)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            FpsMeterTheme {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    keepSplash = false
                    delay(1200)
                    showSplash = false
                }

                MainScreen(
                    viewModel = viewModel,
                    showSplash = showSplash,
                    onStartOverlay = { startOverlayService() },
                    onStopOverlay = { stopOverlayService() }
                )
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            FpsOverlayService.CHANNEL_ID,
            "FPS Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live FPS counter overlay"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun startOverlayService() {
        val intent = Intent(this, FpsOverlayService::class.java)
        startForegroundService(intent)
    }

    private fun stopOverlayService() {
        stopService(Intent(this, FpsOverlayService::class.java))
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
        viewModel.checkOverlayPermission(this)
    }
}
