package com.rdevzph.fpsmeter.ui.screen

import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rdevzph.fpsmeter.model.FpsProvider
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.viewmodel.FpsViewModel
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FpsViewModel,
    showSplash: Boolean,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val shizukuAvailable by viewModel.shizukuAvailable.collectAsState()
    val shizukuGranted by viewModel.shizukuPermissionGranted.collectAsState()
    val overlayGranted by viewModel.overlayPermissionGranted.collectAsState()
    val overlayRunning by viewModel.isOverlayRunning.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showAppSelectionDialog by remember { mutableStateOf(false) }

    // Check overlay permission & accessibility on composition
    LaunchedEffect(Unit) {
        viewModel.checkOverlayPermission(context)
        viewModel.checkAccessibilityService(context)
        viewModel.loadInstalledApps()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
                viewModel.checkOverlayPermission(context)
                viewModel.checkAccessibilityService(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show status snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMsg) {
        if (statusMsg.isNotEmpty()) {
            snackbarHostState.showSnackbar(statusMsg)
            viewModel.clearStatus()
        }
    }

    if (showAppSelectionDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            selectedPackages = settings.autoStartPackages,
            onTogglePackage = { pkg -> viewModel.toggleAutoStartPackage(pkg) },
            onDismiss = { showAppSelectionDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "FPS Meter",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Live overlay counter",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // === Shizuku Status ===
                ShizukuCard(
                    available = shizukuAvailable,
                    granted = shizukuGranted,
                    onRequestPermission = { viewModel.requestShizukuPermission() },
                    onRefresh = { viewModel.checkShizukuStatus() }
                )

                // === Overlay Permission ===
                OverlayPermissionCard(
                    granted = overlayGranted,
                    shizukuReady = shizukuAvailable && shizukuGranted,
                    onGrantViaShizuku = { viewModel.grantOverlayViaShizuku(context) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )

                // === FPS Overlay Controls ===
                OverlayControlCard(
                    running = overlayRunning,
                    permissionReady = overlayGranted,
                    shizukuReady = shizukuAvailable && shizukuGranted,
                    settings = settings,
                    onStart = {
                        val intent = Intent(context, FpsOverlayService::class.java).apply {
                            putExtra(FpsOverlayService.EXTRA_COLOR, settings.color)
                            putExtra(FpsOverlayService.EXTRA_SIZE, settings.textSizeSp)
                            putExtra(FpsOverlayService.EXTRA_ALPHA, settings.alpha)
                            putExtra(FpsOverlayService.EXTRA_POSITION_X, settings.posX)
                            putExtra(FpsOverlayService.EXTRA_POSITION_Y, settings.posY)
                            putExtra(FpsOverlayService.EXTRA_SHOW_MS, settings.showMs)
                            putExtra(FpsOverlayService.EXTRA_SHOW_TEMP, settings.showTemp)
                            putExtra(FpsOverlayService.EXTRA_GRAVITY, settings.gravity)
                            putExtra(FpsOverlayService.EXTRA_FLOATING_TOGGLE, settings.floatingToggleEnabled)
                            putExtra(FpsOverlayService.EXTRA_FPS_PROVIDER, settings.fpsProvider.name)
                            putExtra(FpsOverlayService.EXTRA_SHOW_API, settings.showGraphicsApi)
                        }
                        context.startForegroundService(intent)
                        viewModel.setOverlayRunning(true)
                    },
                    onStop = {
                        onStopOverlay()
                        viewModel.setOverlayRunning(false)
                    },
                    onSettingsChange = { newSettings ->
                        viewModel.updateSettings(newSettings)
                        if (overlayRunning) {
                            val intent = Intent(context, FpsOverlayService::class.java).apply {
                                putExtra(FpsOverlayService.EXTRA_COLOR, newSettings.color)
                                putExtra(FpsOverlayService.EXTRA_SIZE, newSettings.textSizeSp)
                                putExtra(FpsOverlayService.EXTRA_ALPHA, newSettings.alpha)
                                putExtra(FpsOverlayService.EXTRA_POSITION_X, newSettings.posX)
                                putExtra(FpsOverlayService.EXTRA_POSITION_Y, newSettings.posY)
                                putExtra(FpsOverlayService.EXTRA_SHOW_MS, newSettings.showMs)
                                putExtra(FpsOverlayService.EXTRA_SHOW_TEMP, newSettings.showTemp)
                                putExtra(FpsOverlayService.EXTRA_GRAVITY, newSettings.gravity)
                                putExtra(FpsOverlayService.EXTRA_FLOATING_TOGGLE, newSettings.floatingToggleEnabled)
                                putExtra(FpsOverlayService.EXTRA_FPS_PROVIDER, newSettings.fpsProvider.name)
                                putExtra(FpsOverlayService.EXTRA_SHOW_API, newSettings.showGraphicsApi)
                            }
                            context.startForegroundService(intent)
                        }
                    }
                )

                // === Quick Access & Floating Controls ===
                QuickAccessCard(
                    settings = settings,
                    onToggleFloatingButton = { enabled ->
                        val newSettings = settings.copy(floatingToggleEnabled = enabled)
                        viewModel.updateSettings(newSettings)
                        if (overlayRunning) {
                            val intent = Intent(context, FpsOverlayService::class.java).apply {
                                putExtra(FpsOverlayService.EXTRA_FLOATING_TOGGLE, enabled)
                            }
                            context.startForegroundService(intent)
                        }
                    }
                )

                // === Auto On/Off per App ===
                AutoStartCard(
                    settings = settings,
                    accessibilityEnabled = accessibilityEnabled,
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onToggleAutoStart = { enabled ->
                        viewModel.updateSettings(settings.copy(autoStartEnabled = enabled))
                    },
                    onOpenAppPicker = { showAppSelectionDialog = true }
                )

                // === Info Card ===
                InfoCard()

                // === Developer Card ===
                DeveloperCard()

                // === Footer (Check Updates Card) ===
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rdevz-ph/FPS-Meter-Android"))
                                context.startActivity(intent)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Check for Updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "v$versionName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "github.com/rdevz-ph/FPS-Meter-Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Splash overlay
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(500))
        ) {
            SplashOverlay()
        }
    }
}

@Composable
fun SplashOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "FPS Meter",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "by rdevzph",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ShizukuCard(
    available: Boolean,
    granted: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isReady = available && granted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Shizuku (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Used to auto-grant overlay permission without opening settings and enable privileged SurfaceFlinger monitoring for real game FPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusChip("Service", available, Modifier.weight(1f))
                    StatusChip("Permission", granted, Modifier.weight(1f))
                }
                if (!isReady) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (available && !granted) {
                            Button(
                                onClick = onRequestPermission,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Grant") }
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Refresh") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (active)
        Color(0xFF4CAF50).copy(alpha = 0.12f) else Color(0xFFB3261E).copy(alpha = 0.12f)
    val borderColor = if (active)
        Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFB3261E).copy(alpha = 0.4f)
    val icon = if (active) Icons.Default.CheckCircle else Icons.Default.Cancel
    val iconColor = if (active) Color(0xFF4CAF50) else Color(0xFFB3261E)

    Surface(
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = iconColor)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun OverlayPermissionCard(
    granted: Boolean,
    shizukuReady: Boolean,
    onGrantViaShizuku: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Layers,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Overlay Permission",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (granted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (granted) "SYSTEM_ALERT_WINDOW is granted — overlay can appear over any app."
                else "Required to show the FPS counter over games. Grant via Shizuku (no root needed) or manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shizukuReady) {
                        Button(
                            onClick = onGrantViaShizuku,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Via Shizuku") }
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Settings") }
                }
            }
        }
    }
}

@Composable
fun OverlayControlCard(
    running: Boolean,
    permissionReady: Boolean,
    shizukuReady: Boolean = false,
    settings: OverlaySettings,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettingsChange: (OverlaySettings) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "FPS Overlay",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                // Running indicator dot
                if (running) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Big toggle button
            Button(
                onClick = if (running) onStop else onStart,
                enabled = permissionReady || running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "STOP OVERLAY" else "START OVERLAY",
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (!permissionReady && !running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ Grant overlay permission first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Collapsible settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Tune,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Overlay Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OverlaySettingsPanel(
                    settings = settings,
                    shizukuReady = shizukuReady,
                    onChange = onSettingsChange
                )
            }
        }
    }
}

@Composable
fun OverlaySettingsPanel(
    settings: OverlaySettings,
    shizukuReady: Boolean = false,
    onChange: (OverlaySettings) -> Unit
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Settings?") },
            text = { Text("Are you sure you want to reset all FPS overlay settings to their defaults? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(OverlaySettings())
                        showResetConfirm = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // FPS Provider Selection Section
    Text(
        "FPS Measurement Provider",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Choreographer Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    onChange(settings.copy(fpsProvider = FpsProvider.CHOREOGRAPHER))
                }
                .border(
                    width = if (settings.fpsProvider == FpsProvider.CHOREOGRAPHER) 2.dp else 1.dp,
                    color = if (settings.fpsProvider == FpsProvider.CHOREOGRAPHER)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            color = if (settings.fpsProvider == FpsProvider.CHOREOGRAPHER)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.fpsProvider == FpsProvider.CHOREOGRAPHER,
                        onClick = { onChange(settings.copy(fpsProvider = FpsProvider.CHOREOGRAPHER)) },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Choreographer",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Display pace (Default)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // SurfaceFlinger Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = shizukuReady) {
                    onChange(settings.copy(fpsProvider = FpsProvider.SURFACE_FLINGER))
                }
                .border(
                    width = if (settings.fpsProvider == FpsProvider.SURFACE_FLINGER) 2.dp else 1.dp,
                    color = if (settings.fpsProvider == FpsProvider.SURFACE_FLINGER)
                        MaterialTheme.colorScheme.primary
                    else if (shizukuReady)
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ),
            color = if (settings.fpsProvider == FpsProvider.SURFACE_FLINGER)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else if (shizukuReady)
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            else
                MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp).copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.fpsProvider == FpsProvider.SURFACE_FLINGER,
                        onClick = {
                            if (shizukuReady) {
                                onChange(settings.copy(fpsProvider = FpsProvider.SURFACE_FLINGER))
                            }
                        },
                        enabled = shizukuReady,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SurfaceFlinger",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (shizukuReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (shizukuReady) "Real Game FPS" else "Requires Shizuku",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    fontWeight = if (!shizukuReady) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }

    if (settings.fpsProvider == FpsProvider.SURFACE_FLINGER) {
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Graphics API Badge", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Auto-detects Vulkan [VK] or OpenGL [GL]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.showGraphicsApi,
                onCheckedChange = { onChange(settings.copy(showGraphicsApi = it)) },
                modifier = Modifier.scale(0.8f)
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Spacer(Modifier.height(12.dp))

    // Text size
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
            Slider(
                value = settings.textSizeSp,
                onValueChange = { onChange(settings.copy(textSizeSp = it)) },
                valueRange = 10f..28f,
                steps = 5,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${settings.textSizeSp.toInt()}sp",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp),
                fontFamily = FontFamily.Monospace
            )
        }

        // Opacity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
            Slider(
                value = settings.alpha,
                onValueChange = { onChange(settings.copy(alpha = it)) },
                valueRange = 0.3f..1.0f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(settings.alpha * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp),
                fontFamily = FontFamily.Monospace
            )
        }

        // Color picker row
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Auto option first
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Red)
                            )
                        )
                        .border(
                            width = if (settings.color == OverlaySettings.AUTO_COLOR) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { onChange(settings.copy(color = OverlaySettings.AUTO_COLOR)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                val colors = listOf(
                    AColor.GREEN to "Green",
                    AColor.WHITE to "White",
                    AColor.YELLOW to "Yellow",
                    AColor.CYAN to "Cyan",
                    AColor.rgb(255, 100, 100) to "Red"
                )
                colors.forEach { (color, label) ->
                    val composeColor = Color(color)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(composeColor)
                            .border(
                                width = if (settings.color == color) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { onChange(settings.copy(color = color)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Toggle row 1: ms and temp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Show ms toggle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show ms", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showMs,
                        onCheckedChange = { onChange(settings.copy(showMs = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))

            // Show temp toggle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Temp", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showTemp,
                        onCheckedChange = { onChange(settings.copy(showTemp = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Position presets
        Text("Position Presets", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        
        val presets = listOf(
            "Top Left" to (Gravity.TOP or Gravity.START),
            "Top Center" to (Gravity.TOP or Gravity.CENTER_HORIZONTAL),
            "Top Right" to (Gravity.TOP or Gravity.END),
            "Bottom Left" to (Gravity.BOTTOM or Gravity.START),
            "Bottom Center" to (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
            "Bottom Right" to (Gravity.BOTTOM or Gravity.END)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.chunked(3).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPresets.forEach { (label, gravity) ->
                        OutlinedButton(
                            onClick = {
                                onChange(settings.copy(gravity = gravity, posX = 0, posY = 100))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (settings.gravity == gravity)
                                    MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Text(
                                label, 
                                style = MaterialTheme.typography.labelSmall, 
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // FPS preview chip
        Surface(
            color = Color.DarkGray.copy(alpha = 0.8f),
            shape = RoundedCornerShape(100.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp), tint = Color(0xAAFFFFFF))
                Spacer(Modifier.width(8.dp))
                Text(
                    buildAnnotatedString {
                        val labelColor = Color(0xFF00E5FF)
                        val valueColor = Color.White
                        val fpsValueColor = if (settings.color == OverlaySettings.AUTO_COLOR) Color(0xFF4CAF50) else Color(settings.color)

                        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) {
                            append("FPS ")
                        }
                        withStyle(SpanStyle(color = fpsValueColor, fontWeight = FontWeight.Bold)) {
                            append("60")
                        }

                        if (settings.fpsProvider == FpsProvider.SURFACE_FLINGER && settings.showGraphicsApi) {
                            withStyle(SpanStyle(color = Color.Gray)) { append("  |  ") }
                            withStyle(SpanStyle(color = Color(0xFFFF5722), fontWeight = FontWeight.Bold)) {
                                append("VK")
                            }
                        }

                        if (settings.showMs) {
                            withStyle(SpanStyle(color = Color.Gray)) { append("  |  ") }
                            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) {
                                append("MS ")
                            }
                            withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) {
                                append("16")
                            }
                        }

                        if (settings.showTemp) {
                            withStyle(SpanStyle(color = Color.Gray)) { append("  |  ") }
                            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) {
                                append("TEMP ")
                            }
                            withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) {
                                append("38.5°C")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = settings.textSizeSp.sp * 0.65f
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reset Settings Button
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Reset Settings",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "How it works",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "FPS can be measured using two distinct providers:\n\n" +
                "• Choreographer (Default): Samples the Android vsync signal driving display refreshes with minimal overhead.\n" +
                "• SurfaceFlinger (Shizuku): Samples real frame presentation buffers directly from Android's compositor. Automatically detects whether the running game uses Vulkan or OpenGL ES (e.g. Genshin Impact, Wuthering Waves) to measure true game FPS.\n\n" +
                "• Position: Drag the counter or use quick presets to snap it to any corner.\n" +
                "• Auto Color: Values change color dynamically based on performance (60/45/30 FPS).\n" +
                "• Permissions: Grant overlay access manually or via Shizuku for a seamless setup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun DeveloperCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "rdevz-ph",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Android Developer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rdevz-ph"))
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Visit GitHub Profile", fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "Built with ❤️ for gamers",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun QuickAccessCard(
    settings: OverlaySettings,
    onToggleFloatingButton: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Quick Access & Toggles",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Floating Toggle Assistive Bubble Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Floating Assistive Bubble",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Draggable on-screen bubble to instantly show/hide FPS overlay from any app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.floatingToggleEnabled,
                    onCheckedChange = onToggleFloatingButton
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Quick Settings Panel Tile Guide
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Quick Settings Panel Tile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Swipe down from your status bar in any game and tap the 'FPS Meter' tile to toggle without opening this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AutoStartCard(
    settings: OverlaySettings,
    accessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onOpenAppPicker: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Auto On/Off per App",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Accessibility Service Warning / Status
            if (!accessibilityEnabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accessibility Service Required",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Enable FPS Meter in Accessibility to detect when games open.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Enable", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Auto-start switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-Start on Target Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Automatically start overlay when target apps open, and stop when exited.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.autoStartEnabled,
                    onCheckedChange = onToggleAutoStart,
                    enabled = accessibilityEnabled
                )
            }

            Spacer(Modifier.height(12.dp))

            // App selection button
            OutlinedButton(
                onClick = onOpenAppPicker,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Gamepad,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (settings.autoStartPackages.isEmpty()) "Select Target Games & Apps"
                    else "Target Apps (${settings.autoStartPackages.size} selected)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionDialog(
    installedApps: List<FpsViewModel.AppInfo>,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(0) } // 0: All, 1: User Apps only, 2: Selected only

    val filteredApps = remember(searchQuery, installedApps, filterMode, selectedPackages) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                1 -> !app.isSystemApp // User apps only
                2 -> selectedPackages.contains(app.packageName) // Selected only
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Target Games & Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps or games...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(8.dp))

                // Filter options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0 },
                        label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1 },
                        label = { Text("User Apps", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filterMode == 2,
                        onClick = { filterMode = 2 },
                        label = { Text("Selected (${selectedPackages.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No apps found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTogglePackage(app.packageName) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onTogglePackage(app.packageName) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (!app.isSystemApp) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "User",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done")
            }
        }
    )
}
