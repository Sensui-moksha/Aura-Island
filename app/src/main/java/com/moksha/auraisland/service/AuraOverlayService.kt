/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.moksha.auraisland.MainActivity
import com.moksha.auraisland.R
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.data.AuraCommand
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.util.OemDeviceRules
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.model.IslandNotification
import com.moksha.auraisland.ui.IslandViewModel
import com.moksha.auraisland.ui.OverlayIsland
import com.moksha.auraisland.ui.expanded.sendIntentWithOptions
import com.moksha.auraisland.util.runCatchingLogged
import com.moksha.auraisland.util.runSuspendCatchingLogged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotificationAction

@AndroidEntryPoint
class AuraOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    @Inject lateinit var repository: AuraSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository
    private var islandView: ComposeView? = null
    private val overlayOwners = OverlayViewTreeOwners()
    private lateinit var systemEventReceiver: SystemEventReceiver
    private lateinit var viewModel: IslandViewModel
    private var isLockScreenActive: Boolean = false
    private var systemEventReceiverRegistered = false
    private var screenStateReceiverRegistered = false
    private var torchCallbackRegistered = false
    private var foregroundStarted = false
    private val isTouchableRegionSupported = MutableStateFlow(false)
    private val windowXOffsetDp = MutableStateFlow(0f)
    private val windowYOffsetDp = MutableStateFlow(0f)
    @Volatile private var destroyed = false
    private var isWindowExpanded: Boolean = false
    private var collapseJob: kotlinx.coroutines.Job? = null
    private var nativeIslandDismissJob: kotlinx.coroutines.Job? = null
    private var lastParams: WindowManager.LayoutParams? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (destroyed || !::viewModel.isInitialized) return
            if (enabled) {
                notificationRepository.postNotification(
                    IslandNotification(
                        key = "system_flashlight",
                        packageName = "com.android.systemui",
                        appName = "Flashlight",
                        title = "Flashlight ON",
                        text = "Tap to turn off",
                        mode = IslandMode.Flashlight,
                        timeMillis = System.currentTimeMillis(),
                        actionIntents = listOf(
                            IslandNotificationAction("Turn Off", null)
                        )
                    ),
                    autoExpand = true
                )
            } else {
                notificationRepository.removeNotification("system_flashlight")
            }
        }
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, error ->
                Log.e(TAG, "Unhandled overlay coroutine failure", error)
            }
    )

    // Monitor screen state and unlock events to show/hide the island accordingly
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatchingLogged(TAG, "Screen-state callback failed") {
                if (destroyed || !::viewModel.isInitialized) return@runCatchingLogged
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        overlayOwners.resume()
                        isLockScreenActive = keyguardManager?.isKeyguardLocked == true
                        updateWindowLayoutParams(
                            isWindowExpanded,
                            viewModel.settings.value
                        )
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        overlayOwners.pause()
                        isLockScreenActive = true
                        updateWindowLayoutParams(
                            isWindowExpanded,
                            viewModel.settings.value
                        )
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        isLockScreenActive = false
                        updateWindowLayoutParams(
                            isWindowExpanded,
                            viewModel.settings.value
                        )
                    }
                }
            }
        }
    }

    // Fallback sync: check if keyguard locked state changed on window changes.
    // Wrapped: an uncaught throw here makes Android auto-disable the
    // AccessibilityService, which is exactly the "turns off by itself" symptom.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatchingLogged(TAG, "onAccessibilityEvent failed") {
            if (destroyed || !::viewModel.isInitialized) return@runCatchingLogged
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val locked = keyguardManager?.isKeyguardLocked == true
            if (isLockScreenActive != locked) {
                isLockScreenActive = locked
                updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
            }

            val openedPackage = event?.packageName?.toString().orEmpty()
            val eventClass = event?.className?.toString().orEmpty()

            // OEM SystemUI plugins fire events on notification shade open/close.
            // They are NOT native dynamic island events and must be excluded.
            val isSystemUiPlugin = openedPackage == "com.vivo.systemuiplugin" ||
                openedPackage == "com.miui.systemui.plugin" ||
                openedPackage == "com.android.systemui" ||
                openedPackage.startsWith("com.android.launcher") ||
                openedPackage.startsWith("com.bbk.launcher")

            val isNativeIslandEvent = !openedPackage.startsWith("demo_") &&
                !isSystemUiPlugin && (
                OemDeviceRules.isNativeSystemIslandPackage(openedPackage) ||
                eventClass.contains("OriginIsland", ignoreCase = true) ||
                eventClass.contains("DynamicIsland", ignoreCase = true) ||
                eventClass.contains("AtomicIsland", ignoreCase = true) ||
                eventClass.contains("MiniCapsule", ignoreCase = true) ||
                eventClass.contains("FluidCloud", ignoreCase = true)
            )

            if (isNativeIslandEvent) {
                Log.d(TAG, "Native dynamic island window event detected: pkg=$openedPackage class=$eventClass")
                notificationRepository.setNativeIslandActive(true)
                nativeIslandDismissJob?.cancel()
                nativeIslandDismissJob = serviceScope.launch {
                    kotlinx.coroutines.delay(NATIVE_ISLAND_WINDOW_DEBOUNCE_MS)
                    notificationRepository.setNativeIslandActive(false)
                }
            }

            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (openedPackage.isNotEmpty() &&
                    openedPackage != packageName &&
                    !isSystemUiPlugin &&
                    !isNativeIslandEvent
                ) {
                    viewModel.foregroundPackage.value = openedPackage
                    val hasNonMusicNotifications = notificationRepository.notifications.value.any {
                        it.packageName == openedPackage && it.mode != IslandMode.Music
                    }
                    if (hasNonMusicNotifications) {
                        notificationRepository.removeNotificationsForPackage(openedPackage)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override, no-op
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Wrapped: a throw here would make Android disable the service automatically.
        runCatchingLogged(TAG, "onConfigurationChanged failed") {
            if (destroyed || !::viewModel.isInitialized) return@runCatchingLogged
            updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
        }
    }

    override fun onCreate() {
        super.onCreate()
        destroyed = false

        runCatchingLogged(TAG, "createNotificationChannel failed") {
            createNotificationChannel()
            stopForegroundSafely()
        }

        val resolvedWindowManager = runCatchingLogged(TAG, "WindowManager initialization failed") {
            getSystemService(WindowManager::class.java)
        }
        if (resolvedWindowManager == null) {
            Log.e(TAG, "WindowManager is unavailable; overlay cannot start")
            return
        }
        windowManager = resolvedWindowManager

        val initializedViewModel = runCatchingLogged(TAG, "Overlay ViewModel initialization failed") {
            // Lifecycle must be restored before the service-owned ViewModel is created.
            overlayOwners.resume()
            ViewModelProvider(
                overlayOwners,
                IslandViewModel.provideFactory(repository, notificationRepository)
            )[IslandViewModel::class.java]
        }
        if (initializedViewModel == null) {
            Log.e(TAG, "Overlay ViewModel is unavailable; overlay cannot start")
            return
        }
        viewModel = initializedViewModel
        
        systemEventReceiver = SystemEventReceiver(
            notificationRepository = notificationRepository,
            settingsProvider = { viewModel.settings.value },
            coroutineScope = serviceScope
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        
        // CRASH FIX: Android 13+/14+ requires explicit export flag for system broadcasts
        runCatchingLogged(TAG, "registerReceiver failed") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(systemEventReceiver, filter)
            }
            systemEventReceiverRegistered = true
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isLockScreenActive = keyguardManager?.isKeyguardLocked == true

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatchingLogged(TAG, "registerReceiver screenStateReceiver failed") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, screenFilter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(screenStateReceiver, screenFilter)
            }
            screenStateReceiverRegistered = true
        }

        runCatchingLogged(TAG, "registerTorchCallback failed") {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            torchCallbackRegistered = true
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Settings collector failed") {
                repository.settings.collect { settings ->
                    if (destroyed) return@collect
                    if (!settings.enabled) {
                        stopOverlaySession()
                    } else {
                        startOverlaySession(settings)
                    }
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Expanded-state collector failed") {
                viewModel.expanded.collectLatest { expanded ->
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    collapseJob?.cancel()
                    if (expanded) {
                        isWindowExpanded = true
                        updateWindowLayoutParams(true, viewModel.settings.value)
                    } else {
                        collapseJob = serviceScope.launch {
                            delay(AUTO_COLLAPSE_DELAY_MS.milliseconds)
                            isWindowExpanded = false
                            updateWindowLayoutParams(false, viewModel.settings.value)
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Notifications-state collector failed") {
                viewModel.visibleNotifications.collectLatest {
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Input-active collector failed") {
                viewModel.isInputActive.collectLatest {
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Auto-hidden collector failed") {
                viewModel.isAutoHidden.collectLatest {
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
                }
            }
        }

        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Oem-yielding collector failed") {
                viewModel.isOemYielding.collectLatest { yielding ->
                    if (destroyed || !viewModel.settings.value.enabled) {
                        return@collectLatest
                    }
                    if (yielding) {
                        viewModel.collapse()
                    }
                    updateWindowLayoutParams(isWindowExpanded, viewModel.settings.value)
                }
            }
        }

        // Start the background watchdog that silently re-asserts ENABLED_ACCESSIBILITY_SERVICES
        // via WRITE_SECURE_SETTINGS every 30 s. This is the primary defence against OEM task
        // killers (MIUI, OneUI, ColorOS) that clear the secure setting mid-session.
        runCatchingLogged(TAG, "SystemServiceRecovery watchdog failed to start") {
            com.moksha.auraisland.util.SystemServiceRecovery.ensureWatchdogRunning(this)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isSystemConnected = true
        if (destroyed || !::viewModel.isInitialized) return
        serviceScope.launch {
            runSuspendCatchingLogged(TAG, "Service reconnect failed") {
                val settings = repository.settings.first()
                if (settings.enabled) {
                    startOverlaySession(settings)
                } else {
                    stopOverlaySession()
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isSystemConnected = false
        // Immediately attempt recovery: if WRITE_SECURE_SETTINGS is held, this silently
        // re-enables the accessibility service without prompting the user, defeating
        // aggressive OEM task killers (Xiaomi, Vivo, Samsung, OPPO, etc.)
        runCatchingLogged(TAG, "onUnbind recovery trigger failed") {
            com.moksha.auraisland.util.SystemServiceRecovery.requestRecovery(applicationContext)
        }
        // Return true so Android system knows to re-bind the accessibility service automatically
        return true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runCatchingLogged(TAG, "onTaskRemoved recovery failed") {
            if (!destroyed &&
                ::viewModel.isInitialized &&
                viewModel.settings.value.enabled
            ) {
                ensureForegroundStarted()
                ensureCollapsedWindow()
            }
        }
    }

    override fun onDestroy() {
        if (destroyed) return
        destroyed = true
        isSystemConnected = false
        serviceScope.cancel()

        if (::systemEventReceiver.isInitialized && systemEventReceiverRegistered) {
            runCatchingLogged(TAG, "unregisterReceiver failed") {
                unregisterReceiver(systemEventReceiver)
            }
            systemEventReceiverRegistered = false
        }
        if (screenStateReceiverRegistered) {
            runCatchingLogged(TAG, "unregisterReceiver screenStateReceiver failed") {
                unregisterReceiver(screenStateReceiver)
            }
            screenStateReceiverRegistered = false
        }
        if (torchCallbackRegistered) {
            runCatchingLogged(TAG, "unregisterTorchCallback failed") {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                cameraManager?.unregisterTorchCallback(torchCallback)
            }
            torchCallbackRegistered = false
        }

        removeCollapsedWindow()
        stopForegroundSafely()
        runCatchingLogged(TAG, "Overlay owners destroy failed") {
            overlayOwners.destroy()
        }
        super.onDestroy()
    }

    private fun startOverlaySession(settings: AuraSettings) {
        if (destroyed || !::windowManager.isInitialized || !::viewModel.isInitialized) return
        ensureForegroundStarted()
        ensureCollapsedWindow()
        updateWindowLayoutParams(isWindowExpanded, settings)
    }

    private fun stopOverlaySession() {
        removeCollapsedWindow()
        stopForegroundSafely()
        if (::viewModel.isInitialized) {
            viewModel.collapse()
        }
    }

    private var hasDismissedForegroundNotification = false

    private fun ensureForegroundStarted() {
        if (!hasDismissedForegroundNotification) {
            stopForegroundSafely()
            hasDismissedForegroundNotification = true
        }
    }

    private fun stopForegroundSafely() {
        runCatchingLogged(TAG, "stopForeground failed") {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val cancelNotification = NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_aura)
                .setOngoing(false)
                .build()
            nm?.notify(NOTIFICATION_ID, cancelNotification)
            nm?.cancel(NOTIFICATION_ID)
        }
        foregroundStarted = false
    }

    private val statusBarHeight: Float
        get() {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val heightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
            val heightDp = heightPx / resources.displayMetrics.density
            return if (heightDp > 0f) heightDp else 24f
        }

    private fun ensureCollapsedWindow() {
        if (destroyed ||
            islandView != null ||
            !::windowManager.isInitialized ||
            !::viewModel.isInitialized
        ) return
        try {
            islandView = ComposeView(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                fitsSystemWindows = false
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = keyguardManager?.isKeyguardLocked == true
                isLockScreenActive = isLocked
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val visibleList = viewModel.visibleNotifications.value
                val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(viewModel.settings.value.deviceType)
                val hasNativeIsland = OemDeviceRules.hasNativeSystemIsland(effectiveDevice)
                val isAutoHidden = viewModel.isAutoHidden.value
                val isOemYielding = viewModel.isOemYielding.value
                val isIdleHidden = isOemYielding || isAutoHidden || (viewModel.settings.value.hideWhenIdle && visibleList.isEmpty() && !viewModel.settings.value.enableAppShortcuts && !viewModel.settings.value.enableCutoutRingMode)
                val isHidden = (!viewModel.settings.value.showOnLockScreen && isLocked) || (isLandscape && !viewModel.settings.value.showInLandscape) || isIdleHidden
                visibility = if (isHidden) View.GONE else View.VISIBLE

                installOverlayViewTreeOwners()
                isFocusable = true
                isFocusableInTouchMode = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val fullWidth by isTouchableRegionSupported.collectAsState()
                    val winX by windowXOffsetDp.collectAsState()
                    val winY by windowYOffsetDp.collectAsState()
                    OverlayIsland(
                        viewModel = this@AuraOverlayService.viewModel,
                        statusBarHeight = statusBarHeight,
                        onOpenNotification = { notification -> openNotification(notification) },
                        onLaunchApp = { packageName -> launchApp(packageName) },
                        onOpenFloatingWindow = { openCurrentNotificationInFloatingWindow() },
                        onOpenNotificationShade = {
                            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                            viewModel.collapse()
                        },
                        onConfigureShortcuts = { openShortcutPicker() },
                        isFullWidth = fullWidth,
                        windowXOffsetDp = winX,
                        windowYOffsetDp = winY
                    )
                }

                setupTouchableRegion(this)
            }
            runCatchingLogged(TAG, "windowManager.addView failed") {
                windowManager.addView(islandView, collapsedParams(viewModel.settings.value))
            } ?: run {
                islandView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureCollapsedWindow fatal", e)
            islandView = null
        }
    }

    // Use reflection to set up OnComputeInternalInsetsListener since it is a hidden system API.
    // This allows the overlay window to pass through touches outside the pill boundary.
    // Keep the suppression local: this best-effort workaround is guarded by
    // runCatchingLogged so unsupported devices fall back without crashing.
    @SuppressLint("PrivateApi", "SoonBlockedPrivateApi")
    private fun setupTouchableRegion(view: ComposeView) {
        Log.d(TAG, "setupTouchableRegion: starting registration for view=$view")
        runCatchingLogged(TAG, "Failed to setup touchable region") {
            val listenerClass = Class.forName("android.view.ViewTreeObserver" + '$' + "OnComputeInternalInsetsListener")
            val insetsClass = Class.forName("android.view.ViewTreeObserver" + '$' + "InternalInsetsInfo")
            
            val setTouchableInsetsMethod = insetsClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = insetsClass.getDeclaredField("touchableRegion").apply {
                isAccessible = true
            }
            
            // InternalInsetsInfo touchable insets options
            val touchableInsetsFrame = 0
            val touchableInsetsRegion = 3
            
            // Create a dynamic proxy implementation of OnComputeInternalInsetsListener
            val proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val insets = args[0]
                    val isExpanded = viewModel.expanded.value
                    val isGone = view.visibility == View.GONE
                    val settingsVal = viewModel.settings.value
                    val visibleList = viewModel.visibleNotifications.value
                    val notificationsCount = visibleList.size
                    val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(settingsVal.deviceType)
                    val hasNativeIsland = OemDeviceRules.hasNativeSystemIsland(effectiveDevice)
                    val isAutoHidden = viewModel.isAutoHidden.value
                    val isOemYielding = viewModel.isOemYielding.value
                    val isIdleHidden = isOemYielding || isAutoHidden || (settingsVal.hideWhenIdle && notificationsCount == 0 && !settingsVal.enableAppShortcuts && !settingsVal.enableCutoutRingMode)
                    Log.d(TAG, "onComputeInternalInsets callback: isExpanded=$isExpanded isGone=$isGone isIdleHidden=$isIdleHidden isOemYielding=$isOemYielding isAutoHidden=$isAutoHidden")
                    if (isGone || isIdleHidden) {
                        setTouchableInsetsMethod.invoke(insets, touchableInsetsRegion)
                        val region = touchableRegionField.get(insets) as Region
                        region.setEmpty()
                    } else if (isExpanded) {
                        // When expanded, let the entire frame intercept touches so clicking outside collapses it
                        setTouchableInsetsMethod.invoke(insets, touchableInsetsFrame)
                    } else {
                        // PILL-ONLY TOUCHABLE REGION:
                        // Restrict touch interception to ONLY the pill bounds + padding.
                        // The region is local to this already-offset window. Touches outside
                        // the visible collapsed group pass through to the system.
                        setTouchableInsetsMethod.invoke(insets, touchableInsetsRegion)
                        
                        val selectedIdx = viewModel.selectedIndex.value
                        val activeNotification = visibleList.getOrNull(selectedIdx) ?: visibleList.firstOrNull()
                        val activeMode = activeNotification?.mode ?: IslandMode.Empty
                        val isCustomizing = viewModel.isCustomizing.value
                        val hasActiveEvents = visibleList.any { it.key != "system_battery" }
                        val isCircleMode = settingsVal.enableCutoutRingMode && !settingsVal.enableNotchMode && !isCustomizing && !hasActiveEvents
                        val isCutoutActive = settingsVal.enableCutoutRingMode || settingsVal.enableBatteryRing || settingsVal.showBatteryIndicator || isCustomizing

                        val density = resources.displayMetrics.density
                        val screenWidth = resources.displayMetrics.widthPixels
                        val touchPaddingPx = 16f * density

                        val circleSizePx = settingsVal.cameraCircleSize * density
                        val circleCenterXPx = screenWidth / 2f + settingsVal.cameraCircleOffsetX * density
                        val circleTopPx = (settingsVal.cameraCircleOffsetY * density).coerceAtLeast(0f)

                        val circleLeft = (circleCenterXPx - circleSizePx / 2f - touchPaddingPx).toInt().coerceAtLeast(0)
                        val circleTop = (circleTopPx - touchPaddingPx).toInt().coerceAtLeast(0)
                        val circleRight = (circleCenterXPx + circleSizePx / 2f + touchPaddingPx).toInt().coerceAtMost(screenWidth)
                        val circleBottom = (circleTopPx + circleSizePx + (if (isCircleMode) 48f * density else touchPaddingPx)).toInt()

                        val region = touchableRegionField.get(insets) as Region

                        if (isCircleMode && !isCustomizing) {
                            Log.d(TAG, "onComputeInternalInsets: circle touch region set to ($circleLeft, $circleTop, $circleRight, $circleBottom)")
                            region.set(circleLeft, circleTop, circleRight, circleBottom)
                        } else {
                            val isSplitMode = notificationsCount >= 2 && !settingsVal.enableNotchMode
                            val isCircleLeft = settingsVal.circlePosition == AuraSettings.CIRCLE_POSITION_LEFT

                            val mainWidthPx = settingsVal.width * density
                            val compactCircleSizePx = settingsVal.height * density
                            val compactGapPx = 8f * density
                            val edgePaddingPx = 8f * density
                            val touchPaddingXPx = 16f * density
                            val touchPaddingYPx = 16f * density
                            val groupWidthPx = mainWidthPx + if (isSplitMode) compactGapPx + compactCircleSizePx else 0f

                            val desiredMainLeftPx = screenWidth / 2f +
                                settingsVal.xOffset * density - mainWidthPx / 2f
                            val (minMainLeftPx, maxMainLeftPx) = when {
                                !isSplitMode -> edgePaddingPx to (screenWidth - edgePaddingPx - mainWidthPx).coerceAtLeast(edgePaddingPx)
                                isCircleLeft -> (edgePaddingPx + compactCircleSizePx + compactGapPx) to (screenWidth - edgePaddingPx - mainWidthPx).coerceAtLeast(edgePaddingPx + compactCircleSizePx + compactGapPx)
                                else -> edgePaddingPx to (screenWidth - edgePaddingPx - groupWidthPx).coerceAtLeast(edgePaddingPx)
                            }
                            val mainLeftPx = desiredMainLeftPx.coerceIn(minMainLeftPx, maxMainLeftPx)
                            val groupStartPx = if (isCircleLeft && isSplitMode) mainLeftPx - compactGapPx - compactCircleSizePx else mainLeftPx
                            val groupEndPx = if (!isCircleLeft && isSplitMode) mainLeftPx + mainWidthPx + compactGapPx + compactCircleSizePx else mainLeftPx + mainWidthPx
                            val left = (groupStartPx - touchPaddingXPx).toInt().coerceAtLeast(0)
                            val pillTopPx = if (settingsVal.enableNotchMode) 0f else (settingsVal.yOffset * density).coerceAtLeast(0f)
                            val pillBottomPx = pillTopPx + (settingsVal.height * density)
                            val top = (pillTopPx - touchPaddingYPx).toInt().coerceAtLeast(0)
                            val right = (groupEndPx + touchPaddingXPx).toInt().coerceAtMost(screenWidth)
                            val bottom = (pillBottomPx + touchPaddingYPx).toInt()

                            Log.d(TAG, "onComputeInternalInsets: region set to ($left, $top, $right, $bottom), isSplitMode=$isSplitMode")
                            region.set(left, top, right, bottom)

                            if (isCutoutActive) {
                                val circleRegion = Region(circleLeft, circleTop, circleRight, circleBottom)
                                region.op(circleRegion, Region.Op.UNION)
                            }
                        }
                    }
                }
                null
            }
            
            val registerListener = {
                val observer = view.viewTreeObserver
                Log.d(TAG, "registerListener lambda: viewTreeObserver=$observer, isAlive=${observer.isAlive}")
                if (observer.isAlive) {
                    val addListenerMethod = observer.javaClass.getMethod(
                        "addOnComputeInternalInsetsListener",
                        listenerClass
                    )
                    addListenerMethod.invoke(observer, proxyListener)
                    isTouchableRegionSupported.value = true
                    Log.d(TAG, "OnComputeInternalInsetsListener successfully registered on live ViewTreeObserver")
                    if (::viewModel.isInitialized && !isWindowExpanded) {
                        updateWindowLayoutParams(false, viewModel.settings.value)
                    }
                }
            }
            
            // ViewTreeObserver changes when the view is attached to a window.
            // We must register the listener on the live ViewTreeObserver of the attached window.
            Log.d(TAG, "setupTouchableRegion: isAttachedToWindow=${view.isAttachedToWindow}")
            if (view.isAttachedToWindow) {
                registerListener()
            } else {
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        Log.d(TAG, "onViewAttachedToWindow: registering listener now")
                        registerListener()
                    }
                    override fun onViewDetachedFromWindow(v: View) {
                        Log.d(TAG, "onViewDetachedFromWindow called")
                    }
                })
            }
        } ?: run {
            isTouchableRegionSupported.value = false
            Log.w(TAG, "Touchable region reflection unsupported or blocked, falling back to physical bounds")
        }
    }

    private fun updateWindowLayoutParams(expanded: Boolean, settings: AuraSettings) {
        if (destroyed || !::windowManager.isInitialized || !::viewModel.isInitialized) return
        val view = islandView ?: return
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels.toFloat()
        
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        isLockScreenActive = isLocked
        viewModel.isLocked.value = isLocked
        
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val visibleList = viewModel.visibleNotifications.value
        val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(settings.deviceType)
        val hasNativeIsland = OemDeviceRules.hasNativeSystemIsland(effectiveDevice)
        val isAutoHidden = viewModel.isAutoHidden.value
        val selectedIdx = viewModel.selectedIndex.value
        val activeNotification = visibleList.getOrNull(selectedIdx) ?: visibleList.firstOrNull()
        val activeMode = activeNotification?.mode ?: IslandMode.Empty
        val isCustomizing = viewModel.isCustomizing.value
        val hasActiveEvents = visibleList.any { it.key != "system_battery" }
        val isCircleMode = settings.enableCutoutRingMode && !settings.enableNotchMode && !isCustomizing && !hasActiveEvents
        val isOemYielding = viewModel.isOemYielding.value
        val isIdleHidden = isOemYielding || isAutoHidden || (settings.hideWhenIdle && !hasActiveEvents && !settings.enableAppShortcuts)

        // Check live battery charging state so the ring can appear on the lock screen
        // independently of the global showOnLockScreen toggle
        val isSystemCharging = if (isLocked && settings.enableBatteryRing && settings.batteryRingOnLockScreen && !isCircleMode) {
            runCatching {
                val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val statusIntent = registerReceiver(null, ifilter)
                val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }.getOrDefault(false)
        } else false
        // Hide when locked UNLESS the battery ring is configured to show on lock screen and device is charging
        val isHiddenByLock = !settings.showOnLockScreen && isLocked && !(isSystemCharging)
        val isHidden = isHiddenByLock || (isLandscape && !settings.showInLandscape) || isIdleHidden
        Log.d(TAG, "updateWindowLayoutParams: isHidden=$isHidden targetVisibility=${if (isHidden) "GONE" else "VISIBLE"} isOemYielding=$isOemYielding isAutoHidden=$isAutoHidden isLocked=$isLocked isLandscape=$isLandscape isIdleHidden=$isIdleHidden isSystemCharging=$isSystemCharging visibleCount=${visibleList.size} hasNativeIsland=$hasNativeIsland")

        val targetVisibility = if (isHidden) View.GONE else View.VISIBLE
        if (view.visibility != targetVisibility) {
            view.visibility = targetVisibility
        }

        val bounds = calculateOverlayBounds(
            settings = settings,
            expanded = expanded,
            isHidden = isHidden,
            isCustomizing = isCustomizing,
            notificationsCount = visibleList.size
        )
        val w = bounds.widthPx
        val h = bounds.heightPx
        val currentX = bounds.windowXPx
        val currentY = bounds.windowYPx
        windowXOffsetDp.value = if (bounds.widthPx == WindowManager.LayoutParams.MATCH_PARENT) 0f else (currentX / density)
        windowYOffsetDp.value = if (bounds.widthPx == WindowManager.LayoutParams.MATCH_PARENT) 0f else (currentY / density)

        val isInput = viewModel.isInputActive.value && expanded
        val focusFlags = if (isInput) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val touchableFlags = if (isHidden) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        val currentFlags = focusFlags or
            touchableFlags or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val currentSoftInputMode = if (isInput) {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            0
        }

        val lp = lastParams
        if (lp != null &&
            lp.width == w &&
            lp.height == h &&
            lp.flags == currentFlags &&
            lp.x == currentX &&
            lp.y == currentY &&
            lp.softInputMode == currentSoftInputMode
        ) {
            return
        }

        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            currentFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = currentX
            y = currentY
            softInputMode = currentSoftInputMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        lastParams = params
        runCatchingLogged(TAG, "Failed to update view layout") { 
            windowManager.updateViewLayout(view, params) 
        }
    }

    private fun removeCollapsedWindow() {
        val view = islandView ?: return
        // Clear the reference before removal so repeated teardown calls are harmless,
        // even when an OEM WindowManager throws while detaching an already-removed view.
        islandView = null
        lastParams = null
        isWindowExpanded = false
        collapseJob?.cancel()
        collapseJob = null
        if (!::windowManager.isInitialized) return
        runCatchingLogged(TAG, "Failed to remove view") {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            }
        }
    }

    private data class OverlayWindowBounds(
        val windowXPx: Int,
        val windowYPx: Int,
        val widthPx: Int,
        val heightPx: Int
    )

    private fun calculateOverlayBounds(
        settings: AuraSettings,
        expanded: Boolean,
        isHidden: Boolean,
        isCustomizing: Boolean,
        notificationsCount: Int
    ): OverlayWindowBounds {
        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels.toFloat()
        if (isHidden) {
            return OverlayWindowBounds(0, 0, 0, 0)
        }
        val isFullWidth = expanded || isTouchableRegionSupported.value
        val pillBottomDp = (if (settings.enableNotchMode) 0f else settings.yOffset) + settings.height
        val cutoutBottomDp = settings.cameraCircleOffsetY + settings.cameraCircleSize
        val isCutoutActive = settings.enableCutoutRingMode || settings.enableBatteryRing || settings.showBatteryIndicator || isCustomizing
        val maxBottomDp = if (isCutoutActive) maxOf(pillBottomDp, cutoutBottomDp) else pillBottomDp

        if (isFullWidth) {
            val h = if (expanded) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                ((maxBottomDp + 24f) * density).toInt()
            }
            return OverlayWindowBounds(
                windowXPx = 0,
                windowYPx = 0,
                widthPx = WindowManager.LayoutParams.MATCH_PARENT,
                heightPx = h
            )
        }

        val hasActiveEvents = if (::viewModel.isInitialized) {
            viewModel.visibleNotifications.value.any { it.key != "system_battery" }
        } else (notificationsCount > 0)
        val isCircleMode = settings.enableCutoutRingMode && !settings.enableNotchMode && !isCustomizing && !hasActiveEvents && !expanded
        val isSplitMode = (notificationsCount >= 2) && !settings.enableNotchMode && !isCircleMode
        val isCircleLeft = settings.circlePosition == AuraSettings.CIRCLE_POSITION_LEFT
        val mainWidthPx = settings.width * density
        val compactCircleSizePx = settings.height * density
        val compactGapPx = 8f * density
        val edgePaddingPx = 8f * density
        val groupWidthPx = mainWidthPx + if (isSplitMode) compactGapPx + compactCircleSizePx else 0f

        val desiredMainLeftPx = screenWidthPx / 2f + settings.xOffset * density - mainWidthPx / 2f
        val (minMainLeftPx, maxMainLeftPx) = when {
            !isSplitMode -> edgePaddingPx to (screenWidthPx - edgePaddingPx - mainWidthPx).coerceAtLeast(edgePaddingPx)
            isCircleLeft -> (edgePaddingPx + compactCircleSizePx + compactGapPx) to (screenWidthPx - edgePaddingPx - mainWidthPx).coerceAtLeast(edgePaddingPx + compactCircleSizePx + compactGapPx)
            else -> edgePaddingPx to (screenWidthPx - edgePaddingPx - groupWidthPx).coerceAtLeast(edgePaddingPx)
        }
        val mainLeftPx = desiredMainLeftPx.coerceIn(minMainLeftPx, maxMainLeftPx)
        val groupStartPx = if (isCircleLeft && isSplitMode) mainLeftPx - compactGapPx - compactCircleSizePx else mainLeftPx
        val groupEndPx = if (!isCircleLeft && isSplitMode) mainLeftPx + mainWidthPx + compactGapPx + compactCircleSizePx else mainLeftPx + mainWidthPx

        val pillTopPx = if (settings.enableNotchMode) 0f else (settings.yOffset * density).coerceAtLeast(0f)
        val pillBottomPx = pillTopPx + (settings.height * density)

        val circleSizePx = settings.cameraCircleSize * density
        val circleCenterXPx = screenWidthPx / 2f + settings.cameraCircleOffsetX * density
        val circleTopPx = (settings.cameraCircleOffsetY * density).coerceAtLeast(0f)
        val circleBottomPx = circleTopPx + circleSizePx
        val circleLeftPx = circleCenterXPx - circleSizePx / 2f
        val circleRightPx = circleCenterXPx + circleSizePx / 2f

        val (windowLeftPx, windowRightPx, windowTopPx, windowBottomPx) = when {
            isCircleMode && !isCustomizing -> {
                listOf(
                    circleLeftPx - 20f * density,
                    circleRightPx + 20f * density,
                    (circleTopPx - 12f * density).coerceAtLeast(0f),
                    circleBottomPx + 48f * density
                )
            }
            isCutoutActive -> {
                listOf(
                    minOf(groupStartPx, circleLeftPx) - 16f * density,
                    maxOf(groupEndPx, circleRightPx) + 16f * density,
                    minOf(pillTopPx, circleTopPx).coerceAtLeast(0f),
                    maxOf(pillBottomPx, circleBottomPx) + 16f * density
                )
            }
            else -> {
                listOf(
                    groupStartPx - 16f * density,
                    groupEndPx + 16f * density,
                    pillTopPx,
                    pillBottomPx + 16f * density
                )
            }
        }

        val windowCenterX = (windowLeftPx + windowRightPx) / 2f
        val windowXPx = (windowCenterX - screenWidthPx / 2f).toInt()
        val windowYPx = windowTopPx.toInt()
        val w = (windowRightPx - windowLeftPx).toInt().coerceAtLeast(1)
        val h = (windowBottomPx - windowTopPx).toInt().coerceAtLeast(1)

        return OverlayWindowBounds(
            windowXPx = windowXPx,
            windowYPx = windowYPx,
            widthPx = w,
            heightPx = h
        )
    }

    private fun collapsedParams(settings: AuraSettings): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val visibleList = if (::viewModel.isInitialized) viewModel.visibleNotifications.value else emptyList()
        val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(settings.deviceType)
        val hasNativeIsland = OemDeviceRules.hasNativeSystemIsland(effectiveDevice)
        val isAutoHidden = if (::viewModel.isInitialized) viewModel.isAutoHidden.value else false
        val isNativeActive = if (::viewModel.isInitialized) {
            OemDeviceRules.isNativeIslandActive(
                notifications = viewModel.notifications.value,
                device = effectiveDevice,
                yieldEnabled = settings.yieldToSystemIsland,
                handlingMode = settings.oemIslandHandlingMode,
                yieldMusic = settings.yieldMusicToSystemIsland
            )
        } else false
        val isOemYielding = settings.yieldToSystemIsland && hasNativeIsland && isNativeActive
        val isIdleHidden = isOemYielding || isAutoHidden || (settings.hideWhenIdle && visibleList.isEmpty() && !settings.enableAppShortcuts && !settings.enableCutoutRingMode)
        val isCustomizing = if (::viewModel.isInitialized) viewModel.isCustomizing.value else false

        val bounds = calculateOverlayBounds(
            settings = settings,
            expanded = false,
            isHidden = isIdleHidden,
            isCustomizing = isCustomizing,
            notificationsCount = visibleList.size
        )
        windowXOffsetDp.value = if (bounds.widthPx == WindowManager.LayoutParams.MATCH_PARENT) 0f else (bounds.windowXPx / density)
        windowYOffsetDp.value = if (bounds.widthPx == WindowManager.LayoutParams.MATCH_PARENT) 0f else (bounds.windowYPx / density)

        val touchableFlags = if (isIdleHidden) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        val currentFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            touchableFlags or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        return WindowManager.LayoutParams(
            bounds.widthPx,
            bounds.heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            currentFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = bounds.windowXPx
            y = bounds.windowYPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }.also {
            lastParams = it
        }
    }

    private fun openNotification(notification: IslandNotification) {
        if (notification.contentIntent != null) {
            sendIntentWithOptions(this, notification.contentIntent)
        } else {
            runCatchingLogged(TAG, "Failed to launch package activity") {
                when (notification.mode) {
                    IslandMode.Bluetooth -> {
                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                    IslandMode.Battery -> {
                        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        } else {
                            val altIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(altIntent)
                        }
                    }
                    IslandMode.Hotspot -> {
                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                    IslandMode.Timer,
                    IslandMode.Stopwatch -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                            ?: Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        if (launchIntent.resolveActivity(packageManager) != null) {
                            startActivity(launchIntent)
                        } else {
                            val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(clockIntent)
                        }
                    }
                    else -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        } else {
                            Toast.makeText(this, "Opening ${notification.appName} (Demo)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        if (notification.mode != IslandMode.Music) {
            notificationRepository.removeNotificationsForPackage(notification.packageName)
        }
        viewModel.collapse()
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "App is no longer available", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatchingLogged(TAG, "Failed to launch shortcut app") {
            startActivity(launchIntent)
        }
        notificationRepository.removeNotificationsForPackage(packageName)
        viewModel.collapse()
    }

    private fun openShortcutPicker() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("extra_destination_section", "AppShortcuts")
        }
        runCatchingLogged(TAG, "Failed to open shortcut picker") {
            startActivity(intent)
        }
        viewModel.collapse()
    }

    private fun openCurrentNotificationInFloatingWindow() {
        val list = viewModel.visibleNotifications.value
        val index = viewModel.selectedIndex.value
        if (list.isNotEmpty() && index in list.indices) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Toast.makeText(this, "Floating window requires Android 7+.", Toast.LENGTH_SHORT).show()
                viewModel.collapse()
                return
            }
            val notification = list[index]
            val options = ActivityOptions.makeBasic()
            runCatchingLogged(TAG, "Failed to set launch bounds") {
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                val w = (screenWidth * 0.90f).toInt()
                val h = (screenHeight * 0.65f).toInt()
                val left = (screenWidth - w) / 2
                val top = (screenHeight - h) / 2
                options.setLaunchBounds(Rect(left, top, left + w, top + h))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatchingLogged(TAG, "Failed to set background activity start mode") {
                    val mode = if (Build.VERSION.SDK_INT >= 36) {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    } else {
                        @Suppress("DEPRECATION")
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                    options.setPendingIntentBackgroundActivityStartMode(mode)
                }
            }
            val bundle = options.toBundle() ?: Bundle()
            bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM)

            val fillInIntent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }

            if (notification.contentIntent != null) {
                runCatchingLogged(TAG, "Failed to send content intent") {
                    notification.contentIntent.send(this, 0, fillInIntent, null, null, null, bundle)
                }
            } else {
                runCatchingLogged(TAG, "Failed to launch package activity") {
                    val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        startActivity(launchIntent, bundle)
                    } else {
                        Toast.makeText(this, "Opening ${notification.appName} in floating window (Demo)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            notificationRepository.removeNotification(notification.key)
            notificationRepository.sendCommand(AuraCommand.CancelNotification(notification.key))
        }
        viewModel.collapse()
    }

    private fun Float.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun ComposeView.installOverlayViewTreeOwners() {
        setViewTreeLifecycleOwner(overlayOwners)
        setViewTreeViewModelStoreOwner(overlayOwners)
        setViewTreeSavedStateRegistryOwner(overlayOwners)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                OVERLAY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Aura overlay running"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setContentTitle("Aura is active")
            .setContentText("Tap to open Aura")
            .setSmallIcon(R.drawable.ic_stat_aura)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    companion object {
        @Volatile
        var isSystemConnected: Boolean = false
            internal set

        private const val TAG = "AuraOverlayService"
        private const val NOTIFICATION_ID = 8105
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val OVERLAY_CHANNEL_ID = "aura_overlay"
        private const val OVERLAY_CHANNEL_NAME = "Aura overlay"
        private const val AUTO_COLLAPSE_DELAY_MS = 220L
        private const val NATIVE_ISLAND_WINDOW_DEBOUNCE_MS = 2500L
    }
}
