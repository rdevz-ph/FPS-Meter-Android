package com.rdevzph.fpsmeter.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents an aggregated gameplay FPS session.
 * Stores summary statistics alongside compact second-by-second time-series samples
 * for interactive charting and CSV export.
 */
data class FpsSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val avgFps: Int,
    val maxFps: Int,
    val minFps: Int,
    val fpsVariance: Float = 0f,
    val smoothnessPercent: Float = 100f,
    val low5PercentFps: Float = 0f,
    val maxTemperature: Float = 0f,
    val avgPowerWatts: Float = 0f,
    val maxCpuTemperature: Float = 0f,
    val maxGpuTemperature: Float = 0f,
    val fpsSamples: List<Int> = emptyList(),
    val tempSamples: List<Float> = emptyList(),
    val cpuTempSamples: List<Float> = emptyList(),
    val gpuTempSamples: List<Float> = emptyList()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("packageName", packageName)
            put("appName", appName)
            put("startTime", startTime)
            put("endTime", endTime)
            put("durationSeconds", durationSeconds)
            put("avgFps", avgFps)
            put("maxFps", maxFps)
            put("minFps", minFps)
            put("fpsVariance", fpsVariance.toDouble())
            put("smoothnessPercent", smoothnessPercent.toDouble())
            put("low5PercentFps", low5PercentFps.toDouble())
            put("maxTemperature", maxTemperature.toDouble())
            put("avgPowerWatts", avgPowerWatts.toDouble())
            if (maxCpuTemperature > 0f) put("maxCpuTemperature", maxCpuTemperature.toDouble())
            if (maxGpuTemperature > 0f) put("maxGpuTemperature", maxGpuTemperature.toDouble())
            if (fpsSamples.isNotEmpty()) {
                val arr = JSONArray()
                fpsSamples.forEach { arr.put(it) }
                put("fpsSamples", arr)
            }
            if (tempSamples.isNotEmpty()) {
                val arr = JSONArray()
                tempSamples.forEach { arr.put(it.toDouble()) }
                put("tempSamples", arr)
            }
            if (cpuTempSamples.isNotEmpty()) {
                val arr = JSONArray()
                cpuTempSamples.forEach { arr.put(it.toDouble()) }
                put("cpuTempSamples", arr)
            }
            if (gpuTempSamples.isNotEmpty()) {
                val arr = JSONArray()
                gpuTempSamples.forEach { arr.put(it.toDouble()) }
                put("gpuTempSamples", arr)
            }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FpsSessionRecord? {
            return try {
                val fpsList = mutableListOf<Int>()
                json.optJSONArray("fpsSamples")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        fpsList.add(arr.optInt(i))
                    }
                }
                val tempList = mutableListOf<Float>()
                json.optJSONArray("tempSamples")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        tempList.add(arr.optDouble(i).toFloat())
                    }
                }
                val cpuList = mutableListOf<Float>()
                json.optJSONArray("cpuTempSamples")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        cpuList.add(arr.optDouble(i).toFloat())
                    }
                }
                val gpuList = mutableListOf<Float>()
                json.optJSONArray("gpuTempSamples")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        gpuList.add(arr.optDouble(i).toFloat())
                    }
                }

                val minFps = json.getInt("minFps")
                val fallbackLow5 = json.optDouble("low5PercentFps", minFps.toDouble()).toFloat()

                FpsSessionRecord(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    packageName = json.getString("packageName"),
                    appName = json.optString("appName", json.getString("packageName")),
                    startTime = json.getLong("startTime"),
                    endTime = json.optLong("endTime", json.getLong("startTime")),
                    durationSeconds = json.getLong("durationSeconds"),
                    avgFps = json.getInt("avgFps"),
                    maxFps = json.getInt("maxFps"),
                    minFps = minFps,
                    fpsVariance = json.optDouble("fpsVariance", 0.0).toFloat(),
                    smoothnessPercent = json.optDouble("smoothnessPercent", 100.0).toFloat(),
                    low5PercentFps = fallbackLow5,
                    maxTemperature = json.optDouble("maxTemperature", 0.0).toFloat(),
                    avgPowerWatts = json.optDouble("avgPowerWatts", 0.0).toFloat(),
                    maxCpuTemperature = json.optDouble("maxCpuTemperature", 0.0).toFloat(),
                    maxGpuTemperature = json.optDouble("maxGpuTemperature", 0.0).toFloat(),
                    fpsSamples = fpsList,
                    tempSamples = tempList,
                    cpuTempSamples = cpuList,
                    gpuTempSamples = gpuList
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
