/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.moksha.auraisland.service.AuraNotificationListenerService
import com.moksha.auraisland.service.AuraOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {

    /**
     * Checks if the Shizuku app is installed on the device.
     * 100% crash proof against Throwable (including unit test stubs).
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Safely checks if the Shizuku service binder is currently alive and responsive.
     * Catches any Throwable (DeadObjectException, ExceptionInInitializerError, RemoteException).
     */
    fun isBinderAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Safely checks if Shizuku API permission has been granted to Aura.
     */
    fun hasPermission(): Boolean {
        if (!isBinderAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Safely requests Shizuku permission. Does not crash if Shizuku service is dead.
     */
    fun requestPermission(requestCode: Int = 1001) {
        if (!isBinderAvailable()) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (t: Throwable) {
            android.util.Log.e("ShizukuManager", "Failed to request Shizuku permission", t)
        }
    }

    internal fun runShizukuCommands(commands: List<String>): Result<String> {
        if (!hasPermission()) {
            return Result.failure(IllegalStateException("Shizuku permission not granted or service binder offline."))
        }
        return runCatching {
            val fullScript = commands.joinToString("; ")
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", fullScript), null, null) as Process

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0 || output.isNotBlank() || error.isBlank()) {
                output.ifBlank { "Permissions auto-granted successfully via Shizuku." }
            } else {
                throw RuntimeException("Shizuku command error (exit $exitCode): $error $output")
            }
        }
    }

    suspend fun runCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        runShizukuCommands(listOf(command))
    }

    suspend fun runCommands(commands: List<String>): Result<String> = withContext(Dispatchers.IO) {
        runShizukuCommands(commands)
    }

    internal fun mergeColonSeparated(currentList: String, newEntry: String): String {
        val list = currentList.split(':')
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains("smartisland", ignoreCase = true) }
            .toMutableList()
        if (!list.contains(newEntry)) {
            list.add(newEntry)
        }
        return list.joinToString(":")
    }

    internal fun getMergedAccessibilityServices(context: Context, serviceComponent: String): String {
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        return mergeColonSeparated(current, serviceComponent)
    }

    internal fun getMergedNotificationListeners(context: Context, listenerComponent: String): String {
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        return mergeColonSeparated(current, listenerComponent)
    }

    /**
     * Executes ADB shell commands via Shizuku process on IO dispatcher to auto-grant:
     * - Allow restricted settings (Android 13+)
     * - Usage Access / Usage Stats (GET_USAGE_STATS)
     * - Accessibility Service & System Alert Window (preserving existing accessibility services)
     * - Notification Listener Access (preserving existing notification listeners)
     * - Battery Optimization whitelist
     * - All files storage access for persistent backup
     */
    suspend fun autoGrantAllPermissions(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val accessibilityClass = "$pkg/${AuraOverlayService::class.java.name}"
        val notificationClass = "$pkg/${AuraNotificationListenerService::class.java.name}"
        val mergedAccessibilityServices = getMergedAccessibilityServices(context, accessibilityClass)
        val mergedNotificationListeners = getMergedNotificationListeners(context, notificationClass)

        val commands = listOf(
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true",
            "appops set $pkg GET_USAGE_STATS allow 2>/dev/null || true",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null || true",
            "appops set $pkg BIND_ACCESSIBILITY_SERVICE allow 2>/dev/null || true",
            "appops set $pkg POST_NOTIFICATION allow 2>/dev/null || true",
            "appops set $pkg AUTO_START allow 2>/dev/null || true",
            "appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null || true",
            "appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true",
            "appops set $pkg MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true",
            "settings put secure accessibility_enabled 1 2>/dev/null || true",
            "settings put secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "cmd accessibility enable-service $accessibilityClass 2>/dev/null || true",
            "cmd accessibility start-service $accessibilityClass 2>/dev/null || true",
            "cmd notification allow_listener $notificationClass 2>/dev/null || true",
            "settings put secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true",
            "am set-standby-bucket $pkg active 2>/dev/null || true",
            "dumpsys deviceidle whitelist +$pkg 2>/dev/null || true",
            "cmd deviceidle whitelist +$pkg 2>/dev/null || true",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS 2>/dev/null || true",
            "pm grant $pkg android.permission.BLUETOOTH_CONNECT 2>/dev/null || true",
            "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true",
            "pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true",
            "settings put global heads_up_notifications_enabled 0 2>/dev/null || true"
        )
        val res = runShizukuCommands(commands)
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
     * Grants OEM Autostart and disables background kill / app standby restrictions via Shizuku.
     */
    suspend fun grantOemAutostartAndKillProtection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val commands = listOf(
            "appops set $pkg AUTO_START allow 2>/dev/null || true",
            "appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null || true",
            "appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true",
            "am set-standby-bucket $pkg active 2>/dev/null || true",
            "dumpsys deviceidle whitelist +$pkg 2>/dev/null || true",
            "cmd deviceidle whitelist +$pkg 2>/dev/null || true"
        )
        runShizukuCommands(commands)
    }

    /**
     * Grants Battery Optimization exemption via Shizuku.
     */
    suspend fun grantBatteryOptimization(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val commands = listOf(
            "dumpsys deviceidle whitelist +$pkg 2>/dev/null || true",
            "cmd deviceidle whitelist +$pkg 2>/dev/null || true",
            "am set-standby-bucket $pkg active 2>/dev/null || true"
        )
        runShizukuCommands(commands)
    }

    /**
     * Grants Notification Listener permission via Shizuku without overwriting other active listeners.
     */
    suspend fun grantNotificationListener(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val notificationClass = "$pkg/${AuraNotificationListenerService::class.java.name}"
        val mergedNotificationListeners = getMergedNotificationListeners(context, notificationClass)
        val commands = listOf(
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true",
            "cmd notification allow_listener $notificationClass 2>/dev/null || true",
            "settings put secure enabled_notification_listeners $mergedNotificationListeners 2>/dev/null || true"
        )
        val res = runShizukuCommands(commands)
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
     * Grants Accessibility service permission via Shizuku without overwriting other active accessibility services.
     */
    suspend fun grantAccessibility(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val accessibilityClass = "$pkg/${AuraOverlayService::class.java.name}"
        val mergedAccessibilityServices = getMergedAccessibilityServices(context, accessibilityClass)
        val commands = listOf(
            "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true",
            "appops set $pkg BIND_ACCESSIBILITY_SERVICE allow 2>/dev/null || true",
            "appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null || true",
            "settings put secure accessibility_enabled 1 2>/dev/null || true",
            "settings put secure enabled_accessibility_services $mergedAccessibilityServices 2>/dev/null || true",
            "cmd accessibility enable-service $accessibilityClass 2>/dev/null || true",
            "cmd accessibility start-service $accessibilityClass 2>/dev/null || true"
        )
        val res = runShizukuCommands(commands)
        withContext(Dispatchers.Main) {
            SystemServiceRecovery.requestRecovery(context)
        }
        res
    }
}

