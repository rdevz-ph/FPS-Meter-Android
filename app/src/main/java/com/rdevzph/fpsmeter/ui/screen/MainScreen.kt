package com.rdevzph.fpsmeter.ui.screen

import androidx.compose.ui.res.stringResource
import com.rdevzph.fpsmeter.R

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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

enum class MainNavTab(val titleRes: Int, val icon: ImageVector) {
    METER(R.string.tab_meter, Icons.Default.Speed),
    GAMES(R.string.tab_games, Icons.Default.SportsEsports),
    HISTORY(R.string.tab_history, Icons.Default.QueryStats)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FpsViewModel,
    showSplash: Boolean,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "2.2"
        } catch (e: Exception) {
            "2.2"
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
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    val sessions by viewModel.sessions.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(MainNavTab.METER) }

    // Check overlay permission & accessibility on composition
    LaunchedEffect(Unit) {
        viewModel.checkOverlayPermission(context)
        viewModel.checkAccessibilityService(context)
    }

    // Lazy load installed apps only when navigating to the Games tab
    LaunchedEffect(currentTab) {
        if (currentTab == MainNavTab.GAMES && installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
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

    val themeSettings by viewModel.themeSettings.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var showDonationDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSettingsOpen) {
            SettingsScreen(
                themeSettings = themeSettings,
                currentLanguage = currentLanguage,
                onThemeChange = { viewModel.updateThemeSettings(it) },
                onLanguageChange = onLanguageChange,
                onOpenDonation = { showDonationDialog = true },
                onBack = { isSettingsOpen = false }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = currentTab.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        when (currentTab) {
                                            MainNavTab.METER -> stringResource(R.string.subtitle_meter)
                                            MainNavTab.GAMES -> stringResource(R.string.subtitle_games)
                                            MainNavTab.HISTORY -> stringResource(R.string.subtitle_history)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.common_more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_about)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Info, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showAboutDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_settings)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Settings, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            isSettingsOpen = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_donate)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63))
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDonationDialog = true
                                        }
                                    )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    MainNavTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
                            },
                            label = {
                                Text(stringResource(tab.titleRes))
                            }
                        )
                    }
                }
            }
        ) { padding ->
            when (currentTab) {
                MainNavTab.METER -> {
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
                            putExtra(FpsOverlayService.EXTRA_BACKGROUND_ALPHA, settings.backgroundAlpha)
                            putExtra(FpsOverlayService.EXTRA_POSITION_X, settings.posX)
                            putExtra(FpsOverlayService.EXTRA_POSITION_Y, settings.posY)
                            putExtra(FpsOverlayService.EXTRA_SHOW_MS, settings.showMs)
                            putExtra(FpsOverlayService.EXTRA_SHOW_TEMP, settings.showTemp)
                            putExtra(FpsOverlayService.EXTRA_SHOW_SOC_TEMP, settings.showSocTemp)
                            putExtra(FpsOverlayService.EXTRA_SHOW_CPU_TEMP, settings.showCpuTemp)
                            putExtra(FpsOverlayService.EXTRA_SHOW_GPU_TEMP, settings.showGpuTemp)
                            putExtra(FpsOverlayService.EXTRA_GRAVITY, settings.gravity)
                            putExtra(FpsOverlayService.EXTRA_FLOATING_TOGGLE, settings.floatingToggleEnabled)
                            putExtra(FpsOverlayService.EXTRA_FPS_PROVIDER, settings.fpsProvider.name)
                            putExtra(FpsOverlayService.EXTRA_SHOW_API, settings.showGraphicsApi)
                            putExtra(FpsOverlayService.EXTRA_SHOW_BATTERY_LEVEL, settings.showBatteryLevel)
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
                                putExtra(FpsOverlayService.EXTRA_BACKGROUND_ALPHA, newSettings.backgroundAlpha)
                                putExtra(FpsOverlayService.EXTRA_POSITION_X, newSettings.posX)
                                putExtra(FpsOverlayService.EXTRA_POSITION_Y, newSettings.posY)
                                putExtra(FpsOverlayService.EXTRA_SHOW_MS, newSettings.showMs)
                                putExtra(FpsOverlayService.EXTRA_SHOW_TEMP, newSettings.showTemp)
                                putExtra(FpsOverlayService.EXTRA_SHOW_SOC_TEMP, newSettings.showSocTemp)
                                putExtra(FpsOverlayService.EXTRA_SHOW_CPU_TEMP, newSettings.showCpuTemp)
                                putExtra(FpsOverlayService.EXTRA_SHOW_GPU_TEMP, newSettings.showGpuTemp)
                                putExtra(FpsOverlayService.EXTRA_GRAVITY, newSettings.gravity)
                                putExtra(FpsOverlayService.EXTRA_FLOATING_TOGGLE, newSettings.floatingToggleEnabled)
                                putExtra(FpsOverlayService.EXTRA_FPS_PROVIDER, newSettings.fpsProvider.name)
                                putExtra(FpsOverlayService.EXTRA_SHOW_API, newSettings.showGraphicsApi)
                                putExtra(FpsOverlayService.EXTRA_SHOW_BATTERY_LEVEL, newSettings.showBatteryLevel)
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


                // === Info Card ===
                InfoCard()

                // === Developer & Footer Links Section ===
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeveloperCard()

                    // === Official Website Card ===
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
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rdevz-ph.github.io/FPS-Meter-Android/"))
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.official_website),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.official_website_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // === Footer (Check Updates Card) ===
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
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
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
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.check_for_updates),
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
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        MainNavTab.GAMES -> {
            GamesScreen(
                settings = settings,
                installedApps = installedApps,
                isLoading = isLoadingApps,
                accessibilityEnabled = accessibilityEnabled,
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onToggleAutoStart = { enabled ->
                    viewModel.updateSettings(settings.copy(autoStartEnabled = enabled))
                },
                onToggleAutoRecordAll = { enabled ->
                    viewModel.setAutoRecordAll(enabled)
                },
                onTogglePackage = { pkg ->
                    viewModel.toggleAutoStartPackage(pkg)
                },
                onSetRecordingPackage = { pkg, enabled ->
                    viewModel.setRecordingPackage(pkg, enabled)
                },
                modifier = Modifier.padding(padding)
            )
        }
        MainNavTab.HISTORY -> {
            HistoryScreen(
                sessions = sessions,
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onClearAllSessions = {
                    viewModel.clearAllSessions()
                },
                onNavigateToGames = {
                    currentTab = MainNavTab.GAMES
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}
        }

        // Splash overlay
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(500))
        ) {
            SplashOverlay()
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }

        if (showDonationDialog) {
            DonationChooserDialog(onDismiss = { showDonationDialog = false })
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
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.app_by_author),
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
                    stringResource(R.string.shizuku_optional),
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
                    stringResource(R.string.shizuku_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusChip(stringResource(R.string.shizuku_service), available, Modifier.weight(1f))
                    StatusChip(stringResource(R.string.shizuku_permission), granted, Modifier.weight(1f))
                }
                if (!isReady) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (available && !granted) {
                            Button(
                                onClick = onRequestPermission,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(stringResource(R.string.common_grant)) }
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(stringResource(R.string.common_refresh)) }
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
                    stringResource(R.string.overlay_permission),
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
                if (granted) stringResource(R.string.overlay_granted_desc)
                else stringResource(R.string.overlay_required_desc),
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
                        ) { Text(stringResource(R.string.overlay_via_shizuku)) }
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(stringResource(R.string.common_settings)) }
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
                    stringResource(R.string.fps_overlay),
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
                    if (running) stringResource(R.string.stop_overlay) else stringResource(R.string.start_overlay),
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (!permissionReady && !running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.grant_overlay_first),
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
                    stringResource(R.string.overlay_settings),
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
            title = { Text(stringResource(R.string.reset_settings_title)) },
            text = { Text(stringResource(R.string.reset_settings_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(OverlaySettings())
                        showResetConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.common_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // FPS Provider Selection Section
    Text(
        stringResource(R.string.fps_measurement_provider),
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
                        stringResource(R.string.provider_choreographer),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.provider_choreographer_desc),
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
                        stringResource(R.string.provider_surfaceflinger),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (shizukuReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (shizukuReady) stringResource(R.string.provider_surfaceflinger_real) else stringResource(R.string.common_requires_shizuku),
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
                Text(stringResource(R.string.show_api_badge), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.show_api_badge_desc),
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
            Text(stringResource(R.string.text_size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
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

        // Overall Opacity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.opacity), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
            Slider(
                value = settings.alpha,
                onValueChange = { onChange(settings.copy(alpha = it)) },
                valueRange = 0.2f..1.0f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(settings.alpha * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp),
                fontFamily = FontFamily.Monospace
            )
        }

        // Background Opacity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bg_opacity), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
            Slider(
                value = settings.backgroundAlpha,
                onValueChange = { onChange(settings.copy(backgroundAlpha = it)) },
                valueRange = 0.0f..1.0f,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(settings.backgroundAlpha * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp),
                fontFamily = FontFamily.Monospace
            )
        }

        // Color picker row
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.color_label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
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
                    Text(stringResource(R.string.show_ms), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showMs,
                        onCheckedChange = { onChange(settings.copy(showMs = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))

            // Battery temp toggle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.battery_temp), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showTemp,
                        onCheckedChange = { onChange(settings.copy(showTemp = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // CPU & GPU temp toggles (Requires Shizuku)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // CPU temp toggle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.cpu_temp), style = MaterialTheme.typography.labelMedium)
                        if (!shizukuReady) {
                            Text(
                                stringResource(R.string.common_requires_shizuku),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showCpuTemp,
                        enabled = shizukuReady,
                        onCheckedChange = { onChange(settings.copy(showCpuTemp = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))

            // GPU temp toggle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.gpu_temp), style = MaterialTheme.typography.labelMedium)
                        if (!shizukuReady) {
                            Text(
                                stringResource(R.string.common_requires_shizuku),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showGpuTemp,
                        enabled = shizukuReady,
                        onCheckedChange = { onChange(settings.copy(showGpuTemp = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // SoC temp & Battery level toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // SoC temp toggle (Requires Shizuku - Silicon Hotspot)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.soc_temp), style = MaterialTheme.typography.labelMedium)
                        if (!shizukuReady) {
                            Text(
                                stringResource(R.string.common_requires_shizuku),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showSocTemp,
                        enabled = shizukuReady,
                        onCheckedChange = { onChange(settings.copy(showSocTemp = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))

            // Battery level toggle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.battery_level), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showBatteryLevel,
                        onCheckedChange = { onChange(settings.copy(showBatteryLevel = it)) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Position presets
        Text(stringResource(R.string.position_presets), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        
        val presets = listOf(
            stringResource(R.string.pos_top_left) to (Gravity.TOP or Gravity.START),
            stringResource(R.string.pos_top_center) to (Gravity.TOP or Gravity.CENTER_HORIZONTAL),
            stringResource(R.string.pos_top_right) to (Gravity.TOP or Gravity.END),
            stringResource(R.string.pos_bottom_left) to (Gravity.BOTTOM or Gravity.START),
            stringResource(R.string.pos_bottom_center) to (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
            stringResource(R.string.pos_bottom_right) to (Gravity.BOTTOM or Gravity.END)
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

        val hasApi = settings.fpsProvider == FpsProvider.SURFACE_FLINGER && settings.showGraphicsApi
        val hasMs = settings.showMs
        val hasCpu = settings.showCpuTemp
        val hasGpu = settings.showGpuTemp
        val hasSoc = settings.showSocTemp
        val hasBatt = settings.showTemp
        val hasBattLevel = settings.showBatteryLevel
        val hasBattBoth = hasBatt && hasBattLevel

        val extraOverlayCount = (if (hasApi) 1 else 0) +
                (if (hasMs) 1 else 0) +
                (if (hasCpu) 1 else 0) +
                (if (hasGpu) 1 else 0) +
                (if (hasSoc) 1 else 0) +
                (if (hasBattBoth) 1 else ((if (hasBatt) 1 else 0) + (if (hasBattLevel) 1 else 0)))

        val hasThermals = hasCpu || hasGpu || hasSoc || hasBatt || hasBattLevel
        // Only wrap to next line if more than 3 extra overlays are enabled (> 3); stay horizontal for 1-3 overlays
        val useNextLine = extraOverlayCount > 3 && hasThermals

        // FPS preview chip
        Surface(
            color = Color.DarkGray.copy(alpha = 0.8f),
            shape = RoundedCornerShape(if (useNextLine) 12.dp else 100.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = if (useNextLine) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp), tint = Color(0xAAFFFFFF))
                Spacer(Modifier.width(8.dp))
                Text(
                    buildAnnotatedString {
                        val labelColor = Color(0xFF00E5FF)
                        val valueColor = Color.White
                        val fpsValueColor = if (settings.color == OverlaySettings.AUTO_COLOR) Color(0xFF4CAF50) else Color(settings.color)

                        // === Line 1: Performance Group (FPS, Graphics API, Frame Time) ===
                        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) {
                            append("FPS ")
                        }
                        withStyle(SpanStyle(color = fpsValueColor, fontWeight = FontWeight.Bold)) {
                            append("60")
                        }

                        if (hasApi) {
                            withStyle(SpanStyle(color = Color.Gray)) { append("  |  ") }
                            withStyle(SpanStyle(color = Color(0xFFFF5722), fontWeight = FontWeight.Bold)) {
                                append("VK")
                            }
                        }

                        if (hasMs) {
                            withStyle(SpanStyle(color = Color.Gray)) { append("  |  ") }
                            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) {
                                append("MS ")
                            }
                            withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) {
                                append("16")
                            }
                        }

                        // === Hardware / Thermals Group (CPU, GPU, SoC, Battery) ===
                        if (hasThermals) {
                            if (useNextLine) {
                                append("\n")
                            }
                            var isFirstThermalOnLine = useNextLine
                            fun appendThermal(tag: String, value: String) {
                                if (!isFirstThermalOnLine) {
                                    withStyle(SpanStyle(color = Color.Gray)) { append("  |  ") }
                                }
                                withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) {
                                    append("$tag ")
                                }
                                withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) {
                                    append(value)
                                }
                                isFirstThermalOnLine = false
                            }

                            if (hasCpu) appendThermal("CPU", "42.1°C")
                            if (hasGpu) appendThermal("GPU", "38.5°C")
                            if (hasSoc) appendThermal("SOC", "45.2°C")
                            if (hasBattBoth) {
                                appendThermal("BAT", "38.5°C (85%)")
                            } else if (hasBatt) {
                                appendThermal("BATT", "38.5°C")
                            } else if (hasBattLevel) {
                                appendThermal("BAT", "85%")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = settings.textSizeSp.sp * 0.65f,
                        textAlign = TextAlign.Center
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
                stringResource(R.string.reset_settings_btn),
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
                    stringResource(R.string.how_it_works),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.how_it_works_body),
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
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://romel-portfolio.vercel.app/"))
                        context.startActivity(intent)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(Modifier.height(10.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://romel-portfolio.vercel.app/"))
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Romel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.content_desc_portfolio),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Text(
                text = stringResource(R.string.developer_role),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rdevz-ph.github.io/FPS-Meter-Android/"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.button_website), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(12.dp))

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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.button_github_profile), fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.built_with_passion),
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
                    stringResource(R.string.quick_access),
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
                        stringResource(R.string.floating_bubble),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.floating_bubble_desc),
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
                        stringResource(R.string.quick_settings_tile),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.quick_settings_tile_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


