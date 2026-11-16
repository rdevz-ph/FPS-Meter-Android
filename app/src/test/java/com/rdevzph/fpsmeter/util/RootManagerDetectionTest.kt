package com.rdevzph.fpsmeter.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RootManagerDetectionTest {

    @Test
    fun testMagiskSuDetection() {
        // User's emulator case: 31.1:MAGISKSU
        val manager = ShellHelper.resolveRootManager(
            suVersion = "31.1:MAGISKSU",
            suPath = "/system_ext/bin/su",
            mountsRootHint = "",
            isGranted = true,
            isAvailable = true
        )
        assertEquals("Magisk", manager)
    }

    @Test
    fun testKernelSuDetection() {
        val managerVersion = ShellHelper.resolveRootManager(
            suVersion = "11874:kernelsu",
            suPath = "/system/bin/su",
            mountsRootHint = "",
            isGranted = true,
            isAvailable = true
        )
        assertEquals("KernelSU", managerVersion)

        val managerMount = ShellHelper.resolveRootManager(
            suVersion = "",
            suPath = "",
            mountsRootHint = "KernelSU",
            isGranted = true,
            isAvailable = true
        )
        assertEquals("KernelSU", managerMount)
    }

    @Test
    fun testAPatchDetection() {
        val manager = ShellHelper.resolveRootManager(
            suVersion = "10763:apatch",
            suPath = "/system/bin/su",
            mountsRootHint = "",
            isGranted = true,
            isAvailable = true
        )
        assertEquals("APatch", manager)
    }

    @Test
    fun testGenericSuDetection() {
        val manager = ShellHelper.resolveRootManager(
            suVersion = "13",
            suPath = "/system/bin/su",
            mountsRootHint = "",
            isGranted = true,
            isAvailable = true
        )
        assertEquals("Generic SU / Userdebug", manager)
    }

    @Test
    fun testUnrootedDetection() {
        val manager = ShellHelper.resolveRootManager(
            suVersion = "",
            suPath = "",
            mountsRootHint = "",
            isGranted = false,
            isAvailable = false
        )
        assertEquals("None", manager)
    }
}
