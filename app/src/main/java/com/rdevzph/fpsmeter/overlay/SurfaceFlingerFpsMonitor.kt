package com.rdevzph.fpsmeter.overlay

import android.content.pm.PackageManager
import android.util.Log
import com.rdevzph.fpsmeter.model.GraphicsApi
import com.rdevzph.fpsmeter.model.SurfaceFlingerMethod
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

/**
 * Monitors game FPS and Graphics API (Vulkan vs OpenGL ES) directly from SurfaceFlinger
 * using Shizuku privileged shell commands.
 *
 * Supports two measurement engines:
 * - Layer Latency: Queries per-layer presentation timestamps via dumpsys SurfaceFlinger --latency.
 * - TimeStats: Queries global/per-layer compositor statistics via dumpsys SurfaceFlinger --timestats.
 */
class SurfaceFlingerFpsMonitor(
    private var method: SurfaceFlingerMethod = SurfaceFlingerMethod.LATENCY,
    private val onFpsUpdate: (fps: Int, frameTimeMs: Float, api: GraphicsApi, layerName: String?, foregroundPackage: String?) -> Unit,
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
    private var apiCheckCounter = 0

    private var consecutiveFailures = 0

    fun setMethod(newMethod: SurfaceFlingerMethod) {
        if (method != newMethod) {
            method = newMethod
            currentTargetLayer = null
            lastPresentTimestamp = 0L
            lastLayerFrameCount = -1L
            zeroFpsCount = 0
            consecutiveFailures = 0
            Log.d(TAG, "Switched SurfaceFlinger method to: $method")
        }
    }

    fun start() {
        stop()
        Log.d(TAG, "Starting SurfaceFlinger FPS monitor with method: $method")
        lastSampleTime = System.currentTimeMillis()
        lastPresentTimestamp = 0L
        lastLayerFrameCount = -1L
        zeroFpsCount = 0
        apiCheckCounter = 0
        consecutiveFailures = 0

        // Initialize TimeStats in background so compositor tracking is active
        coroutineScope.launch {
            runShellCommand("dumpsys SurfaceFlinger --timestats -clear -enable")
        }

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
                    val isShizukuReady = try {
                        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    } catch (_: Exception) {
                        false
                    }
                    if (!isShizukuReady && consecutiveFailures >= 3) {
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
        apiCheckCounter = 0

        coroutineScope.launch {
            runShellCommand("dumpsys SurfaceFlinger --timestats -disable")
        }
    }

    fun getCurrentForegroundPackage(): String? = currentForegroundPackage

    private suspend fun sampleFps() {
        val now = System.currentTimeMillis()
        val elapsedMs = if (lastSampleTime == 0L) 1000L else (now - lastSampleTime).coerceAtLeast(1L)
        lastSampleTime = now

        // 1. Identify Foreground Package and System State
        val fgState = getForegroundState()
        var foregroundPkg = fgState.packageName
        if (fgState.isHomeOrSystem) {
            // User is on home screen, launcher, recents, or system UI
            currentForegroundPackage = null
            currentTargetLayer = null
            currentGraphicsApi = GraphicsApi.UNKNOWN
            lastPresentTimestamp = 0L
            lastLayerFrameCount = -1L
            zeroFpsCount = 0
            apiCheckCounter = 0
            foregroundPkg = null
        }

        // Handle package changes and graphics API detection if package is known
        if (foregroundPkg != null) {
            if (foregroundPkg != currentForegroundPackage) {
                Log.d(TAG, "Foreground package changed: '$currentForegroundPackage' -> '$foregroundPkg'")
                currentForegroundPackage = foregroundPkg
                currentGraphicsApi = detectGraphicsApi(foregroundPkg)
                currentTargetLayer = null
                lastPresentTimestamp = 0L
                lastLayerFrameCount = -1L
                zeroFpsCount = 0
                apiCheckCounter = 0
            } else {
                apiCheckCounter++
                val shouldCheckApi = if (currentGraphicsApi != GraphicsApi.VULKAN) {
                    apiCheckCounter >= 2
                } else {
                    apiCheckCounter >= 5
                }

                if (shouldCheckApi) {
                    apiCheckCounter = 0
                    val detectedApi = detectGraphicsApi(foregroundPkg)
                    if (detectedApi != currentGraphicsApi) {
                        Log.d(TAG, "Graphics API changed for $foregroundPkg: $currentGraphicsApi -> $detectedApi")
                        currentGraphicsApi = detectedApi
                        currentTargetLayer = null
                        lastPresentTimestamp = 0L
                        lastLayerFrameCount = -1L
                        zeroFpsCount = 0
                    }
                }
            }
        }

        // 2. Branch based on selected method
        if (method == SurfaceFlingerMethod.TIMESTATS) {
            val statsResult = sampleTimeStatsFps(foregroundPkg)
            val effectivePkg = foregroundPkg ?: statsResult.resolvedPackage
            if (effectivePkg != null && effectivePkg != currentForegroundPackage) {
                currentForegroundPackage = effectivePkg
                currentGraphicsApi = detectGraphicsApi(effectivePkg)
            }
            consecutiveFailures = 0
            withContext(Dispatchers.Main) {
                onFpsUpdate(statsResult.fps, statsResult.frameTimeMs, currentGraphicsApi, null, currentForegroundPackage)
            }
            return
        }

        // 3. Method is LATENCY: Try layer latency measurement first
        var measuredFps = -1
        var measuredFrameTime = 0f
        var measuredLayer: String? = null

        if (foregroundPkg != null) {
            var layer = currentTargetLayer
            if (layer == null) {
                layer = resolveLayerForPackage(foregroundPkg)
                currentTargetLayer = layer
                lastPresentTimestamp = 0L
                lastLayerFrameCount = -1L
                zeroFpsCount = 0
                Log.d(TAG, "Resolved target layer for $foregroundPkg: $layer")
            } else if (!layer.contains("SurfaceView", ignoreCase = true)) {
                val candidateLayer = resolveLayerForPackage(foregroundPkg)
                if (candidateLayer != null && candidateLayer != layer && candidateLayer.contains("SurfaceView", ignoreCase = true)) {
                    Log.d(TAG, "Game upgraded from window layer to SurfaceView: '$layer' -> '$candidateLayer'")
                    layer = candidateLayer
                    currentTargetLayer = candidateLayer
                    lastPresentTimestamp = 0L
                    lastLayerFrameCount = -1L
                    zeroFpsCount = 0
                }
            }

            if (layer != null) {
                val fpsResult = measureLayerFps(layer, elapsedMs, currentGraphicsApi)
                if (fpsResult.fps > 0) {
                    measuredFps = fpsResult.fps
                    measuredFrameTime = fpsResult.frameTimeMs
                    measuredLayer = layer
                    zeroFpsCount = 0
                    consecutiveFailures = 0
                }
            }
        }

        // If latency produced valid FPS, deliver it directly
        if (measuredFps > 0) {
            withContext(Dispatchers.Main) {
                onFpsUpdate(measuredFps, measuredFrameTime, currentGraphicsApi, measuredLayer, foregroundPkg)
            }
            return
        }

        // 4. Fallback to TimeStats: Layer latency returned 0 FPS or layer could not be resolved
        val fallbackResult = sampleTimeStatsFps(foregroundPkg)
        if (fallbackResult.fps > 0) {
            val effectivePkg = foregroundPkg ?: fallbackResult.resolvedPackage
            if (effectivePkg != null && effectivePkg != currentForegroundPackage) {
                currentForegroundPackage = effectivePkg
                currentGraphicsApi = detectGraphicsApi(effectivePkg)
            }
            zeroFpsCount = 0
            consecutiveFailures = 0
            withContext(Dispatchers.Main) {
                onFpsUpdate(fallbackResult.fps, fallbackResult.frameTimeMs, currentGraphicsApi, currentTargetLayer, currentForegroundPackage)
            }
            return
        }

        // 5. Both methods returned 0 FPS (screen idle, home launcher, or loading screen)
        zeroFpsCount++
        if (foregroundPkg != null && currentTargetLayer != null) {
            val activeLayer = resolveLayerForPackage(foregroundPkg)
            if (activeLayer != null && activeLayer != currentTargetLayer) {
                currentTargetLayer = activeLayer
                lastPresentTimestamp = 0L
                lastLayerFrameCount = -1L
                zeroFpsCount = 0
            } else if (zeroFpsCount >= 2) {
                currentTargetLayer = null
                lastPresentTimestamp = 0L
                lastLayerFrameCount = -1L
                zeroFpsCount = 0
            }
        }

        consecutiveFailures = 0
        withContext(Dispatchers.Main) {
            onFpsUpdate(0, 0f, currentGraphicsApi, currentTargetLayer, foregroundPkg ?: currentForegroundPackage)
        }
    }

    private data class MeasurementResult(val fps: Int, val frameTimeMs: Float)

    private fun measureLayerFps(layer: String, elapsedMs: Long, api: GraphicsApi): MeasurementResult {
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
                    val newFrames = presentTimestamps.filter { it > lastPresentTimestamp }
                    calculatedFps = ((newFrames.size * 1000f) / elapsedMs).roundToInt()
                    if (newFrames.isNotEmpty()) {
                        lastPresentTimestamp = newFrames.maxOrNull() ?: lastPresentTimestamp
                    }
                }
            }
        }

        // Secondary / Cross-check measurement: SurfaceFlinger active layer frame counter
        val layerFrameCount = queryLayerFrameCount(layer)
        if (layerFrameCount != null && layerFrameCount >= 0) {
            if (lastLayerFrameCount >= 0 && layerFrameCount >= lastLayerFrameCount) {
                val deltaFrames = layerFrameCount - lastLayerFrameCount
                val counterFps = ((deltaFrames * 1000f) / elapsedMs).roundToInt()

                if (calculatedFps <= 0 && counterFps > 0) {
                    calculatedFps = counterFps
                } else if (deltaFrames == 0L && calculatedFps > 0) {
                    calculatedFps = 0
                } else if (api == GraphicsApi.VULKAN && counterFps > 0 && calculatedFps > 0) {
                    calculatedFps = if (Math.abs(counterFps - calculatedFps) <= 2) calculatedFps else counterFps
                } else if (api == GraphicsApi.OPENGL && counterFps > 0 && calculatedFps > 0) {
                    if (Math.abs(counterFps - calculatedFps) > 5) {
                        calculatedFps = counterFps
                    }
                }
            }
            lastLayerFrameCount = layerFrameCount
        }

        val finalFps = calculatedFps.coerceAtLeast(0)
        frameTimeMs = if (finalFps > 0) (1000f / finalFps) else 0f

        return MeasurementResult(finalFps, frameTimeMs)
    }

    private data class TimeStatsLayer(
        val layerName: String,
        val totalFrames: Long,
        val averageFps: Float
    )

    private data class TimeStatsResult(
        val fps: Int,
        val frameTimeMs: Float,
        val resolvedPackage: String?
    )

    private fun sampleTimeStatsFps(targetPkg: String?): TimeStatsResult {
        val output = runShellCommand("dumpsys SurfaceFlinger --timestats -dump; dumpsys SurfaceFlinger --timestats -clear -enable")
        if (output.isBlank()) {
            return TimeStatsResult(0, 0f, null)
        }

        val layers = parseTimeStatsDump(output)

        // Priority 1: Match layers belonging to target package
        if (!targetPkg.isNullOrEmpty()) {
            val matchingLayers = layers.filter {
                it.layerName.contains(targetPkg, ignoreCase = true) && isRenderableLayer(it.layerName, targetPkg)
            }
            val best = matchingLayers.firstOrNull { it.layerName.contains("SurfaceView", ignoreCase = true) && it.layerName.contains("BLAST", ignoreCase = true) }
                ?: matchingLayers.firstOrNull { it.layerName.contains("SurfaceView", ignoreCase = true) }
                ?: matchingLayers.maxByOrNull { it.totalFrames }
                ?: matchingLayers.firstOrNull()

            if (best != null && best.averageFps > 0f) {
                val fps = best.averageFps.roundToInt()
                val frameTime = if (fps > 0) 1000f / best.averageFps else 0f
                return TimeStatsResult(fps, frameTime, targetPkg)
            }
        }

        // Priority 2: Match any active candidate game layer
        val candidateLayers = layers.filter { isCandidateGameLayer(it.layerName) && it.totalFrames > 0 }
        val bestCandidate = candidateLayers.firstOrNull { it.layerName.contains("SurfaceView", ignoreCase = true) }
            ?: candidateLayers.maxByOrNull { it.totalFrames }
            ?: candidateLayers.firstOrNull()

        if (bestCandidate != null && bestCandidate.averageFps > 0f) {
            val extractedPkg = extractPackageFromLayerName(bestCandidate.layerName)
            val fps = bestCandidate.averageFps.roundToInt()
            val frameTime = if (fps > 0) 1000f / bestCandidate.averageFps else 0f
            return TimeStatsResult(fps, frameTime, extractedPkg)
        }

        // Priority 3: Global compositor averageFPS (keeps FPS reliable everywhere, even outside games)
        val globalMatch = Regex("averageFPS\\s*=\\s*([0-9.]+)").find(output)
        if (globalMatch != null) {
            val avgFps = globalMatch.groupValues[1].toFloatOrNull() ?: 0f
            if (avgFps > 0f) {
                val fps = avgFps.roundToInt()
                val frameTime = 1000f / avgFps
                return TimeStatsResult(fps, frameTime, targetPkg)
            }
        }

        return TimeStatsResult(0, 0f, null)
    }

    private fun parseTimeStatsDump(output: String): List<TimeStatsLayer> {
        val layers = mutableListOf<TimeStatsLayer>()
        var currentLayer: String? = null
        var totalFrames: Long = 0L
        var avgFps: Float = 0f

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("layerName =")) {
                if (currentLayer != null && (totalFrames > 0 || avgFps > 0f)) {
                    layers.add(TimeStatsLayer(currentLayer, totalFrames, avgFps))
                }
                currentLayer = trimmed.substringAfter("layerName =").trim()
                totalFrames = 0L
                avgFps = 0f
            } else if (trimmed.startsWith("totalFrames =")) {
                totalFrames = trimmed.substringAfter("totalFrames =").trim().toLongOrNull() ?: 0L
            } else if (trimmed.startsWith("averageFPS =")) {
                avgFps = trimmed.substringAfter("averageFPS =").trim().toFloatOrNull() ?: 0f
            }
        }
        if (currentLayer != null && (totalFrames > 0 || avgFps > 0f)) {
            layers.add(TimeStatsLayer(currentLayer, totalFrames, avgFps))
        }
        return layers
    }

    private fun isCandidateGameLayer(layerName: String): Boolean {
        val lower = layerName.lowercase()
        if (lower.contains("systemui") ||
            lower.contains("navigationbar") ||
            lower.contains("statusbar") ||
            lower.contains("notification") ||
            lower.contains("inputmethod") ||
            lower.contains("wallpaper") ||
            lower.contains("screenshot") ||
            lower.contains("launcher") ||
            lower.contains("quickstep") ||
            lower.contains("fpsmeter") ||
            lower.contains("pipcontentoverlay") ||
            lower.contains("activityrecordinputsink") ||
            lower.contains("bounds for") ||
            lower.contains("background for") ||
            lower.contains("dim layer") ||
            lower.contains("snapshotstartingwindow") ||
            lower.contains("transition") ||
            lower.contains("leash") ||
            layerName.startsWith("Task=")
        ) {
            return false
        }
        return true
    }

    private fun extractPackageFromLayerName(layerName: String): String? {
        val match = Regex("([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(layerName)
        if (match != null) {
            val pkg = match.groupValues[1]
            if (isGameCandidate(pkg)) return pkg
        }
        val pkgMatch = Regex("([a-zA-Z][a-zA-Z0-9_]*\\.[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)").find(layerName)
        if (pkgMatch != null) {
            val pkg = pkgMatch.groupValues[1]
            if (isGameCandidate(pkg)) return pkg
        }
        return null
    }

    private fun queryLayerFrameCount(layer: String): Long? {
        try {
            val sanitized = layer.substringBefore("#").trim().replace("'", "'\\''")
            val output = runShellCommand("dumpsys SurfaceFlinger | grep -A 2 -F '$sanitized'")
            val lines = output.lines()
            var expectFrameOnNextLine = false
            for (line in lines) {
                if (line.contains(sanitized) && !line.contains("Background for") && !line.contains("mirrored")) {
                    val match = Regex("frame=(\\d+)").find(line)
                    if (match != null) {
                        return match.groupValues[1].toLongOrNull()
                    }
                    if (line.contains("Layer [") || line.contains("Output Layer")) {
                        expectFrameOnNextLine = true
                    }
                } else if (expectFrameOnNextLine) {
                    val match = Regex("frame=(\\d+)").find(line)
                    if (match != null) {
                        return match.groupValues[1].toLongOrNull()
                    }
                    if (line.contains("Layer [") || line.contains("--")) {
                        expectFrameOnNextLine = false
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private data class ForegroundState(
        val packageName: String?,
        val isHomeOrSystem: Boolean
    )

    private fun getForegroundState(): ForegroundState {
        val windowOutput = runShellCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        for (line in windowOutput.lines()) {
            val match = Regex("(?:mCurrentFocus|mFocusedApp)=.*?([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
            if (match != null) {
                val pkg = match.groupValues[1]
                if (isSystemOrLauncher(pkg)) {
                    return ForegroundState(null, isHomeOrSystem = true)
                }
                return ForegroundState(pkg, isHomeOrSystem = false)
            }
        }

        // Fallback: dumpsys activity
        val actOutput = runShellCommand("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity'")
        for (line in actOutput.lines()) {
            val match = Regex("(?:topResumedActivity|mResumedActivity|ResumedActivity).*?([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
            if (match != null) {
                val pkg = match.groupValues[1]
                if (isSystemOrLauncher(pkg)) {
                    return ForegroundState(null, isHomeOrSystem = true)
                }
                return ForegroundState(pkg, isHomeOrSystem = false)
            }
            val generalMatch = Regex("([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
            if (generalMatch != null) {
                val pkg = generalMatch.groupValues[1]
                if (isSystemOrLauncher(pkg)) {
                    return ForegroundState(null, isHomeOrSystem = true)
                }
                return ForegroundState(pkg, isHomeOrSystem = false)
            }
        }

        return ForegroundState(null, isHomeOrSystem = false)
    }

    private fun isSystemOrLauncher(pkg: String): Boolean {
        if (pkg == "android" ||
            pkg.contains("systemui", ignoreCase = true) ||
            pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("quickstep", ignoreCase = true) ||
            pkg == "com.rdevzph.fpsmeter"
        ) {
            return true
        }
        return false
    }

    private fun isGameCandidate(pkg: String): Boolean = !isSystemOrLauncher(pkg)

    /**
     * Detects whether the running game is using Vulkan or OpenGL ES.
     */
    private fun detectGraphicsApi(packageName: String): GraphicsApi {
        try {
            // 1. Primary inspection via Android GPU service: dumpsys gpu
            val gpuOutput = runShellCommand("dumpsys gpu")
            val packageSections = extractPackageSectionsFromGpuDump(gpuOutput, packageName)

            if (packageSections.isNotEmpty()) {
                val hasVulkanSwapchain = packageSections.any {
                    it.contains("createdVulkanSwapchain = 1") || it.contains("createdVulkanDevice = 1")
                }
                val hasGlesContext = packageSections.any {
                    it.contains("createdGlesContext = 1") || it.contains("gles1InUse = 1")
                }

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

    private fun extractPackageSectionsFromGpuDump(gpuDump: String, packageName: String): List<String> {
        val sections = mutableListOf<String>()
        val marker = "appPackageName = $packageName"
        var startIndex = gpuDump.indexOf(marker)
        while (startIndex != -1) {
            val nextSectionIndex = gpuDump.indexOf("appPackageName =", startIndex + marker.length)
            val section = if (nextSectionIndex != -1) {
                gpuDump.substring(startIndex, nextSectionIndex)
            } else {
                gpuDump.substring(startIndex)
            }
            sections.add(section)
            startIndex = if (nextSectionIndex != -1) gpuDump.indexOf(marker, nextSectionIndex) else -1
        }
        return sections
    }

    /**
     * Resolves the active rendering layer in SurfaceFlinger for the given package.
     */
    private fun resolveLayerForPackage(packageName: String): String? {
        // Step 1: Scan active Output Layers from SurfaceFlinger
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
