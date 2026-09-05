package com.rdevzph.fpsmeter.overlay

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import kotlin.math.roundToInt

/**
 * Monitors SoC (CPU/GPU) temperature using Shizuku privileged shell commands.
 * Runs on a lightweight background coroutine polling every 1.5 seconds.
 */
class SocThermalMonitor(
    private val onTempUpdate: (tempCelsius: Float) -> Unit,
    private val onUnavailable: () -> Unit
) {
    companion object {
        private const val TAG = "SocThermalMonitor"
        private const val POLL_INTERVAL_MS = 1500L

        // Regex for Android Thermal HAL output:
        // Temperature{mValue=42.5, mType=0, mName=cpu0-gold-usr, mStatus=0}
        private val THERMAL_SERVICE_PATTERN = Pattern.compile(
            """Temperature\{mValue=([0-9.]+),\s*mType=(\d+),\s*mName=([^,}]+)(?:,\s*mStatus=(\d+))?"""
        )

        // Type constants from android.os.Temperature
        private const val TYPE_CPU = 0
        private const val TYPE_GPU = 1
        private const val TYPE_NPU = 9
        private const val TYPE_SOC = 10
        private const val TYPE_SOC_AIDL = 13
    }

    private var pollJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        stop()
        Log.d(TAG, "Starting SoC thermal monitor...")

        pollJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val isShizukuReady = try {
                        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    } catch (e: Exception) {
                        false
                    }

                    if (!isShizukuReady) {
                        withContext(Dispatchers.Main) { onUnavailable() }
                        delay(POLL_INTERVAL_MS * 2)
                        continue
                    }

                    val temp = readSocTemperature()
                    if (temp != null && temp in 20.0f..115.0f) {
                        val rounded = (temp * 10f).roundToInt() / 10f
                        withContext(Dispatchers.Main) { onTempUpdate(rounded) }
                    } else {
                        withContext(Dispatchers.Main) { onUnavailable() }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling SoC temperature", e)
                    withContext(Dispatchers.Main) { onUnavailable() }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Attempts to read SoC / CPU temperature first via dumpsys thermalservice,
     * falling back to /sys/class/thermal/thermal_zone* if necessary.
     */
    private fun readSocTemperature(): Float? {
        // Strategy 1: dumpsys thermalservice (Android 10+ standard Thermal HAL)
        val dumpsysOutput = runShellCommand("dumpsys thermalservice")
        if (dumpsysOutput.isNotEmpty()) {
            val parsedFromHal = parseThermalServiceOutput(dumpsysOutput)
            if (parsedFromHal != null) {
                return parsedFromHal
            }
        }

        // Strategy 2: Fallback query Linux sysfs thermal zones
        val sysfsOutput = runShellCommand(
            "for f in /sys/class/thermal/thermal_zone*; do echo \"$(cat \$f/type 2>/dev/null):$(cat \$f/temp 2>/dev/null)\"; done"
        )
        if (sysfsOutput.isNotEmpty()) {
            val parsedFromSysfs = parseSysfsThermalOutput(sysfsOutput)
            if (parsedFromSysfs != null) {
                return parsedFromSysfs
            }
        }

        return null
    }

    private fun parseThermalServiceOutput(output: String): Float? {
        // Target live HAL temperatures section to avoid stale cached/shutdown values
        val targetText = if (output.contains("Current temperatures from HAL:")) {
            output.substringAfter("Current temperatures from HAL:")
                .substringBefore("Current cooling devices from HAL:")
                .substringBefore("Temperature static thresholds")
        } else {
            output
        }

        val matcher = THERMAL_SERVICE_PATTERN.matcher(targetText)
        var exactSocTemp: Float? = null
        val cpuTemps = mutableListOf<Float>()
        val genericSocTemps = mutableListOf<Float>()

        while (matcher.find()) {
            try {
                val value = matcher.group(1)?.toFloatOrNull() ?: continue
                val type = matcher.group(2)?.toIntOrNull() ?: -1
                val name = (matcher.group(3) ?: "").trim().lowercase()
                val status = matcher.group(4)?.toIntOrNull() ?: 0

                // Ignore shutdown/tripped cached latch (status 4) when normal readings exist
                if (status == 4) continue
                if (value !in 20.0f..115.0f) continue

                // Check for explicit SoC or AP sensors
                if (name == "soc" || type == TYPE_SOC_AIDL) {
                    exactSocTemp = value
                } else if (type == TYPE_SOC || name.contains("soc") || name.contains("ap-thermal") || name.contains("tsens")) {
                    genericSocTemps.add(value)
                } else if (type == TYPE_CPU || name.contains("cpu") || name.contains("gold") || name.contains("silver")) {
                    cpuTemps.add(value)
                } else if (type == TYPE_GPU || name.contains("gpu")) {
                    cpuTemps.add(value)
                }
            } catch (e: Exception) {
                // Ignore parse errors for individual lines
            }
        }

        return exactSocTemp
            ?: genericSocTemps.maxOrNull()
            ?: cpuTemps.maxOrNull()
    }

    private fun parseSysfsThermalOutput(output: String): Float? {
        val validTemps = mutableListOf<Float>()

        output.lineSequence().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val type = parts[0].trim().lowercase()
                val rawTemp = parts[1].trim().toFloatOrNull()

                if (rawTemp != null && isValidSocZoneType(type)) {
                    val normalized = normalizeSysfsTemp(rawTemp)
                    if (normalized != null && normalized in 20.0f..115.0f) {
                        validTemps.add(normalized)
                    }
                }
            }
        }

        return validTemps.maxOrNull()
    }

    private fun isValidSocZoneType(type: String): Boolean {
        // Exclude non-SoC zones
        if (type.contains("battery") || type.contains("bms") || type.contains("chg") ||
            type.contains("usb") || type.contains("wifi") || type.contains("modem") ||
            type.contains("skin") || type.contains("quiet") || type.contains("camera") ||
            type.contains("speaker") || type.contains("display") || type.contains("panel")
        ) {
            return false
        }

        // Include CPU/SoC/AP indicators across Qualcomm, MediaTek, Exynos, Tensor
        return type.contains("cpu") || type.contains("soc") || type.contains("ap") ||
                type.contains("tsens") || type.contains("mtktscpu") || type.contains("cluster") ||
                type.contains("exynos") || type.contains("kryo") || type.contains("cortex")
    }

    private fun normalizeSysfsTemp(raw: Float): Float? {
        return when {
            raw > 10000f -> raw / 1000f      // Millidegrees (e.g. 45000 -> 45.0°C)
            raw > 1000f -> raw / 10f         // Deci-degrees (e.g. 450 -> 45.0°C)
            raw in 20f..115f -> raw          // Direct degrees Celsius
            else -> null
        }
    }

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
