package com.rdevzph.fpsmeter.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rdevzph.fpsmeter.R
import com.rdevzph.fpsmeter.model.FpsSessionRecord
import com.rdevzph.fpsmeter.util.BenchmarkCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameAnalyticsScreen(
    game: GroupedGameRecord,
    onNavigateBack: () -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()) }

    // Session filter: null = All sessions combined, or a specific FpsSessionRecord
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<FpsSessionRecord?>(null) }
    var sessionToShare by remember { mutableStateOf<FpsSessionRecord?>(null) }

    val activeSession = remember(selectedSessionId, game.sessions) {
        if (selectedSessionId == null) null else game.sessions.firstOrNull { it.id == selectedSessionId }
    }

    // Combined or single session statistics
    val analyticsStats = remember(activeSession, game) {
        if (activeSession != null) {
            val samples = activeSession.fpsSamples.ifEmpty {
                synthesizeSamples(activeSession.durationSeconds, activeSession.avgFps, activeSession.minFps, activeSession.maxFps)
            }
            val tempSamples = activeSession.tempSamples
            val cpuSamples = activeSession.cpuTempSamples
            val gpuSamples = activeSession.gpuTempSamples
            val variance = if (samples.isNotEmpty()) {
                calculateVariance(samples)
            } else if (activeSession.fpsVariance > 0f) {
                if (activeSession.fpsVariance > 100f) kotlin.math.sqrt(activeSession.fpsVariance) else activeSession.fpsVariance
            } else 0f
            val smoothness = if (activeSession.smoothnessPercent > 0f) activeSession.smoothnessPercent else calculateSmoothness(samples, activeSession.maxFps)
            val low5 = if (activeSession.low5PercentFps > 0f) activeSession.low5PercentFps else calculateLow5(samples, activeSession.minFps)
            val maxT = if (activeSession.maxTemperature > 0f) activeSession.maxTemperature else (tempSamples.maxOrNull() ?: 0f)
            val maxCpu = if (activeSession.maxCpuTemperature > 0f) activeSession.maxCpuTemperature else (cpuSamples.maxOrNull() ?: 0f)
            val maxGpu = if (activeSession.maxGpuTemperature > 0f) activeSession.maxGpuTemperature else (gpuSamples.maxOrNull() ?: 0f)
            val avgP = activeSession.avgPowerWatts

            AnalyticsData(
                label = dateFormat.format(Date(activeSession.startTime)),
                durationSeconds = activeSession.durationSeconds,
                maxFps = activeSession.maxFps,
                minFps = activeSession.minFps,
                avgFps = activeSession.avgFps,
                variance = variance,
                smoothness = smoothness,
                low5Fps = low5,
                maxTemp = maxT,
                maxCpuTemp = maxCpu,
                maxGpuTemp = maxGpu,
                avgPower = avgP,
                fpsSamples = samples,
                tempSamples = tempSamples,
                cpuTempSamples = cpuSamples,
                gpuTempSamples = gpuSamples
            )
        } else {
            // Aggregate all sessions for this game
            val allFpsSamples = mutableListOf<Int>()
            val allTempSamples = mutableListOf<Float>()
            val allCpuSamples = mutableListOf<Float>()
            val allGpuSamples = mutableListOf<Float>()
            var totalPowerSum = 0f
            var powerCount = 0

            game.sessions.forEach { s ->
                val samples = s.fpsSamples.ifEmpty {
                    synthesizeSamples(s.durationSeconds, s.avgFps, s.minFps, s.maxFps)
                }
                allFpsSamples.addAll(samples)
                allTempSamples.addAll(s.tempSamples)
                allCpuSamples.addAll(s.cpuTempSamples)
                allGpuSamples.addAll(s.gpuTempSamples)
                if (s.avgPowerWatts > 0f) {
                    totalPowerSum += s.avgPowerWatts
                    powerCount++
                }
            }

            val maxFps = game.peakFps
            val minFps = game.lowestFps
            val avgFps = game.overallAvgFps
            val variance = calculateVariance(allFpsSamples)
            val smoothness = calculateSmoothness(allFpsSamples, maxFps)
            val low5 = calculateLow5(allFpsSamples, minFps)
            val maxT = allTempSamples.maxOrNull() ?: 0f
            val maxCpu = allCpuSamples.maxOrNull() ?: 0f
            val maxGpu = allGpuSamples.maxOrNull() ?: 0f
            val avgP = if (powerCount > 0) totalPowerSum / powerCount else 0f

            AnalyticsData(
                label = "${game.sessionCount} sessions • Total: ${formatDuration(game.totalDurationSeconds)}",
                durationSeconds = game.totalDurationSeconds,
                maxFps = maxFps,
                minFps = minFps,
                avgFps = avgFps,
                variance = variance,
                smoothness = smoothness,
                low5Fps = low5,
                maxTemp = maxT,
                maxCpuTemp = maxCpu,
                maxGpuTemp = maxGpu,
                avgPower = avgP,
                fpsSamples = allFpsSamples,
                tempSamples = allTempSamples,
                cpuTempSamples = allCpuSamples,
                gpuTempSamples = allGpuSamples
            )
        }
    }

    // CSV Document Export Launcher
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val csvContent = generateCsvContent(game, activeSession, analyticsStats)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(csvContent.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.csv_saved_toast), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.csv_failed_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun triggerSaveCsv() {
        val cleanName = game.appName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        saveCsvLauncher.launch("fps_record_${cleanName}_$timestamp.csv")
    }

    fun triggerShareCsv() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val csvContent = generateCsvContent(game, activeSession, analyticsStats)
                val cacheDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
                val cleanName = game.appName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(cacheDir, "fps_record_${cleanName}_$timestamp.csv")
                file.writeText(csvContent, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "${game.appName} FPS Performance Recording")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_csv)).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.csv_failed_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Delete single session confirmation dialog
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(stringResource(R.string.delete_session_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(stringResource(R.string.delete_session_msg, game.appName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(session.id)
                        if (selectedSessionId == session.id) {
                            selectedSessionId = null
                        }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToDelete = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Share benchmark scorecard dialog
    sessionToShare?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToShare = null },
            title = { Text(stringResource(R.string.share_benchmark_scorecard), fontWeight = FontWeight.Bold) },
            text = {
                Text("Generate and share a verified benchmark scorecard image for this session?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = sessionToShare
                        sessionToShare = null
                        if (target != null) {
                            coroutineScope.launch {
                                BenchmarkCardGenerator.shareSessionCard(context, target)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.common_share))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToShare = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Compact Top Toolbar matching Scene reference design
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.width(6.dp))

            Text(
                text = game.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Session selector filter
            Box {
                IconButton(
                    onClick = { showFilterMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.analytics_session_selector),
                        tint = if (selectedSessionId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.analytics_all_sessions),
                                fontWeight = if (selectedSessionId == null) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            if (selectedSessionId == null) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            selectedSessionId = null
                            showFilterMenu = false
                        }
                    )
                    HorizontalDivider()
                    game.sessions.forEachIndexed { idx, s ->
                        val dateStr = shortDateFormat.format(Date(s.startTime))
                        val durStr = formatDuration(s.durationSeconds)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        "Session ${game.sessions.size - idx}: $durStr",
                                        fontWeight = if (selectedSessionId == s.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        "$dateStr • ${s.avgFps} FPS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                if (selectedSessionId == s.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                selectedSessionId = s.id
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }

            // CSV Export options
            var showExportMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showExportMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.analytics_export_csv),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_csv)) },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = {
                            showExportMenu = false
                            triggerSaveCsv()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_csv)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showExportMenu = false
                            triggerShareCsv()
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Performance Summary Card
            item {
                PerformanceSummaryCard(
                    stats = analyticsStats,
                    appName = game.appName,
                    packageName = game.packageName
                )
            }

            // Card 2: FPS & Temperature Timeline Chart
            item {
                FpsTemperatureChartCard(
                    stats = analyticsStats
                )
            }

            // Card 3: Frame Time (ms) Chart
            item {
                FrameTimeChartCard(
                    stats = analyticsStats
                )
            }

            // Card 4: Recorded Sessions breakdown list
            if (game.sessions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.analytics_recorded_sessions, game.sessions.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                items(game.sessions.size) { index ->
                    val s = game.sessions[index]
                    val isSelected = selectedSessionId == s.id
                    SessionRowCard(
                        session = s,
                        index = game.sessions.size - index,
                        formattedDate = shortDateFormat.format(Date(s.startTime)),
                        isSelected = isSelected,
                        onSelect = {
                            selectedSessionId = if (isSelected) null else s.id
                        },
                        onShare = { sessionToShare = s },
                        onDelete = { sessionToDelete = s }
                    )
                }
            }
        }
    }
}

data class AnalyticsData(
    val label: String,
    val durationSeconds: Long,
    val maxFps: Int,
    val minFps: Int,
    val avgFps: Int,
    val variance: Float,
    val smoothness: Float,
    val low5Fps: Float,
    val maxTemp: Float,
    val maxCpuTemp: Float = 0f,
    val maxGpuTemp: Float = 0f,
    val avgPower: Float,
    val fpsSamples: List<Int>,
    val tempSamples: List<Float>,
    val cpuTempSamples: List<Float> = emptyList(),
    val gpuTempSamples: List<Float> = emptyList()
)

@Composable
private fun PerformanceSummaryCard(
    stats: AnalyticsData,
    appName: String,
    packageName: String
) {
    val hasSocTemps = stats.maxCpuTemp > 0f || stats.maxGpuTemp > 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: App icon + Session label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(packageName = packageName, appName = appName, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stats.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 5-Column Grid Layout: Exactly 2 rows when CPU/GPU thermals exist (10 metrics total)
            if (hasSocTemps) {
                // Row 1 (5 items): MAX, MIN, AVG, VARIANCE, SMOOTHNESS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_max_fps),
                        value = String.format(Locale.US, "%.1f", stats.maxFps.toFloat()),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_min_fps),
                        value = String.format(Locale.US, "%.1f", stats.minFps.toFloat()),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_avg_fps),
                        value = String.format(Locale.US, "%.1f", stats.avgFps.toFloat()),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_variance),
                        value = String.format(Locale.US, "%.1f", stats.variance),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.smoothness_score_label),
                        value = String.format(Locale.US, "%.1f%%", stats.smoothness),
                        bottomLabel = stringResource(R.string.analytics_smoothness),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Row 2 (5 items): 5% LOW, BATTERY, CPU, GPU, POWER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_low_5),
                        value = String.format(Locale.US, "%.1f", stats.low5Fps),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_max_fps),
                        value = if (stats.maxTemp > 0f) String.format(Locale.US, "%.1f°C", stats.maxTemp) else "N/A",
                        bottomLabel = stringResource(R.string.analytics_temp_label),
                        valueColor = if (stats.maxTemp > 0f) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_max_fps),
                        value = if (stats.maxCpuTemp > 0f) String.format(Locale.US, "%.1f°C", stats.maxCpuTemp) else "N/A",
                        bottomLabel = stringResource(R.string.analytics_cpu_temp_label),
                        valueColor = if (stats.maxCpuTemp > 0f) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_max_fps),
                        value = if (stats.maxGpuTemp > 0f) String.format(Locale.US, "%.1f°C", stats.maxGpuTemp) else "N/A",
                        bottomLabel = stringResource(R.string.analytics_gpu_temp_label),
                        valueColor = if (stats.maxGpuTemp > 0f) Color(0xFFAB47BC) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_avg_fps),
                        value = if (stats.avgPower > 0f) String.format(Locale.US, "%.2f", stats.avgPower) else formatDuration(stats.durationSeconds),
                        bottomLabel = if (stats.avgPower > 0f) stringResource(R.string.analytics_power_label) else stringResource(R.string.bench_duration),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Standard 4-Column Grid: 2 rows of 4 columns (8 metrics total)
                // Row 1: MAX, MIN, AVG, VARIANCE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_max_fps),
                        value = String.format(Locale.US, "%.1f", stats.maxFps.toFloat()),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_min_fps),
                        value = String.format(Locale.US, "%.1f", stats.minFps.toFloat()),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_avg_fps),
                        value = String.format(Locale.US, "%.1f", stats.avgFps.toFloat()),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_variance),
                        value = String.format(Locale.US, "%.1f", stats.variance),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Row 2: Smoothness, 5% Low, MAX Temp, AVG Power
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricTile(
                        topLabel = stringResource(R.string.smoothness_score_label),
                        value = String.format(Locale.US, "%.1f%%", stats.smoothness),
                        bottomLabel = stringResource(R.string.analytics_smoothness),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_low_5),
                        value = String.format(Locale.US, "%.1f", stats.low5Fps),
                        bottomLabel = "FPS",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_max_fps),
                        value = if (stats.maxTemp > 0f) String.format(Locale.US, "%.1f°C", stats.maxTemp) else "N/A",
                        bottomLabel = stringResource(R.string.analytics_temp_label),
                        valueColor = if (stats.maxTemp > 0f) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        topLabel = stringResource(R.string.analytics_avg_fps),
                        value = if (stats.avgPower > 0f) String.format(Locale.US, "%.2f", stats.avgPower) else formatDuration(stats.durationSeconds),
                        bottomLabel = if (stats.avgPower > 0f) stringResource(R.string.analytics_power_label) else stringResource(R.string.bench_duration),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    topLabel: String,
    value: String,
    bottomLabel: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = topLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = bottomLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FpsTemperatureChartCard(stats: AnalyticsData) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    val samples = stats.fpsSamples
    val tempSamples = stats.tempSamples
    val cpuSamples = stats.cpuTempSamples
    val gpuSamples = stats.gpuTempSamples

    val hasBatTemp = tempSamples.isNotEmpty()
    val hasCpuTemp = cpuSamples.isNotEmpty()
    val hasGpuTemp = gpuSamples.isNotEmpty()
    val hasAnyTemp = hasBatTemp || hasCpuTemp || hasGpuTemp

    val batColor = Color(0xFFFFA000)   // Amber/Orange for Battery
    val cpuColor = Color(0xFFFF5722)   // Deep Orange/Coral for CPU
    val gpuColor = Color(0xFFAB47BC)   // Purple for GPU

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.analytics_fps_chart),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hasAnyTemp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.analytics_temp_chart),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Chart Canvas
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            val fpsLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            val indicatorColor = MaterialTheme.colorScheme.primary

            val maxFpsVal = max(120, ((stats.maxFps + 9) / 10) * 10)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .pointerInput(samples) {
                        detectTapGestures(
                            onTap = { offset -> touchX = offset.x },
                            onPress = { offset ->
                                touchX = offset.x
                                tryAwaitRelease()
                                touchX = null
                            }
                        )
                    }
                    .pointerInput(samples) {
                        detectDragGestures(
                            onDrag = { change, _ -> touchX = change.position.x },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val leftPadding = 32f
                    val rightPadding = if (hasAnyTemp) 32f else 12f
                    val topPadding = 12f
                    val bottomPadding = 20f

                    val chartWidth = w - leftPadding - rightPadding
                    val chartHeight = h - topPadding - bottomPadding

                    // 1. Draw horizontal grid lines (0, 30, 60, 90, 120...)
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val y = topPadding + chartHeight * (i.toFloat() / gridSteps)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPadding, y),
                            end = Offset(w - rightPadding, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // 2. Draw FPS Path
                    if (samples.isNotEmpty()) {
                        val fpsPath = Path()
                        val stepX = if (samples.size > 1) chartWidth / (samples.size - 1) else chartWidth

                        samples.forEachIndexed { i, fpsVal ->
                            val x = leftPadding + i * stepX
                            val normalizedFps = (fpsVal.toFloat() / maxFpsVal).coerceIn(0f, 1f)
                            val y = topPadding + chartHeight * (1f - normalizedFps)
                            if (i == 0) {
                                fpsPath.moveTo(x, y)
                            } else {
                                fpsPath.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = fpsPath,
                            color = fpsLineColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 3. Draw Temperature Paths (Battery, CPU, GPU) on shared temperature axis
                    if (hasAnyTemp) {
                        val minTemp = 30f
                        val peakObservedTemp = maxOf(
                            tempSamples.maxOrNull() ?: 0f,
                            cpuSamples.maxOrNull() ?: 0f,
                            gpuSamples.maxOrNull() ?: 0f,
                            45f
                        )
                        val maxTemp = max(50f, peakObservedTemp + 2f)
                        val tempRange = max(1f, maxTemp - minTemp)

                        // Helper function to draw a temperature line
                        fun drawTempLine(tempData: List<Float>, lineColor: Color) {
                            if (tempData.isEmpty()) return
                            val path = Path()
                            val stepX = if (tempData.size > 1) chartWidth / (tempData.size - 1) else chartWidth
                            tempData.forEachIndexed { i, tempVal ->
                                val x = leftPadding + i * stepX
                                val normalizedTemp = ((tempVal - minTemp) / tempRange).coerceIn(0f, 1f)
                                val y = topPadding + chartHeight * (1f - normalizedTemp)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Draw Battery Temp
                        drawTempLine(tempSamples, batColor)
                        // Draw CPU Temp
                        drawTempLine(cpuSamples, cpuColor)
                        // Draw GPU Temp
                        drawTempLine(gpuSamples, gpuColor)
                    }

                    // 4. Touch Indicator vertical line
                    touchX?.let { tx ->
                        val clampedX = tx.coerceIn(leftPadding, w - rightPadding)
                        drawLine(
                            color = indicatorColor,
                            start = Offset(clampedX, topPadding),
                            end = Offset(clampedX, topPadding + chartHeight),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                // Left Y-Axis labels (120, 90, 60, 30, 0)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$maxFpsVal", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${maxFpsVal * 3 / 4}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${maxFpsVal / 2}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${maxFpsVal / 4}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Touch tooltip display
                touchX?.let { tx ->
                    if (samples.isNotEmpty()) {
                        val sampleIdx = ((tx / (samples.size.toFloat())).toInt()).coerceIn(0, samples.lastIndex)
                        val fpsAtTouch = samples[sampleIdx]
                        val batAtTouch = if (hasBatTemp && sampleIdx < tempSamples.size) tempSamples[sampleIdx] else null
                        val cpuAtTouch = if (hasCpuTemp && sampleIdx < cpuSamples.size) cpuSamples[sampleIdx] else null
                        val gpuAtTouch = if (hasGpuTemp && sampleIdx < gpuSamples.size) gpuSamples[sampleIdx] else null

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 3.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(formatDuration(sampleIdx.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("$fpsAtTouch FPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                if (batAtTouch != null && batAtTouch > 0f) {
                                    Text(String.format(Locale.US, "BAT: %.1f°C", batAtTouch), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = batColor)
                                }
                                if (cpuAtTouch != null && cpuAtTouch > 0f) {
                                    Text(String.format(Locale.US, "CPU: %.1f°C", cpuAtTouch), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cpuColor)
                                }
                                if (gpuAtTouch != null && gpuAtTouch > 0f) {
                                    Text(String.format(Locale.US, "GPU: %.1f°C", gpuAtTouch), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = gpuColor)
                                }
                            }
                        }
                    }
                }
            }

            // X-Axis Time Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dur = stats.durationSeconds
                Text("0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur / 4), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur / 2), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur * 3 / 4), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(4.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendPill(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), label = "FPS")
                if (hasBatTemp) {
                    Spacer(Modifier.width(14.dp))
                    LegendPill(color = batColor, label = "BAT(°C)")
                }
                if (hasCpuTemp) {
                    Spacer(Modifier.width(14.dp))
                    LegendPill(color = cpuColor, label = "CPU(°C)")
                }
                if (hasGpuTemp) {
                    Spacer(Modifier.width(14.dp))
                    LegendPill(color = gpuColor, label = "GPU(°C)")
                }
            }
        }
    }
}

@Composable
private fun FrameTimeChartCard(stats: AnalyticsData) {
    val samples = stats.fpsSamples
    val frameTimes = remember(samples) {
        samples.map { if (it > 0) 1000f / it else 0f }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.analytics_frametime_chart),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            val barColor = Color(0xFF64B5F6) // Sky Blue matching Scene reference
            val targetLine60Fps = Color(0xFF4CAF50).copy(alpha = 0.6f)
            val maxFrameTime = 50f // 50ms cap (20 FPS)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val leftPadding = 32f
                    val rightPadding = 12f
                    val topPadding = 12f
                    val bottomPadding = 20f

                    val chartWidth = w - leftPadding - rightPadding
                    val chartHeight = h - topPadding - bottomPadding

                    // 1. Draw horizontal grid lines (0, 8, 16.6, 25, 33.3, 41, 50 ms)
                    val msMarkers = listOf(8f, 16.67f, 25f, 33.33f, 41f, 50f)
                    msMarkers.forEach { ms ->
                        val norm = (ms / maxFrameTime).coerceIn(0f, 1f)
                        val y = topPadding + chartHeight * (1f - norm)
                        drawLine(
                            color = if (ms == 16.67f) targetLine60Fps else gridColor,
                            start = Offset(leftPadding, y),
                            end = Offset(w - rightPadding, y),
                            strokeWidth = if (ms == 16.67f) 1.5f else 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // 2. Draw Frame Time Bars / Histogram
                    if (frameTimes.isNotEmpty()) {
                        val barWidth = max(1f, chartWidth / frameTimes.size)
                        frameTimes.forEachIndexed { i, msVal ->
                            val x = leftPadding + i * barWidth
                            val clampedMs = msVal.coerceIn(0f, maxFrameTime)
                            val barHeight = chartHeight * (clampedMs / maxFrameTime)
                            val y = topPadding + chartHeight - barHeight

                            drawRect(
                                color = barColor,
                                topLeft = Offset(x, y),
                                size = Size(max(1f, barWidth - 0.5f), barHeight)
                            )
                        }
                    }
                }

                // Y-Axis ms Labels
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("50", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("41", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("33", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("25", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("16", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF4CAF50))
                    Text("8", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // X-Axis Time Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dur = stats.durationSeconds
                Text("0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur / 4), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur / 2), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur * 3 / 4), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(dur), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionRowCard(
    session: FpsSessionRecord,
    index: Int,
    formattedDate: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatDuration(session.durationSeconds)} • Avg ${session.avgFps} FPS (Peak: ${session.maxFps} | Low: ${session.minFps})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helpers for calculations and formatting
private fun formatDuration(durationSeconds: Long): String {
    val min = durationSeconds / 60
    val sec = durationSeconds % 60
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}

private fun calculateVariance(samples: List<Int>): Float {
    if (samples.isEmpty()) return 0f
    val mean = samples.average()
    val sumSquared = samples.sumOf { (it - mean) * (it - mean) }
    return String.format(Locale.US, "%.1f", kotlin.math.sqrt(sumSquared / samples.size)).toFloatOrNull() ?: 0f
}

private fun calculateSmoothness(samples: List<Int>, high: Int): Float {
    if (samples.isEmpty()) return 100f
    val threshold = if (high >= 90) 60 else 45
    val smoothCount = samples.count { it >= threshold }
    return String.format(Locale.US, "%.1f", smoothCount.toFloat() / samples.size * 100f).toFloatOrNull() ?: 100f
}

private fun calculateLow5(samples: List<Int>, minFps: Int): Float {
    if (samples.isEmpty()) return minFps.toFloat()
    val sorted = samples.sorted()
    val idx = (sorted.size * 0.05).toInt().coerceIn(0, sorted.lastIndex)
    return sorted[idx].toFloat()
}

private fun synthesizeSamples(durationSec: Long, avg: Int, min: Int, max: Int): List<Int> {
    val count = durationSec.toInt().coerceIn(1, 3600)
    return List(count) { avg }
}

private fun generateCsvContent(
    game: GroupedGameRecord,
    selectedSession: FpsSessionRecord?,
    stats: AnalyticsData
): String {
    val sb = StringBuilder()
    sb.appendLine("# FPS Meter - Performance Recording Data")
    sb.appendLine("# Application: ${game.appName} (${game.packageName})")
    sb.appendLine("# Exported Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    sb.appendLine("# Session Mode: ${if (selectedSession != null) "Single Session (${stats.label})" else "All Sessions Combined"}")
    sb.appendLine("# Duration: ${formatDuration(stats.durationSeconds)}")
    sb.appendLine("# Max FPS: ${stats.maxFps}, Min FPS: ${stats.minFps}, Avg FPS: ${stats.avgFps}")
    sb.appendLine("# Variance: ${stats.variance}, Smoothness (>=45FPS): ${stats.smoothness}%, 5% Low FPS: ${stats.low5Fps}")
    if (stats.maxTemp > 0f) sb.appendLine("# Max Battery Temperature: ${stats.maxTemp} C")
    if (stats.maxCpuTemp > 0f) sb.appendLine("# Max CPU Temperature: ${stats.maxCpuTemp} C")
    if (stats.maxGpuTemp > 0f) sb.appendLine("# Max GPU Temperature: ${stats.maxGpuTemp} C")
    if (stats.avgPower > 0f) sb.appendLine("# Avg Power: ${stats.avgPower} W")
    sb.appendLine("#")
    sb.appendLine("Elapsed_Seconds,FPS,FrameTime_ms,Battery_Temp_C,CPU_Temp_C,GPU_Temp_C")

    val samples = stats.fpsSamples
    val temps = stats.tempSamples
    val cpuTemps = stats.cpuTempSamples
    val gpuTemps = stats.gpuTempSamples

    samples.forEachIndexed { sec, fps ->
        val frameTimeMs = if (fps > 0) String.format(Locale.US, "%.2f", 1000f / fps) else "0.00"
        val batTemp = if (sec < temps.size && temps[sec] > 0f) String.format(Locale.US, "%.1f", temps[sec]) else ""
        val cpuTemp = if (sec < cpuTemps.size && cpuTemps[sec] > 0f) String.format(Locale.US, "%.1f", cpuTemps[sec]) else ""
        val gpuTemp = if (sec < gpuTemps.size && gpuTemps[sec] > 0f) String.format(Locale.US, "%.1f", gpuTemps[sec]) else ""
        sb.appendLine("$sec,$fps,$frameTimeMs,$batTemp,$cpuTemp,$gpuTemp")
    }

    return sb.toString()
}
