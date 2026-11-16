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
     * Checks if a su binary exists in common system paths.
     */
    fun checkSuBinaryAvailable(): Boolean {
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

        // Fallback: which su
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            val available = exitCode == 0
            _isRootAvailable.value = available
            available
        } catch (_: Exception) {
            _isRootAvailable.value = false
            false
        }
    }

    /**
     * Tests if root permission is granted by running `su -c id` or `su -c echo test`.
     */
    suspend fun checkOrRequestRootAccess(requestPrompt: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!checkSuBinaryAvailable() && !requestPrompt) {
            _isRootGranted.value = false
            return@withContext false
        }

        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()

            val granted = process.exitValue() == 0 && (output.contains("uid=0") || output.contains("root"))
            _isRootGranted.value = granted
            _isRootAvailable.value = true
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
        if (useRoot && _isRootGranted.value) {
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
                process.waitFor()
                output.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error running root command: $command", e)
                ""
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
