/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.auraDataStore by preferencesDataStore(
    name = "aura_settings",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e("AuraSettings", "Settings were corrupted; restoring safe defaults")
        emptyPreferences()
    }
)

class AuraSettingsRepository(private val context: Context) {
    private object Keys {
        val Enabled = booleanPreferencesKey("enabled")
        val Width = floatPreferencesKey("width")
        val Height = floatPreferencesKey("height")
        val XOffset = floatPreferencesKey("x_offset")
        val YOffset = floatPreferencesKey("y_offset")
        val CornerRadius = floatPreferencesKey("corner_radius")
        val Opacity = floatPreferencesKey("opacity")
        val PillColor = longPreferencesKey("pill_color")
        val BatteryColor = longPreferencesKey("battery_color")
        val NotificationDotColor = longPreferencesKey("notification_dot_color")
        val MusicVisualizerColor = longPreferencesKey("music_visualizer_color")
        val HotspotColor = longPreferencesKey("hotspot_color")
        val CallColor = longPreferencesKey("call_color")
        val LiveActivityColor = longPreferencesKey("live_activity_color")
        val TransferColor = longPreferencesKey("transfer_color")
        val NavigationColor = longPreferencesKey("navigation_color")
        val BluetoothColor = longPreferencesKey("bluetooth_color")
        val FlashlightColor = longPreferencesKey("flashlight_color")
        val ScreenRecordingColor = longPreferencesKey("screen_recording_color")
        val TimerColor = longPreferencesKey("timer_color")
        val StopwatchColor = longPreferencesKey("stopwatch_color")
        val EnableAppShortcuts = booleanPreferencesKey("enable_app_shortcuts")
        val ShortcutPackages = stringSetPreferencesKey("shortcut_packages")
        val ShowRecentApps = booleanPreferencesKey("show_recent_apps")
        val WelcomeDialogShown = booleanPreferencesKey("welcome_dialog_shown")
        val ShowOnLockScreen = booleanPreferencesKey("show_on_lock_screen")
        val LockScreenPrivacy = stringPreferencesKey("lock_screen_privacy")
        val ShowNotificationActions = booleanPreferencesKey("show_notification_actions")
        val HideFromNotificationShade = booleanPreferencesKey("hide_from_notification_shade")
        val SuppressSystemHeadsUp = booleanPreferencesKey("suppress_system_heads_up")
        val LiveActivitiesEnabled = booleanPreferencesKey("live_activities_enabled")
        val NavigationEnabled = booleanPreferencesKey("navigation_enabled")
        val DisabledNotificationPackages = stringSetPreferencesKey("disabled_notification_packages")
        val DisabledSoundPackages = stringSetPreferencesKey("disabled_sound_packages")
        val HideWhenIdle = booleanPreferencesKey("hide_when_idle")
        val AutoHidePill = booleanPreferencesKey("auto_hide_pill")
        val AutoHideTimeoutSeconds = intPreferencesKey("auto_hide_timeout_seconds")
        val ShowInLandscape = booleanPreferencesKey("show_in_landscape")
        val AutoExpandOnNotification = booleanPreferencesKey("auto_expand_on_notification")
        val EnableShadow = booleanPreferencesKey("enable_shadow")
        val ShadowElevation = floatPreferencesKey("shadow_elevation")
        val EnableMusicArtworkBackground = booleanPreferencesKey("enable_music_artwork_background")
        val EnableNotificationBackdrop = booleanPreferencesKey("enable_notification_backdrop")
        val DeviceType = stringPreferencesKey("device_type")
        val AllowNetworkChecks = booleanPreferencesKey("allow_network_checks")
        val EnableNotificationHistory = booleanPreferencesKey("enable_notification_history")
        val NotificationHistoryRetentionHours = intPreferencesKey("notification_history_retention_hours")
        val ShowBluetoothBattery = booleanPreferencesKey("show_bluetooth_battery")
        val EnableBatteryMode = booleanPreferencesKey("enable_battery_mode")
        val EnableNotificationCooldown = booleanPreferencesKey("enable_notification_cooldown")
        val NotificationCooldownDurationMinutes = intPreferencesKey("notification_cooldown_duration_minutes")
        val NotificationCooldownThreshold = intPreferencesKey("notification_cooldown_threshold")
        val NotificationCooldownExcludedPackages = stringSetPreferencesKey("notification_cooldown_excluded_packages")
        val DeveloperModeEnabled = booleanPreferencesKey("developer_mode_enabled")
        val RecordLogs = booleanPreferencesKey("record_logs")
        val EnableNotchMode = booleanPreferencesKey("enable_notch_mode")
        val EnableSwipeActions = booleanPreferencesKey("enable_swipe_actions")
        val SwipeUpAction = stringPreferencesKey("swipe_up_action")
        val SwipeHoldUpAction = stringPreferencesKey("swipe_hold_up_action")
        val SwipeDownAction = stringPreferencesKey("swipe_down_action")
        val SwipeDownCollapsedAction = stringPreferencesKey("swipe_down_collapsed_action")
        val SwipeHorizontalCollapsedAction = stringPreferencesKey("swipe_horizontal_collapsed_action")
        val EnablePillSwipeActions = booleanPreferencesKey("enable_pill_swipe_actions")
        val PillSwipeUpAction = stringPreferencesKey("pill_swipe_up_action")
        val PillSwipeDownAction = stringPreferencesKey("pill_swipe_down_action")
        val PillSwipeLeftAction = stringPreferencesKey("pill_swipe_left_action")
        val PillSwipeRightAction = stringPreferencesKey("pill_swipe_right_action")
        val CirclePosition = stringPreferencesKey("circle_position")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val UseDynamicColor = booleanPreferencesKey("use_dynamic_color")
        val RootModeEnabled = booleanPreferencesKey("root_mode_enabled")
        val YieldToSystemIsland = booleanPreferencesKey("yield_to_system_island")
        val YieldMusicToSystemIsland = booleanPreferencesKey("yield_music_to_system_island")
        val OemIslandHandlingMode = stringPreferencesKey("oem_island_handling_mode")
        val EnableCutoutRingMode = booleanPreferencesKey("enable_cutout_ring_mode")
        val CameraCircleSize = floatPreferencesKey("camera_circle_size")
        val CameraCircleOffsetX = floatPreferencesKey("camera_circle_offset_x")
        val CameraCircleOffsetY = floatPreferencesKey("camera_circle_offset_y")
        val EnableBatteryRing = booleanPreferencesKey("enable_battery_ring")
        val ShowBatteryIndicator = booleanPreferencesKey("show_battery_indicator")
        val BatteryRingPulse = booleanPreferencesKey("battery_ring_pulse")
        val BatteryRingOnLockScreen = booleanPreferencesKey("battery_ring_on_lock_screen")
        val BatteryRingShowCenterIcon = booleanPreferencesKey("battery_ring_show_center_icon")
        val BatteryRingStrokeWidth = floatPreferencesKey("battery_ring_stroke_width")
        val BatteryRingGap = floatPreferencesKey("battery_ring_gap")
        val EnableOutline = booleanPreferencesKey("enable_outline")
        val OutlineColor = longPreferencesKey("outline_color")
        val OutlineWidth = floatPreferencesKey("outline_width")
        val OutlineOnlyInCustomization = booleanPreferencesKey("outline_only_in_customization")
    }

    val settings: Flow<AuraSettings> = context.auraDataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.e(TAG, "Unable to read settings; using safe defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            val defaults = AuraSettings.Default
            AuraSettings(
                enabled = prefs[Keys.Enabled] ?: defaults.enabled,
                width = validDimension(
                    prefs[Keys.Width],
                    defaults.width,
                    AuraSettings.MIN_WIDTH,
                    AuraSettings.MAX_WIDTH
                ),
                height = validDimension(
                    prefs[Keys.Height],
                    defaults.height,
                    AuraSettings.MIN_HEIGHT,
                    AuraSettings.MAX_HEIGHT
                ),
                xOffset = validDimension(
                    prefs[Keys.XOffset],
                    defaults.xOffset,
                    AuraSettings.MIN_X_OFFSET,
                    AuraSettings.MAX_X_OFFSET
                ),
                yOffset = validDimension(
                    prefs[Keys.YOffset],
                    defaults.yOffset,
                    AuraSettings.MIN_Y_OFFSET,
                    AuraSettings.MAX_Y_OFFSET
                ),
                cornerRadius = validDimension(
                    prefs[Keys.CornerRadius],
                    defaults.cornerRadius,
                    AuraSettings.MIN_CORNER_RADIUS,
                    AuraSettings.MAX_CORNER_RADIUS
                ),
                opacity = validDimension(
                    prefs[Keys.Opacity],
                    defaults.opacity,
                    AuraSettings.MIN_OPACITY,
                    AuraSettings.MAX_OPACITY
                ),
                pillColor = validColor(prefs[Keys.PillColor], defaults.pillColor),
                batteryColor = validColor(prefs[Keys.BatteryColor], defaults.batteryColor),
                notificationDotColor = validColor(
                    prefs[Keys.NotificationDotColor],
                    defaults.notificationDotColor
                ),
                musicVisualizerColor = validColor(
                    prefs[Keys.MusicVisualizerColor],
                    defaults.musicVisualizerColor
                ),
                hotspotColor = validColor(prefs[Keys.HotspotColor], defaults.hotspotColor),
                callColor = validColor(prefs[Keys.CallColor], defaults.callColor),
                liveActivityColor = validColor(prefs[Keys.LiveActivityColor], defaults.liveActivityColor),
                transferColor = validColor(prefs[Keys.TransferColor], defaults.transferColor),
                navigationColor = validColor(prefs[Keys.NavigationColor], defaults.navigationColor),
                bluetoothColor = validColor(prefs[Keys.BluetoothColor], defaults.bluetoothColor),
                flashlightColor = validColor(prefs[Keys.FlashlightColor], defaults.flashlightColor),
                screenRecordingColor = validColor(prefs[Keys.ScreenRecordingColor], defaults.screenRecordingColor),
                timerColor = validColor(prefs[Keys.TimerColor], defaults.timerColor),
                stopwatchColor = validColor(prefs[Keys.StopwatchColor], defaults.stopwatchColor),
                enableAppShortcuts = prefs[Keys.EnableAppShortcuts] ?: defaults.enableAppShortcuts,
                shortcutPackages = prefs[Keys.ShortcutPackages]
                    ?.asSequence()
                    ?.filter { it.isNotBlank() && it.length <= MAX_PACKAGE_NAME_LENGTH }
                    ?.take(MAX_SHORTCUTS)
                    ?.toSet()
                    ?: defaults.shortcutPackages,
                showRecentApps = prefs[Keys.ShowRecentApps] ?: defaults.showRecentApps,
                welcomeDialogShown = prefs[Keys.WelcomeDialogShown] ?: defaults.welcomeDialogShown,
                showOnLockScreen = prefs[Keys.ShowOnLockScreen] ?: defaults.showOnLockScreen,
                lockScreenPrivacy = prefs[Keys.LockScreenPrivacy]
                    ?.takeIf { it in VALID_LOCK_SCREEN_PRIVACY_VALUES }
                    ?: defaults.lockScreenPrivacy,
                showNotificationActions = prefs[Keys.ShowNotificationActions]
                    ?: defaults.showNotificationActions,
                hideFromNotificationShade = prefs[Keys.HideFromNotificationShade]
                    ?: defaults.hideFromNotificationShade,
                suppressSystemHeadsUp = prefs[Keys.SuppressSystemHeadsUp]
                    ?: defaults.suppressSystemHeadsUp,
                liveActivitiesEnabled = prefs[Keys.LiveActivitiesEnabled]
                    ?: defaults.liveActivitiesEnabled,
                navigationEnabled = prefs[Keys.NavigationEnabled]
                    ?: defaults.navigationEnabled,
                disabledNotificationPackages = prefs[Keys.DisabledNotificationPackages]
                    ?.asSequence()
                    ?.filter { it.isNotBlank() && it.length <= MAX_PACKAGE_NAME_LENGTH }
                    ?.toSet()
                    ?: defaults.disabledNotificationPackages,
                disabledSoundPackages = prefs[Keys.DisabledSoundPackages]
                    ?.asSequence()
                    ?.filter { pkg -> pkg.isNotBlank() && pkg.length <= MAX_PACKAGE_NAME_LENGTH }
                    ?.toSet()
                    ?: defaults.disabledSoundPackages,
                hideWhenIdle = prefs[Keys.HideWhenIdle] ?: defaults.hideWhenIdle,
                autoHidePill = prefs[Keys.AutoHidePill] ?: defaults.autoHidePill,
                autoHideTimeoutSeconds = prefs[Keys.AutoHideTimeoutSeconds] ?: defaults.autoHideTimeoutSeconds,
                showInLandscape = prefs[Keys.ShowInLandscape] ?: defaults.showInLandscape,
                autoExpandOnNotification = prefs[Keys.AutoExpandOnNotification] ?: defaults.autoExpandOnNotification,
                enableShadow = prefs[Keys.EnableShadow] ?: defaults.enableShadow,
                shadowElevation = validDimension(
                    prefs[Keys.ShadowElevation],
                    defaults.shadowElevation,
                    AuraSettings.MIN_SHADOW_ELEVATION,
                    AuraSettings.MAX_SHADOW_ELEVATION
                ),
                enableMusicArtworkBackground = prefs[Keys.EnableMusicArtworkBackground] ?: defaults.enableMusicArtworkBackground,
                enableNotificationBackdrop = prefs[Keys.EnableNotificationBackdrop] ?: defaults.enableNotificationBackdrop,
                deviceType = prefs[Keys.DeviceType] ?: defaults.deviceType,
                allowNetworkChecks = prefs[Keys.AllowNetworkChecks] ?: defaults.allowNetworkChecks,
                enableNotificationHistory = prefs[Keys.EnableNotificationHistory] ?: defaults.enableNotificationHistory,
                notificationHistoryRetentionHours = prefs[Keys.NotificationHistoryRetentionHours] ?: defaults.notificationHistoryRetentionHours,
                showBluetoothBattery = prefs[Keys.ShowBluetoothBattery] ?: defaults.showBluetoothBattery,
                enableBatteryMode = prefs[Keys.EnableBatteryMode] ?: defaults.enableBatteryMode,
                enableNotificationCooldown = prefs[Keys.EnableNotificationCooldown] ?: defaults.enableNotificationCooldown,
                notificationCooldownDurationMinutes = prefs[Keys.NotificationCooldownDurationMinutes] ?: defaults.notificationCooldownDurationMinutes,
                notificationCooldownThreshold = prefs[Keys.NotificationCooldownThreshold] ?: defaults.notificationCooldownThreshold,
                notificationCooldownExcludedPackages = prefs[Keys.NotificationCooldownExcludedPackages] ?: defaults.notificationCooldownExcludedPackages,
                developerModeEnabled = prefs[Keys.DeveloperModeEnabled] ?: defaults.developerModeEnabled,
                recordLogs = prefs[Keys.RecordLogs] ?: defaults.recordLogs,
                enableNotchMode = prefs[Keys.EnableNotchMode] ?: defaults.enableNotchMode,
                enableSwipeActions = prefs[Keys.EnableSwipeActions] ?: defaults.enableSwipeActions,
                swipeUpAction = prefs[Keys.SwipeUpAction] ?: defaults.swipeUpAction,
                swipeHoldUpAction = prefs[Keys.SwipeHoldUpAction] ?: defaults.swipeHoldUpAction,
                swipeDownAction = prefs[Keys.SwipeDownAction] ?: defaults.swipeDownAction,
                swipeDownCollapsedAction = prefs[Keys.SwipeDownCollapsedAction] ?: defaults.swipeDownCollapsedAction,
                swipeHorizontalCollapsedAction = prefs[Keys.SwipeHorizontalCollapsedAction] ?: defaults.swipeHorizontalCollapsedAction,
                enablePillSwipeActions = prefs[Keys.EnablePillSwipeActions] ?: defaults.enablePillSwipeActions,
                pillSwipeUpAction = prefs[Keys.PillSwipeUpAction] ?: defaults.pillSwipeUpAction,
                pillSwipeDownAction = prefs[Keys.PillSwipeDownAction] ?: defaults.pillSwipeDownAction,
                pillSwipeLeftAction = prefs[Keys.PillSwipeLeftAction] ?: defaults.pillSwipeLeftAction,
                pillSwipeRightAction = prefs[Keys.PillSwipeRightAction] ?: defaults.pillSwipeRightAction,
                circlePosition = prefs[Keys.CirclePosition]?.takeIf { it in VALID_CIRCLE_POSITIONS } ?: defaults.circlePosition,
                themeMode = prefs[Keys.ThemeMode]?.takeIf { it in VALID_THEME_MODES } ?: defaults.themeMode,
                useDynamicColor = prefs[Keys.UseDynamicColor] ?: defaults.useDynamicColor,
                rootModeEnabled = prefs[Keys.RootModeEnabled] ?: defaults.rootModeEnabled,
                yieldToSystemIsland = prefs[Keys.YieldToSystemIsland] ?: defaults.yieldToSystemIsland,
                yieldMusicToSystemIsland = prefs[Keys.YieldMusicToSystemIsland] ?: defaults.yieldMusicToSystemIsland,
                oemIslandHandlingMode = prefs[Keys.OemIslandHandlingMode] ?: defaults.oemIslandHandlingMode,
                enableCutoutRingMode = prefs[Keys.EnableCutoutRingMode] ?: defaults.enableCutoutRingMode,
                cameraCircleSize = validDimension(
                    prefs[Keys.CameraCircleSize],
                    defaults.cameraCircleSize,
                    AuraSettings.MIN_CAMERA_CIRCLE_SIZE,
                    AuraSettings.MAX_CAMERA_CIRCLE_SIZE
                ),
                cameraCircleOffsetX = validDimension(
                    prefs[Keys.CameraCircleOffsetX],
                    defaults.cameraCircleOffsetX,
                    AuraSettings.MIN_CAMERA_CIRCLE_X,
                    AuraSettings.MAX_CAMERA_CIRCLE_X
                ),
                cameraCircleOffsetY = validDimension(
                    prefs[Keys.CameraCircleOffsetY],
                    defaults.cameraCircleOffsetY,
                    AuraSettings.MIN_CAMERA_CIRCLE_Y,
                    AuraSettings.MAX_CAMERA_CIRCLE_Y
                ),
                enableBatteryRing = prefs[Keys.EnableBatteryRing] ?: defaults.enableBatteryRing,
                showBatteryIndicator = prefs[Keys.ShowBatteryIndicator] ?: defaults.showBatteryIndicator,
                batteryRingPulse = prefs[Keys.BatteryRingPulse] ?: defaults.batteryRingPulse,
                batteryRingOnLockScreen = prefs[Keys.BatteryRingOnLockScreen] ?: defaults.batteryRingOnLockScreen,
                batteryRingShowCenterIcon = prefs[Keys.BatteryRingShowCenterIcon] ?: defaults.batteryRingShowCenterIcon,
                batteryRingStrokeWidth = validDimension(
                    prefs[Keys.BatteryRingStrokeWidth],
                    defaults.batteryRingStrokeWidth,
                    AuraSettings.MIN_BATTERY_RING_STROKE,
                    AuraSettings.MAX_BATTERY_RING_STROKE
                ),
                batteryRingGap = validDimension(
                    prefs[Keys.BatteryRingGap],
                    defaults.batteryRingGap,
                    AuraSettings.MIN_BATTERY_RING_GAP,
                    AuraSettings.MAX_BATTERY_RING_GAP
                ),
                enableOutline = prefs[Keys.EnableOutline] ?: defaults.enableOutline,
                outlineColor = validColor(prefs[Keys.OutlineColor], defaults.outlineColor),
                outlineWidth = validDimension(
                    prefs[Keys.OutlineWidth],
                    defaults.outlineWidth,
                    AuraSettings.MIN_OUTLINE_WIDTH,
                    AuraSettings.MAX_OUTLINE_WIDTH
                ),
                outlineOnlyInCustomization = prefs[Keys.OutlineOnlyInCustomization] ?: defaults.outlineOnlyInCustomization
            )
        }

    suspend fun setThemeMode(value: String) = editSafely {
        if (value in VALID_THEME_MODES) {
            it[Keys.ThemeMode] = value
        }
    }
    suspend fun setUseDynamicColor(value: Boolean) = editSafely { it[Keys.UseDynamicColor] = value }
    suspend fun setDeviceType(value: String) = editSafely { it[Keys.DeviceType] = value }
    suspend fun setEnabled(value: Boolean) = editSafely { it[Keys.Enabled] = value }
    suspend fun setEnableShadow(value: Boolean) = editSafely { it[Keys.EnableShadow] = value }
    suspend fun setShadowElevation(value: Float) = editSafely {
        it[Keys.ShadowElevation] = validDimension(
            value,
            AuraSettings.Default.shadowElevation,
            AuraSettings.MIN_SHADOW_ELEVATION,
            AuraSettings.MAX_SHADOW_ELEVATION
        )
    }
    suspend fun setEnableMusicArtworkBackground(value: Boolean) = editSafely { it[Keys.EnableMusicArtworkBackground] = value }
    suspend fun setEnableNotificationBackdrop(value: Boolean) = editSafely { it[Keys.EnableNotificationBackdrop] = value }
    suspend fun setWidth(value: Float) = editSafely {
        it[Keys.Width] = validDimension(
            value,
            AuraSettings.Default.width,
            AuraSettings.MIN_WIDTH,
            AuraSettings.MAX_WIDTH
        )
    }
    suspend fun setHeight(value: Float) = editSafely {
        it[Keys.Height] = validDimension(
            value,
            AuraSettings.Default.height,
            AuraSettings.MIN_HEIGHT,
            AuraSettings.MAX_HEIGHT
        )
    }
    suspend fun setXOffset(value: Float) = editSafely {
        it[Keys.XOffset] = validDimension(
            value,
            AuraSettings.Default.xOffset,
            AuraSettings.MIN_X_OFFSET,
            AuraSettings.MAX_X_OFFSET
        )
    }
    suspend fun setYOffset(value: Float) = editSafely {
        it[Keys.YOffset] = validDimension(
            value,
            AuraSettings.Default.yOffset,
            AuraSettings.MIN_Y_OFFSET,
            AuraSettings.MAX_Y_OFFSET
        )
    }
    suspend fun setPosition(
        width: Float,
        height: Float,
        xOffset: Float,
        yOffset: Float
    ) = editSafely {
        it[Keys.Width] = validDimension(width, AuraSettings.Default.width, AuraSettings.MIN_WIDTH, AuraSettings.MAX_WIDTH)
        it[Keys.Height] = validDimension(height, AuraSettings.Default.height, AuraSettings.MIN_HEIGHT, AuraSettings.MAX_HEIGHT)
        it[Keys.XOffset] = validDimension(xOffset, AuraSettings.Default.xOffset, AuraSettings.MIN_X_OFFSET, AuraSettings.MAX_X_OFFSET)
        it[Keys.YOffset] = validDimension(yOffset, AuraSettings.Default.yOffset, AuraSettings.MIN_Y_OFFSET, AuraSettings.MAX_Y_OFFSET)
    }

    suspend fun setEnableCutoutRingMode(value: Boolean) = editSafely {
        it[Keys.EnableCutoutRingMode] = value
    }

    suspend fun setCameraCircleSize(value: Float) = editSafely {
        it[Keys.CameraCircleSize] = validDimension(
            value,
            AuraSettings.Default.cameraCircleSize,
            AuraSettings.MIN_CAMERA_CIRCLE_SIZE,
            AuraSettings.MAX_CAMERA_CIRCLE_SIZE
        )
    }

    suspend fun setCameraCircleOffsetX(value: Float) = editSafely {
        it[Keys.CameraCircleOffsetX] = validDimension(
            value,
            AuraSettings.Default.cameraCircleOffsetX,
            AuraSettings.MIN_CAMERA_CIRCLE_X,
            AuraSettings.MAX_CAMERA_CIRCLE_X
        )
    }

    suspend fun setCameraCircleOffsetY(value: Float) = editSafely {
        it[Keys.CameraCircleOffsetY] = validDimension(
            value,
            AuraSettings.Default.cameraCircleOffsetY,
            AuraSettings.MIN_CAMERA_CIRCLE_Y,
            AuraSettings.MAX_CAMERA_CIRCLE_Y
        )
    }

    suspend fun setEnableBatteryRing(value: Boolean) = editSafely {
        it[Keys.EnableBatteryRing] = value
    }

    suspend fun setShowBatteryIndicator(value: Boolean) = editSafely {
        it[Keys.ShowBatteryIndicator] = value
    }

    suspend fun setBatteryRingPulse(value: Boolean) = editSafely {
        it[Keys.BatteryRingPulse] = value
    }

    suspend fun setBatteryRingOnLockScreen(value: Boolean) = editSafely {
        it[Keys.BatteryRingOnLockScreen] = value
    }

    suspend fun setBatteryRingShowCenterIcon(value: Boolean) = editSafely {
        it[Keys.BatteryRingShowCenterIcon] = value
    }

    suspend fun setBatteryRingStrokeWidth(value: Float) = editSafely {
        it[Keys.BatteryRingStrokeWidth] = validDimension(
            value,
            AuraSettings.Default.batteryRingStrokeWidth,
            AuraSettings.MIN_BATTERY_RING_STROKE,
            AuraSettings.MAX_BATTERY_RING_STROKE
        )
    }

    suspend fun setBatteryRingGap(value: Float) = editSafely {
        it[Keys.BatteryRingGap] = validDimension(
            value,
            AuraSettings.Default.batteryRingGap,
            AuraSettings.MIN_BATTERY_RING_GAP,
            AuraSettings.MAX_BATTERY_RING_GAP
        )
    }

    suspend fun setEnableOutline(value: Boolean) = editSafely {
        it[Keys.EnableOutline] = value
    }

    suspend fun setOutlineColor(value: Long) = editSafely {
        it[Keys.OutlineColor] = validColor(value, AuraSettings.Default.outlineColor)
    }

    suspend fun setOutlineWidth(value: Float) = editSafely {
        it[Keys.OutlineWidth] = validDimension(
            value,
            AuraSettings.Default.outlineWidth,
            AuraSettings.MIN_OUTLINE_WIDTH,
            AuraSettings.MAX_OUTLINE_WIDTH
        )
    }

    suspend fun setOutlineOnlyInCustomization(value: Boolean) = editSafely {
        it[Keys.OutlineOnlyInCustomization] = value
    }

    suspend fun setCornerRadius(value: Float) = editSafely {
        it[Keys.CornerRadius] = validDimension(
            value,
            AuraSettings.Default.cornerRadius,
            AuraSettings.MIN_CORNER_RADIUS,
            AuraSettings.MAX_CORNER_RADIUS
        )
    }
    suspend fun setOpacity(value: Float) = editSafely {
        it[Keys.Opacity] = validDimension(
            value,
            AuraSettings.Default.opacity,
            AuraSettings.MIN_OPACITY,
            AuraSettings.MAX_OPACITY
        )
    }
    suspend fun setPillColor(value: Long) = editSafely {
        it[Keys.PillColor] = validColor(value, AuraSettings.Default.pillColor)
    }
    suspend fun setBatteryColor(value: Long) = editSafely {
        it[Keys.BatteryColor] = validColor(value, AuraSettings.Default.batteryColor)
    }
    suspend fun setNotificationDotColor(value: Long) = editSafely {
        it[Keys.NotificationDotColor] = validColor(
            value,
            AuraSettings.Default.notificationDotColor
        )
    }
    suspend fun setMusicVisualizerColor(value: Long) = editSafely {
        it[Keys.MusicVisualizerColor] = validColor(
            value,
            AuraSettings.Default.musicVisualizerColor
        )
    }
    suspend fun setHotspotColor(value: Long) = editSafely {
        it[Keys.HotspotColor] = validColor(value, AuraSettings.Default.hotspotColor)
    }
    suspend fun setCallColor(value: Long) = editSafely {
        it[Keys.CallColor] = validColor(value, AuraSettings.Default.callColor)
    }
    suspend fun setLiveActivityColor(value: Long) = editSafely {
        it[Keys.LiveActivityColor] = validColor(value, AuraSettings.Default.liveActivityColor)
    }
    suspend fun setTransferColor(value: Long) = editSafely {
        it[Keys.TransferColor] = validColor(value, AuraSettings.Default.transferColor)
    }
    suspend fun setNavigationColor(value: Long) = editSafely {
        it[Keys.NavigationColor] = validColor(value, AuraSettings.Default.navigationColor)
    }
    suspend fun setBluetoothColor(value: Long) = editSafely {
        it[Keys.BluetoothColor] = validColor(value, AuraSettings.Default.bluetoothColor)
    }
    suspend fun setFlashlightColor(value: Long) = editSafely {
        it[Keys.FlashlightColor] = validColor(value, AuraSettings.Default.flashlightColor)
    }
    suspend fun setScreenRecordingColor(value: Long) = editSafely {
        it[Keys.ScreenRecordingColor] = validColor(value, AuraSettings.Default.screenRecordingColor)
    }
    suspend fun setTimerColor(value: Long) = editSafely {
        it[Keys.TimerColor] = validColor(value, AuraSettings.Default.timerColor)
    }
    suspend fun setStopwatchColor(value: Long) = editSafely {
        it[Keys.StopwatchColor] = validColor(value, AuraSettings.Default.stopwatchColor)
    }
    suspend fun setEnableAppShortcuts(value: Boolean) = editSafely {
        it[Keys.EnableAppShortcuts] = value
    }
    suspend fun setShortcutPackages(value: Set<String>) = editSafely {
        it[Keys.ShortcutPackages] = value
            .asSequence()
            .filter { packageName ->
                packageName.isNotBlank() && packageName.length <= MAX_PACKAGE_NAME_LENGTH
            }
            .take(MAX_SHORTCUTS)
            .toSet()
    }
    suspend fun setShowRecentApps(value: Boolean) = editSafely {
        it[Keys.ShowRecentApps] = value
    }
    suspend fun setWelcomeDialogShown(value: Boolean) = editSafely {
        it[Keys.WelcomeDialogShown] = value
    }

    /**
     * Reads the REAL persisted value (awaits the first on-disk emission) instead of
     * relying on the AuraSettings.Default placeholder that State flows emit on the
     * first frame. Used to decide whether the welcome dialog should show.
     */
    suspend fun isWelcomeDialogShown(): Boolean = settings.first().welcomeDialogShown

    suspend fun setShowOnLockScreen(value: Boolean) = editSafely {
        it[Keys.ShowOnLockScreen] = value
    }
    suspend fun setLockScreenPrivacy(value: String) = editSafely {
        it[Keys.LockScreenPrivacy] = value.takeIf { privacy ->
            privacy in VALID_LOCK_SCREEN_PRIVACY_VALUES
        } ?: AuraSettings.Default.lockScreenPrivacy
    }
    suspend fun setShowNotificationActions(value: Boolean) = editSafely {
        it[Keys.ShowNotificationActions] = value
    }
    suspend fun setHideFromNotificationShade(value: Boolean) = editSafely {
        it[Keys.HideFromNotificationShade] = value
    }
    suspend fun setSuppressSystemHeadsUp(value: Boolean) {
        editSafely {
            it[Keys.SuppressSystemHeadsUp] = value
        }
        com.moksha.auraisland.util.HeadsUpNotificationController.applySuppression(context, value)
    }
    suspend fun setLiveActivitiesEnabled(value: Boolean) = editSafely {
        it[Keys.LiveActivitiesEnabled] = value
    }
    suspend fun setNavigationEnabled(value: Boolean) = editSafely {
        it[Keys.NavigationEnabled] = value
    }
    suspend fun setDisabledNotificationPackages(value: Set<String>) = editSafely {
        it[Keys.DisabledNotificationPackages] = value
            .asSequence()
            .filter { pkg -> pkg.isNotBlank() && pkg.length <= MAX_PACKAGE_NAME_LENGTH }
            .toSet()
    }
    suspend fun setDisabledSoundPackages(value: Set<String>) = editSafely {
        it[Keys.DisabledSoundPackages] = value
            .asSequence()
            .filter { pkg -> pkg.isNotBlank() && pkg.length <= MAX_PACKAGE_NAME_LENGTH }
            .toSet()
    }
    suspend fun setHideWhenIdle(value: Boolean) = editSafely {
        it[Keys.HideWhenIdle] = value
    }
    suspend fun setAutoHidePill(value: Boolean) = editSafely {
        it[Keys.AutoHidePill] = value
    }
    suspend fun setAutoHideTimeoutSeconds(value: Int) = editSafely {
        it[Keys.AutoHideTimeoutSeconds] = value.coerceIn(1, 120)
    }
    suspend fun setShowInLandscape(value: Boolean) = editSafely {
        it[Keys.ShowInLandscape] = value
    }
    suspend fun setAutoExpandOnNotification(value: Boolean) = editSafely {
        it[Keys.AutoExpandOnNotification] = value
    }
    suspend fun setAllowNetworkChecks(value: Boolean) = editSafely {
        it[Keys.AllowNetworkChecks] = value
    }
    suspend fun setEnableNotificationHistory(value: Boolean) = editSafely {
        it[Keys.EnableNotificationHistory] = value
    }
    suspend fun setNotificationHistoryRetentionHours(value: Int) = editSafely {
        it[Keys.NotificationHistoryRetentionHours] = value
    }
    suspend fun setShowBluetoothBattery(value: Boolean) = editSafely {
        it[Keys.ShowBluetoothBattery] = value
    }
    suspend fun setEnableBatteryMode(value: Boolean) = editSafely {
        it[Keys.EnableBatteryMode] = value
    }
    suspend fun setEnableNotificationCooldown(value: Boolean) = editSafely {
        it[Keys.EnableNotificationCooldown] = value
    }
    suspend fun setNotificationCooldownDurationMinutes(value: Int) = editSafely {
        it[Keys.NotificationCooldownDurationMinutes] = value.coerceIn(1, 60)
    }
    suspend fun setNotificationCooldownThreshold(value: Int) = editSafely {
        it[Keys.NotificationCooldownThreshold] = value.coerceIn(2, 20)
    }
    suspend fun setNotificationCooldownExcludedPackages(packages: Set<String>) = editSafely {
        it[Keys.NotificationCooldownExcludedPackages] = packages
    }
    suspend fun setDeveloperModeEnabled(value: Boolean) = editSafely {
        it[Keys.DeveloperModeEnabled] = value
    }
    suspend fun setRecordLogs(value: Boolean) = editSafely {
        it[Keys.RecordLogs] = value
    }
    suspend fun setEnableNotchMode(value: Boolean) = editSafely {
        it[Keys.EnableNotchMode] = value
    }
    suspend fun setEnableSwipeActions(value: Boolean) = editSafely {
        it[Keys.EnableSwipeActions] = value
    }
    suspend fun setSwipeUpAction(value: String) = editSafely {
        it[Keys.SwipeUpAction] = value
    }
    suspend fun setSwipeHoldUpAction(value: String) = editSafely {
        it[Keys.SwipeHoldUpAction] = value
    }
    suspend fun setSwipeDownAction(value: String) = editSafely {
        it[Keys.SwipeDownAction] = value
    }
    suspend fun setSwipeDownCollapsedAction(value: String) = editSafely {
        it[Keys.SwipeDownCollapsedAction] = value
    }
    suspend fun setSwipeHorizontalCollapsedAction(value: String) = editSafely {
        it[Keys.SwipeHorizontalCollapsedAction] = value
    }
    suspend fun setEnablePillSwipeActions(value: Boolean) = editSafely {
        it[Keys.EnablePillSwipeActions] = value
    }
    suspend fun setPillSwipeUpAction(value: String) = editSafely {
        it[Keys.PillSwipeUpAction] = value
    }
    suspend fun setPillSwipeDownAction(value: String) = editSafely {
        it[Keys.PillSwipeDownAction] = value
    }
    suspend fun setPillSwipeLeftAction(value: String) = editSafely {
        it[Keys.PillSwipeLeftAction] = value
    }
    suspend fun setPillSwipeRightAction(value: String) = editSafely {
        it[Keys.PillSwipeRightAction] = value
    }
    suspend fun setCirclePosition(value: String) = editSafely {
        it[Keys.CirclePosition] = if (value in VALID_CIRCLE_POSITIONS) value else AuraSettings.CIRCLE_POSITION_RIGHT
    }
    suspend fun toggleNotificationCooldownExcludedPackage(packageName: String) = editSafely { prefs ->
        val current = prefs[Keys.NotificationCooldownExcludedPackages] ?: emptySet()
        prefs[Keys.NotificationCooldownExcludedPackages] = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
    }

    suspend fun resetPosition() = editSafely {
        it[Keys.Width] = AuraSettings.Default.width
        it[Keys.Height] = AuraSettings.Default.height
        it[Keys.XOffset] = AuraSettings.Default.xOffset
        it[Keys.YOffset] = AuraSettings.Default.yOffset
        it[Keys.CornerRadius] = AuraSettings.Default.cornerRadius
        it[Keys.Opacity] = AuraSettings.Default.opacity
        it[Keys.PillColor] = AuraSettings.Default.pillColor
        it[Keys.ShadowElevation] = AuraSettings.Default.shadowElevation
    }

    suspend fun restoreSettings(settings: AuraSettings) = editSafely { prefs ->
        prefs[Keys.Enabled] = settings.enabled
        prefs[Keys.Width] = validDimension(
            settings.width,
            AuraSettings.Default.width,
            AuraSettings.MIN_WIDTH,
            AuraSettings.MAX_WIDTH
        )
        prefs[Keys.Height] = validDimension(
            settings.height,
            AuraSettings.Default.height,
            AuraSettings.MIN_HEIGHT,
            AuraSettings.MAX_HEIGHT
        )
        prefs[Keys.XOffset] = validDimension(
            settings.xOffset,
            AuraSettings.Default.xOffset,
            AuraSettings.MIN_X_OFFSET,
            AuraSettings.MAX_X_OFFSET
        )
        prefs[Keys.YOffset] = validDimension(
            settings.yOffset,
            AuraSettings.Default.yOffset,
            AuraSettings.MIN_Y_OFFSET,
            AuraSettings.MAX_Y_OFFSET
        )
        prefs[Keys.CornerRadius] = validDimension(
            settings.cornerRadius,
            AuraSettings.Default.cornerRadius,
            AuraSettings.MIN_CORNER_RADIUS,
            AuraSettings.MAX_CORNER_RADIUS
        )
        prefs[Keys.Opacity] = validDimension(
            settings.opacity,
            AuraSettings.Default.opacity,
            AuraSettings.MIN_OPACITY,
            AuraSettings.MAX_OPACITY
        )
        prefs[Keys.PillColor] = validColor(settings.pillColor, AuraSettings.Default.pillColor)
        prefs[Keys.BatteryColor] = validColor(settings.batteryColor, AuraSettings.Default.batteryColor)
        prefs[Keys.NotificationDotColor] = validColor(settings.notificationDotColor, AuraSettings.Default.notificationDotColor)
        prefs[Keys.MusicVisualizerColor] = validColor(settings.musicVisualizerColor, AuraSettings.Default.musicVisualizerColor)
        prefs[Keys.HotspotColor] = validColor(settings.hotspotColor, AuraSettings.Default.hotspotColor)
        prefs[Keys.CallColor] = validColor(settings.callColor, AuraSettings.Default.callColor)
        prefs[Keys.LiveActivityColor] = validColor(settings.liveActivityColor, AuraSettings.Default.liveActivityColor)
        prefs[Keys.TransferColor] = validColor(settings.transferColor, AuraSettings.Default.transferColor)
        prefs[Keys.NavigationColor] = validColor(settings.navigationColor, AuraSettings.Default.navigationColor)
        prefs[Keys.BluetoothColor] = validColor(settings.bluetoothColor, AuraSettings.Default.bluetoothColor)
        prefs[Keys.FlashlightColor] = validColor(settings.flashlightColor, AuraSettings.Default.flashlightColor)
        prefs[Keys.ScreenRecordingColor] = validColor(settings.screenRecordingColor, AuraSettings.Default.screenRecordingColor)
        prefs[Keys.TimerColor] = validColor(settings.timerColor, AuraSettings.Default.timerColor)
        prefs[Keys.StopwatchColor] = validColor(settings.stopwatchColor, AuraSettings.Default.stopwatchColor)
        prefs[Keys.EnableAppShortcuts] = settings.enableAppShortcuts
        prefs[Keys.ShortcutPackages] = settings.shortcutPackages
            .asSequence()
            .filter { it.isNotBlank() && it.length <= MAX_PACKAGE_NAME_LENGTH }
            .take(MAX_SHORTCUTS)
            .toSet()
        prefs[Keys.ShowRecentApps] = settings.showRecentApps
        prefs[Keys.WelcomeDialogShown] = settings.welcomeDialogShown
        prefs[Keys.ShowOnLockScreen] = settings.showOnLockScreen
        prefs[Keys.LockScreenPrivacy] = settings.lockScreenPrivacy.takeIf { it in VALID_LOCK_SCREEN_PRIVACY_VALUES }
            ?: AuraSettings.Default.lockScreenPrivacy
        prefs[Keys.ShowNotificationActions] = settings.showNotificationActions
        prefs[Keys.HideFromNotificationShade] = settings.hideFromNotificationShade
        prefs[Keys.SuppressSystemHeadsUp] = settings.suppressSystemHeadsUp
        prefs[Keys.LiveActivitiesEnabled] = settings.liveActivitiesEnabled
        prefs[Keys.NavigationEnabled] = settings.navigationEnabled
        prefs[Keys.DisabledNotificationPackages] = settings.disabledNotificationPackages
            .asSequence()
            .filter { it.isNotBlank() && it.length <= MAX_PACKAGE_NAME_LENGTH }
            .toSet()
        prefs[Keys.DisabledSoundPackages] = settings.disabledSoundPackages
            .asSequence()
            .filter { it.isNotBlank() && it.length <= MAX_PACKAGE_NAME_LENGTH }
            .toSet()
        prefs[Keys.HideWhenIdle] = settings.hideWhenIdle
        prefs[Keys.AutoHidePill] = settings.autoHidePill
        prefs[Keys.AutoHideTimeoutSeconds] = settings.autoHideTimeoutSeconds.coerceIn(1, 120)
        prefs[Keys.ShowInLandscape] = settings.showInLandscape
        prefs[Keys.AutoExpandOnNotification] = settings.autoExpandOnNotification
        prefs[Keys.EnableShadow] = settings.enableShadow
        prefs[Keys.ShadowElevation] = validDimension(
            settings.shadowElevation,
            AuraSettings.Default.shadowElevation,
            AuraSettings.MIN_SHADOW_ELEVATION,
            AuraSettings.MAX_SHADOW_ELEVATION
        )
        prefs[Keys.EnableMusicArtworkBackground] = settings.enableMusicArtworkBackground
        prefs[Keys.EnableNotificationBackdrop] = settings.enableNotificationBackdrop
        prefs[Keys.DeviceType] = settings.deviceType
        prefs[Keys.AllowNetworkChecks] = settings.allowNetworkChecks
        prefs[Keys.EnableNotificationHistory] = settings.enableNotificationHistory
        prefs[Keys.NotificationHistoryRetentionHours] = settings.notificationHistoryRetentionHours
        prefs[Keys.ShowBluetoothBattery] = settings.showBluetoothBattery
        prefs[Keys.EnableBatteryMode] = settings.enableBatteryMode
        prefs[Keys.EnableNotificationCooldown] = settings.enableNotificationCooldown
        prefs[Keys.NotificationCooldownDurationMinutes] = settings.notificationCooldownDurationMinutes
        prefs[Keys.NotificationCooldownThreshold] = settings.notificationCooldownThreshold
        prefs[Keys.NotificationCooldownExcludedPackages] = settings.notificationCooldownExcludedPackages
        prefs[Keys.DeveloperModeEnabled] = settings.developerModeEnabled
        prefs[Keys.RecordLogs] = settings.recordLogs
        prefs[Keys.EnableNotchMode] = settings.enableNotchMode
        prefs[Keys.EnableSwipeActions] = settings.enableSwipeActions
        prefs[Keys.SwipeUpAction] = settings.swipeUpAction
        prefs[Keys.SwipeHoldUpAction] = settings.swipeHoldUpAction
        prefs[Keys.SwipeDownAction] = settings.swipeDownAction
        prefs[Keys.SwipeDownCollapsedAction] = settings.swipeDownCollapsedAction
        prefs[Keys.SwipeHorizontalCollapsedAction] = settings.swipeHorizontalCollapsedAction
        prefs[Keys.EnablePillSwipeActions] = settings.enablePillSwipeActions
        prefs[Keys.PillSwipeUpAction] = settings.pillSwipeUpAction
        prefs[Keys.PillSwipeDownAction] = settings.pillSwipeDownAction
        prefs[Keys.PillSwipeLeftAction] = settings.pillSwipeLeftAction
        prefs[Keys.PillSwipeRightAction] = settings.pillSwipeRightAction
        prefs[Keys.CirclePosition] = if (settings.circlePosition in VALID_CIRCLE_POSITIONS) {
            settings.circlePosition
        } else {
            AuraSettings.CIRCLE_POSITION_RIGHT
        }
        prefs[Keys.ThemeMode] = if (settings.themeMode in VALID_THEME_MODES) {
            settings.themeMode
        } else {
            AuraSettings.THEME_MODE_SYSTEM
        }
        prefs[Keys.UseDynamicColor] = settings.useDynamicColor
        prefs[Keys.RootModeEnabled] = settings.rootModeEnabled
        prefs[Keys.YieldToSystemIsland] = settings.yieldToSystemIsland
        prefs[Keys.YieldMusicToSystemIsland] = settings.yieldMusicToSystemIsland
        prefs[Keys.OemIslandHandlingMode] = settings.oemIslandHandlingMode
        prefs[Keys.EnableCutoutRingMode] = settings.enableCutoutRingMode
        prefs[Keys.CameraCircleSize] = settings.cameraCircleSize
        prefs[Keys.CameraCircleOffsetX] = settings.cameraCircleOffsetX
        prefs[Keys.CameraCircleOffsetY] = settings.cameraCircleOffsetY
        prefs[Keys.EnableBatteryRing] = settings.enableBatteryRing
        prefs[Keys.ShowBatteryIndicator] = settings.showBatteryIndicator
        prefs[Keys.BatteryRingPulse] = settings.batteryRingPulse
        prefs[Keys.BatteryRingOnLockScreen] = settings.batteryRingOnLockScreen
        prefs[Keys.BatteryRingShowCenterIcon] = settings.batteryRingShowCenterIcon
        prefs[Keys.BatteryRingStrokeWidth] = settings.batteryRingStrokeWidth
        prefs[Keys.BatteryRingGap] = settings.batteryRingGap
        prefs[Keys.EnableOutline] = settings.enableOutline
        prefs[Keys.OutlineColor] = settings.outlineColor
        prefs[Keys.OutlineWidth] = settings.outlineWidth
        prefs[Keys.OutlineOnlyInCustomization] = settings.outlineOnlyInCustomization
    }

    suspend fun setRootModeEnabled(enabled: Boolean) = editSafely {
        it[Keys.RootModeEnabled] = enabled
    }

    suspend fun setYieldToSystemIsland(value: Boolean) = editSafely {
        it[Keys.YieldToSystemIsland] = value
    }

    suspend fun setYieldMusicToSystemIsland(value: Boolean) = editSafely {
        it[Keys.YieldMusicToSystemIsland] = value
    }

    suspend fun setOemIslandHandlingMode(mode: String) = editSafely {
        it[Keys.OemIslandHandlingMode] = mode
    }

    suspend fun resetAllSettings() = restoreSettings(AuraSettings.Default.copy(welcomeDialogShown = true))

    private var backupJob: kotlinx.coroutines.Job? = null
    private val backupScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private suspend fun editSafely(transform: suspend (MutablePreferences) -> Unit) {
        try {
            context.auraDataStore.edit(transform)
            // Debounced auto-backup: schedule a backup 2s after last change
            // to avoid excessive I/O during slider drags
            scheduleAutoBackup()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Unable to persist settings", error)
        }
    }

    private fun scheduleAutoBackup() {
        backupJob?.cancel()
        backupJob = backupScope.launch {
            kotlinx.coroutines.delay(2000L)
            try {
                val currentSettings = settings.first()
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                } catch (_: Exception) { "" }
                SettingsBackupManager.saveBackup(currentSettings, appVersion, context)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Auto-backup failed", error)
            }
        }
    }

    /**
     * Forces an immediate backup of current settings to external storage.
     * Call this on significant events like app going to background.
     */
    suspend fun forceBackup() {
        try {
            val currentSettings = settings.first()
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }
            SettingsBackupManager.saveBackup(currentSettings, appVersion, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Force backup failed", error)
        }
    }

    private fun validDimension(
        value: Float?,
        fallback: Float,
        min: Float,
        max: Float
    ): Float = value?.takeIf { it.isFinite() }?.coerceIn(min, max) ?: fallback

    private fun validColor(value: Long?, fallback: Long): Long =
        value?.takeIf { it != 0L } ?: fallback

    private companion object {
        const val TAG = "AuraSettings"
        const val MAX_SHORTCUTS = 8
        const val MAX_PACKAGE_NAME_LENGTH = 255
        const val MIN_ARGB_COLOR = 0x01000000L
        const val MAX_ARGB_COLOR = 0xFFFFFFFFL
        const val ALPHA_SHIFT = 24
        val VALID_LOCK_SCREEN_PRIVACY_VALUES = setOf("AppIconOnly", "FullContent")
        val VALID_CIRCLE_POSITIONS = setOf(
            AuraSettings.CIRCLE_POSITION_RIGHT,
            AuraSettings.CIRCLE_POSITION_LEFT
        )
        val VALID_THEME_MODES = setOf(
            AuraSettings.THEME_MODE_SYSTEM,
            AuraSettings.THEME_MODE_DARK,
            AuraSettings.THEME_MODE_LIGHT
        )
    }
}
