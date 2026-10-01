/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.moksha.auraisland.data.AuraCommand
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.data.INotificationHistoryRepository
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.data.NotificationHistoryEntry
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import com.moksha.auraisland.model.IslandNotificationAction
import com.moksha.auraisland.util.NotificationCooldownManager
import com.moksha.auraisland.util.NotificationFilter
import com.moksha.auraisland.util.OemDeviceRules
import com.moksha.auraisland.util.PermissionUtils
import com.moksha.auraisland.util.TimerStopwatchParser
import com.moksha.auraisland.util.isCallEnded
import com.moksha.auraisland.util.isDownloadComplete
import com.moksha.auraisland.util.isScreenRecordingComplete
import com.moksha.auraisland.util.runCatchingLogged
import com.moksha.auraisland.util.runSuspendCatchingLogged
import com.moksha.auraisland.util.toIslandMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class AuraNotificationListenerService : NotificationListenerService() {
    // Keys we have canceled ourselves to make island-only. Keeps island copy alive.
    private val suppressedKeys = ConcurrentHashMap<String, Long>()
    @Volatile private var currentSettings = AuraSettings.Default
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unhandled notification-listener coroutine failure", error)
    }
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)
    @Volatile private var _mainScope: CoroutineScope? = null
    private val mainScope: CoroutineScope
        get() = _mainScope ?: synchronized(this) {
            _mainScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineExceptionHandler).also { _mainScope = it }
        }

    @Inject lateinit var repository: AuraSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository
    @Inject lateinit var historyRepository: INotificationHistoryRepository
    private var lastHistoryCleanupTime = 0L
    private val cooldownReleaseJobs = ConcurrentHashMap<String, Job>()
    private var lastSoundPlayedTimeMs = 0L
    private var lastAutoExpandTimeMs = 0L
    private val mediaControllerCallbacks = ConcurrentHashMap<MediaController, MediaController.Callback>()
    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        serviceScope.launch {
            runCatchingLogged(TAG, "onActiveSessionsChanged failed") {
                updateMediaControllers(controllers.orEmpty())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Settings collector failed") {
                repository.settings.collect { settings ->
                    currentSettings = settings
                    if (settings.enabled && settings.suppressSystemHeadsUp) {
                        com.moksha.auraisland.util.HeadsUpNotificationController.applySuppression(this@AuraNotificationListenerService, true)
                    } else if (!settings.suppressSystemHeadsUp) {
                        com.moksha.auraisland.util.HeadsUpNotificationController.applySuppression(this@AuraNotificationListenerService, false)
                    }
                    if (!settings.enabled || !settings.hideFromNotificationShade) {
                        suppressedKeys.clear()
                    } else {
                        cleanupSuppressedKeys()
                    }
                    if (settings.disabledNotificationPackages.isNotEmpty()) {
                        val currentIslandNotifications = notificationRepository.notifications.value
                        currentIslandNotifications
                            .filter { it.packageName in settings.disabledNotificationPackages }
                            .forEach { notificationRepository.removeNotification(it.key) }
                    }
                }
            }
        }
        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Command collector failed") {
                notificationRepository.commands.collect { command ->
                    runCatchingLogged(TAG, "Notification command failed") {
                        when (command) {
                            is AuraCommand.CancelNotification -> {
                                forceCancelNotification(command.key)
                            }
                            is AuraCommand.SeekTo -> {
                                bestControllerFor(command.packageName)
                                    ?.transportControls
                                    ?.seekTo(command.positionMs)
                            }
                            is AuraCommand.SkipNext -> {
                                val controller = command.packageName?.let { bestControllerFor(it) }
                                    ?: activeMediaControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                                    ?: activeMediaControllers.firstOrNull()
                                controller?.transportControls?.skipToNext()
                            }
                            is AuraCommand.SkipPrevious -> {
                                val controller = command.packageName?.let { bestControllerFor(it) }
                                    ?: activeMediaControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                                    ?: activeMediaControllers.firstOrNull()
                                controller?.transportControls?.skipToPrevious()
                            }
                            is AuraCommand.PlayPause -> {
                                val controller = command.packageName?.let { bestControllerFor(it) }
                                    ?: activeMediaControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                                    ?: activeMediaControllers.firstOrNull()
                                if (controller != null) {
                                    val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                                    if (isPlaying) {
                                        controller.transportControls.pause()
                                    } else {
                                        controller.transportControls.play()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isSystemConnected = false
        lastSoundPlayedTimeMs = 0L
        lastAutoExpandTimeMs = 0L
        cooldownReleaseJobs.values.forEach { it.cancel() }
        cooldownReleaseJobs.clear()
        NotificationCooldownManager.clear()
        pendingRemovals.values.forEach { it.cancel() }
        pendingRemovals.clear()
        pendingSuppressionJobs.values.forEach { it.cancel() }
        pendingSuppressionJobs.clear()
        suppressedKeys.clear()
        iconCache.evictAll()
        runCatchingLogged(TAG, "Unregister active sessions listener failed in onDestroy") {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            mediaControllerCallbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
            mediaControllerCallbacks.clear()
        }
        serviceScope.cancel()
        _mainScope?.cancel()
        super.onDestroy()
    }

    private val pendingRemovals = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val pendingSuppressionJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        runCatchingLogged(TAG, "onNotificationPosted callback failed") {
        if (sbn.packageName == packageName) {
            if (sbn.id == 8105) {
                runCatching { cancelNotification(sbn.key) }
            }
            return@runCatchingLogged
        }

        val notification = sbn.notification

        // ── Group summary handling ────────────────────────────────────────────────────────────
        // Apps like WhatsApp post two kinds of notifications per conversation:
        //   1. Child notifications  (individual messages) — these are canceled via the block below
        //   2. A group SUMMARY notification (FLAG_GROUP_SUMMARY) — this is what causes WhatsApp to
        //      still appear in the system shade even after all children have been canceled.
        //
        // `shouldSuppressFromIsland` correctly returns true for group summaries (so they're never
        // added to the island), but that also prevents the cancellation block below from running,
        // leaving the summary untouched in the system shade.
        //
        // Fix: intercept group summaries for third-party apps and cancel them from the system shade
        // immediately, BEFORE falling through to the normal island-or-ignore logic.
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            if (currentSettings.enabled &&
                currentSettings.hideFromNotificationShade &&
                sbn.packageName !in currentSettings.disabledNotificationPackages &&
                NotificationFilter.isThirdPartyApp(
                    sbn.packageName,
                    packageManager
                )
            ) {
                Log.d(TAG, "Cancelling group summary from system shade: ${sbn.key} pkg=${sbn.packageName}")
                suppressSystemNotification(sbn.key) // adds to suppressedKeys + cancels with retry
            }
            // Group summaries are never added to the island — stop processing here.
            pendingRemovals.remove(sbn.key)?.cancel()
            return@runCatchingLogged
        }

        // ── Regular notification: suppress from system shade if it belongs in the island ──────
        // We do this synchronously — before the coroutine is even scheduled — so the notification
        // never appears in the system shade. cancelNotification() is used exclusively;
        // snoozeNotification() is deliberately avoided because it moves to a "snoozed" shade
        // section instead of removing the notification entirely.
        try {
            if (currentSettings.enabled && !shouldSuppressFromIsland(sbn, rankingMap)) {
                if (currentSettings.hideFromNotificationShade) {
                    val modeQuick = notification.toIslandMode(
                        sbn,
                        currentSettings.liveActivitiesEnabled,
                        currentSettings.navigationEnabled,
                        currentSettings.deviceType
                    )
                    val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(currentSettings.deviceType)
                    val shouldYield = OemDeviceRules.shouldYieldToNativeIsland(
                        mode = modeQuick,
                        packageName = sbn.packageName,
                        device = effectiveDevice,
                        yieldEnabled = currentSettings.yieldToSystemIsland,
                        handlingMode = currentSettings.oemIslandHandlingMode,
                        yieldMusic = currentSettings.yieldMusicToSystemIsland
                    )
                    if (shouldYield) {
                        Log.d(TAG, "Yielding immediate suppress for $modeQuick (pkg=${sbn.packageName}) to native system island ($effectiveDevice)")
                        return@runCatchingLogged
                    }
                    if (shouldBeIslandOnly(notification, modeQuick)) {
                        markSuppressed(sbn.key)
                        runCatchingLogged(TAG, "Immediate cancel failed") { cancelNotification(sbn.key) }
                        Log.d(TAG, "Immediate island-only suppress: ${sbn.key}")
                    }
                } else {
                    // Keep in Android notification section (shade/drawer),
                    // but suppress native heads-up popup on screen so Aura Island displays the expanded view!
                    runCatchingLogged(TAG, "Immediate setNotificationsShown failed") {
                        setNotificationsShown(arrayOf(sbn.key))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Immediate suppress exception", e)
        }

        // Cancel any pending removal job for this key to keep island copy
        pendingRemovals.remove(sbn.key)?.cancel()

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "NotificationPosted async failed") {
                if (shouldSuppressFromIsland(sbn, rankingMap)) return@runSuspendCatchingLogged

                val settings = currentSettings
                if (!settings.enabled) return@runSuspendCatchingLogged

                Log.d(TAG, "Processing island-only async: key=${sbn.key}")
                handleNotificationPosted(sbn, settings)
            }
        }
        }
    }

    /**
     * Use the reason-aware overload so we can distinguish between:
     *  - REASON_LISTENER_CANCEL: we suppressed it ourselves → keep island copy, keep suppressedKeys
     *  - Any other reason (user dismissed, app canceled, etc.) → remove from island and suppressedKeys
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        runCatchingLogged(TAG, "onNotificationRemoved callback failed") {
            Log.d(TAG, "onNotificationRemoved: key=${sbn.key} pkg=${sbn.packageName} reason=$reason")

            pendingRemovals.remove(sbn.key)?.cancel()

            if (sbn.packageName == packageName) return@runCatchingLogged

            if (OemDeviceRules.isNativeSystemIslandPackage(sbn.packageName)) {
                Log.d(TAG, "Native system island notification removed: pkg=${sbn.packageName}")
                notificationRepository.setNativeIslandActive(false)
                return@runCatchingLogged
            }

            val now = SystemClock.elapsedRealtime()
            val lastSuppressedTime = suppressedKeys[sbn.key] ?: 0L
            val isRecentInitialSuppression = (now - lastSuppressedTime) < INITIAL_SUPPRESSION_WINDOW_MS

            if (reason == REASON_LISTENER_CANCEL && isRecentInitialSuppression) {
                // Aura just suppressed this notification from system shade < 1.5s ago.
                // Keep the island copy alive during initial suppression.
                Log.d(TAG, "Recent listener-cancel (<1.5s), keeping island: ${sbn.key}")
                return@runCatchingLogged
            }

            // Removed by posting app, user, framework timeout, or OEM dynamic island:
            // Clean up immediately so Aura Island pops up without delay!
            Log.d(TAG, "Genuinely removed, cleaning up immediately: ${sbn.key}")
            clearSuppressed(sbn.key)
            notificationRepository.removeNotification(sbn.key)
        }
    }

    // Keep the no-arg override as a fallback (some OEMs may only call this one).
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatchingLogged(TAG, "onNotificationRemoved fallback callback failed") {
            Log.d(TAG, "onNotificationRemoved (no reason): key=${sbn.key} pkg=${sbn.packageName}")

            pendingRemovals.remove(sbn.key)?.cancel()

            if (sbn.packageName == packageName) return@runCatchingLogged

            if (OemDeviceRules.isNativeSystemIslandPackage(sbn.packageName)) {
                Log.d(TAG, "Native system island notification removed (fallback): pkg=${sbn.packageName}")
                notificationRepository.setNativeIslandActive(false)
                return@runCatchingLogged
            }

            val now = SystemClock.elapsedRealtime()
            val lastSuppressedTime = suppressedKeys[sbn.key] ?: 0L
            val isRecentInitialSuppression = (now - lastSuppressedTime) < INITIAL_SUPPRESSION_WINDOW_MS

            if (isRecentInitialSuppression) {
                Log.d(TAG, "Suppressed key recently (<1.5s), keeping island: ${sbn.key}")
                return@runCatchingLogged
            }

            Log.d(TAG, "Removing from island repo immediately: ${sbn.key}")
            clearSuppressed(sbn.key)
            notificationRepository.removeNotification(sbn.key)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isSystemConnected = true
        runCatchingLogged(TAG, "onListenerConnected callback failed") {
            Log.d(TAG, "onListenerConnected")
            val componentName = ComponentName(this, AuraNotificationListenerService::class.java)
            runCatchingLogged(TAG, "Register active sessions listener failed") {
                mediaSessionManager?.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    componentName,
                    Handler(Looper.getMainLooper())
                )
                updateMediaControllers(activeMediaControllers)
            }
            serviceScope.launch {
                runSuspendCatchingLogged(TAG, "ListenerConnected failed") {
                    val settings = repository.settings.first()
                    currentSettings = settings
                    if (!settings.enabled) return@runSuspendCatchingLogged

                    val overlayReady = ensureOverlayServiceRunning()
                    runCatching {
                        activeNotifications?.filter { it.packageName == packageName && it.id == 8105 }
                            ?.forEach { cancelNotification(it.key) }
                    }
                    val active = runCatchingLogged(TAG, "Failed to get active notifications") {
                        activeNotifications?.toList()
                    }?.filter { it.packageName != packageName }
                        ?.filterNot { shouldSuppressFromIsland(it) }
                        .orEmpty()

                    Log.d(TAG, "ListenerConnected: ${active.size} active, overlayReady=$overlayReady")

                    active.forEach { sbn ->
                        val mode = sbn.notification.toIslandMode(
                            sbn,
                            settings.liveActivitiesEnabled,
                            settings.navigationEnabled,
                            settings.deviceType
                        )
                        // On initial listener connect or rebind, only restore persistent ongoing activities.
                        // Stale standard notifications from the notification shade are not imported to avoid sudden floods.
                        if (!isPersistentIslandMode(mode)) {
                            return@forEach
                        }
                        val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(settings.deviceType)
                        val isYielded = OemDeviceRules.shouldYieldToNativeIsland(
                            mode = mode,
                            packageName = sbn.packageName,
                            device = effectiveDevice,
                            yieldEnabled = settings.yieldToSystemIsland,
                            handlingMode = settings.oemIslandHandlingMode,
                            yieldMusic = settings.yieldMusicToSystemIsland
                        )
                        if (!isYielded && settings.hideFromNotificationShade &&
                            shouldBeIslandOnly(sbn.notification, mode)
                        ) {
                            suppressSystemNotification(sbn.key)
                        }
                        handleNotificationPosted(sbn, settings, isInitialSync = true)
                    }
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        isSystemConnected = false
        super.onListenerDisconnected()
        runCatchingLogged(TAG, "Unregister active sessions listener failed") {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            mediaControllerCallbacks.forEach { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
            mediaControllerCallbacks.clear()
        }
        runCatchingLogged(TAG, "Notification-listener self-rebind failed") {
            requestRebind(
                ComponentName(
                    this,
                    AuraNotificationListenerService::class.java
                )
            )
        }
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        super.onInterruptionFilterChanged(interruptionFilter)
        runCatchingLogged(TAG, "onInterruptionFilterChanged callback failed") {
            val isDndActive = interruptionFilter != INTERRUPTION_FILTER_ALL &&
                interruptionFilter != INTERRUPTION_FILTER_UNKNOWN
            Log.d(TAG, "onInterruptionFilterChanged: filter=$interruptionFilter, isDndActive=$isDndActive")
            if (isDndActive) {
                purgeDndBlockedNotifications()
            }
        }
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        super.onNotificationRankingUpdate(rankingMap)
        runCatchingLogged(TAG, "onNotificationRankingUpdate callback failed") {
            val filter = runCatchingLogged(TAG, "Failed to get filter in ranking update") {
                currentInterruptionFilter
            }?.takeIf { it != INTERRUPTION_FILTER_UNKNOWN }
                ?: runCatchingLogged(TAG, "Failed to get filter from NotificationManager") {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.currentInterruptionFilter
                } ?: INTERRUPTION_FILTER_ALL

            val isDndActive = filter != INTERRUPTION_FILTER_ALL && filter != INTERRUPTION_FILTER_UNKNOWN
            if (isDndActive) {
                purgeDndBlockedNotifications(rankingMap)
            }
        }
    }

    private fun purgeDndBlockedNotifications(rankingMap: RankingMap? = null) {
        serviceScope.launch {
            val activeMap = runCatchingLogged(TAG, "Failed to get active notifications for DND purge") {
                activeNotifications?.associateBy { it.key }
            }.orEmpty()

            val currentIsland = notificationRepository.notifications.value
            currentIsland.forEach { islandNotif ->
                val sbn = activeMap[islandNotif.key]
                if (sbn != null) {
                    if (isBlockedByDoNotDisturb(sbn, rankingMap)) {
                        Log.d(TAG, "DND active: purging blocked notification ${islandNotif.key}")
                        notificationRepository.removeNotification(islandNotif.key)
                    }
                } else if (islandNotif.mode == IslandMode.Notification) {
                    Log.d(TAG, "DND active: purging untracked notification ${islandNotif.key}")
                    notificationRepository.removeNotification(islandNotif.key)
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return PermissionUtils.isAccessibilityServiceEnabled(this)
    }

    private fun ensureOverlayServiceRunning(): Boolean {
        return isAccessibilityServiceEnabled() || Settings.canDrawOverlays(this)
    }

    private fun isIncomingCall(notification: Notification): Boolean {
        return notification.actions?.any { action ->
            val label = action.title?.toString()?.lowercase().orEmpty()
            label.contains("answer") || label.contains("accept") || label.contains("take")
        } == true
    }

    private fun handleNotificationPosted(
        sbn: StatusBarNotification,
        settings: AuraSettings,
        isInitialSync: Boolean = false
    ) {
        if (sbn.packageName == packageName) return
        if (OemDeviceRules.isNativeSystemIslandPackage(sbn.packageName)) {
            Log.d(TAG, "Native system island notification posted: pkg=${sbn.packageName}")
            notificationRepository.setNativeIslandActive(true)
            return
        }
        val notification = sbn.notification
        if (shouldSuppressFromIsland(sbn)) return

        val extras = notification.extras
        val mode = notification.toIslandMode(
            sbn,
            settings.liveActivitiesEnabled,
            settings.navigationEnabled,
            settings.deviceType
        )
        Log.d(TAG, "handleNotificationPosted: mode=$mode key=${sbn.key} title=${extras.getCharSequence(Notification.EXTRA_TITLE)}")

        // OEM Dynamic Island Coexistence:
        // Check if this event yields to native island (OnePlus Fluid Cloud, Vivo Origin Island, HyperOS capsule, etc.)
        val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(settings.deviceType)
        val isYielded = OemDeviceRules.shouldYieldToNativeIsland(
            mode = mode,
            packageName = sbn.packageName,
            device = effectiveDevice,
            yieldEnabled = settings.yieldToSystemIsland,
            handlingMode = settings.oemIslandHandlingMode,
            yieldMusic = settings.yieldMusicToSystemIsland
        )

        // Check intelligent anti-spam notification cooldown
        if (!isYielded && NotificationCooldownManager.shouldThrottle(sbn, settings, mode)) {
            Log.d(TAG, "Notification throttled by cooldown: pkg=${sbn.packageName} key=${sbn.key}")
            scheduleCooldownRelease(sbn.packageName, settings.notificationCooldownDurationMinutes)
            return
        }

        val shouldIslandOnly = !isYielded && shouldBeIslandOnly(notification, mode)

        if (settings.hideFromNotificationShade && shouldIslandOnly) {
            // Ensure the notification is removed from the system shade when hideFromNotificationShade is enabled
            suppressSystemNotification(sbn.key)
        } else if (!isYielded) {
            // Dismiss heads-up peeking pop-up on screen while retaining notification in notification shade
            runCatchingLogged(TAG, "Dismiss heads-up peeking") {
                setNotificationsShown(arrayOf(sbn.key))
            }
        }

        val mediaInfo = if (mode == IslandMode.Music) findMediaInfo(notification, sbn.packageName) else null
        val appName = runCatchingLogged(TAG, "GetApplicationInfo failed") {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } ?: sbn.packageName

        val isNewNotif = notificationRepository.notifications.value.none { it.key == sbn.key }

        val existingNotif = notificationRepository.notifications.value.find { it.key == sbn.key || (it.mode == IslandMode.IncomingCall && it.packageName == sbn.packageName) }
        val actions = notification.actions?.mapNotNull { action ->
            action.title?.toString()?.let { title ->
                val remoteInput = action.remoteInputs?.firstOrNull()
                val isReply = remoteInput != null || title.lowercase().contains("reply")
                IslandNotificationAction(
                    title = title,
                    pendingIntent = action.actionIntent,
                    isQuickReply = isReply,
                    remoteInputKey = remoteInput?.resultKey ?: "key_text_reply"
                )
            }
        }.orEmpty()
        val isNowRinging = actions.any { it.title.lowercase().let { t -> t.contains("answer") || t.contains("accept") || t.contains("take") } }

        val computedTimeMillis = when {
            mode == IslandMode.IncomingCall && existingNotif != null && existingNotif.isCallRinging && !isNowRinging -> {
                System.currentTimeMillis()
            }
            existingNotif != null && mode == IslandMode.IncomingCall && !isNowRinging && existingNotif.timeMillis > 0 -> {
                existingNotif.timeMillis
            }
            mode == IslandMode.Timer -> {
                val remSec = TimerStopwatchParser.parseTimerRemainingSeconds(notification)
                if (remSec != null && remSec > 0) {
                    System.currentTimeMillis() + remSec * 1000L
                } else if (notification.`when` > System.currentTimeMillis()) {
                    notification.`when`
                } else if (existingNotif != null && existingNotif.mode == IslandMode.Timer && existingNotif.timeMillis > System.currentTimeMillis()) {
                    existingNotif.timeMillis
                } else {
                    System.currentTimeMillis()
                }
            }
            mode == IslandMode.Stopwatch -> {
                val elSec = TimerStopwatchParser.parseStopwatchElapsedSeconds(notification)
                if (elSec != null && elSec > 0) {
                    System.currentTimeMillis() - elSec * 1000L
                } else if (existingNotif != null && existingNotif.mode == IslandMode.Stopwatch && existingNotif.timeMillis > 0) {
                    existingNotif.timeMillis
                } else if (notification.`when` in 1..System.currentTimeMillis()) {
                    notification.`when`
                } else {
                    System.currentTimeMillis()
                }
            }
            notification.`when` != 0L -> notification.`when`
            else -> sbn.postTime
        }

        val notifTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: (if (mode == IslandMode.Stopwatch) "Stopwatch" else if (mode == IslandMode.Timer) "Timer" else "")
        val notifText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
            ?: notification.tickerText?.toString()
            ?: (if (mode == IslandMode.Stopwatch) "Running" else if (mode == IslandMode.Timer) "Running" else "")

        notificationRepository.postNotification(
            IslandNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = notifTitle,
                text = notifText,
                timeMillis = computedTimeMillis,
                icon = loadAppIconBitmap(sbn.packageName),
                largeIcon = mediaInfo?.artwork ?: notification.loadLargeIconBitmap(),
                actionIntents = actions,
                category = notification.category,
                progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
                progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
                mediaPositionMs = mediaInfo?.positionMs,
                mediaDurationMs = mediaInfo?.durationMs,
                mediaIsPlaying = mediaInfo?.isPlaying == true,
                mediaToken = runCatchingLogged(TAG, "GetMediaToken failed") {
                    val ex = notification.extras
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ex.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        ex.getParcelable(Notification.EXTRA_MEDIA_SESSION)
                    }
                },
                mode = mode,
                contentIntent = notification.contentIntent
            ),
            autoExpand = if (!isInitialSync && settings.autoExpandOnNotification && mode != IslandMode.Music) {
                val hasNewContent = existingNotif == null || existingNotif.text != notifText || existingNotif.title != notifTitle
                val now = SystemClock.elapsedRealtime()
                if ((isNewNotif || hasNewContent) && (now - lastAutoExpandTimeMs >= 400L)) {
                    lastAutoExpandTimeMs = now
                    true
                } else if (now - lastAutoExpandTimeMs >= AUTO_EXPAND_DEBOUNCE_MS) {
                    lastAutoExpandTimeMs = now
                    true
                } else {
                    false
                }
            } else {
                false
            }
        )

        if (settings.enableNotificationHistory && mode != IslandMode.Music) {
            serviceScope.launch {
                runSuspendCatchingLogged(TAG, "Failed to record notification history") {
                    historyRepository.saveEntry(
                        NotificationHistoryEntry(
                            notificationKey = sbn.key,
                            packageName = sbn.packageName,
                            appName = appName,
                            title = notifTitle,
                            text = notifText,
                            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                            postTimeMillis = computedTimeMillis,
                            category = notification.category,
                            channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else null,
                            mode = mode.name,
                            actionTitles = actions.map { it.title }
                        )
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastHistoryCleanupTime > 15 * 60 * 1000L) {
                        lastHistoryCleanupTime = now
                        historyRepository.cleanupOldEntries(settings.notificationHistoryRetentionHours)
                    }
                }
            }
        }

        if (!isInitialSync && isNewNotif && (mode == IslandMode.Notification || shouldIslandOnly) && mode != IslandMode.DownloadUpload) {
            playNotificationSound(sbn)
        }

        if (mode == IslandMode.Notification) {
            val existing = notificationRepository.notifications.value
            existing.filter { it.mode == IslandMode.Notification && it.packageName == sbn.packageName && it.key != sbn.key }
                .forEach { notificationRepository.removeNotification(it.key) }
        }

        if (mode == IslandMode.Music) {
            val existing = notificationRepository.notifications.value
            existing.filter { it.packageName == sbn.packageName && it.key != sbn.key }
                .forEach { notificationRepository.removeNotification(it.key) }
        }

        if (mode == IslandMode.IncomingCall) {
            val isEnded = notification.isCallEnded()
            if (isEnded) {
                pendingRemovals.remove(sbn.key)?.cancel()
                clearSuppressed(sbn.key)
                notificationRepository.removeNotification(sbn.key)
            }
        }

        if (mode == IslandMode.DownloadUpload) {
            val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            val isDone = notification.isDownloadComplete() || progressMax in 1..progress
            if (isDone) {
                pendingRemovals.remove(sbn.key)?.cancel()
                val job = serviceScope.launch {
                    delay(3500L)
                    clearSuppressed(sbn.key)
                    notificationRepository.removeNotification(sbn.key)
                }
                pendingRemovals[sbn.key] = job
            }
        }

        if (mode == IslandMode.ScreenRecording) {
            val isDone = notification.isScreenRecordingComplete()
            if (isDone) {
                pendingRemovals.remove(sbn.key)?.cancel()
                val job = serviceScope.launch {
                    delay(3500L)
                    clearSuppressed(sbn.key)
                    notificationRepository.removeNotification(sbn.key)
                }
                pendingRemovals[sbn.key] = job
            }
        }

        if (mode == IslandMode.Timer) {
            val isDone = TimerStopwatchParser.isTimerFinished(notification)
            if (isDone) {
                pendingRemovals.remove(sbn.key)?.cancel()
                val job = serviceScope.launch {
                    delay(5000L)
                    clearSuppressed(sbn.key)
                    notificationRepository.removeNotification(sbn.key)
                }
                pendingRemovals[sbn.key] = job
            }
        }
    }

    internal fun isBlockedByDoNotDisturb(
        sbn: StatusBarNotification,
        rankingMap: RankingMap? = null,
        filterOverride: Int? = null
    ): Boolean {
        val filter = filterOverride ?: runCatchingLogged(TAG, "Failed to get interruption filter") {
            currentInterruptionFilter
        }?.takeIf { it != INTERRUPTION_FILTER_UNKNOWN }
            ?: runCatchingLogged(TAG, "Failed to get interruption filter from NotificationManager") {
                (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.currentInterruptionFilter
            } ?: INTERRUPTION_FILTER_ALL

        // If filter is ALL or UNKNOWN, Do Not Disturb is OFF
        if (filter == INTERRUPTION_FILTER_ALL || filter == INTERRUPTION_FILTER_UNKNOWN) {
            return false
        }

        val notification = sbn.notification ?: return true
        val mode = notification.toIslandMode(
            sbn,
            currentSettings.liveActivitiesEnabled,
            currentSettings.navigationEnabled,
            currentSettings.deviceType
        )

        // Music playback and ongoing system hardware states (Hotspot, ScreenRecording)
        // are not intrusive notification alerts and must not be blocked by DND.
        if (mode == IslandMode.Music || mode == IslandMode.Hotspot || mode == IslandMode.ScreenRecording) {
            return false
        }

        val rMap = rankingMap ?: runCatchingLogged(TAG, "Failed to get current ranking") { currentRanking }
        if (rMap != null) {
            val ranking = Ranking()
            if (rMap.getRanking(sbn.key, ranking)) {
                // matchesInterruptionFilter() returns true if allowed by DND policy, false if blocked
                return !ranking.matchesInterruptionFilter()
            }
        }

        if (filter == INTERRUPTION_FILTER_NONE) {
            return true
        }

        if (filter == INTERRUPTION_FILTER_ALARMS) {
            return notification.category != Notification.CATEGORY_ALARM &&
                mode != IslandMode.Timer &&
                mode != IslandMode.Stopwatch
        }

        // Priority only mode: standard notifications are blocked by default if not verified
        return true
    }

    internal fun shouldSuppressFromIsland(
        sbn: StatusBarNotification,
        rankingMap: RankingMap? = null,
        filterOverride: Int? = null
    ): Boolean {
        if (isBlockedByDoNotDisturb(sbn, rankingMap, filterOverride)) {
            Log.d(TAG, "Suppressed by Do Not Disturb: key=${sbn.key} pkg=${sbn.packageName}")
            return true
        }
        val pm = runCatching { packageManager }.getOrNull() ?: return false
        return NotificationFilter.shouldSuppressFromIsland(
            sbn,
            pm,
            currentSettings.liveActivitiesEnabled,
            currentSettings.navigationEnabled,
            currentSettings.disabledNotificationPackages,
            currentSettings.deviceType
        )
    }

    internal fun shouldBeIslandOnly(notification: Notification, mode: IslandMode): Boolean {
        if (mode == IslandMode.IncomingCall) {
            if (!isIncomingCall(notification)) return false // ongoing call stays in system
        }
        if (mode == IslandMode.Music || mode == IslandMode.Navigation || mode == IslandMode.Timer || mode == IslandMode.Stopwatch) {
            return false // Media/Music, Navigation, Timer & Stopwatch notifications must NOT be canceled from system shade by default
        }
        // All others: island-only
        return true
    }

    // Kept for tests
    @Suppress("unused")
    internal fun isHighPriorityNotification(sbn: StatusBarNotification, notification: Notification): Boolean {
        val ranking = Ranking()
        val rankingMap = currentRanking
        val isHigh = rankingMap != null && rankingMap.getRanking(sbn.key, ranking) && ranking.importance >= NotificationManager.IMPORTANCE_HIGH
        return isHigh || notification.fullScreenIntent != null
    }

    /**
     * Suppress a notification from the system shade so it only appears in the island.
     *
     * Uses ONLY cancelNotification() — snoozeNotification() is deliberately avoided
     * because it moves the notification to a "snoozed" section in the system shade
     * rather than removing it, which causes notifications to appear in both the
     * system shade AND the island.
     *
     * Retries up to 3 times with increasing delays for reliability on devices where
     * the first cancel attempt may not take effect immediately.
     */
    private fun suppressSystemNotification(key: String) {
        if (!currentSettings.enabled || !currentSettings.hideFromNotificationShade) return
        val activeSbn = runCatchingLogged(TAG, "Failed to get active notifications for key lookup") {
            activeNotifications.find { it.key == key }
        }
        if (activeSbn != null && activeSbn.packageName in currentSettings.disabledNotificationPackages) return

        val now = SystemClock.elapsedRealtime()
        val lastSuppressedTime = suppressedKeys[key] ?: 0L
        val isRecentlySuppressed = (now - lastSuppressedTime) < 300L

        markSuppressed(key)

        // Cancel any pending suppression retry job for this key
        pendingSuppressionJobs.remove(key)?.cancel()

        // Synchronous attempt for fastest possible suppression (if not throttled)
        if (!isRecentlySuppressed) {
            runCatchingLogged(TAG, "sync cancel failed") { cancelNotification(key) }
        }

        // Asynchronous retry with delays for reliability
        val job = mainScope.launch {
            runSuspendCatchingLogged(TAG, "Notification suppression retries failed") {
                repeat(3) { attempt ->
                    delay(100L * (attempt + 1)) // 100, 200, 300ms
                    if (!currentSettings.enabled ||
                        !currentSettings.hideFromNotificationShade
                    ) {
                        clearSuppressed(key)
                        return@runSuspendCatchingLogged
                    }
                    val stillActive = runCatchingLogged(TAG, "Failed checking stillActive") {
                        activeNotifications?.any { it.key == key }
                    } ?: false
                    if (!stillActive) {
                        Log.d(TAG, "Successfully suppressed after ${attempt + 1} attempts: $key")
                        return@runSuspendCatchingLogged
                    }
                    Log.d(TAG, "Still active after attempt ${attempt + 1}, retrying: $key")
                    runCatchingLogged(TAG, "cancel retry $attempt failed") {
                        cancelNotification(key)
                    }
                }
                Log.w(TAG, "Failed to suppress after retries: $key")
            }
            pendingSuppressionJobs.remove(key)
        }
        pendingSuppressionJobs[key] = job
    }

    private fun forceCancelNotification(key: String) {
        // This is an explicit user action, so the island copy must disappear as well.
        clearSuppressed(key)
        runCatchingLogged(TAG, "forceCancel failed") { cancelNotification(key) }
        notificationRepository.removeNotification(key)
    }

    private fun playNotificationSound(sbn: StatusBarNotification) {
        if (currentSettings.disabledSoundPackages.contains(sbn.packageName)) return
        if (isBlockedByDoNotDisturb(sbn)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSoundPlayedTimeMs < SOUND_DEBOUNCE_MS) return
        lastSoundPlayedTimeMs = now
        runCatchingLogged(TAG, "Failed to play notification sound for ${sbn.packageName}") {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null || audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

            val notification = sbn.notification
            var soundUri: Uri? = null
            var audioAttributes: AudioAttributes? = null

            // 1. Check Notification Channel (Android 8.0+, API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ranking = Ranking()
                val hasRanking = currentRanking?.getRanking(sbn.key, ranking) == true
                val channel = if (hasRanking) ranking.channel else null

                if (channel != null) {
                    // Silent channels (IMPORTANCE_NONE, IMPORTANCE_MIN, IMPORTANCE_LOW) must not play sound
                    if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                        return
                    }
                    val chSound = channel.sound
                    if (chSound == null || chSound == Uri.EMPTY || chSound.toString().isEmpty()) {
                        // Channel is explicitly configured without sound (Silent)
                        return
                    }
                    soundUri = chSound
                    audioAttributes = channel.audioAttributes
                }
            }

            // 2. Fallback to notification payload if channel was not present
            if (soundUri == null) {
                @Suppress("DEPRECATION")
                val notifSound = notification.sound
                @Suppress("DEPRECATION")
                val defaults = notification.defaults

                if (notifSound != null && notifSound != Uri.EMPTY && notifSound.toString().isNotEmpty()) {
                    soundUri = notifSound
                } else if ((defaults and Notification.DEFAULT_SOUND) != 0) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                @Suppress("DEPRECATION")
                audioAttributes = notification.audioAttributes
            }

            if (soundUri == null) {
                return
            }

            // 3. Resolve and play the exact notification sound
            var ringtone: Ringtone? = runCatching {
                RingtoneManager.getRingtone(applicationContext, soundUri)
            }.getOrNull()

            // Fallback to default tone if custom URI failed to load
            if (ringtone == null) {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = runCatching {
                    RingtoneManager.getRingtone(applicationContext, fallbackUri)
                }.getOrNull()
            }

            if (ringtone != null) {
                val attrs = audioAttributes ?: AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                ringtone.audioAttributes = attrs
                ringtone.play()
            }
        }
    }

    private fun markSuppressed(key: String) {
        val now = SystemClock.elapsedRealtime()
        suppressedKeys[key] = now
        cleanupSuppressedKeys(now)
    }

    private fun clearSuppressed(key: String) {
        pendingSuppressionJobs.remove(key)?.cancel()
        suppressedKeys.remove(key)
    }

    private fun cleanupSuppressedKeys(now: Long = SystemClock.elapsedRealtime()) {
        val cutoff = now - SUPPRESSED_KEY_TTL_MS
        suppressedKeys.entries.forEach { entry ->
            if (entry.value < cutoff) {
                suppressedKeys.remove(entry.key, entry.value)
            }
        }
        val overflow = suppressedKeys.size - MAX_SUPPRESSED_KEYS
        if (overflow > 0) {
            suppressedKeys.entries
                .sortedBy { it.value }
                .take(overflow)
                .forEach { suppressedKeys.remove(it.key, it.value) }
        }
    }

    private val iconCache = LruCache<String, Bitmap>(50)

    private fun loadAppIconBitmap(packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatchingLogged(TAG, "LoadAppIconBitmap failed") {
            val drawable = packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = ICON_BITMAP_SIZE, height = ICON_BITMAP_SIZE).also { iconCache.put(packageName, it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun Notification.loadLargeIconBitmap(): Bitmap? {
        val extraLarge = extras.get(Notification.EXTRA_LARGE_ICON)
        extraLarge.toBitmapOrNull()?.let { return it }
        val extraLargeBig = extras.get(Notification.EXTRA_LARGE_ICON_BIG)
        extraLargeBig.toBitmapOrNull()?.let { return it }
        val largeIconObj = getLargeIcon()
        runCatchingLogged(TAG, "LoadLargeIconBitmap failed") {
            largeIconObj?.loadDrawable(this@AuraNotificationListenerService)
                ?.toBitmap(width = LARGE_ICON_BITMAP_SIZE, height = LARGE_ICON_BITMAP_SIZE)
        }?.let { return it }
        return runCatchingLogged(TAG, "LoadMessagingStyleAvatar failed") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
                } else {
                    extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                }
                if (!messages.isNullOrEmpty()) {
                    val lastMessageBundle = messages.lastOrNull() as? Bundle
                    val senderPerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lastMessageBundle?.getParcelable("sender_person", Person::class.java)
                    } else {
                        lastMessageBundle?.getParcelable("sender_person")
                    }
                    senderPerson?.icon?.loadDrawable(this@AuraNotificationListenerService)
                        ?.toBitmap(width = LARGE_ICON_BITMAP_SIZE, height = LARGE_ICON_BITMAP_SIZE)
                } else null
            } else null
        }
    }

    private fun Any?.toBitmapOrNull(): Bitmap? {
        return when (this) {
            is Bitmap -> this
            is Icon -> runCatchingLogged(TAG, "Icon toBitmapOrNull failed") {
                loadDrawable(this@AuraNotificationListenerService)
                    ?.toBitmap(width = 128, height = 128)
            }
            else -> null
        }
    }

    private fun controllersFor(packageName: String): List<MediaController> =
        activeMediaControllers.filter { it.packageName == packageName }

    private fun bestControllerFor(packageName: String): MediaController? {
        val matches = controllersFor(packageName)
        return matches.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: matches.firstOrNull()
    }

    private fun findMediaInfo(notification: Notification, packageName: String): MediaInfo? {
        notification.mediaSessionController()?.extractMediaInfo()?.let { return it }
        val controller = bestControllerFor(packageName) ?: return null
        return controller.extractMediaInfo()
    }

    private fun Notification.mediaSessionController(): MediaController? {
        val token = runCatchingLogged(TAG, "GetMediaSessionToken failed") {
            val ex = extras
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ex.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                ex.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
        } ?: return null
        return runCatchingLogged(TAG, "MediaController init failed") { MediaController(this@AuraNotificationListenerService, token) }
    }

    private fun MediaController.extractMediaInfo(): MediaInfo {
        val metadata = this.metadata
        val playbackState = this.playbackState
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
        val positionMs = playbackState?.estimatedPosition()
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return MediaInfo(artwork, positionMs, durationMs, playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    private val activeMediaControllers: List<MediaController>
        get() = runCatchingLogged(TAG, "GetActiveSessions failed") {
            val mgr = mediaSessionManager ?: return emptyList()
            val componentName = ComponentName(this, AuraNotificationListenerService::class.java)
            mgr.getActiveSessions(componentName)
        } ?: emptyList()

    private val mediaSessionManager: MediaSessionManager? by lazy {
        runCatching { getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager }.getOrNull()
    }

    private fun PlaybackState.estimatedPosition(): Long? {
        if (position < 0) return null
        if (state != PlaybackState.STATE_PLAYING) return position
        val elapsed = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        return (position + (elapsed * playbackSpeed).toLong()).coerceAtLeast(0L)
    }

    private fun updateMediaControllers(controllers: List<MediaController>) {
        val activeSet = controllers.toSet()
        val iterator = mediaControllerCallbacks.entries.iterator()
        while (iterator.hasNext()) {
            val (controller, callback) = iterator.next()
            if (controller !in activeSet) {
                runCatching { controller.unregisterCallback(callback) }
                iterator.remove()
            }
        }

        for (controller in controllers) {
            if (!mediaControllerCallbacks.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        onMediaPlaybackChanged(controller, state)
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        onMediaMetadataChanged(controller, metadata)
                    }

                    override fun onSessionDestroyed() {
                        mediaControllerCallbacks.remove(controller)
                    }
                }
                runCatchingLogged(TAG, "Failed to register media callback for ${controller.packageName}") {
                    controller.registerCallback(callback)
                    mediaControllerCallbacks[controller] = callback
                }
            }

            val playbackState = controller.playbackState
            if (playbackState?.state == PlaybackState.STATE_PLAYING) {
                onMediaPlaybackChanged(controller, playbackState)
            }
        }
    }

    private fun onMediaPlaybackChanged(controller: MediaController, state: PlaybackState?) {
        if (!currentSettings.enabled) return
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val packageName = controller.packageName
        if (packageName in currentSettings.disabledNotificationPackages) return

        val currentList = notificationRepository.notifications.value
        val existingNotif = currentList.find { it.packageName == packageName && it.mode == IslandMode.Music }

        if (existingNotif != null) {
            val mediaInfo = controller.extractMediaInfo()
            val updated = existingNotif.copy(
                mediaIsPlaying = isPlaying,
                mediaPositionMs = mediaInfo.positionMs ?: existingNotif.mediaPositionMs,
                mediaDurationMs = mediaInfo.durationMs ?: existingNotif.mediaDurationMs,
                largeIcon = mediaInfo.artwork ?: existingNotif.largeIcon,
                title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: existingNotif.title,
                text = (controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)) ?: existingNotif.text
            )
            notificationRepository.postNotification(updated, autoExpand = false)
        } else if (isPlaying) {
            val sbn = runCatching { activeNotifications?.firstOrNull { it.packageName == packageName } }.getOrNull()
            if (sbn != null) {
                handleNotificationPosted(sbn, currentSettings, isInitialSync = false)
            } else {
                val mediaInfo = controller.extractMediaInfo()
                val appName = runCatching {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrNull() ?: packageName

                val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: "Playing"
                val artist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    ?: ""

                val synthetic = IslandNotification(
                    key = "media_$packageName",
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = artist,
                    timeMillis = System.currentTimeMillis(),
                    icon = loadAppIconBitmap(packageName),
                    largeIcon = mediaInfo.artwork,
                    mediaPositionMs = mediaInfo.positionMs,
                    mediaDurationMs = mediaInfo.durationMs,
                    mediaIsPlaying = true,
                    mediaToken = controller.sessionToken,
                    mode = IslandMode.Music,
                    contentIntent = controller.sessionActivity
                )
                notificationRepository.postNotification(synthetic, autoExpand = false)
            }
        }
    }

    private fun onMediaMetadataChanged(controller: MediaController, metadata: MediaMetadata?) {
        if (!currentSettings.enabled) return
        val packageName = controller.packageName
        val currentList = notificationRepository.notifications.value
        val existingNotif = currentList.find { it.packageName == packageName && it.mode == IslandMode.Music } ?: return

        val mediaInfo = controller.extractMediaInfo()
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: existingNotif.title
        val artist = (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)) ?: existingNotif.text

        val updated = existingNotif.copy(
            title = title,
            text = artist,
            largeIcon = mediaInfo.artwork ?: existingNotif.largeIcon,
            mediaPositionMs = mediaInfo.positionMs ?: existingNotif.mediaPositionMs,
            mediaDurationMs = mediaInfo.durationMs ?: existingNotif.mediaDurationMs,
            mediaIsPlaying = mediaInfo.isPlaying
        )
        notificationRepository.postNotification(updated, autoExpand = false)
    }

    private data class MediaInfo(val artwork: Bitmap?, val positionMs: Long?, val durationMs: Long?, val isPlaying: Boolean)

    private fun scheduleCooldownRelease(packageName: String, durationMinutes: Int) {
        if (cooldownReleaseJobs[packageName]?.isActive == true) return
        val job = serviceScope.launch {
            delay(durationMinutes * 60_000L)
            val buffered = NotificationCooldownManager.pollBufferedNotification(packageName)
            if (buffered != null) {
                val current = repository.settings.first()
                if (current.enabled) {
                    Log.d(TAG, "Releasing buffered cooldown notification: pkg=$packageName key=${buffered.key}")
                    handleNotificationPosted(buffered, current)
                }
            }
            cooldownReleaseJobs.remove(packageName)
        }
        cooldownReleaseJobs[packageName] = job
    }

    private fun isPersistentIslandMode(mode: IslandMode): Boolean {
        return mode == IslandMode.Music ||
            mode == IslandMode.IncomingCall ||
            mode == IslandMode.Timer ||
            mode == IslandMode.Stopwatch ||
            mode == IslandMode.Navigation ||
            mode == IslandMode.LiveActivity ||
            mode == IslandMode.DownloadUpload ||
            mode == IslandMode.Hotspot ||
            mode == IslandMode.ScreenRecording
    }

    companion object {
        @Volatile
        var isSystemConnected: Boolean = false
            internal set

        private const val TAG = "AuraNotificationListener"
        private const val ICON_BITMAP_SIZE = 96
        private const val LARGE_ICON_BITMAP_SIZE = 128
        private const val MAX_SUPPRESSED_KEYS = 100
        private const val SUPPRESSED_KEY_TTL_MS = 10 * 60 * 1000L
        private const val INITIAL_SUPPRESSION_WINDOW_MS = 1500L
        private const val SOUND_DEBOUNCE_MS = 1200L
        private const val AUTO_EXPAND_DEBOUNCE_MS = 1500L
    }
}
