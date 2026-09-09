package com.rdevzph.fpsmeter.model

import org.json.JSONObject
import java.util.UUID

/**
 * Represents an aggregated gameplay FPS session.
 * Stores only summary statistics to keep storage footprint negligible.
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
    val minFps: Int
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
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FpsSessionRecord? {
            return try {
                FpsSessionRecord(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    packageName = json.getString("packageName"),
                    appName = json.optString("appName", json.getString("packageName")),
                    startTime = json.getLong("startTime"),
                    endTime = json.optLong("endTime", json.getLong("startTime")),
                    durationSeconds = json.getLong("durationSeconds"),
                    avgFps = json.getInt("avgFps"),
                    maxFps = json.getInt("maxFps"),
                    minFps = json.getInt("minFps")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
