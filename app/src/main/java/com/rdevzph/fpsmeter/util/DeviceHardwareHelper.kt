package com.rdevzph.fpsmeter.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

object DeviceHardwareHelper {

    @Volatile
    private var cachedSocModel: String? = null

    fun getSocModel(): String {
        cachedSocModel?.let { return it }
        var soc = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val model = Build.SOC_MODEL
            if (!model.isNullOrEmpty() && !model.equals("unknown", ignoreCase = true)) {
                soc = model
            }
        }
        if (soc.isEmpty()) {
            try {
                val p = Runtime.getRuntime().exec("getprop ro.soc.model")
                val line = p.inputStream.bufferedReader().readLine()?.trim()
                if (!line.isNullOrEmpty()) {
                    soc = line
                }
            } catch (_: Exception) {}
        }
        if (soc.isEmpty()) {
            try {
                val p = Runtime.getRuntime().exec("getprop ro.board.platform")
                val line = p.inputStream.bufferedReader().readLine()?.trim()
                if (!line.isNullOrEmpty() && !line.equals("unknown", ignoreCase = true)) {
                    soc = line
                }
            } catch (_: Exception) {}
        }
        if (soc.isEmpty()) {
            soc = if (!Build.HARDWARE.isNullOrEmpty() && !Build.HARDWARE.equals("unknown", ignoreCase = true)) {
                Build.HARDWARE
            } else {
                Build.BOARD
            }
        }
        val result = soc.uppercase(Locale.US)
        cachedSocModel = result
        return result
    }

    fun getCpuFrequencyMhz(): Int {
        return try {
            val f = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (f.exists() && f.canRead()) {
                val khz = f.readText().trim().toIntOrNull() ?: 0
                khz / 1000
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun getUsedRamMb(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0L) {
                val clampedAvail = memInfo.availMem.coerceIn(0L, memInfo.totalMem)
                ((memInfo.totalMem - clampedAvail) / (1024L * 1024L)).toInt().coerceAtLeast(0)
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }
}
