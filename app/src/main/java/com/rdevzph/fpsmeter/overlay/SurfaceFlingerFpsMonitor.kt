package com.rdevzph.fpsmeter.overlay

import android.content.pm.PackageManager
import android.util.Log
import com.rdevzph.fpsmeter.model.GraphicsApi
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

/**
 * Monitors game FPS and Graphics API (Vulkan vs OpenGL ES) directly from SurfaceFlinger
 * using Shizuku privileged shell commands.
 */
class SurfaceFlingerFpsMonitor(
    private val onFpsUpdate: (fps: Int, frameTimeMs: Float, api: GraphicsApi, layerName: String?) -> Unit,
    private val onFallbackNeeded: () -> Unit
) {
    companion object {
        private const val TAG = "SurfaceFlingerMonitor"
        private const val SAMPLE_INTERVAL_MS = 1000L
        private const val PENDING_TIMESTAMP = 9223372036854775807L // Long.MAX_VALUE
    }

    private var monitorJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastSampleTime = 0L
    private var lastPresentTimestamp = 0L
    private var lastLayerFrameCount = -1L
    private var currentTargetLayer: String? = null
    private var currentForegroundPackage: String? = null
    private var currentGraphicsApi = GraphicsApi.UNKNOWN
    private var zeroFpsCount = 0

    private var consecutiveFailures = 0

    fun start() {
        stop()
        Log.d(TAG, "Starting SurfaceFlinger FPS monitor...")
        lastSampleTime = System.currentTimeMillis()
        lastPresentTimestamp = 0L
        lastLayerFrameCount = -1L
        zeroFpsCount = 0
        consecutiveFailures = 0

        monitorJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val isShizukuReady = try {
                        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    } catch (e: Exception) {
                        false
                    }

                    if (!isShizukuReady) {
                        Log.w(TAG, "Shizuku not ready, triggering fallback")
                        withContext(Dispatchers.Main) { onFallbackNeeded() }
                        break
                    }

                    sampleFps()
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in SurfaceFlinger sampling loop", e)
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        withContext(Dispatchers.Main) { onFallbackNeeded() }
                        break
                    }
                }

                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        currentTargetLayer = null
        currentForegroundPackage = null
        currentGraphicsApi = GraphicsApi.UNKNOWN
        lastPresentTimestamp = 0L
        lastLayerFrameCount = -1L
        zeroFpsCount = 0
    }

    private suspend fun sampleFps() {
        val now = System.currentTimeMillis()
        val elapsedMs = if (lastSampleTime == 0L) 1000L else (now - lastSampleTime).coerceAtLeast(1L)
        lastSampleTime = now

        // 1. Identify Foreground Package
        val foregroundPkg = getForegroundPackage()
        if (foregroundPkg == null) {
            // User is on launcher, home screen, recents, or system UI
            currentForegroundPackage = null
            currentTargetLayer = null
            lastPresentTimestamp = 0L
            lastLayerFrameCount = -1L
            zeroFpsCount = 0
            consecutiveFailures = 0
            withContext(Dispatchers.Main) {
                onFpsUpdate(0, 0f, GraphicsApi.UNKNOWN, null)
            }
            return
        }

        if (foregroundPkg != currentForegroundPackage) {
            Log.d(TAG, "Foreground package changed: '$currentForegroundPackage' -> '$foregroundPkg'")
            currentForegroundPackage = foregroundPkg
            currentGraphicsApi = detectGraphicsApi(foregroundPkg)
            currentTargetLayer = null
            lastPresentTimestamp = 0L
            lastLayerFrameCount = -1L
            zeroFpsCount = 0
        }

        // 2. Resolve Target Layer
        var layer = currentTargetLayer
        if (layer == null) {
            layer = resolveLayerForPackage(foregroundPkg)
            currentTargetLayer = layer
            lastPresentTimestamp = 0L
            lastLayerFrameCount = -1L
            zeroFpsCount = 0
            Log.d(TAG, "Resolved target layer for $foregroundPkg: $layer")
        }

        if (layer == null) {
            withContext(Dispatchers.Main) {
                onFpsUpdate(0, 0f, currentGraphicsApi, null)
            }
            return
        }

        // 3. Measure FPS based on Graphics API & Latency / Frame Counts
        val fpsResult = measureLayerFps(layer, elapsedMs, currentGraphicsApi)

        // 4. Self-healing layer recovery if stuck at 0 FPS
        if (fpsResult.fps <= 0) {
            zeroFpsCount++
            // If the current layer produced 0 FPS for 2 consecutive samples, invalidate it
            // so we re-scan SurfaceFlinger for the active rendering layer next tick
            if (zeroFpsCount >= 2) {
                Log.d(TAG, "Layer '$layer' produced 0 FPS for $zeroFpsCount samples. Invalidating to re-resolve.")
                currentTargetLayer = null
                lastPresentTimestamp = 0L
                lastLayerFrameCount = -1L
                zeroFpsCount = 0
            }
        } else {
            zeroFpsCount = 0
        }

        consecutiveFailures = 0
        withContext(Dispatchers.Main) {
            onFpsUpdate(fpsResult.fps, fpsResult.frameTimeMs, currentGraphicsApi, layer)
        }
    }

    private data class MeasurementResult(val fps: Int, val frameTimeMs: Float)

    private fun measureLayerFps(layer: String, elapsedMs: Long, api: GraphicsApi): MeasurementResult {
        // Query dumpsys SurfaceFlinger --latency '<layer>'
        val latencyOutput = runShellCommand("dumpsys SurfaceFlinger --latency '$layer'")
        val lines = latencyOutput.lines().filter { it.isNotBlank() }

        var calculatedFps = -1
        var frameTimeMs = 0f

        if (lines.size > 1) {
            val presentTimestamps = mutableListOf<Long>()
            for (i in 1 until lines.size) {
                val parts = lines[i].trim().split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val col1 = parts[1].toLongOrNull() ?: 0L
                    val col2 = parts[2].toLongOrNull() ?: 0L

                    val timestamp = if (col1 > 0 && col1 != PENDING_TIMESTAMP) {
                        col1
                    } else if (col2 > 0 && col2 != PENDING_TIMESTAMP) {
                        col2
                    } else {
                        0L
                    }

                    if (timestamp > 0 && timestamp != PENDING_TIMESTAMP) {
                        presentTimestamps.add(timestamp)
                    }
                }
            }

            if (presentTimestamps.isNotEmpty()) {
                val currentMax = presentTimestamps.maxOrNull() ?: 0L

                if (lastPresentTimestamp == 0L || currentMax < lastPresentTimestamp) {
                    // First sample or timestamps wrapped/desynced:
                    // Compute from internal buffer interval if available
                    if (presentTimestamps.size >= 2) {
                        val minTs = presentTimestamps.minOrNull() ?: 0L
                        val maxTs = currentMax
                        val durationNs = maxTs - minTs
                        if (durationNs > 0) {
                            val durationSec = durationNs / 1_000_000_000f
                            if (durationSec in 0.01f..2.5f) {
                                calculatedFps = ((presentTimestamps.size - 1) / durationSec).roundToInt()
                            }
                        }
                    }
                    lastPresentTimestamp = currentMax
                } else {
                    // Subsequent samples: count newly presented frames since last timestamp
                    val newFrames = presentTimestamps.filter { it > lastPresentTimestamp }
                    calculatedFps = ((newFrames.size * 1000f) / elapsedMs).roundToInt()
                    if (newFrames.isNotEmpty()) {
                        lastPresentTimestamp = newFrames.maxOrNull() ?: lastPresentTimestamp
                    }
                }
            }
        }

        // Secondary / Cross-check measurement: SurfaceFlinger active layer frame counter
        // Crucial for Vulkan games or drivers that don't output full timestamps to --latency
        val layerFrameCount = queryLayerFrameCount(layer)
        if (layerFrameCount != null && layerFrameCount >= 0) {
            if (lastLayerFrameCount >= 0) {
                val deltaFrames = (layerFrameCount - lastLayerFrameCount).coerceAtLeast(0L)
                val counterFps = ((deltaFrames * 1000f) / elapsedMs).roundToInt()

                if (calculatedFps <= 0 && counterFps > 0) {
                    calculatedFps = counterFps
                } else if (api == GraphicsApi.VULKAN && counterFps > 0 && calculatedFps > 0) {
                    calculatedFps = if (Math.abs(counterFps - calculatedFps) <= 3) calculatedFps else counterFps
                }
            }
            lastLayerFrameCount = layerFrameCount
        }

        val finalFps = calculatedFps.coerceAtLeast(0)
        frameTimeMs = if (finalFps > 0) (1000f / finalFps) else 0f

        return MeasurementResult(finalFps, frameTimeMs)
    }

    private fun queryLayerFrameCount(layer: String): Long? {
        try {
            val sanitized = layer.substringBefore("#").trim()
            val output = runShellCommand("dumpsys SurfaceFlinger")
            for (line in output.lines()) {
                if (line.contains(sanitized) && !line.contains("Background for") && line.contains("frame=")) {
                    val match = Regex("frame=(\\d+)").find(line)
                    if (match != null) {
                        return match.groupValues[1].toLongOrNull()
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Finds the foreground package name using dumpsys window or dumpsys activity.
     */
    private fun getForegroundPackage(): String? {
        val windowOutput = runShellCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        for (line in windowOutput.lines()) {
            val match = Regex("(?:mCurrentFocus|mFocusedApp)=.*?([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
            if (match != null) {
                val pkg = match.groupValues[1]
                if (isGameCandidate(pkg)) {
                    return pkg
                }
            }
        }

        // Fallback: dumpsys activity
        val actOutput = runShellCommand("dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity'")
        for (line in actOutput.lines()) {
            val match = Regex("([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
            if (match != null) {
                val pkg = match.groupValues[1]
                if (isGameCandidate(pkg)) {
                    return pkg
                }
            }
        }

        return null
    }

    private fun isGameCandidate(pkg: String): Boolean {
        if (pkg == "android" ||
            pkg.contains("systemui", ignoreCase = true) ||
            pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("quickstep", ignoreCase = true) ||
            pkg == "com.rdevzph.fpsmeter"
        ) {
            return false
        }
        return true
    }

    /**
     * Detects whether the running game is using Vulkan or OpenGL ES.
     */
    private fun detectGraphicsApi(packageName: String): GraphicsApi {
        try {
            // 1. Primary inspection via Android GPU service: dumpsys gpu
            val gpuOutput = runShellCommand("dumpsys gpu")
            val packageSection = extractPackageSectionFromGpuDump(gpuOutput, packageName)

            if (packageSection != null) {
                val hasVulkanSwapchain = packageSection.contains("createdVulkanSwapchain = 1") ||
                        packageSection.contains("createdVulkanDevice = 1")
                val hasGlesContext = packageSection.contains("createdGlesContext = 1") ||
                        packageSection.contains("gles1InUse = 1")

                if (hasVulkanSwapchain) {
                    Log.d(TAG, "Package $packageName detected as VULKAN via dumpsys gpu (createdVulkanSwapchain=1)")
                    return GraphicsApi.VULKAN
                } else if (hasGlesContext) {
                    Log.d(TAG, "Package $packageName detected as OPENGL via dumpsys gpu (createdGlesContext=1)")
                    return GraphicsApi.OPENGL
                }
            }

            // 2. Secondary inspection: dumpsys gfxinfo <package>
            val gfxOutput = runShellCommand("dumpsys gfxinfo $packageName | grep -i 'Pipeline'")
            if (gfxOutput.contains("Pipeline=Skia (Vulkan)", ignoreCase = true)) {
                return GraphicsApi.VULKAN
            } else if (gfxOutput.contains("Pipeline=Skia (OpenGL)", ignoreCase = true)) {
                return GraphicsApi.OPENGL
            }

            // 3. Known heuristics for popular games
            val lowerPkg = packageName.lowercase()
            if (lowerPkg.contains("genshinimpact") || lowerPkg.contains("hoyoverse")) {
                return GraphicsApi.VULKAN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect graphics API for $packageName", e)
        }

        return GraphicsApi.OPENGL // Safe standard default on Android
    }

    private fun extractPackageSectionFromGpuDump(gpuDump: String, packageName: String): String? {
        val marker = "appPackageName = $packageName"
        val startIndex = gpuDump.indexOf(marker)
        if (startIndex == -1) return null

        val nextSectionIndex = gpuDump.indexOf("appPackageName =", startIndex + marker.length)
        return if (nextSectionIndex != -1) {
            gpuDump.substring(startIndex, nextSectionIndex)
        } else {
            gpuDump.substring(startIndex)
        }
    }

    /**
     * Resolves the active rendering layer in SurfaceFlinger for the given package.
     */
    private fun resolveLayerForPackage(packageName: String): String? {
        // Step 1: Scan active Output Layers from SurfaceFlinger
        // Output Layers represent what SurfaceFlinger is actively presenting to the screen
        try {
            val sfOutput = runShellCommand("dumpsys SurfaceFlinger | grep -E 'Output Layer.*${Regex.escape(packageName)}'")
            val outputLayers = mutableListOf<String>()
            val outputRegex = Regex("Output Layer.*?\\((.*?${Regex.escape(packageName)}.*?)\\)")
            for (match in outputRegex.findAll(sfOutput)) {
                val candidate = match.groupValues[1].trim()
                if (isRenderableLayer(candidate, packageName)) {
                    outputLayers.add(candidate)
                }
            }

            if (outputLayers.isNotEmpty()) {
                val blast = outputLayers.lastOrNull { it.contains("SurfaceView", ignoreCase = true) && it.contains("BLAST", ignoreCase = true) }
                if (blast != null) return blast

                val sv = outputLayers.lastOrNull { it.contains("SurfaceView", ignoreCase = true) }
                if (sv != null) return sv

                val out = outputLayers.lastOrNull()
                if (out != null) return out
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning Output Layers for $packageName", e)
        }

        // Step 2: Fallback to dumpsys SurfaceFlinger --list
        val listOutput = runShellCommand("dumpsys SurfaceFlinger --list")
        val matchingLayers = mutableListOf<String>()

        for (rawLine in listOutput.lines()) {
            val line = rawLine.trim()
            if (!line.contains(packageName)) continue

            val layerName = cleanLayerName(line)
            if (isRenderableLayer(layerName, packageName)) {
                matchingLayers.add(layerName)
            }
        }

        if (matchingLayers.isEmpty()) return null

        // Priority 1: SurfaceView BLAST layer (pick the latest active instance)
        val surfaceViewBlast = matchingLayers.lastOrNull { 
            it.contains("SurfaceView", ignoreCase = true) && it.contains("BLAST", ignoreCase = true) 
        }
        if (surfaceViewBlast != null) return surfaceViewBlast

        // Priority 2: Any SurfaceView layer (pick the latest active instance)
        val surfaceView = matchingLayers.lastOrNull { it.contains("SurfaceView", ignoreCase = true) }
        if (surfaceView != null) return surfaceView

        // Priority 3: BLAST or BBQ wrapper layer
        val bbqBlast = matchingLayers.lastOrNull { it.contains("BLAST", ignoreCase = true) || it.contains("BBQ", ignoreCase = true) }
        if (bbqBlast != null) return bbqBlast

        // Priority 4: Main Activity window layer (avoiding popup windows)
        val mainWindow = matchingLayers.lastOrNull { !it.contains("PopupWindow") }
        if (mainWindow != null) return mainWindow

        return matchingLayers.lastOrNull()
    }

    private fun isRenderableLayer(layer: String, packageName: String): Boolean {
        if (layer.isEmpty() || !layer.contains(packageName)) return false
        if (layer.contains("ActivityRecord") ||
            layer.contains("ActivityRecordInputSink") ||
            layer.contains("Background for") ||
            layer.contains("Bounds for") ||
            layer.contains("Dim layer") ||
            layer.contains("SnapshotStartingWindow") ||
            layer.contains("Transition") ||
            layer.contains("leash") ||
            layer.startsWith("Task=")
        ) {
            return false
        }
        return true
    }

    private fun cleanLayerName(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("RequestedLayerState{")) {
            str = str.removePrefix("RequestedLayerState{")
            str = str.substringBefore(" parentId=").substringBefore(" relativeParentId=").substringBefore(" z=")
            str = str.removeSuffix("}")
        } else if (str.startsWith("Layer [")) {
            str = str.substringAfter("] ").trim()
        }
        return str.trim()
    }

    /**
     * Executes a shell command via Shizuku.
     */
    @Suppress("DEPRECATION")
    private fun runShellCommand(command: String): String {
        var process: Process? = null
        return try {
            process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error running command: $command", e)
            ""
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
