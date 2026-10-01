/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.content.Context
import com.moksha.auraisland.service.AuraNotificationListenerService
import com.moksha.auraisland.service.AuraOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Root manager supporting modern Android root implementations:
 * - Magisk (MagiskSU)
 * - KernelSU (ksu)
 * - KernelSU with SUSFS (Suspicious File System mount hiding)
 * - APatch (KernelPatch)
 * - RootFS / Generic SU
 */
object RootManager {

    enum class RootFlavor(val displayName: String, val isRooted: Boolean) {
        NONE("Not Rooted", false),
        MAGISK("Magisk", true),
        KERNELSU("KernelSU", true),
        KERNELSU_SUSFS("KernelSU (SUSFS)", true),
        APATCH("APatch", true),
        ROOTFS("RootFS / Generic SU", true)
    }

    private val SU_BINARY_LOCATIONS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/vendor/bin/su",
        "/apex/com.android.runtime/bin/su",
        "/su/bin/su",
        "/product/bin/su",
        "/product/xbin/su",
        "/system_ext/bin/su",
        "/odm/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/data/adb/magisk/busybox"
    )

    private val ROOT_MANAGER_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "io.github.a13e300.ksu",
        "com.nagisa.ksu",
        "org.bndeff.apd",
        "me.bndeff.apd"
    )

    /**
     * Checks if SUSFS kernel features or patches are present.
     */
    fun isSusfsDetected(): Boolean {
        return try {
            if (File("/proc/susfs").exists()) return true
            val version = File("/proc/version")
            if (version.exists() && version.readText().contains("susfs", ignoreCase = true)) return true
            if (File("/data/adb/modules/susfs4ksu").exists()) return true
            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if KernelSU driver / node is present.
     */
    fun isKernelSuDetected(): Boolean {
        return try {
            File("/dev/ksu").exists() || File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if APatch is present.
     */
    fun isAPatchDetected(): Boolean {
        return try {
            File("/data/adb/ap").exists() || File("/data/adb/apd").exists()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if Magisk is present.
     */
    fun isMagiskDetected(): Boolean {
        return try {
            File("/data/adb/magisk").exists() || File("/sbin/.magisk").exists()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if any root manager app is installed.
     */
    fun hasRootManagerApp(context: Context): Boolean {
        val pm = context.packageManager ?: return false
        for (pkg in ROOT_MANAGER_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Throwable) {
                // Not installed, continue checking
            }
        }
        return false
    }

    /**
     * Checks if the `su` binary or a root environment is present.
     * Crash-proof against security exceptions and SUSFS mount masks.
     */
    fun isRootAvailable(context: Context? = null): Boolean {
        return try {
            // 1. Direct binary presence
            for (path in SU_BINARY_LOCATIONS) {
                if (File(path).exists()) return true
            }

            // 2. PATH inspection
            val pathEnv = System.getenv("PATH")
            if (!pathEnv.isNullOrBlank()) {
                for (dir in pathEnv.split(":")) {
                    if (dir.isNotBlank() && File(dir, "su").exists()) return true
                }
            }

            // 3. KernelSU / APatch / SUSFS nodes
            if (isKernelSuDetected() || isAPatchDetected() || isSusfsDetected() || isMagiskDetected()) {
                return true
            }

            // 4. Installed Root Manager packages (KernelSU, Magisk, APatch)
            if (context != null && hasRootManagerApp(context)) {
                return true
            }

            // 5. Fallback: query shell for su command availability
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v su || which su"))
            val output = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            proc.waitFor()
            output.isNotBlank()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Detects the specific Root flavor (Magisk, KernelSU, KernelSU+SUSFS, APatch, RootFS).
     */
    fun detectRootFlavor(context: Context? = null): RootFlavor {
        if (!isRootAvailable(context)) return RootFlavor.NONE

        val susfs = isSusfsDetected()

        // 1. Try querying `su -v` or `su -V` for definitive identity
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-v"))
            val out = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText().trim() }
            proc.waitFor()
            if (out.contains("MAGISK", ignoreCase = true) || out.contains("topjohnwu", ignoreCase = true)) {
                return RootFlavor.MAGISK
            }
            if (out.contains("KernelSU", ignoreCase = true) || out.contains("ksu", ignoreCase = true)) {
                return if (susfs) RootFlavor.KERNELSU_SUSFS else RootFlavor.KERNELSU
            }
            if (out.contains("APatch", ignoreCase = true) || out.contains("KernelPatch", ignoreCase = true)) {
                return RootFlavor.APATCH
            }
        } catch (_: Throwable) {
            // Fall through to filesystem / manager heuristics
        }

        // 2. Kernel / device node heuristics
        if (isKernelSuDetected()) {
            return if (susfs) RootFlavor.KERNELSU_SUSFS else RootFlavor.KERNELSU
        }
        if (isAPatchDetected()) {
            return RootFlavor.APATCH
        }
        if (isMagiskDetected()) {
            return RootFlavor.MAGISK
        }

        // 3. Package manager heuristics
        if (context != null) {
            val pm = context.packageManager
            if (pm != null) {
                if (isPackageInstalled(pm, "me.weishu.kernelsu") || isPackageInstalled(pm, "io.github.a13e300.ksu") || isPackageInstalled(pm, "com.nagisa.ksu")) {
                    return if (susfs) RootFlavor.KERNELSU_SUSFS else RootFlavor.KERNELSU
                }
                if (isPackageInstalled(pm, "org.bndeff.apd") || isPackageInstalled(pm, "me.bndeff.apd")) {
                    return RootFlavor.APATCH
                }
                if (isPackageInstalled(pm, "com.topjohnwu.magisk")) {
                    return RootFlavor.MAGISK
                }
            }
        }

        // 4. SUSFS fallback check
        if (susfs) {
            return RootFlavor.KERNELSU_SUSFS
        }

        return RootFlavor.ROOTFS
    }

    private fun isPackageInstalled(pm: android.content.pm.PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if superuser privileges (UID 0) can be acquired by executing `id`.
     * Grants 8 seconds for the user to approve prompt dialog in Magisk / KernelSU / APatch.
     */
    suspend fun isRootGranted(): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        val result = runRootCommand("id", timeoutMs = 8000L)
        result.isSuccess && (result.getOrNull()?.contains("uid=0") == true)
    }

    /**
     * Runs a single command with root privileges.
     */
    suspend fun runRootCommand(command: String, timeoutMs: Long = 10000L): Result<String> {
        return runRootCommands(listOf(command), timeoutMs)
    }

    /**
     * Executes a batch of shell commands via an interactive `su` process.
     * Uses asynchronous stream reading to avoid OS pipe deadlock and reliably terminates.
     */
    suspend fun runRootCommands(commands: List<String>, timeoutMs: Long = 15000L): Result<String> = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext Result.failure(IllegalStateException("Root (su) binary or manager not detected on this device."))
        }

        var process: Process? = null
        try {
            val execution = withTimeoutOrNull(timeoutMs) {
                val proc = Runtime.getRuntime().exec("su")
                process = proc

                val outputDeferred = async(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
                }
                val errorDeferred = async(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText() }
                }

                proc.outputStream.bufferedWriter().use { writer ->
                    for (cmd in commands) {
                        writer.write(cmd)
                        writer.newLine()
                    }
                    writer.write("exit 0")
                    writer.newLine()
                    writer.flush()
                }

                val exitCode = proc.waitFor()
                val output = outputDeferred.await().trim()
                val error = errorDeferred.await().trim()
                Pair(exitCode, Pair(output, error))
            }

            if (execution == null) {
                try { process?.destroyForcibly() } catch (_: Throwable) {}
                return@withContext Result.failure(RuntimeException("Root execution timed out after ${timeoutMs}ms. Please ensure superuser permission is granted in Magisk/KernelSU/APatch."))
            }

            val (exitCode, streams) = execution
            val (output, error) = streams

            if (exitCode == 0) {
                Result.success(output.ifBlank { "Root commands executed successfully." })
            } else {
                val errMsg = error.ifBlank { output }.ifBlank { "Root shell exited with code $exitCode" }
                Result.failure(RuntimeException("Root command failed ($exitCode): $errMsg"))
            }
        } catch (e: Throwable) {
            try { process?.destroyForcibly() } catch (_: Throwable) {}
            Result.failure(e)
        }
    }

    /**
     * Automatically grants all Aura permissions via Root:
     * - Access restricted settings (Android 13+)
     * - System Alert Window / Overlay
     * - Usage Access / Usage Stats (GET_USAGE_STATS appop)
     * - Accessibility Service (preserving existing accessibility services)
     * - Notification Listener Access (preserving existing notification listeners)
     * - Battery Optimization whitelist & active standby bucket
     * - OEM Autostart & Background run permissions
     */
    suspend fun autoGrantAllPermissions(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val accessibilityClass = "$pkg/${AuraOverlayService::class.java.name}"
        val notificationClass = "$pkg/${AuraNotificationListenerService::class.java.name}"
        val mergedAccessibilityServices = ShizukuManager.getMergedAccessibilityServices(context, accessibilityClass)
        val mergedNotificationListeners = ShizukuManager.getMergedNotificationListeners(context, notificationClass)

        val commands = listOf(
            // 1. Sideloaded / Restricted settings bypass (Android 13+)
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true",
            // 2. Overlay / System Alert Window
            "appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null || true",
            // 3. Usage stats
            "appops set $pkg GET_USAGE_STATS allow 2>/dev/null || true",
            // 4. Accessibility appop
            "appops set $pkg BIND_ACCESSIBILITY_SERVICE allow 2>/dev/null || true",
            // 5. Notification posting appop
            "appops set $pkg POST_NOTIFICATION allow 2>/dev/null || true",
            // 6. OEM Background execution & autostart
            "appops set $pkg AUTO_START allow 2>/dev/null || true",
            "appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null || true",
            "appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true",
            // 7. Active standby bucket & battery optimization whitelist
            "am set-standby-bucket $pkg active 2>/dev/null || true",
            "dumpsys deviceidle whitelist +$pkg 2>/dev/null || true",
            "cmd deviceidle whitelist +$pkg 2>/dev/null || true",
            "appops set $pkg MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true",
            // 8. Enable Accessibility Service
            "settings put secure accessibility_enabled 1 2>/dev/null || true",
            "settings put --user 0 secure accessibility_enabled 1 2>/dev/null || true",
            "settings put --user current secure accessibility_enabled 1 2>/dev/null || true",
            "settings put secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "settings put --user 0 secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "settings put --user current secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "cmd accessibility enable-service $accessibilityClass 2>/dev/null || true",
            "cmd accessibility enable-service --user 0 $accessibilityClass 2>/dev/null || true",
            "cmd accessibility enable-service --user current $accessibilityClass 2>/dev/null || true",
            "cmd accessibility start-service $accessibilityClass 2>/dev/null || true",
            // 9. Enable Notification Listener Service
            "cmd notification allow_listener $notificationClass 2>/dev/null || true",
            "cmd notification allow_listener --user 0 $notificationClass 2>/dev/null || true",
            "cmd notification allow_listener --user current $notificationClass 2>/dev/null || true",
            "settings put secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            "settings put --user 0 secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            "settings put --user current secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            // 10. Runtime permissions declared in manifest
            "pm grant $pkg android.permission.POST_NOTIFICATIONS 2>/dev/null || true",
            "pm grant $pkg android.permission.BLUETOOTH_CONNECT 2>/dev/null || true",
            "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true",
            "pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true",
            "settings put global heads_up_notifications_enabled 0 2>/dev/null || true",
            "exit 0"
        )
        val result = runRootCommands(commands)
        withContext(Dispatchers.Main) {
            SystemServiceRecovery.requestRecovery(context)
            runCatching {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(context, AuraNotificationListenerService::class.java)
                )
            }
        }
        result
    }

    /**
     * Grants OEM Autostart and disables background kill / app standby restrictions via Root.
     */
    suspend fun grantOemAutostartAndKillProtection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val commands = listOf(
            "appops set $pkg AUTO_START allow 2>/dev/null || true",
            "appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null || true",
            "appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true",
            "am set-standby-bucket $pkg active 2>/dev/null || true",
            "dumpsys deviceidle whitelist +$pkg 2>/dev/null || true",
            "cmd deviceidle whitelist +$pkg 2>/dev/null || true",
            "exit 0"
        )
        runRootCommands(commands)
    }

    /**
     * Grants Battery Optimization exemption via Root.
     */
    suspend fun grantBatteryOptimization(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val commands = listOf(
            "dumpsys deviceidle whitelist +$pkg 2>/dev/null || true",
            "cmd deviceidle whitelist +$pkg 2>/dev/null || true",
            "am set-standby-bucket $pkg active 2>/dev/null || true",
            "exit 0"
        )
        runRootCommands(commands)
    }

    /**
     * Grants Notification Listener permission via Root without overwriting other active listeners.
     */
    suspend fun grantNotificationListener(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val notificationClass = "$pkg/${AuraNotificationListenerService::class.java.name}"
        val mergedNotificationListeners = ShizukuManager.getMergedNotificationListeners(context, notificationClass)
        val commands = listOf(
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true",
            "cmd notification allow_listener $notificationClass 2>/dev/null || true",
            "cmd notification allow_listener --user 0 $notificationClass 2>/dev/null || true",
            "cmd notification allow_listener --user current $notificationClass 2>/dev/null || true",
            "settings put secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            "settings put --user 0 secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            "settings put --user current secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            "exit 0"
        )
        val res = runRootCommands(commands)
        withContext(Dispatchers.Main) {
            SystemServiceRecovery.requestRecovery(context)
            runCatching {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(context, AuraNotificationListenerService::class.java)
                )
            }
        }
        res
    }

    /**
     * Grants Accessibility service permission via Root without overwriting other active accessibility services.
     */
    suspend fun grantAccessibility(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val accessibilityClass = "$pkg/${AuraOverlayService::class.java.name}"
        val mergedAccessibilityServices = ShizukuManager.getMergedAccessibilityServices(context, accessibilityClass)
        val commands = listOf(
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true",
            "appops set $pkg BIND_ACCESSIBILITY_SERVICE allow 2>/dev/null || true",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null || true",
            "settings put secure accessibility_enabled 1 2>/dev/null || true",
            "settings put --user 0 secure accessibility_enabled 1 2>/dev/null || true",
            "settings put --user current secure accessibility_enabled 1 2>/dev/null || true",
            "settings put secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "settings put --user 0 secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "settings put --user current secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "cmd accessibility enable-service $accessibilityClass 2>/dev/null || true",
            "cmd accessibility enable-service --user 0 $accessibilityClass 2>/dev/null || true",
            "cmd accessibility enable-service --user current $accessibilityClass 2>/dev/null || true",
            "cmd accessibility start-service $accessibilityClass 2>/dev/null || true",
            "exit 0"
        )
        val res = runRootCommands(commands)
        withContext(Dispatchers.Main) {
            SystemServiceRecovery.requestRecovery(context)
        }
        res
    }

    /**
     * Formats diagnostic root status for developer diagnostics and log exports.
     */
    fun getDiagnosticStatus(context: Context? = null): String {
        val flavor = detectRootFlavor(context)
        val available = isRootAvailable(context)
        return when {
            !available -> "Not Detected"
            flavor != RootFlavor.NONE -> "Available (${flavor.displayName})"
            else -> "Available"
        }
    }
}

