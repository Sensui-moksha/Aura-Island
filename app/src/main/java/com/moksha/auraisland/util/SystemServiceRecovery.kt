/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 */

package com.moksha.auraisland.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import com.moksha.auraisland.service.AuraNotificationListenerService
import com.moksha.auraisland.service.AuraOverlayService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reconnects system-managed services after OEM task killers or Android force-stops them.
 *
 * Strategy:
 *  1. If WRITE_SECURE_SETTINGS is held (via ADB / Shizuku / Root), silently re-write
 *     enabled_accessibility_services back into secure settings — the most reliable way to
 *     survive MIUI, OneUI, ColorOS, FuntouchOS, and EMUI aggressive kill policies.
 *  2. Request NotificationListenerService rebind via the official API.
 *  3. A periodic watchdog (started once) continuously re-asserts the secure setting every
 *     30 seconds while the process is alive, ensuring any OEM that clears the setting
 *     mid-session gets immediately corrected.
 *
 * What we intentionally DO NOT do:
 *  - Toggle PackageManager component state: some OEMs interpret this as a signal to revoke
 *    the accessibility permission entirely, making the problem worse.
 */
object SystemServiceRecovery {

    private val watchdogStarted = AtomicBoolean(false)
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call from AutostartReceiver, onUnbind, or onTaskRemoved to attempt immediate recovery. */
    fun requestRecovery(context: Context) {
        runCatchingLogged(TAG, "System-service recovery failed") {
            val appContext = context.applicationContext
            requestNotificationListenerRebind(appContext)
            tryRestoreViaSecureSettings(appContext)
            ensureWatchdogRunning(appContext)
        }
    }

    /**
     * Start a long-lived background watchdog that re-asserts ENABLED_ACCESSIBILITY_SERVICES
     * every [WATCHDOG_INTERVAL_MS] ms. Silently defeats OEMs that clear the setting after
     * memory trims, power-save mode changes, or app-limit enforcement (MIUI, OneUI, ColorOS…).
     *
     * Self-stops after 5 consecutive failures (WRITE_SECURE_SETTINGS not granted).
     */
    fun ensureWatchdogRunning(context: Context) {
        if (!watchdogStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        watchdogScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val restored = runCatching { tryRestoreViaSecureSettings(appContext) }
                    .getOrDefault(false)
                if (!restored) {
                    if (++consecutiveFailures >= 5) {
                        Log.d(TAG, "Watchdog: WRITE_SECURE_SETTINGS not available, stopping.")
                        watchdogStarted.set(false)
                        break
                    }
                } else {
                    consecutiveFailures = 0
                }
            }
        }
    }

    /**
     * Silently re-writes enabled_accessibility_services back into Secure Settings.
     * Also re-asserts the global accessibility master switch in case an OEM disabled it.
     * Requires WRITE_SECURE_SETTINGS (granted via ADB / Shizuku / Root).
     * Returns true when the write succeeded; false when permission is not held.
     */
    private fun tryRestoreViaSecureSettings(context: Context): Boolean {
        return runCatching {
            val component = ComponentName(context, AuraOverlayService::class.java)
            val flatComponent = component.flattenToString()
            val resolver = context.contentResolver

            val current = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val alreadyPresent = current.split(':').filter { it.isNotBlank() }.any {
                ComponentName.unflattenFromString(it) == component
            }

            val masterSwitchOn = Settings.Secure.getInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            // Nothing to change — already correct
            if (alreadyPresent && masterSwitchOn) return@runCatching true

            val merged = when {
                alreadyPresent -> current
                current.isBlank() -> flatComponent
                else -> "$current:$flatComponent"
            }

            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                merged
            )
            Log.i(TAG, "Accessibility restored via WRITE_SECURE_SETTINGS " +
                "(wasPresent=$alreadyPresent, masterWasOn=$masterSwitchOn)")
            true
        }.getOrElse { e ->
            Log.d(TAG, "WRITE_SECURE_SETTINGS not available: ${e.message}")
            false
        }
    }

    private fun requestNotificationListenerRebind(context: Context) {
        if (!isNotificationListenerPermissionGranted(context) ||
            AuraNotificationListenerService.isSystemConnected
        ) return
        runCatchingLogged(TAG, "Notification-listener rebind failed") {
            NotificationListenerService.requestRebind(
                ComponentName(context, AuraNotificationListenerService::class.java)
            )
        }
    }

    fun isAccessibilityPermissionGranted(context: Context): Boolean {
        val expected = ComponentName(context, AuraOverlayService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private fun isNotificationListenerPermissionGranted(context: Context): Boolean {
        val expected = ComponentName(context, AuraNotificationListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private const val TAG = "SystemServiceRecovery"
    private const val WATCHDOG_INTERVAL_MS = 30_000L
}
