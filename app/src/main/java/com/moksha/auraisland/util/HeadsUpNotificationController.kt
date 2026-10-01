/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages suppression of native Android heads-up (floating pop-up banner) notifications.
 *
 * When heads-up is suppressed (heads_up_notifications_enabled = 0):
 * - Native Android floating banners DO NOT drop down or cover the screen.
 * - Notifications STILL arrive intact in the system status bar and notification drawer/panel.
 * - Aura's Dynamic Island expands exclusively to display incoming notifications same as iPhone.
 *
 * Supported mechanisms:
 * 1. WRITE_SECURE_SETTINGS (Settings.Global)
 * 2. Shizuku privileged shell
 * 3. Root (su) shell
 */
object HeadsUpNotificationController {

    private const val TAG = "HeadsUpController"
    private const val SETTING_KEY = "heads_up_notifications_enabled"

    const val ADB_COMMAND_SETTING = "adb shell settings put global heads_up_notifications_enabled 0"
    const val ADB_COMMAND_PERMISSION = "adb shell pm grant com.moksha.auraisland android.permission.WRITE_SECURE_SETTINGS"

    /**
     * Returns true if native heads-up pop-up banners are currently suppressed (disabled).
     */
    fun isHeadsUpSuppressed(context: Context): Boolean {
        return try {
            val enabled = Settings.Global.getInt(context.contentResolver, SETTING_KEY, 1)
            enabled == 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $SETTING_KEY", e)
            false
        }
    }

    /**
     * Checks if Aura has permission to write system secure settings directly.
     */
    fun hasWriteSecureSettings(context: Context): Boolean {
        return try {
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if Aura can toggle the heads-up setting via any available method.
     */
    fun canToggleHeadsUp(context: Context): Boolean {
        return hasWriteSecureSettings(context) ||
            ShizukuManager.hasPermission() ||
            RootManager.isRootAvailable(context)
    }

    /**
     * Sets whether native heads-up notifications are enabled or suppressed.
     * @param suppress If true, disables native popups (setting = 0). If false, enables them (setting = 1).
     */
    suspend fun applySuppression(context: Context, suppress: Boolean): Boolean = withContext(Dispatchers.IO) {
        val targetVal = if (suppress) 0 else 1

        // Method 1: WRITE_SECURE_SETTINGS permission
        try {
            val ok = Settings.Global.putInt(context.contentResolver, SETTING_KEY, targetVal)
            if (ok) {
                Log.d(TAG, "Successfully updated $SETTING_KEY to $targetVal via Settings.Global")
                return@withContext true
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "No WRITE_SECURE_SETTINGS permission, falling back to privileged shell")
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting $SETTING_KEY via Settings.Global", e)
        }

        // Method 2: Shizuku privileged shell
        if (ShizukuManager.hasPermission()) {
            val res = ShizukuManager.runCommand("settings put global $SETTING_KEY $targetVal")
            if (res.isSuccess) {
                Log.d(TAG, "Successfully updated $SETTING_KEY to $targetVal via Shizuku")
                return@withContext true
            }
        }

        // Method 3: Root shell
        if (RootManager.isRootAvailable(context)) {
            val res = RootManager.runRootCommands(listOf("settings put global $SETTING_KEY $targetVal"))
            if (res.isSuccess) {
                Log.d(TAG, "Successfully updated $SETTING_KEY to $targetVal via Root")
                return@withContext true
            }
        }

        Log.w(TAG, "Could not update $SETTING_KEY: missing WRITE_SECURE_SETTINGS, Shizuku, and Root")
        false
    }
}
