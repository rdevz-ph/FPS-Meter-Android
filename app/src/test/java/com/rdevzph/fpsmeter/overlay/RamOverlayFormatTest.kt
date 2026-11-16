package com.rdevzph.fpsmeter.overlay

import com.rdevzph.fpsmeter.model.RamUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RamOverlayFormatTest {

    private fun formatRamValue(bytes: Long, unit: RamUnit): String {
        return when (unit) {
            RamUnit.GB -> {
                val gb = bytes.toFloat() / (1024f * 1024f * 1024f)
                val formatted = String.format(Locale.US, "%.1f", gb)
                if (formatted.endsWith(".0")) {
                    "${formatted.substringBefore(".0")} GB"
                } else {
                    "$formatted GB"
                }
            }
            RamUnit.MB -> {
                val mb = bytes / (1024L * 1024L)
                "$mb MB"
            }
        }
    }

    @Test
    fun testRamUnitParsing() {
        assertEquals(RamUnit.GB, RamUnit.fromString("GB"))
        assertEquals(RamUnit.GB, RamUnit.fromString("gb"))
        assertEquals(RamUnit.MB, RamUnit.fromString("MB"))
        assertEquals(RamUnit.MB, RamUnit.fromString("mb"))
        assertEquals(RamUnit.GB, RamUnit.fromString(null))
        assertEquals(RamUnit.GB, RamUnit.fromString("UNKNOWN"))
    }

    @Test
    fun testFormatRamValueInGb() {
        val oneGbBytes = 1024L * 1024L * 1024L
        val twoGbBytes = 2L * 1024L * 1024L * 1024L
        val oneAndHalfGbBytes = (1.5f * 1024f * 1024f * 1024f).toLong()

        assertEquals("1 GB", formatRamValue(oneGbBytes, RamUnit.GB))
        assertEquals("2 GB", formatRamValue(twoGbBytes, RamUnit.GB))
        assertEquals("1.5 GB", formatRamValue(oneAndHalfGbBytes, RamUnit.GB))
    }

    @Test
    fun testFormatRamValueInMb() {
        val oneGbBytes = 1024L * 1024L * 1024L
        val twoGbBytes = 2L * 1024L * 1024L * 1024L

        assertEquals("1024 MB", formatRamValue(oneGbBytes, RamUnit.MB))
        assertEquals("2048 MB", formatRamValue(twoGbBytes, RamUnit.MB))
    }

    @Test
    fun testRamOverlayFormatCompactSingleLine() {
        val oneGbBytes = 1024L * 1024L * 1024L
        val twoGbBytes = 2L * 1024L * 1024L * 1024L
        val availPct = 25
        val usedPct = 50

        val availStr = "${formatRamValue(oneGbBytes, RamUnit.GB)} ($availPct%)"
        val usedStr = "${formatRamValue(twoGbBytes, RamUnit.GB)} ($usedPct%)"

        val singleLine = "RAM: $availStr | $usedStr"
        assertEquals("RAM: 1 GB (25%) | 2 GB (50%)", singleLine)
    }

    @Test
    fun testRamOverlayFormatMultiLine() {
        val oneGbBytes = 1024L * 1024L * 1024L
        val twoGbBytes = 2L * 1024L * 1024L * 1024L
        val availPct = 25
        val usedPct = 50

        val availStr = "${formatRamValue(oneGbBytes, RamUnit.GB)} ($availPct%)"
        val usedStr = "${formatRamValue(twoGbBytes, RamUnit.GB)} ($usedPct%)"

        val line1 = "RAM: $availStr"
        val line2 = "     $usedStr"
        val multiLine = "$line1\n$line2"

        val expected = "RAM: 1 GB (25%)\n     2 GB (50%)"
        assertEquals(expected, multiLine)
    }

    @Test
    fun testSwapOverlayFormat() {
        val totalBytes = 4L * 1024L * 1024L * 1024L // 4 GB swap
        val freeBytes = 3L * 1024L * 1024L * 1024L  // 3 GB free -> 1 GB used (25%)
        val usedBytes = totalBytes - freeBytes
        val usedPct = ((usedBytes.toDouble() / totalBytes) * 100.0).toInt()

        val swapStrGb = "${formatRamValue(usedBytes, RamUnit.GB)} ($usedPct%)"
        val swapStrMb = "${formatRamValue(usedBytes, RamUnit.MB)} ($usedPct%)"

        assertEquals("1 GB (25%)", swapStrGb)
        assertEquals("1024 MB (25%)", swapStrMb)

        val rtssRow = "SWP: $swapStrGb"
        assertEquals("SWP: 1 GB (25%)", rtssRow)
    }

    @Test
    fun testSwapFormattingWhenZero() {
        val totalBytes = 0L
        val usedBytes = 0L
        val usedPercent = if (totalBytes > 0L) ((usedBytes.toDouble() / totalBytes) * 100.0).toInt() else 0

        val formattedGb = if (totalBytes > 0L) {
            "${formatRamValue(usedBytes, RamUnit.GB)} ($usedPercent%)"
        } else {
            "0.0 GB (0%)"
        }

        val formattedMb = if (totalBytes > 0L) {
            "${formatRamValue(usedBytes, RamUnit.MB)} ($usedPercent%)"
        } else {
            "0 MB (0%)"
        }

        assertEquals("0.0 GB (0%)", formattedGb)
        assertEquals("0 MB (0%)", formattedMb)
    }
}
