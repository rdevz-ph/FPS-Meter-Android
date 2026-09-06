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
 * Holds simultaneous hardware temperature readings for the silicon die.
 */
data class ThermalSnapshot(
    val soc: Float?,
    val cpu: Float?,
    val gpu: Float?
)

/**
 * Monitors SoC (CPU/GPU) temperature using Shizuku privileged shell commands.
 * Runs on a lightweight background coroutine polling every 1.5 seconds.
 */
class SocThermalMonitor(
    private val onSnapshotUpdate: (snapshot: ThermalSnapshot) -> Unit,
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
        private const val TYPE_BATTERY = 2
        private const val TYPE_SKIN = 3
        private const val TYPE_BCL_PERCENTAGE = 8
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

                    val snapshot = readThermalSnapshot()
                    if (snapshot != null && (snapshot.soc != null || snapshot.cpu != null || snapshot.gpu != null)) {
                        val roundedSnapshot = ThermalSnapshot(
                            soc = roundTemp(snapshot.soc),
                            cpu = roundTemp(snapshot.cpu),
                            gpu = roundTemp(snapshot.gpu)
                        )
                        withContext(Dispatchers.Main) { onSnapshotUpdate(roundedSnapshot) }
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

    private fun roundTemp(temp: Float?): Float? {
        if (temp == null || temp !in 20.0f..115.0f) return null
        return (temp * 10f).roundToInt() / 10f
    }

    /**
     * Attempts to read SoC / CPU / GPU temperatures first via dumpsys thermalservice,
     * falling back to /sys/class/thermal/thermal_zone* if necessary.
     */
    private fun readThermalSnapshot(): ThermalSnapshot? {
        // Strategy 1: dumpsys thermalservice (Android 10+ standard Thermal HAL)
        val dumpsysOutput = runShellCommand("dumpsys thermalservice")
        if (dumpsysOutput.isNotEmpty()) {
            val parsedFromHal = parseThermalServiceSnapshot(dumpsysOutput)
            if (parsedFromHal != null) {
                return parsedFromHal
            }
        }

        // Strategy 2: Fallback query Linux sysfs thermal zones
        val sysfsOutput = runShellCommand(
            "for f in /sys/class/thermal/thermal_zone*; do echo \"$(cat \$f/type 2>/dev/null):$(cat \$f/temp 2>/dev/null)\"; done"
        )
        if (sysfsOutput.isNotEmpty()) {
            val parsedFromSysfs = parseSysfsThermalSnapshot(sysfsOutput)
            if (parsedFromSysfs != null) {
                return parsedFromSysfs
            }
        }

        return null
    }

    internal fun parseThermalServiceSnapshot(output: String): ThermalSnapshot? {
        // Target live HAL temperatures section to avoid stale cached/shutdown values
        val targetText = if (output.contains("Current temperatures from HAL:")) {
            output.substringAfter("Current temperatures from HAL:")
                .substringBefore("Current cooling devices from HAL:")
                .substringBefore("Temperature static thresholds")
        } else {
            output
        }

        val matcher = THERMAL_SERVICE_PATTERN.matcher(targetText)
        val unifiedSocTemps = mutableListOf<Float>()
        val cpuTemps = mutableListOf<Float>()
        val gpuTemps = mutableListOf<Float>()

        while (matcher.find()) {
            try {
                val value = matcher.group(1)?.toFloatOrNull() ?: continue
                val type = matcher.group(2)?.toIntOrNull() ?: -1
                val name = (matcher.group(3) ?: "").trim().lowercase()
                val status = matcher.group(4)?.toIntOrNull() ?: 0

                // Ignore shutdown/tripped cached latch (status 4) when normal readings exist
                if (status == 4) continue
                if (value !in 20.0f..115.0f) continue
                if (type == TYPE_BATTERY || type == TYPE_SKIN || type == TYPE_BCL_PERCENTAGE) continue
                if (isExcludedZone(name)) continue

                // Classify by CPU, GPU, or unified SoC sensors
                if (type == TYPE_CPU || isCpuCoreSensor(name)) {
                    cpuTemps.add(value)
                } else if (type == TYPE_GPU || isGpuSensor(name)) {
                    gpuTemps.add(value)
                } else if (isUnifiedSocSensor(name, type)) {
                    unifiedSocTemps.add(value)
                }
            } catch (e: Exception) {
                // Ignore parse errors for individual lines
            }
        }

        val cpuMax = cpuTemps.maxOrNull()
        val gpuMax = gpuTemps.maxOrNull()
        val allSocTemps = unifiedSocTemps + cpuTemps + gpuTemps
        val socMax = allSocTemps.maxOrNull()

        if (socMax == null && cpuMax == null && gpuMax == null) return null
        val effectiveCpu = cpuMax ?: if (gpuMax == null) socMax else null
        return ThermalSnapshot(soc = socMax, cpu = effectiveCpu, gpu = gpuMax)
    }

    internal fun parseThermalServiceOutput(output: String): Float? {
        return parseThermalServiceSnapshot(output)?.soc
    }

    internal fun parseSysfsThermalSnapshot(output: String): ThermalSnapshot? {
        val unifiedTemps = mutableListOf<Float>()
        val cpuTemps = mutableListOf<Float>()
        val gpuTemps = mutableListOf<Float>()
        val otherValidTemps = mutableListOf<Float>()

        output.lineSequence().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val type = parts[0].trim().lowercase()
                val rawTemp = parts[1].trim().toFloatOrNull()

                if (rawTemp != null && !isExcludedZone(type) && isValidSocZoneType(type)) {
                    val normalized = normalizeSysfsTemp(rawTemp, type)
                    if (normalized != null && normalized in 20.0f..115.0f) {
                        when {
                            isCpuCoreSensor(type) -> cpuTemps.add(normalized)
                            isGpuSensor(type) -> gpuTemps.add(normalized)
                            isUnifiedSocSensor(type, -1) -> unifiedTemps.add(normalized)
                            else -> otherValidTemps.add(normalized)
                        }
                    }
                }
            }
        }

        val cpuMax = cpuTemps.maxOrNull()
        val gpuMax = gpuTemps.maxOrNull()
        val allSocTemps = unifiedTemps + cpuTemps + gpuTemps
        val socMax = allSocTemps.maxOrNull() ?: otherValidTemps.maxOrNull()

        if (socMax == null && cpuMax == null && gpuMax == null) return null
        val effectiveCpu = cpuMax ?: if (gpuMax == null) socMax else null
        return ThermalSnapshot(soc = socMax, cpu = effectiveCpu, gpu = gpuMax)
    }

    internal fun parseSysfsThermalOutput(output: String): Float? {
        return parseSysfsThermalSnapshot(output)?.soc
    }

    internal fun isExcludedZone(type: String): Boolean {
        // 1. Standalone "soc" (case-insensitive) on Qualcomm platforms is battery State of Charge (%)
        if (type == "soc" || type == "soc-step" || (type.startsWith("soc") && !type.contains("thermal") && !type.contains("max") && !type.contains("temp") && !type.contains("cpu"))) {
            return true
        }

        // 2. Qualcomm thermal mitigation step trip points (not temperature readings)
        if (type.endsWith("-step") || type.contains("-step-") || type.contains("avg-step") || type.contains("max-step")) {
            return true
        }

        // 3. PMIC, battery, voltage/current, power limit telemetry
        if (type.contains("pmic") || type.contains("pm8") || type.contains("pm7") || type.contains("pm6") ||
            type.contains("pm-") || type.contains("bcl") || type.contains("vbat") || type.contains("ibat") ||
            type.contains("vph") || type.contains("bms") || type.contains("chg") || type.contains("charge") ||
            type.contains("battery")
        ) {
            return true
        }

        // 4. Peripherals and external RF / sensors
        if (type.contains("skin") || type.contains("quiet") || type.contains("camera") ||
            type.contains("speaker") || type.contains("display") || type.contains("panel") ||
            type.contains("modem") || type.contains("wifi") || type.contains("wlan") ||
            type.contains("xo-therm") || type.contains("conn-therm") || type.contains("pa1") || type.contains("pa2")
        ) {
            return true
        }

        return false
    }

    internal fun isValidSocZoneType(type: String): Boolean {
        if (isExcludedZone(type)) return false

        // Include CPU/SoC/AP/GPU indicators across Qualcomm, MediaTek, Exynos, Tensor
        return type.contains("cpu") || type.contains("soc_thermal") || type.contains("soc-thermal") ||
                type.contains("soc_max") || type.contains("ap-thermal") || type.contains("ap_thermal") ||
                type.contains("tsens") || type.contains("mtktscpu") || type.contains("mtktsap") ||
                type.contains("cluster") || type.contains("cpuss") || type.contains("gpuss") ||
                type.contains("exynos") || type.contains("kryo") || type.contains("cortex") ||
                type.contains("gpu")
    }

    internal fun isUnifiedSocSensor(name: String, type: Int): Boolean {
        if (type == TYPE_SOC || type == TYPE_SOC_AIDL) return true
        return name.contains("soc_thermal") || name.contains("soc-thermal") || name.contains("soc_max") ||
                name.contains("ap-thermal") || name.contains("ap_thermal") ||
                name.contains("mtktsap") || name.contains("cpuss-") || name.contains("tsens_tz_sensor")
    }

    internal fun isCpuCoreSensor(name: String): Boolean {
        return (name.contains("cpu") && !name.contains("step")) ||
                name.contains("gold") || name.contains("silver") ||
                name.contains("kryo") || name.contains("cortex")
    }

    internal fun isGpuSensor(name: String): Boolean {
        return (name.contains("gpu") || name.contains("gpuss")) && !name.contains("step")
    }

    internal fun normalizeSysfsTemp(raw: Float, type: String = ""): Float? {
        return when {
            raw > 10000f -> raw / 1000f      // Millidegrees (e.g. 29200 -> 29.2°C)
            raw > 1000f -> raw / 10f         // Deci-degrees (e.g. 420 -> 42.0°C)
            raw in 20f..115f -> {
                // Direct Celsius only valid for explicit thermal/CPU/temp sensors, not raw percentages
                if (type.contains("cpu") || type.contains("thermal") || type.contains("temp") || type.contains("tsens")) {
                    raw
                } else {
                    null
                }
            }
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
