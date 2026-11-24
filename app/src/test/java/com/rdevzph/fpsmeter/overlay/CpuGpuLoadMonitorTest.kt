package com.rdevzph.fpsmeter.overlay

/**
 * This code is the property of Romel Brosas (https://github.com/rdevz-ph/FPS-Meter-Android).
 * If you modify or reuse this code, please give appropriate credit to the author.
 */

import org.junit.Assert.*
import org.junit.Test

class CpuGpuLoadMonitorTest {

    @Test
    fun parseCpuStat_validLine_returnsCorrectTotals() {
        // user: 100, nice: 20, system: 30, idle: 800, iowait: 50, irq: 10, softirq: 5, steal: 5
        val statOutput = "cpu  100 20 30 800 50 10 5 5 0 0\n" +
                "cpu0 50 10 15 400 25 5 2 2 0 0"

        val sample = CpuGpuLoadMonitor.parseCpuStat(statOutput)
        assertNotNull(sample)
        assertEquals(850L, sample!!.idle) // idle (800) + iowait (50)
        assertEquals(1020L, sample.total) // sum of all fields
    }

    @Test
    fun calculateCpuLoad_validDeltas_computesPercentage() {
        val prev = CpuStatSample(idle = 850L, total = 1000L)
        // In the next sample, total increased by 1000, idle increased by 400
        val curr = CpuStatSample(idle = 1250L, total = 2000L)

        // deltaTotal = 1000, deltaIdle = 400 => load = (1 - 400/1000) * 100 = 60.0%
        val load = CpuGpuLoadMonitor.calculateCpuLoad(prev, curr)
        assertNotNull(load)
        assertEquals(60.0f, load!!, 0.01f)
    }

    @Test
    fun calculateCpuLoad_idleHigherOrZero_clampsProperly() {
        val prev = CpuStatSample(idle = 500L, total = 1000L)
        val same = CpuStatSample(idle = 500L, total = 1000L)
        assertNull(CpuGpuLoadMonitor.calculateCpuLoad(prev, same))

        val fullyIdle = CpuStatSample(idle = 600L, total = 1100L)
        val zeroLoad = CpuGpuLoadMonitor.calculateCpuLoad(prev, fullyIdle)
        assertEquals(0.0f, zeroLoad!!, 0.01f)
    }

    @Test
    fun parseGpuWork_validDumpsys_extractsUidsAndActiveDurations() {
        val dumpsysSample = """
            driverPackageName = system
            GPU work information.
            gpu_id uid total_active_duration_ns total_inactive_duration_ns
            0 1000 182870922550 9842367474210
            0 10003 1036712819 46322186025
            0 10261 59523602043 1455992951569
        """.trimIndent()

        val map = CpuGpuLoadMonitor.parseGpuWork(dumpsysSample)
        assertEquals(3, map.size)
        assertEquals(182870922550L, map[1000])
        assertEquals(1036712819L, map[10003])
        assertEquals(59523602043L, map[10261])
    }

    @Test
    fun calculateGpuLoad_validDeltas_computesPercentage() {
        val prevMap = mapOf(
            1000 to 1_000_000_000L,
            10261 to 5_000_000_000L
        )
        // Over a 1 second interval (1_000_000_000 ns),
        // UID 1000 worked 50 ms (50_000_000 ns)
        // UID 10261 worked 200 ms (200_000_000 ns)
        // Total active delta = 250 ms (250_000_000 ns) => 25.0% load
        val currMap = mapOf(
            1000 to 1_050_000_000L,
            10261 to 5_200_000_000L
        )

        val load = CpuGpuLoadMonitor.calculateGpuLoad(prevMap, currMap, 1_000_000_000L)
        assertNotNull(load)
        assertEquals(25.0f, load!!, 0.01f)
    }

    @Test
    fun calculateGpuLoad_zeroOrEmpty_returnsSafeValues() {
        val emptyMap = emptyMap<Int, Long>()
        assertNull(CpuGpuLoadMonitor.calculateGpuLoad(emptyMap, emptyMap, 1_000_000_000L))

        val map = mapOf(1000 to 100L)
        assertNull(CpuGpuLoadMonitor.calculateGpuLoad(map, map, 0L))
    }
}
