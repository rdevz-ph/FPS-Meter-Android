package com.rdevzph.fpsmeter.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object ShellHelper {
    private const val TAG = "ShellHelper"

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    /**
     * Checks if a su binary exists in common system paths, mounts, or PATH environment.
     * Supports Magisk, KernelSU, APatch, and traditional su setups.
     */
    fun checkSuBinaryAvailable(): Boolean {
        if (_isRootGranted.value) {
            _isRootAvailable.value = true
            return true
        }

        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/apex/com.android.runtime/bin/su"
        )
        for (path in paths) {
            try {
                if (File(path).exists()) {
                    _isRootAvailable.value = true
                    return true
                }
            } catch (_: Exception) {
            }
        }

        // Check PATH directories
        try {
            val pathEnv = System.getenv("PATH") ?: ""
            for (dir in pathEnv.split(":")) {
                if (File(dir, "su").exists()) {
                    _isRootAvailable.value = true
                    return true
                }
            }
        } catch (_: Exception) {
        }

        // Fallback: which su
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                _isRootAvailable.value = true
                return true
            }
        } catch (_: Exception) {
        }

        // KernelSU / APatch / Magisk mount inspection (/proc/self/mounts)
        try {
            val mountsFile = File("/proc/self/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                val hasRootMount = mountsFile.useLines { lines ->
                    lines.any { line ->
                        line.contains("ksu") || line.contains("kernelsu") || line.contains("magisk") || line.contains("apatch")
                    }
                }
                if (hasRootMount) {
                    _isRootAvailable.value = true
                    return true
                }
            }
        } catch (_: Exception) {
        }

        return _isRootAvailable.value
    }

    /**
     * Tests if root permission is granted by running `su -c id`.
     */
    suspend fun checkOrRequestRootAccess(requestPrompt: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            val exitCode = process.waitFor()

            val granted = exitCode == 0 && (output.contains("uid=0") || output.contains("root"))
            _isRootGranted.value = granted
            if (granted) {
                _isRootAvailable.value = true
            }
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            _isRootGranted.value = false
            false
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Executes a shell command, choosing root (su -c) if useRoot is true, otherwise Shizuku if available.
     */
    fun runShellCommand(command: String, useRoot: Boolean = false): String {
        if (useRoot) {
            var process: Process? = null
            return try {
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val output = StringBuilder()
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                }
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    _isRootGranted.value = true
                    _isRootAvailable.value = true
                    return output.toString()
                }
                if (output.isNotEmpty()) {
                    return output.toString()
                }
                // If root command returned non-zero with no output, fall back to Shizuku
                runShizukuProcess(command)
            } catch (e: Exception) {
                Log.e(TAG, "Error running root command: $command", e)
                runShizukuProcess(command)
            } finally {
                try {
                    process?.destroy()
                } catch (_: Exception) {
                }
            }
        }

        // Shizuku fallback
        return runShizukuProcess(command)
    }

    @Suppress("DEPRECATION")
    private fun runShizukuProcess(command: String): String {
        var process: Process? = null
        return try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return ""
            process = rikka.shizuku.Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error running Shizuku command: $command", e)
            ""
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
