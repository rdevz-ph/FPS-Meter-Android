package com.rdevzph.fpsmeter.overlay

/**
 * This code is the property of Romel Brosas (https://github.com/rdevz-ph/FPS-Meter-Android).
 * If you modify or reuse this code, please give appropriate credit to the author.
 */

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.rdevzph.fpsmeter.util.ShellHelper
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.math.roundToInt

/**
 * Holds simultaneous hardware load readings for CPU and GPU.
 */
data class DeviceLoadSnapshot(
    val cpuLoad: Float?,
    val gpuLoad: Float?
)

data class CpuStatSample(
    val idle: Long,
    val total: Long
)

/**
 * Monitors CPU and GPU load percentages using privileged shell commands (via Shizuku or Root).
 * Runs on a lightweight background coroutine polling every 1.5 seconds.
 */
class CpuGpuLoadMonitor(
    private val onSnapshotUpdate: (snapshot: DeviceLoadSnapshot) -> Unit = {},
    private val onUnavailable: () -> Unit = {},
    private var useRoot: Boolean = false,
    private val needCpu: () -> Boolean = { true },
    private val needGpu: () -> Boolean = { true }
) {
    companion object {
        private const val TAG = "CpuGpuLoadMonitor"
        private const val POLL_INTERVAL_MS = 1500L

        internal fun parseCpuStat(output: String): CpuStatSample? {
            val line = output.lineSequence().firstOrNull { it.startsWith("cpu ") || it.startsWith("cpu\t") } ?: return null
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return null
            val user = parts[1].toLongOrNull() ?: return null
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: return null
            val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
            val irq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L
            val softirq = if (parts.size > 7) parts[7].toLongOrNull() ?: 0L else 0L
            val steal = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L

            val idleTime = idle + iowait
            val totalTime = user + nice + system + idle + iowait + irq + softirq + steal
            return CpuStatSample(idle = idleTime, total = totalTime)
        }

        internal fun calculateCpuLoad(prev: CpuStatSample, current: CpuStatSample): Float? {
            val deltaTotal = current.total - prev.total
            val deltaIdle = current.idle - prev.idle
            if (deltaTotal <= 0) return null
            val load = (1.0f - (deltaIdle.toFloat() / deltaTotal.toFloat())) * 100.0f
            return ((load.coerceIn(0f, 100f) * 10f).roundToInt() / 10f)
        }

        internal fun parseGpuWork(output: String): Map<Int, Long> {
            val map = mutableMapOf<Int, Long>()
            var inGpuWork = false
            output.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.contains("GPU work information", ignoreCase = true)) {
                    inGpuWork = true
                    return@forEach
                }
                if (!inGpuWork) {
                    if (line.startsWith("gpu_id uid") || line.startsWith("gpu_id\tuid")) {
                        inGpuWork = true
                    }
                    return@forEach
                }

                if (line.startsWith("gpu_id") || line.isEmpty()) return@forEach
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val uid = parts[1].toIntOrNull()
                    val activeNs = parts[2].toLongOrNull()
                    if (uid != null && activeNs != null) {
                        map[uid] = activeNs
                    }
                }
            }
            return map
        }

        internal fun calculateGpuLoad(
            prevMap: Map<Int, Long>,
            currMap: Map<Int, Long>,
            deltaTimeNs: Long
        ): Float? {
            if (deltaTimeNs <= 0) return null
            if (currMap.isEmpty()) return null

            var totalActiveDeltaNs = 0L
            for ((uid, currActive) in currMap) {
                val prevActive = prevMap[uid]
                if (prevActive != null) {
                    val delta = currActive - prevActive
                    if (delta > 0) {
                        totalActiveDeltaNs += delta
                    }
                }
            }

            val load = (totalActiveDeltaNs.toFloat() / deltaTimeNs.toFloat()) * 100.0f
            return ((load.coerceIn(0f, 100f) * 10f).roundToInt() / 10f)
        }

        internal fun readSysfsGpuLoad(): Float? {
            val paths = listOf(
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load"
            )
            for (path in paths) {
                try {
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        val text = file.readText().trim()
                        val value = text.replace("%", "").trim().toFloatOrNull()
                        if (value != null && value in 0f..100f) return value
                    }
                } catch (_: Exception) {
                    // Ignore permission or read errors
                }
            }
            return null
        }
    }

    private var pollJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastCpuSample: CpuStatSample? = null
    private var lastGpuMap: Map<Int, Long>? = null
    private var lastGpuTimeNs: Long = 0L

    fun setUseRoot(enabled: Boolean) {
        useRoot = enabled
    }

    private fun isPrivilegedReady(): Boolean {
        if (useRoot) return true
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun start() {
        stop()
        Log.d(TAG, "Starting CPU/GPU load monitor...")

        pollJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val wantsCpu = needCpu()
                    val wantsGpu = needGpu()

                    if (!wantsCpu && !wantsGpu) {
                        delay(POLL_INTERVAL_MS)
                        continue
                    }

                    val isReady = isPrivilegedReady()
                    if (!isReady) {
                        withContext(Dispatchers.Main) { onUnavailable() }
                        delay(POLL_INTERVAL_MS * 2)
                        continue
                    }

                    val nowNs = SystemClock.elapsedRealtimeNanos()
                    val sysfsGpu = if (wantsGpu) readSysfsGpuLoad() else null

                    val command = when {
                        wantsCpu && wantsGpu && sysfsGpu == null -> "grep '^cpu ' /proc/stat; echo '---GPU---'; dumpsys gpu"
                        wantsCpu -> "grep '^cpu ' /proc/stat"
                        wantsGpu && sysfsGpu == null -> "dumpsys gpu"
                        else -> null
                    }

                    val rawOutput = if (command != null) runShellCommand(command) else ""

                    var computedCpuLoad: Float? = null
                    var computedGpuLoad: Float? = sysfsGpu

                    if (wantsCpu) {
                        val cpuText = if (rawOutput.contains("---GPU---")) {
                            rawOutput.substringBefore("---GPU---")
                        } else {
                            rawOutput
                        }
                        val currentCpuSample = parseCpuStat(cpuText)
                        if (currentCpuSample != null) {
                            val prev = lastCpuSample
                            if (prev != null) {
                                computedCpuLoad = calculateCpuLoad(prev, currentCpuSample)
                            }
                            lastCpuSample = currentCpuSample
                        }
                    }

                    if (wantsGpu && sysfsGpu == null) {
                        val gpuText = if (rawOutput.contains("---GPU---")) {
                            rawOutput.substringAfter("---GPU---")
                        } else {
                            rawOutput
                        }
                        val currentGpuMap = parseGpuWork(gpuText)
                        if (currentGpuMap.isNotEmpty()) {
                            val prevMap = lastGpuMap
                            val prevTime = lastGpuTimeNs
                            if (prevMap != null && prevTime > 0L) {
                                val deltaTime = nowNs - prevTime
                                computedGpuLoad = calculateGpuLoad(prevMap, currentGpuMap, deltaTime)
                            }
                            lastGpuMap = currentGpuMap
                            lastGpuTimeNs = nowNs
                        }
                    }

                    if (computedCpuLoad != null || computedGpuLoad != null) {
                        val snapshot = DeviceLoadSnapshot(
                            cpuLoad = computedCpuLoad,
                            gpuLoad = computedGpuLoad
                        )
                        withContext(Dispatchers.Main) { onSnapshotUpdate(snapshot) }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling CPU/GPU load", e)
                    withContext(Dispatchers.Main) { onUnavailable() }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        lastCpuSample = null
        lastGpuMap = null
        lastGpuTimeNs = 0L
    }

    private fun runShellCommand(command: String): String {
        return ShellHelper.runShellCommand(command, useRoot)
    }
}
