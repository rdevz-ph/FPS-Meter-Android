package com.rdevzph.fpsmeter.recording

import com.rdevzph.fpsmeter.model.FpsSessionRecord
import com.rdevzph.fpsmeter.ui.screen.groupSessionsByApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAnalyticsTest {

    @Test
    fun testFpsSessionRecordDataStructure() {
        val record = FpsSessionRecord(
            id = "test-session-123",
            packageName = "com.example.game",
            appName = "Example Game",
            startTime = 1700000000000L,
            endTime = 1700000600000L,
            durationSeconds = 600L,
            avgFps = 120,
            maxFps = 121,
            minFps = 116,
            fpsVariance = 0.8f,
            smoothnessPercent = 100.0f,
            low5PercentFps = 117.8f,
            maxTemperature = 41.0f,
            avgPowerWatts = 4.58f,
            fpsSamples = listOf(120, 121, 120, 119, 120),
            tempSamples = listOf(40.5f, 40.8f, 41.0f)
        )

        assertEquals("test-session-123", record.id)
        assertEquals("com.example.game", record.packageName)
        assertEquals(120, record.avgFps)
        assertEquals(121, record.maxFps)
        assertEquals(116, record.minFps)
        assertEquals(0.8f, record.fpsVariance, 0.01f)
        assertEquals(100.0f, record.smoothnessPercent, 0.01f)
        assertEquals(117.8f, record.low5PercentFps, 0.01f)
        assertEquals(41.0f, record.maxTemperature, 0.01f)
        assertEquals(4.58f, record.avgPowerWatts, 0.01f)
        assertEquals(listOf(120, 121, 120, 119, 120), record.fpsSamples)
        assertEquals(listOf(40.5f, 40.8f, 41.0f), record.tempSamples)
    }

    @Test
    fun testDefaultValuesForOlderVersions() {
        val defaultRecord = FpsSessionRecord(
            packageName = "com.legacy.app",
            appName = "Legacy App",
            startTime = 1600000000000L,
            endTime = 1600000300000L,
            durationSeconds = 300L,
            avgFps = 60,
            maxFps = 60,
            minFps = 55
        )

        assertEquals(0f, defaultRecord.fpsVariance, 0.01f)
        assertEquals(100f, defaultRecord.smoothnessPercent, 0.01f)
        assertEquals(0f, defaultRecord.low5PercentFps, 0.01f)
        assertEquals(0f, defaultRecord.maxTemperature, 0.01f)
        assertEquals(0f, defaultRecord.avgPowerWatts, 0.01f)
        assertEquals(0f, defaultRecord.maxCpuTemperature, 0.01f)
        assertEquals(0f, defaultRecord.maxGpuTemperature, 0.01f)
        assertTrue(defaultRecord.fpsSamples.isEmpty())
        assertTrue(defaultRecord.tempSamples.isEmpty())
        assertTrue(defaultRecord.cpuTempSamples.isEmpty())
        assertTrue(defaultRecord.gpuTempSamples.isEmpty())
    }

    @Test
    fun testGroupSessionsByApp() {
        val session1 = FpsSessionRecord(
            id = "s1",
            packageName = "com.game.a",
            appName = "Game A",
            startTime = 1000L,
            endTime = 1060L,
            durationSeconds = 60L,
            avgFps = 60,
            maxFps = 62,
            minFps = 58
        )
        val session2 = FpsSessionRecord(
            id = "s2",
            packageName = "com.game.a",
            appName = "Game A",
            startTime = 2000L,
            endTime = 2060L,
            durationSeconds = 60L,
            avgFps = 58,
            maxFps = 60,
            minFps = 50
        )
        val session3 = FpsSessionRecord(
            id = "s3",
            packageName = "com.game.b",
            appName = "Game B",
            startTime = 3000L,
            endTime = 3120L,
            durationSeconds = 120L,
            avgFps = 90,
            maxFps = 92,
            minFps = 88
        )

        val grouped = groupSessionsByApp(listOf(session1, session2, session3))

        assertEquals(2, grouped.size)

        val gameA = grouped.firstOrNull { it.packageName == "com.game.a" }
        assertNotNull(gameA)
        assertEquals(2, gameA!!.sessionCount)
        assertEquals(120L, gameA.totalDurationSeconds)
        assertEquals(59, gameA.overallAvgFps)
        assertEquals(62, gameA.peakFps)
        assertEquals(50, gameA.lowestFps)
        assertEquals(2, gameA.sessions.size)

        val gameB = grouped.firstOrNull { it.packageName == "com.game.b" }
        assertNotNull(gameB)
        assertEquals(1, gameB!!.sessionCount)
        assertEquals(120L, gameB.totalDurationSeconds)
        assertEquals(90, gameB.overallAvgFps)
    }

    @Test
    fun testMetricsCalculations() {
        val samples = listOf(60, 60, 59, 60, 58, 60, 61, 60, 59, 60)
        val mean = samples.average()
        val variance = kotlin.math.sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        assertTrue(variance < 1.0) // very stable FPS should have variance < 1.0

        val smoothCount = samples.count { it >= 45 }
        val smoothness = (smoothCount.toFloat() / samples.size) * 100f
        assertEquals(100f, smoothness, 0.01f)

        val sorted = samples.sorted()
        val low5Idx = (sorted.size * 0.05).toInt().coerceIn(0, sorted.lastIndex)
        assertEquals(58, sorted[low5Idx])
    }

    @Test
    fun testJsonBackwardCompatibilityWithLegacyV36Record() {
        // Simulates an older session JSON saved by version 3.6 or earlier (no new fields)
        val legacyJsonString = """
            {
                "id": "legacy-session-001",
                "packageName": "com.miHoYo.GenshinImpact",
                "appName": "Genshin Impact",
                "startTime": 1700000000000,
                "endTime": 1700000060000,
                "durationSeconds": 60,
                "avgFps": 60,
                "maxFps": 61,
                "minFps": 58
            }
        """.trimIndent()

        val jsonObject = org.json.JSONObject(legacyJsonString)
        val parsed = FpsSessionRecord.fromJson(jsonObject)

        assertNotNull(parsed)
        assertEquals("legacy-session-001", parsed!!.id)
        assertEquals("com.miHoYo.GenshinImpact", parsed.packageName)
        assertEquals("Genshin Impact", parsed.appName)
        assertEquals(60, parsed.avgFps)
        assertEquals(61, parsed.maxFps)
        assertEquals(58, parsed.minFps)

        // Verifying default fallbacks for missing fields
        assertEquals(0f, parsed.fpsVariance, 0.01f)
        assertEquals(100f, parsed.smoothnessPercent, 0.01f)
        assertEquals(58f, parsed.low5PercentFps, 0.01f) // falls back to minFps
        assertEquals(0f, parsed.maxTemperature, 0.01f)
        assertEquals(0f, parsed.avgPowerWatts, 0.01f)
        assertEquals(0f, parsed.maxCpuTemperature, 0.01f)
        assertEquals(0f, parsed.maxGpuTemperature, 0.01f)
        assertTrue(parsed.fpsSamples.isEmpty())
        assertTrue(parsed.tempSamples.isEmpty())
        assertTrue(parsed.cpuTempSamples.isEmpty())
        assertTrue(parsed.gpuTempSamples.isEmpty())

        // Ensure serializing legacy record to JSON and re-parsing works flawlessly
        val reSerialized = parsed.toJson()
        val reParsed = FpsSessionRecord.fromJson(reSerialized)
        assertNotNull(reParsed)
        assertEquals(parsed.id, reParsed!!.id)
        assertEquals(parsed.packageName, reParsed.packageName)
        assertEquals(parsed.avgFps, reParsed.avgFps)
    }
}
