package com.rdevzph.fpsmeter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.util.LocaleHelper
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings
import kotlinx.coroutines.*

import android.view.accessibility.AccessibilityWindowInfo

/**
 * Accessibility Service that monitors foreground app/window transitions
 * to automatically launch or stop the FPS overlay for designated games and apps.
 */
class FpsAccessibilityService : AccessibilityService() {

    companion object {
        var isServiceRunning = false
        var currentForegroundPackage: String? = null
        var onServiceStateChanged: ((Boolean) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stopOverlayJob: Job? = null
    private var isCurrentlyInTargetApp = false

    private val baseIgnoredPackages = setOf(
        "com.android.systemui",
        "android",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        // Transsion (Tecno / Infinix / Itel) Smart Panel & Game Space
        "com.transsion.smartpanel",
        "com.transsion.smartpanel.overlay",
        "com.transsion.gamespace",
        "com.transsion.gamespace.app",
        "com.transsion.gamespace.app.overlay",
        "com.transsion.gameassistant",
        "com.transsion.magicshow",
        "com.transsion.multiwindow",
        "com.transsion.multiwindow.overlay",
        "com.transsion.thunderback",
        "com.transsion.screencapture",
        "com.transsion.screenrecorder",
        // Xiaomi Game Turbo / MIUI / HyperOS Security plugins
        "com.miui.securityadd",
        "com.miui.securitycenter",
        "com.miui.touchassistant",
        "com.miui.freeform",
        "com.miui.contentcatcher",
        "com.miui.voiceassist",
        "com.miui.screenshot",
        "com.miui.screenrecorder",
        "com.xiaomi.gameboosterglobal",
        "com.xiaomi.gamebooster",
        "com.xiaomi.migameservice",
        "com.xiaomi.misettings",
        // Samsung Game Tools & Sidebars
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gamehome",
        "com.samsung.android.game.gos",
        "com.samsung.android.sidebarservice",
        "com.samsung.android.app.cocktailbarservice",
        // Oppo / OnePlus / Realme Games & Sidebars
        "com.oplus.games",
        "com.coloros.gamespace",
        "com.coloros.gamespaceui",
        "com.oplus.cosa",
        "com.oplus.smartsidebar",
        "com.coloros.smartsidebar",
        // Vivo / iQOO
        "com.vivo.gamecube",
        "com.vivo.gamewatch",
        "com.iqoo.engineermode",
        // Asus ROG / Black Shark / Lenovo / Motorola
        "com.asus.gamewidget",
        "com.asus.gamecenter",
        "com.blackshark.gamecenter",
        "com.lenovo.leos.gamecenter",
        "com.motorola.gamemode",
        // Google Play Services & Play Games (Sign-in popups, achievements, billing)
        "com.google.android.gms",
        "com.google.android.play.games",
        "com.android.vending",
        "com.google.android.gsf"
    )

    private var lastTargetAppTime = 0L

    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("quickstep", ignoreCase = true)
        ) {
            return true
        }
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == pkg
        } catch (e: Exception) {
            false
        }
    }

    private fun isIgnoredPackage(rawPkg: String): Boolean {
        if (rawPkg == packageName || rawPkg in baseIgnoredPackages) return true
        if (rawPkg.startsWith("com.google.android.gms") ||
            rawPkg == "com.google.android.play.games" ||
            rawPkg == "com.android.vending" ||
            rawPkg == "com.google.android.gsf" ||
            rawPkg.contains("gamespace", ignoreCase = true) ||
            rawPkg.contains("gamebooster", ignoreCase = true) ||
            rawPkg.contains("gameturbo", ignoreCase = true) ||
            rawPkg.contains("gametools", ignoreCase = true) ||
            rawPkg.contains("gamecube", ignoreCase = true) ||
            rawPkg.contains("gamecenter", ignoreCase = true) ||
            rawPkg.contains("gamemode", ignoreCase = true) ||
            rawPkg.contains("gameassistant", ignoreCase = true) ||
            rawPkg.contains("smartpanel", ignoreCase = true) ||
            rawPkg.contains("smartsidebar", ignoreCase = true) ||
            rawPkg.contains("sidepanel", ignoreCase = true) ||
            rawPkg.contains("inputmethod", ignoreCase = true) ||
            rawPkg.contains("keyboard", ignoreCase = true) ||
            rawPkg == "com.touchtype.swiftkey" ||
            rawPkg == "com.samsung.android.honeyboard" ||
            rawPkg == "com.sohu.inputmethod.sogou" ||
            rawPkg == "com.baidu.input"
        ) {
            return true
        }
        return false
    }

    private fun getActiveApplicationPackage(): String? {
        return try {
            val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val nonIgnoredWindows = appWindows.filterNot { window ->
                val title = window.title?.toString()
                if (title != null && title.contains("/")) {
                    val pkg = title.substringBefore("/").trim()
                    isIgnoredPackage(pkg)
                } else {
                    false
                }
            }

            val candidates = if (nonIgnoredWindows.isNotEmpty()) nonIgnoredWindows else appWindows
            val focusedOrActive = candidates.firstOrNull { it.isFocused }
                ?: candidates.firstOrNull { it.isActive }
                ?: candidates.firstOrNull()

            val title = focusedOrActive?.title?.toString()
            if (title != null && title.contains("/")) {
                val pkg = title.substringBefore("/").trim()
                if (pkg.isNotEmpty()) return pkg
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveForegroundPackage(event: AccessibilityEvent?): String? {
        val eventType = event?.eventType ?: return null

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val direct = event.packageName?.toString()
            if (!direct.isNullOrEmpty()) return direct
        }

        val activeApp = getActiveApplicationPackage()
        if (activeApp != null) return activeApp

        val eventPkg = event.packageName?.toString()
        if (!eventPkg.isNullOrEmpty()) return eventPkg

        return try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        onServiceStateChanged?.invoke(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            val eventType = event?.eventType ?: return
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

            // If the event came from a non-application window (such as an overlay, system panel, or keyboard),
            // it must not trigger an application switch or overlay dismissal.
            try {
                val eventWindow = windows.find { it.id == event.windowId }
                if (eventWindow != null && eventWindow.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return
                }
            } catch (e: Exception) {
                // Ignore
            }

            val rawPkg = resolveForegroundPackage(event) ?: return

            if (isIgnoredPackage(rawPkg)) return

            val settings = OverlaySettings.load(this)
            val isTargetApp = settings.autoStartPackages.contains(rawPkg) || settings.recordingPackages.contains(rawPkg)
            val shouldAutoStart = settings.autoStartEnabled && isTargetApp

            if (isTargetApp) {
                isCurrentlyInTargetApp = true
                lastTargetAppTime = System.currentTimeMillis()
                stopOverlayJob?.cancel()
                stopOverlayJob = null
                currentForegroundPackage = rawPkg

                if (shouldAutoStart && !FpsOverlayService.isRunning && Settings.canDrawOverlays(this)) {
                    FpsOverlayService.isAutoStarted = true
                    val startIntent = Intent(this, FpsOverlayService::class.java)
                    ContextCompat.startForegroundService(this, startIntent)
                }
            } else {
                // Non-target app event.
                // 1. Ignore trailing launcher exit/dismissal animations when returning to a target app.
                val now = System.currentTimeMillis()
                val isTrailingLauncherEvent = isLauncherPackage(rawPkg) && (now - lastTargetAppTime < 2000L)
                if (isTrailingLauncherEvent) {
                    return
                }

                // 2. If the user was in a target app, check if the top application window is still the target app.
                // Overlays, transient dialogs, or unclassified assistant panels must not cancel target app state.
                val activeAppPkg = getActiveApplicationPackage()
                val isStillInTargetApp = activeAppPkg != null &&
                    (settings.autoStartPackages.contains(activeAppPkg) || settings.recordingPackages.contains(activeAppPkg))
                if (isCurrentlyInTargetApp && isStillInTargetApp) {
                    return
                }

                isCurrentlyInTargetApp = false
                if (FpsOverlayService.isRunning && FpsOverlayService.isAutoStarted) {
                    if (stopOverlayJob == null || stopOverlayJob?.isActive == false) {
                        stopOverlayJob = serviceScope.launch {
                            delay(1000L)
                            if (isActive) {
                                // Final validation: verify active application window before stopping overlay
                                val currentActiveApp = getActiveApplicationPackage()
                                val verifiedInTarget = currentActiveApp != null &&
                                    (settings.autoStartPackages.contains(currentActiveApp) || settings.recordingPackages.contains(currentActiveApp))

                                if (verifiedInTarget) {
                                    isCurrentlyInTargetApp = true
                                    currentForegroundPackage = currentActiveApp
                                } else if (FpsOverlayService.isRunning && FpsOverlayService.isAutoStarted) {
                                    currentForegroundPackage = null
                                    FpsOverlayService.stop(this@FpsAccessibilityService)
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Guard against any unexpected runtime exceptions taking down the accessibility service
        }
    }

    override fun onInterrupt() {
        // Required by AccessibilityService
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (FpsOverlayService.isRunning && FpsOverlayService.isAutoStarted) {
            FpsOverlayService.stop(this)
        }
        stopOverlayJob?.cancel()
        stopOverlayJob = null
        isCurrentlyInTargetApp = false
        serviceScope.coroutineContext.cancelChildren()
        isServiceRunning = false
        currentForegroundPackage = null
        onServiceStateChanged?.invoke(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (FpsOverlayService.isRunning && FpsOverlayService.isAutoStarted) {
            FpsOverlayService.stop(this)
        }
        stopOverlayJob?.cancel()
        stopOverlayJob = null
        isCurrentlyInTargetApp = false
        serviceScope.cancel()
        isServiceRunning = false
        currentForegroundPackage = null
        onServiceStateChanged?.invoke(false)
    }
}
