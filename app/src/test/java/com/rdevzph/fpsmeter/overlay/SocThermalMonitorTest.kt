package com.rdevzph.fpsmeter.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocThermalMonitorTest {

    private val monitor = SocThermalMonitor(
        onSnapshotUpdate = {},
        onUnavailable = {}
    )

    @Test
    fun testRetroidPocket5_Snapdragon865_ignoresBatterySocAndParsesCpuCores() {
        // Exact thermal dump from Retroid Pocket 5 (Qualcomm SM8250 / Snapdragon 865)
        val sysfsDump = """
            aoss0-usr: 26900
            cpu-0-0-usr: 29200
            cpu-1-3-usr: 28000
            cpu-1-4-usr: 32600
            cpu-1-5-usr: 30700
            cpu-1-6-usr: 28800
            cpu-1-7-usr: 28800
            gpuss-0-usr: 27300
            aoss-1-usr: 27500
            cwlan-usr: 27500
            video-usr: 27900
            ddr-usr: 28300
            cpu-0-1-usr: 29600
            q6-hvx-usr: 27500
            camera-usr: 27100
            cmpss-usr: 28300
            npu-usr: 26700
            gpuss-1-usr: 27900
            gpuss-max-step: 27900
            apc-0-max-step: 29900
            apc-1-max-step: 33400
            pop-mem-step: 28300
            cpu-0-0-step: 29600
            cpu-0-2-usr: 28400
            cpu-0-1-step: 29600
            cpu-0-2-step: 28800
            cpu-0-3-step: 29900
            cpu-1-0-step: 34600
            cpu-1-1-step: 33000
            cpu-1-2-step: 29200
            cpu-1-3-step: 28800
            cpu-1-4-step: 32600
            cpu-1-5-step: 33400
            cpu-1-6-step: 29200
            cpu-0-3-usr: 30700
            cpu-1-7-step: 29900
            cwlan-step: 27500
            video-step: 28700
            ddr-step: 29000
            q6-hvx-step: 28300
            camera-step: 27100
            cmpss-step: 28700
            npu-step: 27100
            cpuss-0-usr: 29900
            cpuss-1-usr: 31900
            pm8150_tz: 29801
            pm8150b_tz: 26590
            pm8150b-ibat-lvl0: 546
            pm8150b-ibat-lvl1: 781
            pm8150b-vbat-lvl0: 3986
            pm8150b-vbat-lvl1: 3986
            cpu-1-0-usr: 31900
            pm8150b-vbat-lvl2: 3986
            pm8150b-bcl-lvl0: 0
            soc: 72
            pm8150l_tz: 31359
            pm8150l-vph-lvl0: 0
            pm8150l-bcl-lvl0: -274000
            cpu-1-1-usr: 34200
            conn-therm-usr: 27616
            xo-therm-usr: 28423
            skin-therm-usr: 28652
            mmw-pa1-usr: 28923
            camera-therm-usr: -40000
            skin-msm-therm-usr: -40000
            mmw-pa2-usr: -40000
            gpu-skin-avg-step: 7600
            cpu-1-2-usr: 29600
            bms: 25500
            battery: 25500
        """.trimIndent()

        val parsed = monitor.parseSysfsThermalOutput(sysfsDump)
        assertNotNull("Should parse a valid CPU/SoC temperature", parsed)

        // Must NOT match the battery percentage 72%
        assertTrue("Temperature should be real CPU reading, not battery %", parsed!! != 72.0f)
        assertTrue("Temperature should be in realistic 25-45C range", parsed in 25.0f..45.0f)

        // cpu-1-1-usr is the hottest CPU core at 34200 millidegrees (34.2C)
        assertEquals(34.2f, parsed, 0.05f)

        // Test snapshot extraction
        val snapshot = monitor.parseSysfsThermalSnapshot(sysfsDump)
        assertNotNull(snapshot)
        assertEquals(34.2f, snapshot!!.cpu!!, 0.05f)
        assertEquals(27.9f, snapshot.gpu!!, 0.05f) // gpuss-1-usr is 27900
        assertEquals(34.2f, snapshot.soc!!, 0.05f)
    }

    @Test
    fun testMediaTek_PocoX8ProMax_parsedCorrectly() {
        val halOutput = """
            Current temperatures from HAL:
            Temperature{mValue=43.5, mType=10, mName=soc_max, mStatus=0}
            Temperature{mValue=42.1, mType=0, mName=mtktscpu, mStatus=0}
            Temperature{mValue=35.0, mType=2, mName=battery, mStatus=0}
            Current cooling devices from HAL:
        """.trimIndent()

        val parsed = monitor.parseThermalServiceOutput(halOutput)
        assertNotNull(parsed)
        assertEquals(43.5f, parsed!!, 0.05f)

        val snapshot = monitor.parseThermalServiceSnapshot(halOutput)
        assertNotNull(snapshot)
        assertEquals(43.5f, snapshot!!.soc!!, 0.05f)
        assertEquals(42.1f, snapshot.cpu!!, 0.05f)
        org.junit.Assert.assertNull(snapshot.gpu)
    }

    @Test
    fun testQualcommHalWithSocBatteryPercentage_ignoresSocAndUsesCpuAndGpu() {
        val halOutput = """
            Current temperatures from HAL:
            Temperature{mValue=74.0, mType=8, mName=soc, mStatus=0}
            Temperature{mValue=33.5, mType=0, mName=cpu-1-4-usr, mStatus=0}
            Temperature{mValue=35.2, mType=1, mName=gpuss-0-usr, mStatus=0}
            Temperature{mValue=30.0, mType=0, mName=cpu-0-0-usr, mStatus=0}
            Temperature{mValue=25.0, mType=2, mName=battery, mStatus=0}
            Current cooling devices from HAL:
        """.trimIndent()

        val snapshot = monitor.parseThermalServiceSnapshot(halOutput)
        assertNotNull(snapshot)
        // Should ignore mName=soc with mValue=74.0
        assertEquals(33.5f, snapshot!!.cpu!!, 0.05f)
        assertEquals(35.2f, snapshot.gpu!!, 0.05f)
        // SoC tracks the peak silicon hotspot: max(CPU 33.5, GPU 35.2) = 35.2
        assertEquals(35.2f, snapshot.soc!!, 0.05f)
    }

    @Test
    fun testExclusionOfStepAndPmicZones() {
        assertTrue(monitor.isExcludedZone("soc"))
        assertTrue(monitor.isExcludedZone("soc-step"))
        assertTrue(monitor.isExcludedZone("gpu-skin-avg-step"))
        assertTrue(monitor.isExcludedZone("pm8150_tz"))
        assertTrue(monitor.isExcludedZone("pm8150b-vbat-lvl0"))
        assertTrue(monitor.isExcludedZone("bms"))
        assertTrue(monitor.isExcludedZone("battery"))
        assertTrue(monitor.isExcludedZone("skin-therm-usr"))

        // Genuine sensors must NOT be excluded
        org.junit.Assert.assertFalse(monitor.isExcludedZone("cpu-0-0-usr"))
        org.junit.Assert.assertFalse(monitor.isExcludedZone("cpu-1-1-usr"))
        org.junit.Assert.assertFalse(monitor.isExcludedZone("cpuss-0-usr"))
        org.junit.Assert.assertFalse(monitor.isExcludedZone("soc_thermal"))
        org.junit.Assert.assertFalse(monitor.isExcludedZone("mtktscpu"))
    }
}
