package com.rdevzph.fpsmeter.crash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import com.rdevzph.fpsmeter.ui.crash.CrashActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Global uncaught exception handler for FPS Meter Android.
 * Catches unhandled crashes on any thread, logs device and trace telemetry,
 * stores the report securely in app cache, and launches [CrashActivity]
 * in an isolated process (:crash).
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val processName = getProcessName(context)
            if (processName?.endsWith(":crash") == true) {
                // Prevent recursive crash loop if CrashActivity itself crashes
                defaultHandler?.uncaughtException(thread, throwable)
                return
            }

            val (versionName, versionCode) = getVersionInfo(context)
            val fullReport = buildCrashReport(context, thread, throwable, versionName, versionCode, processName)

            // Save report to cache file to prevent TransactionTooLargeException
            val reportFile = File(context.cacheDir, CRASH_REPORT_FILE_NAME)
            reportFile.writeText(fullReport)

            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_REPORT_PATH, reportFile.absolutePath)
                putExtra(CrashActivity.EXTRA_ERROR_NAME, throwable.javaClass.simpleName.ifEmpty { "Exception" })
                putExtra(CrashActivity.EXTRA_ERROR_MESSAGE, throwable.localizedMessage ?: throwable.message ?: "Unknown error")
                putExtra(CrashActivity.EXTRA_APP_VERSION, "$versionName ($versionCode)")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error within CrashHandler", t)
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        try {
            Thread.sleep(200)
        } catch (ignored: InterruptedException) {
        }

        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    companion object {
        private const val TAG = "CrashHandler"
        const val CRASH_REPORT_FILE_NAME = "fps_meter_crash_report.txt"

        @Volatile
        private var installed = false

        fun install(context: Context) {
            if (installed) return
            synchronized(this) {
                if (installed) return
                val currentProcess = getProcessName(context)
                if (currentProcess?.endsWith(":crash") == true) {
                    return
                }
                val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, defaultHandler))
                installed = true
                Log.i(TAG, "CrashHandler installed for process: $currentProcess")
            }
        }

        fun getProcessName(context: Context): String? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Application.getProcessName()
            }
            val pid = Process.myPid()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.forEach { processInfo ->
                if (processInfo.pid == pid) {
                    return processInfo.processName
                }
            }
            return null
        }

        fun getVersionInfo(context: Context): Pair<String, String> {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toString()
                }
                Pair(pInfo.versionName ?: "Unknown", vCode)
            } catch (e: Exception) {
                Pair("1.6", "7")
            }
        }

        fun buildCrashReport(
            context: Context,
            thread: Thread,
            throwable: Throwable,
            versionName: String,
            versionCode: String,
            processName: String?
        ): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
            val timestamp = dateFormat.format(Date())
            val stackTrace = Log.getStackTraceString(throwable)

            return buildString {
                appendLine("==========================================")
                appendLine("        FPS METER CRASH REPORT           ")
                appendLine("==========================================")
                appendLine("Timestamp     : $timestamp")
                appendLine("App Version   : $versionName ($versionCode)")
                appendLine("Package       : ${context.packageName}")
                appendLine("Process       : ${processName ?: "unknown"}")
                appendLine()
                appendLine("--- Device Telemetry ---")
                appendLine("Manufacturer  : ${Build.MANUFACTURER}")
                appendLine("Brand         : ${Build.BRAND}")
                appendLine("Model         : ${Build.MODEL}")
                appendLine("Product       : ${Build.PRODUCT}")
                appendLine("Device        : ${Build.DEVICE}")
                appendLine("Board         : ${Build.BOARD}")
                appendLine("Hardware      : ${Build.HARDWARE}")
                appendLine("Android OS    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
                }
                appendLine("Build ID      : ${Build.DISPLAY}")
                appendLine()
                appendLine("--- Thread & Exception ---")
                @Suppress("DEPRECATION")
                val threadId = thread.id
                appendLine("Thread Name   : ${thread.name} (id: $threadId)")
                appendLine("Exception     : ${throwable.javaClass.name}")
                appendLine("Message       : ${throwable.localizedMessage ?: throwable.message ?: "None"}")
                appendLine()
                appendLine("--- Stack Trace ---")
                appendLine(stackTrace.trimEnd())
                appendLine("==========================================")
            }
        }
    }
}
