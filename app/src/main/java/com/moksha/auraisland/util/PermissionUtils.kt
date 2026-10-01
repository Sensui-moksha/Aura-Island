/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.moksha.auraisland.service.AuraNotificationListenerService
import com.moksha.auraisland.service.AuraOverlayService

object PermissionUtils {

    /**
     * Checks if Aura's Accessibility Service is currently active and granted.
     * Priority:
     * 1. Active connection state in AuraOverlayService
     * 2. Android AccessibilityManager framework query
     * 3. Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES inspection
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (AuraOverlayService.isSystemConnected) return true

        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices?.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName } == true) {
                return true
            }
        } catch (_: Throwable) {}

        val enabledServicesSetting = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false

        val expectedComponentName = ComponentName(context, AuraOverlayService::class.java)
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val entry = colonSplitter.next().trim()
            if (entry.isBlank()) continue
            if (entry.startsWith("${context.packageName}/")) return true
            val enabledService = ComponentName.unflattenFromString(entry)
            if (enabledService != null && (enabledService == expectedComponentName || enabledService.packageName == context.packageName)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if Aura's Notification Listener Service is currently active and authorized.
     * Priority:
     * 1. Active connection state in AuraNotificationListenerService
     * 2. Android NotificationManagerCompat framework query
     * 3. Settings.Secure enabled_notification_listeners inspection
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        if (AuraNotificationListenerService.isSystemConnected) return true

        try {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) return true
        } catch (_: Throwable) {}

        val enabled = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull() ?: return false

        return enabled.split(":").any { entry ->
            val trimmed = entry.trim()
            if (trimmed.isBlank()) return@any false
            trimmed.startsWith("${context.packageName}/") ||
                ComponentName.unflattenFromString(trimmed)?.packageName == context.packageName
        }
    }

    /**
     * Checks if Aura is exempted from Android battery optimizations.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
}
