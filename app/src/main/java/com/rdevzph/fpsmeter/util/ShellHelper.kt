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

data class RootDiagnosticsInfo(
    val isAvailable: Boolean = false,
    val isGranted: Boolean = false,
    val rootManager: String = "None",
    val suPath: String = "None",
    val suVersion: String = "N/A"
)

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
     * Inspects root environment to identify the active root manager, su binary path, and version.
     */
    fun detectRootDiagnostics(): RootDiagnosticsInfo {
        var suPath = ""
        var suVersion = ""
        var mountsRootHint = ""
        var isAvailable = checkSuBinaryAvailable()
        var isGranted = _isRootGranted.value

        // 1. Locate su path via `which su`
        var whichProcess: Process? = null
        try {
            whichProcess = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val path = BufferedReader(InputStreamReader(whichProcess.inputStream)).readLine()?.trim() ?: ""
            whichProcess.waitFor()
            if (path.isNotEmpty() && path.contains("/")) {
                suPath = path
                isAvailable = true
            }
        } catch (_: Exception) {
        } finally {
            try { whichProcess?.destroy() } catch (_: Exception) {}
        }

        // Fallback: check static locations
        if (suPath.isEmpty()) {
            val candidatePaths = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/data/local/su",
                "/su/bin/su",
                "/data/adb/ksu/bin/su",
                "/data/adb/ap/bin/su",
                "/apex/com.android.runtime/bin/su"
            )
            for (p in candidatePaths) {
                try {
                    if (File(p).exists()) {
                        suPath = p
                        isAvailable = true
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Read /proc/self/mounts for root manager signatures
        try {
            val mountsFile = File("/proc/self/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                mountsFile.useLines { lines ->
                    for (line in lines) {
                        val lower = line.lowercase()
                        if (lower.contains("kernelsu") || lower.contains("/data/adb/ksu") || lower.contains(" ksu ")) {
                            mountsRootHint = "KernelSU"
                            isAvailable = true
                            break
                        } else if (lower.contains("apatch") || lower.contains("/data/adb/ap")) {
                            mountsRootHint = "APatch"
                            isAvailable = true
                            break
                        } else if (lower.contains("magisk") || lower.contains("/sbin/.magisk")) {
                            mountsRootHint = "Magisk"
                            isAvailable = true
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Test `su -v` or `su -V` for version and manager signature
        var vProcess: Process? = null
        try {
            vProcess = Runtime.getRuntime().exec(arrayOf("su", "-v"))
            val vOutput = BufferedReader(InputStreamReader(vProcess.inputStream)).readLine()?.trim() ?: ""
            vProcess.waitFor()
            if (vOutput.isNotEmpty()) {
                suVersion = vOutput
                isAvailable = true
            }
        } catch (_: Exception) {
        } finally {
            try { vProcess?.destroy() } catch (_: Exception) {}
        }

        if (suVersion.isEmpty()) {
            var vCapProcess: Process? = null
            try {
                vCapProcess = Runtime.getRuntime().exec(arrayOf("su", "-V"))
                val vOutput = BufferedReader(InputStreamReader(vCapProcess.inputStream)).readLine()?.trim() ?: ""
                vCapProcess.waitFor()
                if (vOutput.isNotEmpty()) {
                    suVersion = vOutput
                    isAvailable = true
                }
            } catch (_: Exception) {
            } finally {
                try { vCapProcess?.destroy() } catch (_: Exception) {}
            }
        }

        // 4. Test root permission if not yet confirmed
        if (!isGranted && isAvailable) {
            var idProcess: Process? = null
            try {
                idProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val idOutput = BufferedReader(InputStreamReader(idProcess.inputStream)).readLine()?.trim() ?: ""
                val exitCode = idProcess.waitFor()
                if (exitCode == 0 && (idOutput.contains("uid=0") || idOutput.contains("root"))) {
                    isGranted = true
                    _isRootGranted.value = true
                }
            } catch (_: Exception) {
            } finally {
                try { idProcess?.destroy() } catch (_: Exception) {}
            }
        }

        // If granted and suPath still empty, try su -c "which su"
        if (isGranted && suPath.isEmpty()) {
            var rootWhichProcess: Process? = null
            try {
                rootWhichProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "which su"))
                val path = BufferedReader(InputStreamReader(rootWhichProcess.inputStream)).readLine()?.trim() ?: ""
                rootWhichProcess.waitFor()
                if (path.isNotEmpty() && path.contains("/")) {
                    suPath = path
                }
            } catch (_: Exception) {
            } finally {
                try { rootWhichProcess?.destroy() } catch (_: Exception) {}
            }
        }

        // 5. Determine Root Manager
        val combined = "$suVersion $suPath $mountsRootHint".lowercase()
        val rootManager = when {
            combined.contains("kernelsu") || combined.contains("ksu") -> "KernelSU"
            combined.contains("apatch") -> "APatch"
            combined.contains("magisk") -> "Magisk"
            combined.contains("supersu") -> "SuperSU"
            mountsRootHint.isNotEmpty() -> mountsRootHint
            isGranted || suPath.isNotEmpty() || suVersion.isNotEmpty() -> "Generic SU / Userdebug"
            isAvailable -> "Unknown Root"
            else -> "None"
        }

        if (suPath.isEmpty() && (rootManager == "KernelSU" || rootManager == "APatch")) {
            suPath = "Kernel Syscall Hook"
        }

        return RootDiagnosticsInfo(
            isAvailable = isAvailable,
            isGranted = isGranted,
            rootManager = rootManager,
            suPath = if (suPath.isNotEmpty()) suPath else if (isAvailable) "Not in standard PATH" else "None",
            suVersion = if (suVersion.isNotEmpty()) suVersion else "N/A"
        )
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
