/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.data

import org.json.JSONArray
import org.json.JSONObject

data class AuraSettings(
    val enabled: Boolean = false,
    val width: Float = 112f,
    val height: Float = 34f,
    val xOffset: Float = 0f,
    val yOffset: Float = 12f,
    val cornerRadius: Float = 22f,
    val opacity: Float = 1f,
    val pillColor: Long = 0xFF000000L,
    val batteryColor: Long = 0xFF10B981L,
    val notificationDotColor: Long = 0xFF2563EBL,
    val musicVisualizerColor: Long = 0xFFFF6B9AL,
    val hotspotColor: Long = 0xFFF59E0BL,
    val callColor: Long = 0xFF22C55EL,
    val liveActivityColor: Long = 0xFF8B5CF6L,
    val transferColor: Long = 0xFF06B6D4L,
    val navigationColor: Long = 0xFF10B981L,
    val bluetoothColor: Long = 0xFF2563EBL,
    val flashlightColor: Long = 0xFFF59E0BL,
    val screenRecordingColor: Long = 0xFFEF4444L,
    val timerColor: Long = 0xFFF59E0BL,
    val stopwatchColor: Long = 0xFF06B6D4L,
    val enableAppShortcuts: Boolean = true,
    val shortcutPackages: Set<String> = emptySet(),
    val showRecentApps: Boolean = false,
    val welcomeDialogShown: Boolean = false,
    val showOnLockScreen: Boolean = false,
    val lockScreenPrivacy: String = "AppIconOnly",
    val showNotificationActions: Boolean = true,
    val hideFromNotificationShade: Boolean = false,
    val suppressSystemHeadsUp: Boolean = true,
    val liveActivitiesEnabled: Boolean = true,
    val navigationEnabled: Boolean = true,
    val disabledNotificationPackages: Set<String> = emptySet(),
    val disabledSoundPackages: Set<String> = emptySet(),
    val hideWhenIdle: Boolean = false,
    val autoHidePill: Boolean = false,
    val autoHideTimeoutSeconds: Int = 5,
    val showInLandscape: Boolean = false,
    val autoExpandOnNotification: Boolean = true,
    val enableShadow: Boolean = true,
    val shadowElevation: Float = 14f,
    val enableMusicArtworkBackground: Boolean = true,
    val enableNotificationBackdrop: Boolean = true,
    val deviceType: String = "AUTO",
    val allowNetworkChecks: Boolean = true,
    val enableNotificationHistory: Boolean = false,
    val notificationHistoryRetentionHours: Int = 72,
    val showBluetoothBattery: Boolean = true,
    val enableBatteryMode: Boolean = true,
    val enableNotificationCooldown: Boolean = false,
    val notificationCooldownDurationMinutes: Int = 3,
    val notificationCooldownThreshold: Int = 3,
    val notificationCooldownExcludedPackages: Set<String> = emptySet(),
    val developerModeEnabled: Boolean = true,
    val recordLogs: Boolean = false,
    val enableNotchMode: Boolean = false,
    val enableSwipeActions: Boolean = true,
    val swipeUpAction: String = "DismissCurrent",
    val swipeHoldUpAction: String = "DismissAll",
    val swipeDownAction: String = "FloatingWindow",
    val swipeDownCollapsedAction: String = "Expand",
    val swipeHorizontalCollapsedAction: String = "NextPrevious",
    val enablePillSwipeActions: Boolean = true,
    val pillSwipeUpAction: String = "DismissCurrent",
    val pillSwipeDownAction: String = "Expand",
    val pillSwipeLeftAction: String = "PreviousNotification",
    val pillSwipeRightAction: String = "NextNotification",
    val circlePosition: String = CIRCLE_POSITION_RIGHT,
    val themeMode: String = THEME_MODE_SYSTEM,
    val useDynamicColor: Boolean = false,
    val rootModeEnabled: Boolean = true,
    val yieldToSystemIsland: Boolean = true,
    val yieldMusicToSystemIsland: Boolean = true,
    val oemIslandHandlingMode: String = OEM_ISLAND_MODE_SMART_YIELD,
    val enableCutoutRingMode: Boolean = true,
    val cameraCircleSize: Float = 34f,
    val cameraCircleOffsetX: Float = 0f,
    val cameraCircleOffsetY: Float = 0f,
    val enableBatteryRing: Boolean = true,
    val showBatteryIndicator: Boolean = false,
    val batteryRingPulse: Boolean = true,
    val batteryRingOnLockScreen: Boolean = true,
    val batteryRingShowCenterIcon: Boolean = true,
    val batteryRingStrokeWidth: Float = 2.5f,
    val batteryRingGap: Float = 1.15f,
    val enableOutline: Boolean = true,
    val outlineColor: Long = 0x4DFFFFFFL,
    val outlineWidth: Float = 1.0f,
    val outlineOnlyInCustomization: Boolean = true
) {
    fun toJson(appVersion: String = ""): String {
        val root = JSONObject()
        root.put("formatVersion", BACKUP_FORMAT_VERSION)
        root.put("app", "com.moksha.auraisland")
        if (appVersion.isNotBlank()) {
            root.put("appVersion", appVersion)
        }
        root.put("exportTimestamp", System.currentTimeMillis())

        val settingsObj = JSONObject()
        settingsObj.put("enabled", enabled)
        settingsObj.put("width", width.toDouble())
        settingsObj.put("height", height.toDouble())
        settingsObj.put("xOffset", xOffset.toDouble())
        settingsObj.put("yOffset", yOffset.toDouble())
        settingsObj.put("cornerRadius", cornerRadius.toDouble())
        settingsObj.put("opacity", opacity.toDouble())
        settingsObj.put("pillColor", pillColor)
        settingsObj.put("batteryColor", batteryColor)
        settingsObj.put("notificationDotColor", notificationDotColor)
        settingsObj.put("musicVisualizerColor", musicVisualizerColor)
        settingsObj.put("hotspotColor", hotspotColor)
        settingsObj.put("callColor", callColor)
        settingsObj.put("liveActivityColor", liveActivityColor)
        settingsObj.put("transferColor", transferColor)
        settingsObj.put("navigationColor", navigationColor)
        settingsObj.put("bluetoothColor", bluetoothColor)
        settingsObj.put("flashlightColor", flashlightColor)
        settingsObj.put("screenRecordingColor", screenRecordingColor)
        settingsObj.put("timerColor", timerColor)
        settingsObj.put("stopwatchColor", stopwatchColor)
        settingsObj.put("enableAppShortcuts", enableAppShortcuts)

        val shortcutsArray = JSONArray()
        shortcutPackages.forEach { shortcutsArray.put(it) }
        settingsObj.put("shortcutPackages", shortcutsArray)

        settingsObj.put("showRecentApps", showRecentApps)
        settingsObj.put("welcomeDialogShown", welcomeDialogShown)
        settingsObj.put("showOnLockScreen", showOnLockScreen)
        settingsObj.put("lockScreenPrivacy", lockScreenPrivacy)
        settingsObj.put("showNotificationActions", showNotificationActions)
        settingsObj.put("hideFromNotificationShade", hideFromNotificationShade)
        settingsObj.put("suppressSystemHeadsUp", suppressSystemHeadsUp)
        settingsObj.put("liveActivitiesEnabled", liveActivitiesEnabled)
        settingsObj.put("navigationEnabled", navigationEnabled)

        val disabledNotifArray = JSONArray()
        disabledNotificationPackages.forEach { disabledNotifArray.put(it) }
        settingsObj.put("disabledNotificationPackages", disabledNotifArray)

        val disabledSoundArray = JSONArray()
        disabledSoundPackages.forEach { disabledSoundArray.put(it) }
        settingsObj.put("disabledSoundPackages", disabledSoundArray)

        settingsObj.put("hideWhenIdle", hideWhenIdle)
        settingsObj.put("autoHidePill", autoHidePill)
        settingsObj.put("autoHideTimeoutSeconds", autoHideTimeoutSeconds)
        settingsObj.put("showInLandscape", showInLandscape)
        settingsObj.put("autoExpandOnNotification", autoExpandOnNotification)
        settingsObj.put("enableShadow", enableShadow)
        settingsObj.put("shadowElevation", shadowElevation.toDouble())
        settingsObj.put("enableMusicArtworkBackground", enableMusicArtworkBackground)
        settingsObj.put("enableNotificationBackdrop", enableNotificationBackdrop)
        settingsObj.put("deviceType", deviceType)
        settingsObj.put("allowNetworkChecks", allowNetworkChecks)
        settingsObj.put("enableNotificationHistory", enableNotificationHistory)
        settingsObj.put("notificationHistoryRetentionHours", notificationHistoryRetentionHours)
        settingsObj.put("showBluetoothBattery", showBluetoothBattery)
        settingsObj.put("enableBatteryMode", enableBatteryMode)
        settingsObj.put("enableNotificationCooldown", enableNotificationCooldown)
        settingsObj.put("notificationCooldownDurationMinutes", notificationCooldownDurationMinutes)
        settingsObj.put("notificationCooldownThreshold", notificationCooldownThreshold)

        val cooldownExcludedArray = JSONArray()
        notificationCooldownExcludedPackages.forEach { cooldownExcludedArray.put(it) }
        settingsObj.put("notificationCooldownExcludedPackages", cooldownExcludedArray)

        settingsObj.put("developerModeEnabled", developerModeEnabled)
        settingsObj.put("recordLogs", recordLogs)
        settingsObj.put("enableNotchMode", enableNotchMode)
        settingsObj.put("enableSwipeActions", enableSwipeActions)
        settingsObj.put("swipeUpAction", swipeUpAction)
        settingsObj.put("swipeHoldUpAction", swipeHoldUpAction)
        settingsObj.put("swipeDownAction", swipeDownAction)
        settingsObj.put("swipeDownCollapsedAction", swipeDownCollapsedAction)
        settingsObj.put("swipeHorizontalCollapsedAction", swipeHorizontalCollapsedAction)
        settingsObj.put("enablePillSwipeActions", enablePillSwipeActions)
        settingsObj.put("pillSwipeUpAction", pillSwipeUpAction)
        settingsObj.put("pillSwipeDownAction", pillSwipeDownAction)
        settingsObj.put("pillSwipeLeftAction", pillSwipeLeftAction)
        settingsObj.put("pillSwipeRightAction", pillSwipeRightAction)
        settingsObj.put("circlePosition", circlePosition)
        settingsObj.put("themeMode", themeMode)
        settingsObj.put("useDynamicColor", useDynamicColor)
        settingsObj.put("rootModeEnabled", rootModeEnabled)
        settingsObj.put("yieldToSystemIsland", yieldToSystemIsland)
        settingsObj.put("yieldMusicToSystemIsland", yieldMusicToSystemIsland)
        settingsObj.put("oemIslandHandlingMode", oemIslandHandlingMode)
        settingsObj.put("enableCutoutRingMode", enableCutoutRingMode)
        settingsObj.put("cameraCircleSize", cameraCircleSize.toDouble())
        settingsObj.put("cameraCircleOffsetX", cameraCircleOffsetX.toDouble())
        settingsObj.put("cameraCircleOffsetY", cameraCircleOffsetY.toDouble())
        settingsObj.put("enableBatteryRing", enableBatteryRing)
        settingsObj.put("showBatteryIndicator", showBatteryIndicator)
        settingsObj.put("batteryRingPulse", batteryRingPulse)
        settingsObj.put("batteryRingOnLockScreen", batteryRingOnLockScreen)
        settingsObj.put("batteryRingShowCenterIcon", batteryRingShowCenterIcon)
        settingsObj.put("batteryRingStrokeWidth", batteryRingStrokeWidth.toDouble())
        settingsObj.put("batteryRingGap", batteryRingGap.toDouble())
        settingsObj.put("enableOutline", enableOutline)
        settingsObj.put("outlineColor", outlineColor)
        settingsObj.put("outlineWidth", outlineWidth.toDouble())
        settingsObj.put("outlineOnlyInCustomization", outlineOnlyInCustomization)

        root.put("settings", settingsObj)
        return root.toString(2)
    }

    data class BackupMetadata(
        val formatVersion: Int,
        val appVersion: String,
        val timestamp: Long,
        val settingsCount: Int
    )

    companion object {
        const val OEM_ISLAND_MODE_SMART_YIELD = "SMART_YIELD"
        const val OEM_ISLAND_MODE_HIDE_AURA = "HIDE_AURA"
        const val OEM_ISLAND_MODE_ALWAYS_SHOW = "ALWAYS_SHOW"

        const val CIRCLE_POSITION_RIGHT = "right"
        const val CIRCLE_POSITION_LEFT = "left"

        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_DARK = "dark"
        const val THEME_MODE_LIGHT = "light"

        val Default = AuraSettings()

        const val BACKUP_FORMAT_VERSION = 1

        const val MIN_WIDTH = 76f
        const val MAX_WIDTH = 180f
        const val MIN_HEIGHT = 8f
        const val MAX_HEIGHT = 60f
        const val MIN_X_OFFSET = -140f
        const val MAX_X_OFFSET = 140f
        const val MIN_Y_OFFSET = 0f
        const val MAX_Y_OFFSET = 80f
        const val MIN_CORNER_RADIUS = 8f
        const val MAX_CORNER_RADIUS = 40f
        const val MIN_OPACITY = 0.2f
        const val MAX_OPACITY = 1f
        const val MIN_SHADOW_ELEVATION = 0f
        const val MAX_SHADOW_ELEVATION = 32f

        const val MIN_CAMERA_CIRCLE_SIZE = 16f
        const val MAX_CAMERA_CIRCLE_SIZE = 60f
        const val MIN_CAMERA_CIRCLE_X = -140f
        const val MAX_CAMERA_CIRCLE_X = 140f
        const val MIN_CAMERA_CIRCLE_Y = -30f
        const val MAX_CAMERA_CIRCLE_Y = 80f
        const val MIN_BATTERY_RING_STROKE = 1f
        const val MAX_BATTERY_RING_STROKE = 8f
        const val MIN_BATTERY_RING_GAP = 0.9f
        const val MAX_BATTERY_RING_GAP = 1.5f
        const val MIN_OUTLINE_WIDTH = 0.5f
        const val MAX_OUTLINE_WIDTH = 4.0f

        fun parseBackupMetadata(jsonString: String): BackupMetadata? {
            return runCatching {
                val root = JSONObject(jsonString)
                val formatVersion = root.optInt("formatVersion", 1)
                val appVersion = root.optString("appVersion", "Unknown")
                val timestamp = root.optLong("exportTimestamp", System.currentTimeMillis())
                val settingsObj = root.optJSONObject("settings") ?: root
                BackupMetadata(
                    formatVersion = formatVersion,
                    appVersion = appVersion,
                    timestamp = timestamp,
                    settingsCount = settingsObj.length()
                )
            }.getOrNull()
        }

        fun fromJson(jsonString: String): AuraSettings {
            val root = JSONObject(jsonString)
            val obj = root.optJSONObject("settings") ?: root
            val defaults = Default

            fun safeFloat(key: String, default: Float, min: Float, max: Float): Float {
                val raw = if (obj.has(key)) obj.optDouble(key, default.toDouble()).toFloat() else default
                return if (raw.isFinite()) raw.coerceIn(min, max) else default
            }

            fun safeColor(key: String, default: Long): Long {
                return if (obj.has(key)) {
                    val color = obj.optLong(key, default)
                    if (color != 0L) color else default
                } else default
            }

            fun safeStringSet(key: String, default: Set<String>, maxItems: Int = Int.MAX_VALUE): Set<String> {
                val arr = obj.optJSONArray(key) ?: return default
                val result = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i, "").trim()
                    if (pkg.isNotBlank() && pkg.length <= 255) {
                        result.add(pkg)
                        if (result.size >= maxItems) break
                    }
                }
                return result
            }

            return AuraSettings(
                enabled = obj.optBoolean("enabled", defaults.enabled),
                width = safeFloat("width", defaults.width, MIN_WIDTH, MAX_WIDTH),
                height = safeFloat("height", defaults.height, MIN_HEIGHT, MAX_HEIGHT),
                xOffset = safeFloat("xOffset", defaults.xOffset, MIN_X_OFFSET, MAX_X_OFFSET),
                yOffset = safeFloat("yOffset", defaults.yOffset, MIN_Y_OFFSET, MAX_Y_OFFSET),
                cornerRadius = safeFloat("cornerRadius", defaults.cornerRadius, MIN_CORNER_RADIUS, MAX_CORNER_RADIUS),
                opacity = safeFloat("opacity", defaults.opacity, MIN_OPACITY, MAX_OPACITY),
                pillColor = safeColor("pillColor", defaults.pillColor),
                batteryColor = safeColor("batteryColor", defaults.batteryColor),
                notificationDotColor = safeColor("notificationDotColor", defaults.notificationDotColor),
                musicVisualizerColor = safeColor("musicVisualizerColor", defaults.musicVisualizerColor),
                hotspotColor = safeColor("hotspotColor", defaults.hotspotColor),
                callColor = safeColor("callColor", defaults.callColor),
                liveActivityColor = safeColor("liveActivityColor", defaults.liveActivityColor),
                transferColor = safeColor("transferColor", defaults.transferColor),
                navigationColor = safeColor("navigationColor", defaults.navigationColor),
                bluetoothColor = safeColor("bluetoothColor", defaults.bluetoothColor),
                flashlightColor = safeColor("flashlightColor", defaults.flashlightColor),
                screenRecordingColor = safeColor("screenRecordingColor", defaults.screenRecordingColor),
                timerColor = safeColor("timerColor", defaults.timerColor),
                stopwatchColor = safeColor("stopwatchColor", defaults.stopwatchColor),
                enableAppShortcuts = obj.optBoolean("enableAppShortcuts", defaults.enableAppShortcuts),
                shortcutPackages = safeStringSet("shortcutPackages", defaults.shortcutPackages, 8),
                showRecentApps = obj.optBoolean("showRecentApps", defaults.showRecentApps),
                welcomeDialogShown = obj.optBoolean("welcomeDialogShown", defaults.welcomeDialogShown),
                showOnLockScreen = obj.optBoolean("showOnLockScreen", defaults.showOnLockScreen),
                lockScreenPrivacy = obj.optString("lockScreenPrivacy", defaults.lockScreenPrivacy).let {
                    if (it == "AppIconOnly" || it == "FullContent") it else defaults.lockScreenPrivacy
                },
                showNotificationActions = obj.optBoolean("showNotificationActions", defaults.showNotificationActions),
                hideFromNotificationShade = obj.optBoolean("hideFromNotificationShade", defaults.hideFromNotificationShade),
                suppressSystemHeadsUp = obj.optBoolean("suppressSystemHeadsUp", defaults.suppressSystemHeadsUp),
                liveActivitiesEnabled = obj.optBoolean("liveActivitiesEnabled", defaults.liveActivitiesEnabled),
                navigationEnabled = obj.optBoolean("navigationEnabled", defaults.navigationEnabled),
                disabledNotificationPackages = safeStringSet("disabledNotificationPackages", defaults.disabledNotificationPackages),
                disabledSoundPackages = safeStringSet("disabledSoundPackages", defaults.disabledSoundPackages),
                hideWhenIdle = obj.optBoolean("hideWhenIdle", defaults.hideWhenIdle),
                autoHidePill = obj.optBoolean("autoHidePill", defaults.autoHidePill),
                autoHideTimeoutSeconds = obj.optInt("autoHideTimeoutSeconds", defaults.autoHideTimeoutSeconds).coerceIn(1, 120),
                showInLandscape = obj.optBoolean("showInLandscape", defaults.showInLandscape),
                autoExpandOnNotification = obj.optBoolean("autoExpandOnNotification", defaults.autoExpandOnNotification),
                enableShadow = obj.optBoolean("enableShadow", defaults.enableShadow),
                shadowElevation = safeFloat("shadowElevation", defaults.shadowElevation, MIN_SHADOW_ELEVATION, MAX_SHADOW_ELEVATION),
                enableMusicArtworkBackground = obj.optBoolean("enableMusicArtworkBackground", defaults.enableMusicArtworkBackground),
                enableNotificationBackdrop = obj.optBoolean("enableNotificationBackdrop", defaults.enableNotificationBackdrop),
                deviceType = obj.optString("deviceType", defaults.deviceType),
                allowNetworkChecks = obj.optBoolean("allowNetworkChecks", defaults.allowNetworkChecks),
                enableNotificationHistory = obj.optBoolean("enableNotificationHistory", defaults.enableNotificationHistory),
                notificationHistoryRetentionHours = obj.optInt("notificationHistoryRetentionHours", defaults.notificationHistoryRetentionHours).coerceIn(1, 720),
                showBluetoothBattery = obj.optBoolean("showBluetoothBattery", defaults.showBluetoothBattery),
                enableBatteryMode = obj.optBoolean("enableBatteryMode", defaults.enableBatteryMode),
                enableNotificationCooldown = obj.optBoolean("enableNotificationCooldown", defaults.enableNotificationCooldown),
                notificationCooldownDurationMinutes = obj.optInt("notificationCooldownDurationMinutes", defaults.notificationCooldownDurationMinutes).coerceIn(1, 60),
                notificationCooldownThreshold = obj.optInt("notificationCooldownThreshold", defaults.notificationCooldownThreshold).coerceIn(2, 20),
                notificationCooldownExcludedPackages = safeStringSet("notificationCooldownExcludedPackages", defaults.notificationCooldownExcludedPackages),
                developerModeEnabled = obj.optBoolean("developerModeEnabled", defaults.developerModeEnabled),
                recordLogs = obj.optBoolean("recordLogs", defaults.recordLogs),
                enableNotchMode = obj.optBoolean("enableNotchMode", defaults.enableNotchMode),
                enableSwipeActions = obj.optBoolean("enableSwipeActions", defaults.enableSwipeActions),
                swipeUpAction = obj.optString("swipeUpAction", defaults.swipeUpAction),
                swipeHoldUpAction = obj.optString("swipeHoldUpAction", defaults.swipeHoldUpAction),
                swipeDownAction = obj.optString("swipeDownAction", defaults.swipeDownAction),
                swipeDownCollapsedAction = obj.optString("swipeDownCollapsedAction", defaults.swipeDownCollapsedAction),
                swipeHorizontalCollapsedAction = obj.optString("swipeHorizontalCollapsedAction", defaults.swipeHorizontalCollapsedAction),
                enablePillSwipeActions = obj.optBoolean("enablePillSwipeActions", defaults.enablePillSwipeActions),
                pillSwipeUpAction = obj.optString("pillSwipeUpAction", defaults.pillSwipeUpAction),
                pillSwipeDownAction = obj.optString("pillSwipeDownAction", defaults.pillSwipeDownAction),
                pillSwipeLeftAction = obj.optString("pillSwipeLeftAction", defaults.pillSwipeLeftAction),
                pillSwipeRightAction = obj.optString("pillSwipeRightAction", defaults.pillSwipeRightAction),
                circlePosition = obj.optString("circlePosition", defaults.circlePosition).let {
                    if (it == CIRCLE_POSITION_LEFT || it == CIRCLE_POSITION_RIGHT) it else defaults.circlePosition
                },
                themeMode = obj.optString("themeMode", defaults.themeMode).let {
                    if (it == THEME_MODE_DARK || it == THEME_MODE_LIGHT || it == THEME_MODE_SYSTEM) it else defaults.themeMode
                },
                useDynamicColor = obj.optBoolean("useDynamicColor", defaults.useDynamicColor),
                rootModeEnabled = obj.optBoolean("rootModeEnabled", defaults.rootModeEnabled),
                yieldToSystemIsland = obj.optBoolean("yieldToSystemIsland", defaults.yieldToSystemIsland),
                yieldMusicToSystemIsland = obj.optBoolean("yieldMusicToSystemIsland", defaults.yieldMusicToSystemIsland),
                oemIslandHandlingMode = obj.optString("oemIslandHandlingMode", defaults.oemIslandHandlingMode).let {
                    if (it == OEM_ISLAND_MODE_SMART_YIELD || it == OEM_ISLAND_MODE_HIDE_AURA || it == OEM_ISLAND_MODE_ALWAYS_SHOW) it else defaults.oemIslandHandlingMode
                },
                enableCutoutRingMode = obj.optBoolean("enableCutoutRingMode", defaults.enableCutoutRingMode),
                cameraCircleSize = safeFloat("cameraCircleSize", defaults.cameraCircleSize, MIN_CAMERA_CIRCLE_SIZE, MAX_CAMERA_CIRCLE_SIZE),
                cameraCircleOffsetX = safeFloat("cameraCircleOffsetX", defaults.cameraCircleOffsetX, MIN_CAMERA_CIRCLE_X, MAX_CAMERA_CIRCLE_X),
                cameraCircleOffsetY = safeFloat("cameraCircleOffsetY", defaults.cameraCircleOffsetY, MIN_CAMERA_CIRCLE_Y, MAX_CAMERA_CIRCLE_Y),
                enableBatteryRing = obj.optBoolean("enableBatteryRing", defaults.enableBatteryRing),
                showBatteryIndicator = obj.optBoolean("showBatteryIndicator", defaults.showBatteryIndicator),
                batteryRingPulse = obj.optBoolean("batteryRingPulse", defaults.batteryRingPulse),
                batteryRingOnLockScreen = obj.optBoolean("batteryRingOnLockScreen", defaults.batteryRingOnLockScreen),
                batteryRingShowCenterIcon = obj.optBoolean("batteryRingShowCenterIcon", defaults.batteryRingShowCenterIcon),
                batteryRingStrokeWidth = safeFloat("batteryRingStrokeWidth", defaults.batteryRingStrokeWidth, MIN_BATTERY_RING_STROKE, MAX_BATTERY_RING_STROKE),
                batteryRingGap = safeFloat("batteryRingGap", defaults.batteryRingGap, MIN_BATTERY_RING_GAP, MAX_BATTERY_RING_GAP),
                enableOutline = obj.optBoolean("enableOutline", defaults.enableOutline),
                outlineColor = safeColor("outlineColor", defaults.outlineColor),
                outlineWidth = safeFloat("outlineWidth", defaults.outlineWidth, MIN_OUTLINE_WIDTH, MAX_OUTLINE_WIDTH),
                outlineOnlyInCustomization = obj.optBoolean("outlineOnlyInCustomization", defaults.outlineOnlyInCustomization)
            )
        }
    }
}
