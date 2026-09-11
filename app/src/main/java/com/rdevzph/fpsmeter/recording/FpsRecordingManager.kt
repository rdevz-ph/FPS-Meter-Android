package com.rdevzph.fpsmeter.recording

import android.content.Context
import com.rdevzph.fpsmeter.model.FpsSessionRecord
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight, in-memory FPS session recording manager.
 *
 * - Zero sample-by-sample logging: only aggregates sum, count, min, and max in memory.
 * - Periodic checkpointing every 60 seconds to minimize disk writes while guarding against app termination.
 * - Auto-finalizes and persists session on app exit, game change, or service stop.
 */
object FpsRecordingManager {

    private const val PREFS_NAME = "fps_recording_prefs"
    private const val KEY_SESSIONS = "fps_sessions_json"
    private const val MAX_SESSIONS_PER_APP = 20
    private const val PERIODIC_SAVE_INTERVAL_MS = 60_000L // 1 minute
    private const val MIN_SESSION_DURATION_SEC = 3L // Ignore accidental app switches under 3s

    // Active in-memory session state
    private var activePackage: String? = null
    private var activeAppName: String = ""
    private var sessionStartTime: Long = 0L
    private var sampleCount: Long = 0L
    private var fpsSum: Long = 0L
    private var highestFps: Int = 0
    private var lowestFps: Int = Int.MAX_VALUE
    private var lastPeriodicSaveTime: Long = 0L
    private var activeSessionId: String? = null
    private var lastRecordedPackage: String? = null
    private var lastRecordedAppName: String = ""
    private var userStoppedPackage: String? = null

    private fun isSystemTransientPackage(pkg: String): Boolean {
        return pkg == "com.android.systemui" ||
                pkg == "android" ||
                pkg.contains("inputmethod", ignoreCase = true)
    }

    @Synchronized
    fun onFpsSample(context: Context, currentPackage: String?, fps: Int, appName: String? = null) {
        val now = System.currentTimeMillis()
        val settings = OverlaySettings.load(context)

        // If we are currently recording an active session
        if (activePackage != null) {
            val recordingPkg = activePackage!!
            // Check if foreground package explicitly switched to a different non-exempt app
            val isExplicitlyDifferentApp = currentPackage != null &&
                    currentPackage != recordingPkg &&
                    currentPackage != context.packageName &&
                    !isSystemTransientPackage(currentPackage)

            if (isExplicitlyDifferentApp) {
                // User navigated away to a different app: end current session
                endCurrentSession(context)
                userStoppedPackage = null
                // If the new app is also a recorded game, fall through to start a session for it
                if (!settings.autoRecordAll && !settings.recordingPackages.contains(currentPackage)) {
                    return
                }
            } else {
                // Still in active package (or package is null / overlay / system UI): record sample
                if (fps > 0) {
                    sampleCount++
                    fpsSum += fps
                    if (fps > highestFps) highestFps = fps
                    if (fps < lowestFps) lowestFps = fps
                }

                // Periodic checkpoint save
                if (now - lastPeriodicSaveTime >= PERIODIC_SAVE_INTERVAL_MS) {
                    saveActiveSessionCheckpoint(context)
                    lastPeriodicSaveTime = now
                }
                return
            }
        }

        // If user explicitly stopped recording for this game, do NOT auto-restart while still in this game
        if (currentPackage != null && currentPackage == userStoppedPackage) {
            return
        }
        if (currentPackage != null && currentPackage != userStoppedPackage && currentPackage != context.packageName && !isSystemTransientPackage(currentPackage)) {
            userStoppedPackage = null
        }

        // No active session: auto-start if currentPackage is enabled in recordingPackages or autoRecordAll is enabled
        val isEligible = currentPackage != null &&
                currentPackage != context.packageName &&
                !isSystemTransientPackage(currentPackage) &&
                (settings.autoRecordAll || settings.recordingPackages.contains(currentPackage))
        if (isEligible) {
            activePackage = currentPackage
            activeAppName = appName ?: currentPackage
            lastRecordedPackage = currentPackage
            lastRecordedAppName = activeAppName
            sessionStartTime = now
            lastPeriodicSaveTime = now
            activeSessionId = java.util.UUID.randomUUID().toString()
            sampleCount = if (fps > 0) 1L else 0L
            fpsSum = if (fps > 0) fps.toLong() else 0L
            highestFps = if (fps > 0) fps else 0
            lowestFps = if (fps > 0) fps else Int.MAX_VALUE
        }
    }

    @Synchronized
    fun isRecordingActive(): Boolean = activePackage != null

    @Synchronized
    fun getActivePackage(): String? = activePackage

    @Synchronized
    fun getActiveAppName(): String = activeAppName

    @Synchronized
    fun getLastRecordedPackage(): String? = lastRecordedPackage ?: activePackage

    @Synchronized
    fun getLastRecordedAppName(): String = if (lastRecordedAppName.isNotEmpty()) lastRecordedAppName else activeAppName

    @Synchronized
    fun startRecording(context: Context, packageName: String, appName: String): Boolean {
        val settings = OverlaySettings.load(context)
        if (!settings.autoRecordAll && !settings.recordingPackages.contains(packageName)) {
            return false
        }

        userStoppedPackage = null

        if (activePackage != null) {
            endCurrentSession(context)
        }

        val now = System.currentTimeMillis()
        activePackage = packageName
        activeAppName = appName
        lastRecordedPackage = packageName
        lastRecordedAppName = appName
        sessionStartTime = now
        lastPeriodicSaveTime = now
        activeSessionId = java.util.UUID.randomUUID().toString()
        sampleCount = 0L
        fpsSum = 0L
        highestFps = 0
        lowestFps = Int.MAX_VALUE
        return true
    }

    @Synchronized
    fun stopRecording(context: Context): FpsSessionRecord? {
        val pkg = activePackage
        val name = activeAppName
        if (pkg != null) {
            lastRecordedPackage = pkg
            lastRecordedAppName = name
            userStoppedPackage = pkg
        }
        return endCurrentSession(context)
    }

    @Synchronized
    fun endCurrentSession(context: Context): FpsSessionRecord? {
        val pkg = activePackage ?: return null
        val startTime = sessionStartTime
        val count = sampleCount
        val sum = fpsSum
        val high = highestFps
        val low = if (lowestFps == Int.MAX_VALUE) high else lowestFps
        val now = System.currentTimeMillis()
        val durationSec = ((now - startTime) / 1000L).coerceAtLeast(0L)
        val id = activeSessionId ?: java.util.UUID.randomUUID().toString()
        val name = activeAppName

        // Reset in-memory state
        activePackage = null
        activeAppName = ""
        sessionStartTime = 0L
        sampleCount = 0L
        fpsSum = 0L
        highestFps = 0
        lowestFps = Int.MAX_VALUE
        activeSessionId = null
        lastPeriodicSaveTime = 0L

        // Discard trivial sessions with zero samples or under minimum duration
        if (count == 0L || durationSec < MIN_SESSION_DURATION_SEC) {
            // If an earlier checkpoint was saved for this ID, remove it
            removeSessionById(context, id)
            return null
        }

        val avgFps = (sum / count).toInt()
        val record = FpsSessionRecord(
            id = id,
            packageName = pkg,
            appName = name,
            startTime = startTime,
            endTime = now,
            durationSeconds = durationSec,
            avgFps = avgFps,
            maxFps = high,
            minFps = low
        )

        saveOrUpdateSession(context, record)
        return record
    }

    private fun saveActiveSessionCheckpoint(context: Context) {
        val pkg = activePackage ?: return
        val count = sampleCount
        if (count == 0L) return
        val startTime = sessionStartTime
        val now = System.currentTimeMillis()
        val durationSec = ((now - startTime) / 1000L).coerceAtLeast(0L)
        val avgFps = (fpsSum / count).toInt()
        val high = highestFps
        val low = if (lowestFps == Int.MAX_VALUE) high else lowestFps
        val id = activeSessionId ?: return

        val record = FpsSessionRecord(
            id = id,
            packageName = pkg,
            appName = activeAppName,
            startTime = startTime,
            endTime = now,
            durationSeconds = durationSec,
            avgFps = avgFps,
            maxFps = high,
            minFps = low
        )

        saveOrUpdateSession(context, record)
    }

    @Synchronized
    fun getSessions(context: Context): List<FpsSessionRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<FpsSessionRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                FpsSessionRecord.fromJson(obj)?.let { list.add(it) }
            }
            list.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun getSessionsForPackage(context: Context, packageName: String): List<FpsSessionRecord> {
        return getSessions(context).filter { it.packageName == packageName }
    }

    @Synchronized
    private fun saveOrUpdateSession(context: Context, record: FpsSessionRecord) {
        val existing = getSessions(context).toMutableList()
        val existingIdx = existing.indexOfFirst { it.id == record.id }
        if (existingIdx >= 0) {
            existing[existingIdx] = record
        } else {
            existing.add(0, record)
        }

        // Cap sessions per package to prevent storage growth
        val packageCounts = mutableMapOf<String, Int>()
        val pruned = mutableListOf<FpsSessionRecord>()
        for (item in existing.sortedByDescending { it.startTime }) {
            val count = packageCounts.getOrDefault(item.packageName, 0)
            if (count < MAX_SESSIONS_PER_APP) {
                pruned.add(item)
                packageCounts[item.packageName] = count + 1
            }
        }

        persistSessions(context, pruned)
    }

    @Synchronized
    fun deleteSession(context: Context, sessionId: String) {
        val existing = getSessions(context).filter { it.id != sessionId }
        persistSessions(context, existing)
    }

    @Synchronized
    fun clearSessionsForPackage(context: Context, packageName: String) {
        val existing = getSessions(context).filter { it.packageName != packageName }
        persistSessions(context, existing)
    }

    @Synchronized
    fun clearAllSessions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSIONS).apply()
    }

    private fun removeSessionById(context: Context, sessionId: String) {
        val existing = getSessions(context).filter { it.id != sessionId }
        persistSessions(context, existing)
    }

    private fun persistSessions(context: Context, sessions: List<FpsSessionRecord>) {
        val jsonArray = JSONArray()
        sessions.forEach { jsonArray.put(it.toJson()) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply()
    }
}
