package com.rdevzph.fpsmeter.ui.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDiagnosticsScreenTest {

    @Test
    fun testGenerateThermalZoneDump_containsExpectedCommandHeadersAndOutput() {
        val data = DeviceDiagnosticData(
            deviceModel = "SM-X910",
            manufacturer = "samsung",
            deviceCode = "gts9uwifi",
            androidVersion = "14",
            sdkInt = 34,
            socModelShizuku = "SM8550",
            socPlatformShizuku = "kalama"
        )

        val mockRunner: (String) -> String = { cmd ->
            when {
                cmd.startsWith("id") -> "uid=2000(shell) gid=2000(shell) context=u:r:shell:s0"
                cmd.contains("dumpsys thermalservice") -> "Temperature{mValue=45.5, mType=0, mName=AP, mStatus=0}"
                cmd.contains("cat /sys/class/thermal/thermal_zone*/temp") -> "26900\n28400\ncat: /sys/class/thermal/thermal_zone48/temp: Invalid argument"
                cmd.contains("/proc/stat") -> "cpu  100 20 30 800 50 10 5 5 0 0"
                cmd.contains("dumpsys gpu") -> "GPU work information.\ngpu_id uid total_active_duration_ns total_inactive_duration_ns\n0 1000 182870922550 9842367474210"
                else -> "cpu-0-0-usr: 29200\ngpuss-0-usr: 27300"
            }
        }

        val dump = generateThermalZoneDump(data, mockRunner)

        // Verifies header contains device metadata and shell identity
        assertTrue(dump.contains("# Device: samsung SM-X910 (gts9uwifi)"))
        assertTrue(dump.contains("# Android: 14 (API 34)"))
        assertTrue(dump.contains("# SoC Model: SM8550"))
        assertTrue(dump.contains("# Platform: kalama"))
        assertTrue(dump.contains("# Shell Identity: uid=2000(shell)"))

        // Verifies commands requested by user are present in the dump structure
        assertTrue(dump.contains("$ dumpsys thermalservice"))
        assertTrue(dump.contains("mName=AP"))

        assertTrue(dump.contains("$ cat /sys/class/thermal/thermal_zone*/temp"))
        assertTrue(dump.contains("26900"))
        assertTrue(dump.contains("cat: /sys/class/thermal/thermal_zone48/temp: Invalid argument"))

        assertTrue(dump.contains("$ grep '^cpu ' /proc/stat"))
        assertTrue(dump.contains("cpu  100 20 30 800 50 10 5 5 0 0"))

        assertTrue(dump.contains("$ dumpsys gpu (GPU work information)"))
        assertTrue(dump.contains("182870922550"))

        assertTrue(dump.contains("$ for d in /sys/class/thermal/thermal_zone*; do"))
        assertTrue(dump.contains("echo \"\$(cat \$d/type): \$(cat \$d/temp)\""))
        assertTrue(dump.contains("cpu-0-0-usr: 29200"))
        assertTrue(dump.contains("gpuss-0-usr: 27300"))
    }
}
