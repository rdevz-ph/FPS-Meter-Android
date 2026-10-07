package com.rdevzph.fpsmeter.ui.screen

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rdevzph.fpsmeter.R
import com.rdevzph.fpsmeter.accessibility.FpsAccessibilityService
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.overlay.SocThermalMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

data class DiagnosticItem(
    val label: String,
    val value: String
)

data class DeviceDiagnosticData(
    val appVersion: String = "",
    val deviceModel: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val deviceCode: String = "",
    val product: String = "",
    val board: String = "",
    val hardware: String = "",
    val fingerprint: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val securityPatch: String = "",
    val buildDisplay: String = "",
    val supportedAbis: String = "",
    val resolution: String = "",
    val refreshRate: String = "",
    val supportedModes: String = "",
    val densityDpi: Int = 0,
    val cpuCores: Int = 0,
    val cpuArch: String = "",
    val glEsVersion: String = "",
    val vulkanSupport: String = "",
    val batteryLevel: String = "",
    val batteryTemp: String = "",
    val batteryStatus: String = "",
    val batteryHealth: String = "",
    val powerSaveMode: Boolean = false,
    val systemThermalStatus: String = "",
    val thermalHeadroom: String = "",
    val overlayPermission: Boolean = false,
    val accessibilityRunning: Boolean = false,
    val overlayServiceRunning: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val shizukuVersion: Int = 0,
    val socModelShizuku: String = "",
    val socPlatformShizuku: String = "",
    val surfaceFlingerGles: String = "",
    val surfaceFlingerRefreshRate: String = "",
    val shizukuCpuTemp: String = "",
    val shizukuGpuTemp: String = "",
    val shizukuSocTemp: String = "",
    val rawHalThermals: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var diagnosticData by remember { mutableStateOf(DeviceDiagnosticData()) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadDiagnostics() {
        coroutineScope.launch {
            isLoading = true
            diagnosticData = fetchDiagnostics(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadDiagnostics()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.device_diagnostics),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${diagnosticData.manufacturer} ${diagnosticData.deviceModel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadDiagnostics() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.diag_refresh)
                        )
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val report = formatMarkdownReport(diagnosticData)
                        shareToGitHub(context, diagnosticData, report)
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.diag_share_github),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val report = formatMarkdownReport(diagnosticData)
                        copyToClipboard(context, report)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.diag_copy_all),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        val report = formatMarkdownReport(diagnosticData)
                        shareViaIntent(context, diagnosticData, report)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.diag_share),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else {
                // Section 1: Device & OS
                DiagnosticsCard(
                    title = stringResource(R.string.diag_section_device),
                    icon = Icons.Default.PhoneAndroid,
                    items = listOf(
                        DiagnosticItem("App Version", diagnosticData.appVersion),
                        DiagnosticItem("Device", "${diagnosticData.manufacturer} ${diagnosticData.deviceModel}"),
                        DiagnosticItem("Brand / Product", "${diagnosticData.brand} / ${diagnosticData.product}"),
                        DiagnosticItem("Device Code / Board", "${diagnosticData.deviceCode} / ${diagnosticData.board}"),
                        DiagnosticItem("Hardware", diagnosticData.hardware),
                        DiagnosticItem("Android Version", "Android ${diagnosticData.androidVersion} (API ${diagnosticData.sdkInt})"),
                        DiagnosticItem("Security Patch", diagnosticData.securityPatch),
                        DiagnosticItem("Build Display", diagnosticData.buildDisplay),
                        DiagnosticItem("Supported ABIs", diagnosticData.supportedAbis)
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Section 2: Display & Refresh Rate
                DiagnosticsCard(
                    title = stringResource(R.string.diag_section_display),
                    icon = Icons.Default.Speed,
                    items = listOf(
                        DiagnosticItem("Screen Resolution", diagnosticData.resolution),
                        DiagnosticItem("Current Refresh Rate", diagnosticData.refreshRate),
                        DiagnosticItem("Display Density", "${diagnosticData.densityDpi} DPI"),
                        DiagnosticItem("Supported Modes", diagnosticData.supportedModes.ifEmpty { "Default mode only" })
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Section 3: Processor & GPU
                val socItems = mutableListOf(
                    DiagnosticItem("CPU Cores", "${diagnosticData.cpuCores} cores (${diagnosticData.cpuArch})"),
                    DiagnosticItem("OpenGL ES Version", diagnosticData.glEsVersion),
                    DiagnosticItem("Vulkan Hardware Support", diagnosticData.vulkanSupport)
                )
                if (diagnosticData.socModelShizuku.isNotEmpty()) {
                    socItems.add(DiagnosticItem("SoC Model (Shizuku)", diagnosticData.socModelShizuku))
                }
                if (diagnosticData.socPlatformShizuku.isNotEmpty()) {
                    socItems.add(DiagnosticItem("Platform (Shizuku)", diagnosticData.socPlatformShizuku))
                }
                if (diagnosticData.surfaceFlingerGles.isNotEmpty()) {
                    socItems.add(DiagnosticItem("GPU Driver (SurfaceFlinger)", diagnosticData.surfaceFlingerGles))
                }
                DiagnosticsCard(
                    title = stringResource(R.string.diag_section_soc),
                    icon = Icons.Default.Memory,
                    items = socItems
                )

                Spacer(Modifier.height(16.dp))

                // Section 4: Thermals & Battery
                val thermalItems = mutableListOf(
                    DiagnosticItem("Battery Temperature", diagnosticData.batteryTemp),
                    DiagnosticItem("Battery Level", diagnosticData.batteryLevel),
                    DiagnosticItem("Battery Status", diagnosticData.batteryStatus),
                    DiagnosticItem("Battery Health", diagnosticData.batteryHealth),
                    DiagnosticItem("System Thermal Status", diagnosticData.systemThermalStatus),
                    DiagnosticItem("Thermal Headroom", diagnosticData.thermalHeadroom),
                    DiagnosticItem("Power Save Mode", if (diagnosticData.powerSaveMode) "Active" else "Inactive")
                )
                if (diagnosticData.shizukuCpuTemp.isNotEmpty()) {
                    thermalItems.add(DiagnosticItem("CPU Sensor (Shizuku)", diagnosticData.shizukuCpuTemp))
                }
                if (diagnosticData.shizukuGpuTemp.isNotEmpty()) {
                    thermalItems.add(DiagnosticItem("GPU Sensor (Shizuku)", diagnosticData.shizukuGpuTemp))
                }
                if (diagnosticData.shizukuSocTemp.isNotEmpty()) {
                    thermalItems.add(DiagnosticItem("SoC Unified (Shizuku)", diagnosticData.shizukuSocTemp))
                }
                DiagnosticsCard(
                    title = stringResource(R.string.diag_section_thermals),
                    icon = Icons.Default.DeviceThermostat,
                    items = thermalItems
                )

                Spacer(Modifier.height(16.dp))

                // Section 5: Shizuku & Privileged Raw Data
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shizuku),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.diag_section_shizuku),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        DiagnosticRow("Shizuku Binder Running", if (diagnosticData.shizukuRunning) "Yes" else "No")
                        DiagnosticRow("Shizuku Permission", if (diagnosticData.shizukuPermission) "Granted" else "Denied")
                        if (diagnosticData.shizukuRunning) {
                            DiagnosticRow("Shizuku API Version", "v${diagnosticData.shizukuVersion}")
                        }

                        if (!diagnosticData.shizukuRunning || !diagnosticData.shizukuPermission) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.diag_shizuku_not_connected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (diagnosticData.rawHalThermals.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Raw Thermal HAL Readings:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(8.dp)
                            ) {
                                diagnosticData.rawHalThermals.forEach { reading ->
                                    Text(
                                        reading,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Section 6: Permissions & Services
                DiagnosticsCard(
                    title = stringResource(R.string.diag_section_permissions),
                    icon = Icons.Default.CheckCircle,
                    items = listOf(
                        DiagnosticItem("Overlay Window Permission", if (diagnosticData.overlayPermission) "Granted" else "Denied"),
                        DiagnosticItem("Accessibility Service Running", if (diagnosticData.accessibilityRunning) "Active (Game Auto-Detect Enabled)" else "Inactive"),
                        DiagnosticItem("FPS Meter Overlay Service", if (diagnosticData.overlayServiceRunning) "Running" else "Stopped")
                    )
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    title: String,
    icon: ImageVector,
    items: List<DiagnosticItem>
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                DiagnosticRow(item.label, item.value)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.3f)
        )
    }
}

private suspend fun fetchDiagnostics(context: Context): DeviceDiagnosticData = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val appVersion = try {
        val pInfo = pm.getPackageInfo(context.packageName, 0)
        val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode
        "${pInfo.versionName} ($vCode)"
    } catch (_: Exception) {
        "Unknown"
    }
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    // Display info
    val displayMetrics = context.resources.displayMetrics
    val resolution = "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}"
    val densityDpi = displayMetrics.densityDpi

    val defaultDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try { context.display } catch (_: Exception) { wm?.defaultDisplay }
    } else {
        wm?.defaultDisplay
    }

    val refreshRateStr = defaultDisplay?.let {
        "${(it.refreshRate * 10f).roundToInt() / 10f} Hz"
    } ?: "Unknown"

    val supportedModesStr = defaultDisplay?.supportedModes?.map {
        "${(it.refreshRate).roundToInt()}Hz (${it.physicalWidth}x${it.physicalHeight})"
    }?.distinct()?.joinToString(", ") ?: ""

    // Battery info
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatusIntent = context.registerReceiver(null, ifilter)
    val rawTemp = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val batteryTemp = if (rawTemp > 0) "${rawTemp / 10f}°C" else "Unknown"

    val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryLevel = if (level >= 0 && scale > 0) "${(level * 100) / scale}%" else "Unknown"

    val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val batteryStatus = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
        else -> "Unknown"
    }

    val health = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
    val batteryHealth = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }

    // Power and thermal
    val isPowerSave = powerManager?.isPowerSaveMode ?: false
    val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal (None)"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
    } else {
        "N/A (< Android 10)"
    }

    val thermalHeadroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val headroom = powerManager?.getThermalHeadroom(30)
            if (headroom != null && !headroom.isNaN()) String.format("%.2f", headroom) else "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    } else {
        "N/A (< Android 11)"
    }

    // Graphics info
    val glEsVersion = am?.deviceConfigurationInfo?.glEsVersion ?: "Unknown"
    val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
    val vulkanSupport = if (hasVulkan) {
        val levelFeature = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        "Supported (Hardware level: ${if (levelFeature) "High" else "Base"})"
    } else {
        "Not Supported"
    }

    // Permissions
    val overlayPermission = Settings.canDrawOverlays(context)
    val accessibilityRunning = FpsAccessibilityService.isServiceRunning
    val overlayServiceRunning = FpsOverlayService.isRunning

    // Shizuku diagnostics
    val isShizukuRunning = try { Shizuku.pingBinder() } catch (_: Exception) { false }
    val isShizukuPermission = try {
        isShizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
    val shizukuVersion = try { if (isShizukuRunning) Shizuku.getVersion() else 0 } catch (_: Exception) { 0 }

    var socModelShizuku = ""
    var socPlatformShizuku = ""
    var surfaceFlingerGles = ""
    var surfaceFlingerRefreshRate = ""
    var shizukuCpuTemp = ""
    var shizukuGpuTemp = ""
    var shizukuSocTemp = ""
    val rawHalThermals = mutableListOf<String>()

    if (isShizukuPermission) {
        socModelShizuku = runShizukuShell("getprop ro.soc.model").trim()
        socPlatformShizuku = runShizukuShell("getprop ro.board.platform").trim()

        val sfOutput = runShizukuShell("dumpsys SurfaceFlinger")
        if (sfOutput.isNotEmpty()) {
            val glesLine = sfOutput.lines().firstOrNull { it.contains("GLES:") }?.trim() ?: ""
            surfaceFlingerGles = glesLine.removePrefix("GLES:").trim()
            val refreshLine = sfOutput.lines().firstOrNull { it.contains("peak-refresh-rate") || it.contains("vsyncRate") }?.trim() ?: ""
            surfaceFlingerRefreshRate = refreshLine
        }

        val thermalOutput = runShizukuShell("dumpsys thermalservice")
        if (thermalOutput.isNotEmpty()) {
            thermalOutput.lines().forEach { line ->
                if (line.contains("Temperature{mValue=")) {
                    rawHalThermals.add(line.trim())
                }
            }
            val monitor = SocThermalMonitor({}, {})
            val snapshot = monitor.parseThermalServiceSnapshot(thermalOutput)
            if (snapshot != null) {
                snapshot.cpu?.let { shizukuCpuTemp = "${it}°C" }
                snapshot.gpu?.let { shizukuGpuTemp = "${it}°C" }
                snapshot.soc?.let { shizukuSocTemp = "${it}°C" }
            }
        }
    }

    DeviceDiagnosticData(
        deviceModel = Build.MODEL,
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        deviceCode = Build.DEVICE,
        product = Build.PRODUCT,
        board = Build.BOARD,
        hardware = Build.HARDWARE,
        fingerprint = Build.FINGERPRINT,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        buildDisplay = Build.DISPLAY,
        supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
        resolution = resolution,
        refreshRate = refreshRateStr,
        supportedModes = supportedModesStr,
        densityDpi = densityDpi,
        appVersion = appVersion,
        cpuCores = Runtime.getRuntime().availableProcessors(),
        cpuArch = System.getProperty("os.arch") ?: "Unknown",
        glEsVersion = glEsVersion,
        vulkanSupport = vulkanSupport,
        batteryLevel = batteryLevel,
        batteryTemp = batteryTemp,
        batteryStatus = batteryStatus,
        batteryHealth = batteryHealth,
        powerSaveMode = isPowerSave,
        systemThermalStatus = thermalStatus,
        thermalHeadroom = thermalHeadroom,
        overlayPermission = overlayPermission,
        accessibilityRunning = accessibilityRunning,
        overlayServiceRunning = overlayServiceRunning,
        shizukuRunning = isShizukuRunning,
        shizukuPermission = isShizukuPermission,
        shizukuVersion = shizukuVersion,
        socModelShizuku = socModelShizuku,
        socPlatformShizuku = socPlatformShizuku,
        surfaceFlingerGles = surfaceFlingerGles,
        surfaceFlingerRefreshRate = surfaceFlingerRefreshRate,
        shizukuCpuTemp = shizukuCpuTemp,
        shizukuGpuTemp = shizukuGpuTemp,
        shizukuSocTemp = shizukuSocTemp,
        rawHalThermals = rawHalThermals.take(15)
    )
}

@Suppress("DEPRECATION")
private fun runShizukuShell(command: String): String {
    var process: Process? = null
    return try {
        process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
        }
        process.waitFor()
        sb.toString().trim()
    } catch (_: Exception) {
        ""
    } finally {
        try { process?.destroy() } catch (_: Exception) {}
    }
}

private fun formatMarkdownReport(d: DeviceDiagnosticData): String {
    val sb = StringBuilder()
    sb.appendLine("## FPS Meter - Device Diagnostic Report")
    sb.appendLine()
    sb.appendLine("### Device & OS")
    sb.appendLine("- **App Version:** ${d.appVersion}")
    sb.appendLine("- **Manufacturer / Model:** ${d.manufacturer} ${d.deviceModel}")
    sb.appendLine("- **Brand / Product:** ${d.brand} / ${d.product}")
    sb.appendLine("- **Device / Board:** ${d.deviceCode} / ${d.board}")
    sb.appendLine("- **Hardware:** ${d.hardware}")
    sb.appendLine("- **Android Version:** ${d.androidVersion} (API ${d.sdkInt})")
    sb.appendLine("- **Security Patch:** ${d.securityPatch}")
    sb.appendLine("- **Build Display:** ${d.buildDisplay}")
    sb.appendLine("- **Fingerprint:** `${d.fingerprint}`")
    sb.appendLine("- **Supported ABIs:** ${d.supportedAbis}")
    sb.appendLine()
    sb.appendLine("### Display & Screen")
    sb.appendLine("- **Resolution:** ${d.resolution} (${d.densityDpi} DPI)")
    sb.appendLine("- **Active Refresh Rate:** ${d.refreshRate}")
    sb.appendLine("- **Supported Modes:** ${d.supportedModes}")
    sb.appendLine()
    sb.appendLine("### CPU & Graphics")
    sb.appendLine("- **CPU Cores & Arch:** ${d.cpuCores} cores (${d.cpuArch})")
    sb.appendLine("- **OpenGL ES Version:** ${d.glEsVersion}")
    sb.appendLine("- **Vulkan Hardware:** ${d.vulkanSupport}")
    if (d.socModelShizuku.isNotEmpty()) {
        sb.appendLine("- **SoC Model (Shizuku):** ${d.socModelShizuku}")
    }
    if (d.socPlatformShizuku.isNotEmpty()) {
        sb.appendLine("- **Platform (Shizuku):** ${d.socPlatformShizuku}")
    }
    if (d.surfaceFlingerGles.isNotEmpty()) {
        sb.appendLine("- **GPU Driver (SurfaceFlinger):** ${d.surfaceFlingerGles}")
    }
    sb.appendLine()
    sb.appendLine("### Thermals & Battery")
    sb.appendLine("- **Battery Temp:** ${d.batteryTemp}")
    sb.appendLine("- **Battery Level:** ${d.batteryLevel} (${d.batteryStatus}, ${d.batteryHealth})")
    sb.appendLine("- **System Thermal Status:** ${d.systemThermalStatus}")
    sb.appendLine("- **Thermal Headroom:** ${d.thermalHeadroom}")
    sb.appendLine("- **Power Save Mode:** ${if (d.powerSaveMode) "Active" else "Inactive"}")
    if (d.shizukuCpuTemp.isNotEmpty()) sb.appendLine("- **CPU Temp (HAL):** ${d.shizukuCpuTemp}")
    if (d.shizukuGpuTemp.isNotEmpty()) sb.appendLine("- **GPU Temp (HAL):** ${d.shizukuGpuTemp}")
    if (d.shizukuSocTemp.isNotEmpty()) sb.appendLine("- **SoC Temp (HAL):** ${d.shizukuSocTemp}")
    sb.appendLine()
    sb.appendLine("### Permissions & Shizuku Status")
    sb.appendLine("- **Overlay Permission:** ${if (d.overlayPermission) "Granted" else "Denied"}")
    sb.appendLine("- **Accessibility Service:** ${if (d.accessibilityRunning) "Active" else "Inactive"}")
    sb.appendLine("- **Shizuku Service:** ${if (d.shizukuRunning) "Running (Permission: ${if (d.shizukuPermission) "Granted" else "Denied"})" else "Not Running"}")
    if (d.rawHalThermals.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("### Raw Thermal HAL Dump")
        sb.appendLine("```text")
        d.rawHalThermals.forEach { sb.appendLine(it) }
        sb.appendLine("```")
    }
    return sb.toString().trim()
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Device Diagnostics", text))
    Toast.makeText(context, context.getString(R.string.diag_copied_toast), Toast.LENGTH_SHORT).show()
}

private fun shareToGitHub(context: Context, d: DeviceDiagnosticData, report: String) {
    copyToClipboard(context, report)

    val issueTitle = "[Device Diagnostics] ${d.manufacturer} ${d.deviceModel} (Android ${d.androidVersion})"
    val shortBody = "### Problem / Diagnostic Context\n\n<!-- Full diagnostics copied to clipboard. Paste here if needed -->\n\n```markdown\n${report.take(2000)}\n```"
    val encodedTitle = Uri.encode(issueTitle)
    val encodedBody = Uri.encode(shortBody)
    val url = "https://github.com/rdevz-ph/FPS-Meter-Android/issues/new?title=$encodedTitle&body=$encodedBody"

    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.unable_to_open_browser), Toast.LENGTH_SHORT).show()
    }
}

private fun shareViaIntent(context: Context, d: DeviceDiagnosticData, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "[Device Diagnostics] ${d.manufacturer} ${d.deviceModel}")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.diag_share)))
}
