/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui

import com.moksha.auraisland.data.AuraCommand
import com.moksha.auraisland.model.SwipeAction
import com.moksha.auraisland.util.OemDeviceRules
import com.moksha.auraisland.ui.expanded.IslandExpandedContent
import com.moksha.auraisland.ui.expanded.trySendFirstAction
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.offset
import android.content.res.Configuration
import android.media.AudioManager
import android.media.session.MediaController
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.moksha.auraisland.ui.components.CutoutBatteryRing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.di.AuraRepositories
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import com.moksha.auraisland.data.LaunchableApp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.asImageBitmap


/**
 * Overlay that draws the [CutoutBatteryRing] at the camera punch-hole position
 * inside either the collapsed or expanded pill. Extracted as its own composable
 * to keep [IslandOverlayView] within the JVM method-size limit.
 */
@Composable
private fun ChargingRingOverlay(
    settings: AuraSettings,
    batteryPct: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(settings.cameraCircleSize.dp),
        contentAlignment = Alignment.Center
    ) {
        CutoutBatteryRing(
            batteryPct = batteryPct,
            isCharging = true,
            settings = settings,
            sizeDp = settings.cameraCircleSize.dp,
            showCenterIcon = false
        )
    }
}

@Composable
fun IslandOverlayView(

    settings: AuraSettings,
    expanded: Boolean,
    showAppLauncher: Boolean = false,
    notifications: List<IslandNotification>,
    selectedIndex: Int,
    launcherApps: List<LaunchableApp>?,
    onPageSelected: (Int) -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onLaunchApp: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onOpenAppLauncher: () -> Unit = {},
    onDismissNotification: () -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    statusBarHeight: Float,
    modifier: Modifier = Modifier,
    isInputActive: Boolean = false,
    isAutoHidden: Boolean = false,
    onAutoHiddenChanged: (Boolean) -> Unit = {},
    onReplyStateChanged: (Boolean) -> Unit = {},
    onDismissAllNotifications: () -> Unit = {},
    onConfigureShortcuts: () -> Unit = {},
    isFullWidth: Boolean = true,
    windowXOffsetDp: Float = 0f,
    windowYOffsetDp: Float = 0f,
    isOemYielding: Boolean = false,
    isCustomizing: Boolean = false,
    isLocked: Boolean = false
) {
    // Fix #1: rememberUpdatedState ensures the lambda is always fresh
    // even though pointerInput(Unit) never restarts its coroutine
    val currentOnToggle by rememberUpdatedState(onToggleExpanded)
    val currentOnOpenAppLauncher by rememberUpdatedState(onOpenAppLauncher)
    val currentOnDismiss by rememberUpdatedState(onDismissNotification)
    val currentOnDismissAll by rememberUpdatedState(onDismissAllNotifications)
    val currentOnOpenFloatingWindow by rememberUpdatedState(onOpenFloatingWindow)
    val currentOnOpenNotificationShade by rememberUpdatedState(onOpenNotificationShade)
    val currentOnConfigureShortcuts by rememberUpdatedState(onConfigureShortcuts)
    val currentOnOpenNotification by rememberUpdatedState(onOpenNotification)
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)
    val currentExpanded by rememberUpdatedState(expanded)
    val currentSettings by rememberUpdatedState(settings)
    val currentOnAutoHiddenChanged by rememberUpdatedState(onAutoHiddenChanged)
    val safeIndex = selectedIndex.coerceIn(0, (notifications.size - 1).coerceAtLeast(0))
    val currentNotifications by rememberUpdatedState(notifications)
    val currentSelectedIndex by rememberUpdatedState(safeIndex)
    val shouldShowOutline = settings.enableOutline && (!settings.outlineOnlyInCustomization || isCustomizing)

    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var pillDragOffsetX by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val displayMetrics = context.resources.displayMetrics
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenCenter = screenWidth / 2f
    val expandedWidth = calculateExpandedWidth(
        isLandscape = isLandscape,
        screenWidthDp = configuration.screenWidthDp.toFloat(),
        screenHeightDp = configuration.screenHeightDp.toFloat()
    ).dp
    val transition = updateTransition(targetState = expanded, label = "islandTransition")

    val sizeSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.72f,
        stiffness = 520f
    )
    val sizeSpecFloat = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 520f
    )
    val heightSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.76f,
        stiffness = 520f
    )
    // xOffset must NEVER overshoot — a position that flies past its target
    // and snaps back looks like the pill jerking left then right.
    // DampingRatioNoBouncy (1.0) gives a smooth, critically-damped slide
    // while width/height still get their satisfying elastic pop via sizeSpec.
    val xOffsetSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f
    )
    // yOffset must also NEVER undershoot — during collapse it animates from
    // expandedTopOffset → 0dp. With a bouncy spring it goes negative, pushing the
    // pill above the window top (behind the status bar) for a few frames. The user
    // sees the pill briefly disappear = the intermittent "gap at top" bug.
    val yOffsetSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f
    )
    // Width spec: Must NEVER overshoot horizontally.
    // When width overshoots, the left edge moves too far left, bounces back to the right,
    // and settles — looking like the pill is jerking left and coming back.
    // DampingRatioNoBouncy (1.0) ensures a smooth, monotonic bloom outward.
    val widthSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f
    )
    val alphaSpec = tween<Float>(
        durationMillis = 190,
        easing = FastOutSlowInEasing
    )

    val activeNotification = notifications.getOrNull(safeIndex)
    val activeMode = activeNotification?.mode ?: IslandMode.Empty

    val initialEstimatedHeight = remember(activeMode, notifications.isEmpty(), showAppLauncher) {
        if (notifications.isEmpty() || showAppLauncher) 122.dp else defaultEstimatedHeightForMode(activeMode)
    }
    var expandedHeight by remember { mutableStateOf(initialEstimatedHeight) }

    LaunchedEffect(activeMode, notifications.isEmpty(), showAppLauncher) {
        if (!expanded) {
            expandedHeight = if (notifications.isEmpty() || showAppLauncher) 122.dp else defaultEstimatedHeightForMode(activeMode)
        }
    }

    val compactGap = COMPACT_INDICATOR_GAP_DP.dp
    val miniPillWidth = settings.width.dp
    val circleSize = settings.height.dp
    val compactShapes = compactNotificationShapes(notifications.size, expanded)
    val hasCompanion = if (settings.enableNotchMode) false else notifications.size >= 2
    val isCircleLeft = settings.circlePosition == AuraSettings.CIRCLE_POSITION_LEFT
    val collapsedGroupWidth = settings.width.dp + if (hasCompanion) compactGap + circleSize else 0.dp

    val desiredMainLeft = screenCenter + settings.xOffset.dp - settings.width.dp / 2f
    val (minMainLeft, maxMainLeft) = when {
        !hasCompanion -> compactGap to (screenWidth - compactGap - settings.width.dp).coerceAtLeast(compactGap)
        isCircleLeft -> (compactGap + circleSize + compactGap) to (screenWidth - compactGap - settings.width.dp).coerceAtLeast(compactGap + circleSize + compactGap)
        else -> compactGap to (screenWidth - compactGap - collapsedGroupWidth).coerceAtLeast(compactGap)
    }
    val collapsedMainLeft = desiredMainLeft.coerceIn(minMainLeft, maxMainLeft)
    val mainCenter = collapsedMainLeft + settings.width.dp / 2f
    val circleLeft = if (isCircleLeft) {
        collapsedMainLeft - compactGap - circleSize
    } else {
        collapsedMainLeft + settings.width.dp + compactGap
    }
    val circleCenter = circleLeft + circleSize / 2f
    val groupStart = if (isCircleLeft && hasCompanion) circleLeft else collapsedMainLeft
    val groupEnd = if (!isCircleLeft && hasCompanion) circleLeft + circleSize else collapsedMainLeft + settings.width.dp
    val groupCenter = (groupStart + groupEnd) / 2f

    val effectiveFullWidth = isFullWidth || expanded
    val effectiveWinX = if (effectiveFullWidth) 0.dp else windowXOffsetDp.dp
    val effectiveWinY = if (effectiveFullWidth) 0.dp else windowYOffsetDp.dp

    val collapsedMainOffset = (if (settings.enableNotchMode) {
        settings.xOffset.dp
    } else {
        mainCenter - screenCenter
    }) - effectiveWinX

    val expandedTopOffset = calculateExpandedTopOffset(
        enableNotchMode = settings.enableNotchMode,
        hasCompanion = hasCompanion,
        statusBarHeightDp = statusBarHeight,
        pillYOffsetDp = settings.yOffset,
        notchHeightDp = settings.height,
        circleSizeDp = settings.height,
        compactGapDp = COMPACT_INDICATOR_GAP_DP
    ).dp

    // Cutout Ring Mode:
    // When enabled, the island collapses into a camera circle around the camera punch hole for:
    // 1. Idle / inactive state (no active notifications)
    // 2. Battery indicator / charging state (instead of expanding into a wide pill)
    // NOTE: When music is playing, music defaults as it is (wide media pill with visualizer).
    // NOTE: When an incoming notification arrives, the island expands as usual.
    // NOTE: isCircleMode is suppressed when expanded or when active events are present.
    val hasActiveEvents = notifications.any { it.key != "system_battery" }
    val isCircleMode = settings.enableCutoutRingMode && !settings.enableNotchMode && !isCustomizing && !hasActiveEvents && !expanded

    val targetCollapsedWidth = if (isCircleMode) settings.cameraCircleSize.dp else settings.width.dp
    val targetCollapsedHeight = if (isCircleMode) settings.cameraCircleSize.dp else settings.height.dp
    val targetCollapsedRadius = if (isCircleMode) (settings.cameraCircleSize / 2f).dp else settings.cornerRadius.dp
    val targetCollapsedXOffset = if (isCircleMode) {
        settings.cameraCircleOffsetX.dp - effectiveWinX
    } else collapsedMainOffset
    val targetCollapsedYOffset = if (isCircleMode) {
        settings.cameraCircleOffsetY.dp - effectiveWinY
    } else {
        (if (settings.enableNotchMode) 0.dp else settings.yOffset.dp) - effectiveWinY
    }

    val isIdleHiding = isOemYielding || (settings.hideWhenIdle && notifications.isEmpty() && !settings.enableCutoutRingMode)

    var userInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Reset auto-hide whenever active notifications change or selection changes
    LaunchedEffect(notifications.map { it.key }, selectedIndex) {
        currentOnAutoHiddenChanged(false)
        userInteractionTimestamp = System.currentTimeMillis()
    }

    // Auto-hide countdown timer when pill is collapsed and autoHidePill is enabled
    LaunchedEffect(expanded, settings.autoHidePill, settings.autoHideTimeoutSeconds, userInteractionTimestamp) {
        if (expanded || !settings.autoHidePill) {
            currentOnAutoHiddenChanged(false)
            return@LaunchedEffect
        }
        val timeoutMs = (settings.autoHideTimeoutSeconds.coerceAtLeast(1) * 1000L)
        delay(timeoutMs.milliseconds)
        currentOnAutoHiddenChanged(true)
    }

    val isHiding = isIdleHiding || (settings.autoHidePill && isAutoHidden) || isOemYielding
    val pillBackgroundColor = Color(settings.pillColor)

    val width by transition.animateDp(transitionSpec = { widthSpec }, label = "islandWidth") {
        if (it) expandedWidth else if (isHiding) 0.dp else targetCollapsedWidth
    }
    val height by transition.animateDp(transitionSpec = { heightSpec }, label = "islandHeight") {
        if (it) expandedHeight else if (isHiding) 0.dp else targetCollapsedHeight
    }
    val yOffset by transition.animateDp(transitionSpec = { yOffsetSpec }, label = "islandYOffset") {
        if (it) expandedTopOffset else targetCollapsedYOffset
    }
    val radius by transition.animateDp(transitionSpec = { sizeSpec }, label = "islandRadius") {
        if (it) 34.dp else if (isHiding) 0.dp else targetCollapsedRadius
    }
    val animatedXOffset by transition.animateDp(transitionSpec = { xOffsetSpec }, label = "islandXOffset") {
        if (it) 0.dp else targetCollapsedXOffset
    }

    val collapsedAlpha by transition.animateFloat(
        transitionSpec = { alphaSpec },
        label = "collapsedAlpha"
    ) {
        if (it || isHiding) 0f else 1f
    }

    val expandedAlpha by transition.animateFloat(
        transitionSpec = { alphaSpec },
        label = "expandedAlpha"
    ) {
        if (it) 1f else 0f
    }

    val contentScale by transition.animateFloat(
        transitionSpec = { sizeSpecFloat },
        label = "contentScale"
    ) {
        // Start at 0.82 so content visibly blooms outward from the pill position during expand.
        // On collapse it shrinks back toward the pill, reinforcing the "living in the pill" feel.
        if (it) 1f else 0.82f
    }

    val contentSlideY by transition.animateDp(
        transitionSpec = { sizeSpec },
        label = "contentSlideY"
    ) {
        // Slide content down INTO view from the pill (positive = downward = growing from pill).
        // Previously used -6dp (upward) which felt like a tooltip appearing, not an expansion.
        if (it) 0.dp else (-4).dp
    }

    val safeWidth = width.coerceAtLeast(0.dp)
    val safeHeight = height.coerceAtLeast(0.dp)
    val safeRadius = radius.coerceAtLeast(0.dp)

    // Tactile spring scale bounce animation only when user switches between active notifications
    val switchScaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    var isInitialComposition by remember { mutableStateOf(true) }
    var lastSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (isInitialComposition) {
            isInitialComposition = false
            lastSelectedIndex = selectedIndex
            return@LaunchedEffect
        }
        if (lastSelectedIndex != selectedIndex) {
            lastSelectedIndex = selectedIndex
            switchScaleAnim.animateTo(
                targetValue = 0.92f,
                animationSpec = tween(40, easing = FastOutSlowInEasing)
            )
            switchScaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 650f
                )
            )
        }
    }

    // Dual Pill (Multi-Tasking Split Island) Detection:
    // When 2 or more notifications exist (e.g. Music + Notification/Timer/Call), split into Main Pill + Secondary Bubble
    val secondaryNotification = if (!settings.enableNotchMode && notifications.size >= 2) {
        notifications.firstOrNull { it.key != activeNotification?.key }
    } else null
    val secondaryIndex = if (secondaryNotification != null) {
        notifications.indexOfFirst { it.key == secondaryNotification.key }
    } else -1
    val tertiaryNotification = if (!settings.enableNotchMode && notifications.size >= 3) {
        notifications.firstOrNull {
            it.key != activeNotification?.key && it.key != secondaryNotification?.key
        }
    } else null
    val tertiaryIndex = if (tertiaryNotification != null) {
        notifications.indexOfFirst { it.key == tertiaryNotification.key }
    } else -1
    val isSplitMode = if (settings.enableNotchMode || isCircleMode) false else secondaryNotification != null
    val secondaryIsPill = compactShapes.singleOrNull() == CompactNotificationShape.MiniPill
    val showTertiaryPill = compactShapes.size == 2 && tertiaryNotification != null

    val secondaryAlpha by animateFloatAsState(
        targetValue = if (isSplitMode && !isHiding && !currentExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "secondaryAlpha"
    )
    val secondaryScale by animateFloatAsState(
        targetValue = if (isSplitMode && !isHiding) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "secondaryScale"
    )
    val secondaryBubbleWidth by animateDpAsState(
        targetValue = if (secondaryIsPill) miniPillWidth else circleSize,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "secondaryBubbleWidth"
    )
    val secondaryPillProgress = (miniPillWidth - circleSize).value.let { widthDelta ->
        if (widthDelta == 0f) {
            if (secondaryIsPill) 1f else 0f
        } else {
            ((secondaryBubbleWidth - circleSize).value / widthDelta).coerceIn(0f, 1f)
        }
    }
    val secondaryBubbleCorner by animateDpAsState(
        targetValue = if (secondaryIsPill) settings.cornerRadius.dp else circleSize / 2f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "secondaryBubbleCorner"
    )
    val tertiaryAlpha by animateFloatAsState(
        targetValue = if (showTertiaryPill && !isHiding && !currentExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tertiaryAlpha"
    )
    val tertiaryScale by animateFloatAsState(
        targetValue = if (showTertiaryPill && !isHiding) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f),
        label = "tertiaryScale"
    )

    val collapsedSecondaryOffset = (circleCenter - screenCenter) - effectiveWinX
    val secondaryExpandedOffset = calculateSecondaryExpandedOffset(
        secondaryIsPill = secondaryIsPill,
        isCircleLeft = isCircleLeft,
        isFullWidth = effectiveFullWidth,
        expandedCompactX = collapsedMainLeft.value,
        screenCenter = screenCenter.value,
        miniPillWidth = miniPillWidth.value,
        circleSize = circleSize.value,
        compactGap = compactGap.value
    ).dp - effectiveWinX
    val secondaryOffset by animateDpAsState(
        targetValue = if (!expanded) collapsedSecondaryOffset else secondaryExpandedOffset,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "secondaryOffset"
    )

    // Outer Box: Fills the entire WindowManager window bounds (which are padded for easy touch)
    val outerModifier = if (currentExpanded) {
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (isInputActive) {
                        onReplyStateChanged(false)
                    }
                    currentOnToggle()
                }
            }
    } else {
        modifier.fillMaxSize()
    }

    Box(
        modifier = outerModifier,
        contentAlignment = if (isCircleMode && !effectiveFullWidth && !currentExpanded) Alignment.Center else Alignment.TopCenter
    ) {

        // Invisible touch target over the pill location when hiding, so tapping the area reveals the pill or opens shortcuts
        if (isHiding && !currentExpanded) {
            Box(
                modifier = Modifier
                    .width(settings.width.dp)
                    .height(settings.height.dp)
                    .graphicsLayer {
                        translationX = collapsedMainOffset.toPx()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (settings.autoHidePill && isAutoHidden) {
                                // First tap on auto-hidden pill: awaken and reveal the pill
                                currentOnAutoHiddenChanged(false)
                                userInteractionTimestamp = System.currentTimeMillis()
                            } else if (settings.enableAppShortcuts || currentNotifications.isNotEmpty() || isCircleMode) {
                                // Empty notifications idle hiding: expand favorite shortcuts if enabled
                                currentOnToggle()
                            }
                        }
                    }
            )
        }

        val mainShape = if (isCircleMode && !currentExpanded) {
            CircleShape
        } else if (settings.enableNotchMode && !currentExpanded) {
            RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = safeRadius,
                bottomEnd = safeRadius
            )
        } else {
            RoundedCornerShape(safeRadius)
        }

        // Host charging state so both collapsed, expanded, and camera cutout layers can access it
        val batteryNotif = currentNotifications.find { it.key == "system_battery" || it.mode == IslandMode.Battery }
        val batteryPctText = (batteryNotif ?: activeNotification)?.text?.replace("%", "")?.trim()?.ifEmpty { "100" } ?: "100"
        val notifBatteryPct = batteryPctText.toFloatOrNull() ?: 100f
        val isChargingFromNotif = (batteryNotif ?: activeNotification)?.let {
            it.category == "battery_charging" || it.title.contains("charg", ignoreCase = true)
        } ?: false

        // Live battery state — updates on every ACTION_BATTERY_CHANGED broadcast so the
        // ring percentage is never stale between notification changes.
        var isSystemCharging by remember { mutableStateOf(false) }
        var systemBatteryPct by remember { mutableFloatStateOf(100f) }
        DisposableEffect(context) {
            fun readBattery(intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isSystemCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                systemBatteryPct = if (level >= 0 && scale > 0) (level * 100f / scale) else 100f
            }
            // Read current state immediately via sticky broadcast
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            readBattery(sticky)
            // Then listen for live updates
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context, intent: Intent) = readBattery(intent)
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            onDispose { context.unregisterReceiver(receiver) }
        }

        val isDeviceCharging = isChargingFromNotif || isSystemCharging
        val currentBatteryPct = if (batteryNotif != null || activeMode == IslandMode.Battery) notifBatteryPct else systemBatteryPct

        // Computed early so both the pill alpha-guard (below) and the cutout ring section can use it.
        val isLockedRingOnly = isLocked && !settings.showOnLockScreen &&
            settings.enableBatteryRing && settings.batteryRingOnLockScreen && isDeviceCharging

        val isBatteryRingActive = isCustomizing ||
            settings.showBatteryIndicator ||
            (settings.enableBatteryRing && isDeviceCharging) ||
            (isLockedRingOnly && settings.enableBatteryRing)

        // Inner Box: The actual visible pill container, managing the black background shape and size animations
        //
        // The outer Box is Alignment.TopCenter over the full screen width. The inner pill is placed
        // at x = (screenWidth - safeWidth) / 2, so its visual center is always at screenWidth/2 + translationX.
        // The expansion IS therefore already symmetric — the width grows left+right equally from screenCenter.
        //
        // The "comes from left" perception is caused by the expanded CONTENT laying out from the left
        // while the pill shape symmetrically grows. Fix: the content scale + translation animations
        // below use TransformOrigin.Center so content appears to bloom from the pill's own center.
        Box(
            modifier = Modifier
                .width(safeWidth)
                .height(safeHeight)
                .graphicsLayer {
                    // pillDragOffsetX is zeroed on expand to avoid a sudden horizontal position
                    // jump (which the spring would then over-correct, causing the leftward lurch).
                    // When expanded, the pill is always screen-centered so offset is irrelevant.
                    translationX = animatedXOffset.toPx() + (if (!currentExpanded) pillDragOffsetX else 0f)
                    translationY = yOffset.toPx() + dragOffset
                    scaleX = switchScaleAnim.value
                    scaleY = switchScaleAnim.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                    // When on lock screen with ring-only mode, hide the pill — the ring itself
                    // is rendered in the separate cutout overlay below and remains visible.
                    if (isLockedRingOnly) alpha = 0f
                }
                .then(
                    // Never cast shadow when collapsed to prevent dark mask / halo over wallpaper.
                    // When expanded, cast a soft, subtle elevation shadow with translucent alpha.
                    if (settings.enableShadow && currentExpanded && settings.shadowElevation > 0f && !isHiding) {
                        Modifier.shadow(
                            elevation = 4.dp,
                            shape = mainShape,
                            clip = false,
                            ambientColor = Color(0x33000000),
                            spotColor = Color(0x44000000)
                        )
                    } else Modifier
                )
                .then(
                    if (isCircleMode && !currentExpanded) {
                        Modifier.background(pillBackgroundColor.copy(alpha = settings.opacity), shape = CircleShape)
                    } else {
                        Modifier
                            .clip(mainShape)
                            .background(pillBackgroundColor.copy(alpha = if (currentExpanded) 1f else settings.opacity))
                    }
                )
                .then(
                    if (shouldShowOutline && settings.outlineWidth > 0f) {
                        val outlineShape = if (isCircleMode && !currentExpanded) CircleShape else mainShape
                        Modifier.border(
                            width = settings.outlineWidth.dp,
                            color = Color(settings.outlineColor),
                            shape = outlineShape
                        )
                    } else Modifier
                )
                .pointerInput(displayMetrics.density, isInputActive) {
                    if (isInputActive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        userInteractionTimestamp = System.currentTimeMillis()
                        val pressTimeMs = System.currentTimeMillis()
                        val wasExpandedAtStart = currentExpanded
                        var isHoldRegistered = false
                        var dragAccumulatorY = 0f
                        var dragAccumulatorX = 0f
                        var isDragging = false
                        var pillGestureTriggered = false

                        val holdJob = scope.launch {
                            delay(HOLD_GESTURE_THRESHOLD_MS.milliseconds)
                            isHoldRegistered = true
                            triggerHapticVibration(context)
                        }

                        val pointerId = down.id

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            if (change.changedToUp()) {
                                change.consume()
                                holdJob.cancel()
                                val totalElapsedMs = System.currentTimeMillis() - pressTimeMs
                                val currentNotification = currentNotifications.getOrNull(currentSelectedIndex)

                                if (wasExpandedAtStart) {
                                    val swipeUpThreshold = -SWIPE_THRESHOLD_DP * displayMetrics.density
                                    val swipeDownThreshold = SWIPE_THRESHOLD_DP * displayMetrics.density
                                    if (isDragging && currentSettings.enableSwipeActions && dragOffset < swipeUpThreshold) {
                                        val isHold = isHoldRegistered || totalElapsedMs >= HOLD_GESTURE_THRESHOLD_MS
                                        val actionStr = if (isHold) currentSettings.swipeHoldUpAction else currentSettings.swipeUpAction
                                        val action = SwipeAction.fromId(actionStr, if (isHold) SwipeAction.DismissAll else SwipeAction.DismissCurrent)
                                        executeSwipeAction(
                                            action = action,
                                            currentNotification = currentNotification,
                                            context = context,
                                            onDismiss = currentOnDismiss,
                                            onDismissAll = currentOnDismissAll,
                                            onToggle = currentOnToggle,
                                            onOpenNotification = currentOnOpenNotification,
                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                            onPageSelected = currentOnPageSelected,
                                            notificationsSize = currentNotifications.size,
                                            currentIndex = currentSelectedIndex
                                        )
                                    } else if (isDragging && currentSettings.enableSwipeActions && dragOffset > swipeDownThreshold) {
                                        val isHold = isHoldRegistered || totalElapsedMs >= HOLD_GESTURE_THRESHOLD_MS
                                        if (isHold || !showAppLauncher) {
                                            currentOnOpenAppLauncher()
                                        } else {
                                            val action = SwipeAction.fromId(currentSettings.swipeDownAction, SwipeAction.FloatingWindow)
                                            executeSwipeAction(
                                                action = action,
                                                currentNotification = currentNotification,
                                                context = context,
                                                onDismiss = currentOnDismiss,
                                                onDismissAll = currentOnDismissAll,
                                                onToggle = currentOnToggle,
                                                onOpenNotification = currentOnOpenNotification,
                                                onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                onOpenNotificationShade = currentOnOpenNotificationShade,
                                                onPageSelected = currentOnPageSelected,
                                                notificationsSize = currentNotifications.size,
                                                currentIndex = currentSelectedIndex
                                            )
                                        }
                                    } else if (!isDragging && abs(dragAccumulatorX) < 10f * displayMetrics.density && abs(dragAccumulatorY) < 10f * displayMetrics.density) {
                                        if (!isHoldRegistered) {
                                            if (currentNotification != null && (currentNotification.mode != IslandMode.Battery || !currentSettings.enableCutoutRingMode)) {
                                                currentOnOpenNotification(currentNotification)
                                            } else {
                                                AuraRepositories.notificationRepository(context).resetTimer()
                                            }
                                        }
                                    }
                                } else {
                                    // Collapsed state
                                    if (isCircleMode) {
                                        val pillThreshold = PILL_SWIPE_THRESHOLD_DP * displayMetrics.density
                                        if (dragAccumulatorY > pillThreshold) {
                                            currentOnOpenAppLauncher()
                                        } else if (!isHoldRegistered) {
                                            currentOnOpenAppLauncher()
                                        }
                                    } else if (!pillGestureTriggered) {
                                        val isPillSwipeEnabled = currentSettings.enablePillSwipeActions
                                        val pillThreshold = PILL_SWIPE_THRESHOLD_DP * displayMetrics.density
                                        val absX = abs(dragAccumulatorX)
                                        val absY = abs(dragAccumulatorY)

                                        if (isPillSwipeEnabled && (absX >= pillThreshold || absY >= pillThreshold)) {
                                            if (absX > absY) {
                                                if (dragAccumulatorX < -pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeLeftAction, SwipeAction.PreviousNotification)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                } else if (dragAccumulatorX > pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeRightAction, SwipeAction.NextNotification)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                }
                                            } else {
                                                if (dragAccumulatorY < -pillThreshold) {
                                                    val action = SwipeAction.fromId(currentSettings.pillSwipeUpAction, SwipeAction.DismissCurrent)
                                                    executeSwipeAction(
                                                        action = action,
                                                        currentNotification = currentNotification,
                                                        context = context,
                                                        onDismiss = currentOnDismiss,
                                                        onDismissAll = currentOnDismissAll,
                                                        onToggle = currentOnToggle,
                                                        onOpenNotification = currentOnOpenNotification,
                                                        onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                        onOpenNotificationShade = currentOnOpenNotificationShade,
                                                        onPageSelected = currentOnPageSelected,
                                                        notificationsSize = currentNotifications.size,
                                                        currentIndex = currentSelectedIndex
                                                    )
                                                } else if (dragAccumulatorY > pillThreshold) {
                                                    pillDragOffsetX = 0f
                                                    dragOffset = 0f
                                                    val isHold = isHoldRegistered || totalElapsedMs >= HOLD_GESTURE_THRESHOLD_MS
                                                    if (isCircleMode || isHold || currentNotifications.isEmpty()) {
                                                        // Circle mode or hold and drag down on dynamic island opens App Launcher!
                                                        currentOnOpenAppLauncher()
                                                    } else {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeDownAction, SwipeAction.Expand)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Tap on collapsed pill: expands or reveals
                                            if (!isHoldRegistered) {
                                                pillDragOffsetX = 0f
                                                dragOffset = 0f
                                                if (settings.autoHidePill && isAutoHidden) {
                                                    currentOnAutoHiddenChanged(false)
                                                    userInteractionTimestamp = System.currentTimeMillis()
                                                } else if (isCircleMode || currentNotifications.isEmpty()) {
                                                    currentOnOpenAppLauncher()
                                                } else {
                                                    currentOnToggle()
                                                }
                                            }
                                        }
                                    }
                                }
                                break
                            } else {
                                val dragAmountY = change.positionChange().y
                                val dragAmountX = change.positionChange().x
                                dragAccumulatorY += dragAmountY
                                dragAccumulatorX += dragAmountX

                                if (abs(dragAccumulatorY) > 5f * displayMetrics.density || abs(dragAccumulatorX) > 5f * displayMetrics.density) {
                                    holdJob.cancel()
                                }

                                if (wasExpandedAtStart) {
                                    // In expanded state, only consume vertical swipes (swipe up to dismiss, swipe down for floating window).
                                    // NEVER consume horizontal gestures (abs(dragAccumulatorX) > abs(dragAccumulatorY)) so that child components
                                    // such as the horizontal App Launcher (LazyRow) and seekbars can scroll like butter!
                                    val absX = abs(dragAccumulatorX)
                                    val absY = abs(dragAccumulatorY)
                                    if (absY > absX && absY > 8f * displayMetrics.density) {
                                        isDragging = true
                                        change.consume()
                                        dragOffset = dragAccumulatorY.coerceIn(
                                            -DRAG_MAX_OFFSET_DP * displayMetrics.density,
                                            DRAG_MAX_OFFSET_DP * displayMetrics.density
                                        )
                                    }
                                } else {
                                    if (abs(dragAmountY) > 0.5f || abs(dragAmountX) > 0.5f) {
                                        isDragging = true
                                        change.consume()

                                        // Directional axis locking:
                                        // If dragging predominantly vertically (e.g. downward to expand),
                                        // lock horizontal displacement to 0 so the pill never wanders sideways!
                                        if (abs(dragAccumulatorY) > abs(dragAccumulatorX)) {
                                            pillDragOffsetX = 0f
                                            dragOffset = (dragAccumulatorY * 0.35f).coerceIn(
                                                -12f * displayMetrics.density,
                                                12f * displayMetrics.density
                                            )
                                        } else {
                                            dragOffset = 0f
                                            pillDragOffsetX = (dragAccumulatorX * 0.35f).coerceIn(
                                                -24f * displayMetrics.density,
                                                24f * displayMetrics.density
                                            )
                                        }

                                        // Snappy immediate execution when swipe threshold is crossed while dragging
                                        if (!pillGestureTriggered && (currentSettings.enablePillSwipeActions || isCircleMode)) {
                                            val pillThreshold = PILL_SWIPE_THRESHOLD_DP * displayMetrics.density
                                            val absX = abs(dragAccumulatorX)
                                            val absY = abs(dragAccumulatorY)

                                            if (absX >= pillThreshold || absY >= pillThreshold) {
                                                pillGestureTriggered = true
                                                holdJob.cancel()
                                                val currentNotification = currentNotifications.getOrNull(currentSelectedIndex)

                                                if (absX > absY) {
                                                    if (dragAccumulatorX < -pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeLeftAction, SwipeAction.PreviousNotification)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    } else if (dragAccumulatorX > pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeRightAction, SwipeAction.NextNotification)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    }
                                                } else {
                                                    if (dragAccumulatorY < -pillThreshold) {
                                                        val action = SwipeAction.fromId(currentSettings.pillSwipeUpAction, SwipeAction.DismissCurrent)
                                                        executeSwipeAction(
                                                            action = action,
                                                            currentNotification = currentNotification,
                                                            context = context,
                                                            onDismiss = currentOnDismiss,
                                                            onDismissAll = currentOnDismissAll,
                                                            onToggle = currentOnToggle,
                                                            onOpenNotification = currentOnOpenNotification,
                                                            onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                            onOpenNotificationShade = currentOnOpenNotificationShade,
                                                            onPageSelected = currentOnPageSelected,
                                                            notificationsSize = currentNotifications.size,
                                                            currentIndex = currentSelectedIndex
                                                        )
                                                    } else if (dragAccumulatorY > pillThreshold) {
                                                        pillDragOffsetX = 0f
                                                        dragOffset = 0f
                                                        if (isCircleMode || isHoldRegistered || currentNotifications.isEmpty()) {
                                                            currentOnOpenAppLauncher()
                                                        } else {
                                                            val action = SwipeAction.fromId(currentSettings.pillSwipeDownAction, SwipeAction.Expand)
                                                            executeSwipeAction(
                                                                action = action,
                                                                currentNotification = currentNotification,
                                                                context = context,
                                                                onDismiss = currentOnDismiss,
                                                                onDismissAll = currentOnDismissAll,
                                                                onToggle = currentOnToggle,
                                                                onOpenNotification = currentOnOpenNotification,
                                                                onOpenFloatingWindow = currentOnOpenFloatingWindow,
                                                                onOpenNotificationShade = currentOnOpenNotificationShade,
                                                                onPageSelected = currentOnPageSelected,
                                                                notificationsSize = currentNotifications.size,
                                                                currentIndex = currentSelectedIndex
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        holdJob.cancel()
                        if (pillDragOffsetX != 0f) {
                            val startPillOffset = pillDragOffsetX
                            scope.launch {
                                androidx.compose.animation.core.Animatable(startPillOffset).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) {
                                    pillDragOffsetX = value
                                }
                            }
                        }
                        if (dragOffset != 0f) {
                            val startDrag = dragOffset
                            scope.launch {
                                androidx.compose.animation.core.Animatable(startDrag).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) {
                                    dragOffset = value
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Collapsed content layer (pinned to fixed pill bounds at top-center, cancelling yOffset)
            if (collapsedAlpha > 0f) {

                if (isCircleMode) {
                    Box(
                        modifier = Modifier
                            .size(settings.cameraCircleSize.dp)
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBatteryRingActive) {
                            CutoutBatteryRing(
                                batteryPct = currentBatteryPct,
                                isCharging = isDeviceCharging,
                                settings = settings,
                                sizeDp = settings.cameraCircleSize.dp,
                                showCenterIcon = settings.batteryRingShowCenterIcon && isDeviceCharging
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(settings.width.dp)
                            .height(settings.height.dp)
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                alpha = collapsedAlpha
                            }
                    ) {
                        IslandCollapsedContent(
                            mode = activeMode,
                            notification = activeNotification,
                            collapsedAlpha = collapsedAlpha,
                            settings = settings
                        )
                    }
                }
            }

            // Expanded content layer — smoothly fade out while collapsing
            if (expanded || expandedAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .graphicsLayer {
                            alpha = expandedAlpha
                            scaleX = contentScale
                            scaleY = contentScale
                            translationY = contentSlideY.toPx()
                            // TransformOrigin(0.5, 0): scale from the horizontal center of the pill,
                            // at the top edge — so content blooms outward and downward from the
                            // pill's own position, just like iOS Dynamic Island.
                            // The content box is fillMaxWidth() of the PILL (safeWidth), not the
                            // screen, so (0.5, 0) correctly pivots from the pill's top-center.
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        }
                ) {
                    IslandExpandedContent(
                        notifications = notifications,
                        launcherApps = launcherApps,
                        selectedIndex = selectedIndex,
                        onPageSelected = onPageSelected,
                        onOpenNotification = onOpenNotification,
                        onLaunchApp = onLaunchApp,
                        onCollapse = onToggleExpanded,
                        // Each mode owns its natural height. The launcher already
                        // supplies its own loading height and must not impose that
                        // minimum on compact call or battery content.
                        onHeightMeasured = { expandedHeight = it },
                        settings = settings,
                        onReplyStateChanged = onReplyStateChanged,
                        onConfigureShortcuts = currentOnConfigureShortcuts,
                        showAppLauncher = showAppLauncher
                    )

                }
            }
        }

        // Camera Cutout Ring / Charging Ring — anchored directly to the camera cutout hardware position
        // using cameraCircleOffsetX and cameraCircleOffsetY, completely outside the
        // translated inner pill Box so the pill's xOffset and yOffset NEVER interfere with it!
        // Show the hardware battery ring when: customizing, static indicator enabled,
        // charging (with battery ring enabled), OR on lock screen with lock screen ring enabled.
        val shouldShowCutoutRing = isBatteryRingActive && !isCircleMode
        if (shouldShowCutoutRing) {
            val ringXOffset = settings.cameraCircleOffsetX.dp - effectiveWinX
            val ringYOffset = settings.cameraCircleOffsetY.dp - effectiveWinY
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(
                        x = ringXOffset,
                        y = ringYOffset
                    )
                    .size(settings.cameraCircleSize.dp)
                    .graphicsLayer {
                        alpha = if (expanded || expandedAlpha > 0.01f) expandedAlpha else collapsedAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                CutoutBatteryRing(
                    batteryPct = currentBatteryPct,
                    isCharging = isDeviceCharging,
                    settings = settings,
                    sizeDp = settings.cameraCircleSize.dp,
                    showCenterIcon = settings.batteryRingShowCenterIcon
                )
            }
        }

        // Collapsed: secondary circle. Expanded with 2: the same item morphs
        // into a full-size pill. Expanded with 3+: it stays the circle on the right.
        if (!settings.enableNotchMode && secondaryAlpha > 0f && secondaryNotification != null) {
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        IntOffset(
                            secondaryOffset.roundToPx(),
                            yOffset.roundToPx()
                        )
                    }
                    .width(secondaryBubbleWidth)
                    .height(circleSize)
                    .graphicsLayer {
                        alpha = secondaryAlpha
                        scaleX = secondaryScale * switchScaleAnim.value
                        scaleY = secondaryScale * switchScaleAnim.value
                    }

                    .clip(RoundedCornerShape(secondaryBubbleCorner))
                    .background(pillBackgroundColor.copy(alpha = settings.opacity))
                    .then(
                        if (shouldShowOutline && settings.outlineWidth > 0f) {
                            Modifier.border(
                                width = settings.outlineWidth.dp,
                                color = Color(settings.outlineColor),
                                shape = RoundedCornerShape(secondaryBubbleCorner)
                            )
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (secondaryIndex >= 0) {
                            onPageSelected(secondaryIndex)
                        }
                        if (!currentExpanded) {
                            currentOnToggle()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - secondaryPillProgress },
                    contentAlignment = Alignment.Center
                ) {
                    SecondaryBubbleContent(
                        notification = secondaryNotification,
                        settings = settings
                    )
                }
                Box(
                    modifier = Modifier
                        .requiredWidth(miniPillWidth)
                        .height(circleSize)
                        .graphicsLayer { alpha = secondaryPillProgress },
                    contentAlignment = Alignment.Center
                ) {
                    IslandCollapsedContent(
                        mode = secondaryNotification.mode,
                        notification = secondaryNotification,
                        collapsedAlpha = 1f,
                        settings = settings
                    )
                }
            }
        }

        if (!settings.enableNotchMode && tertiaryAlpha > 0f && tertiaryNotification != null) {
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        IntOffset(
                            (collapsedMainLeft - screenCenter + miniPillWidth / 2f).roundToPx(),
                            yOffset.roundToPx()
                        )
                    }
                    .width(miniPillWidth)
                    .height(circleSize)
                    .graphicsLayer {
                        alpha = tertiaryAlpha
                        scaleX = tertiaryScale * switchScaleAnim.value
                        scaleY = tertiaryScale * switchScaleAnim.value
                    }

                    .clip(RoundedCornerShape(settings.cornerRadius.dp))
                    .background(pillBackgroundColor.copy(alpha = settings.opacity))
                    .then(
                        if (shouldShowOutline && settings.outlineWidth > 0f) {
                            Modifier.border(
                                width = settings.outlineWidth.dp,
                                color = Color(settings.outlineColor),
                                shape = RoundedCornerShape(settings.cornerRadius.dp)
                            )
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (tertiaryIndex >= 0) {
                            onPageSelected(tertiaryIndex)
                        }
                        if (!currentExpanded) {
                            currentOnToggle()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                IslandCollapsedContent(
                    mode = tertiaryNotification.mode,
                    notification = tertiaryNotification,
                    collapsedAlpha = 1f,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun SecondaryBubbleContent(
    notification: IslandNotification,
    settings: AuraSettings
) {
    when (notification.mode) {
        IslandMode.Bluetooth -> {
            BluetoothCollapsedRight(
                notification = notification,
                settings = settings
            )
        }
        IslandMode.Flashlight -> {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.FlashlightOn,
                    contentDescription = "Flashlight",
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        IslandMode.Hotspot -> {
            HotspotCollapsedGlyph(settings = settings)
        }
        IslandMode.Battery -> {
            BatteryCollapsedGlyph(notification = notification, settings = settings)
        }
        IslandMode.LiveActivity -> {
            LiveActivityCollapsedGlyph(notification = notification, settings = settings)
        }
        IslandMode.Navigation -> {
            NavigationCollapsedGlyph(notification = notification, settings = settings)
        }
        IslandMode.IncomingCall -> {
            val icon = notification.largeIcon ?: notification.icon
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = null,
                    tint = Color(settings.callColor),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        IslandMode.Music -> {
            val artwork = notification.largeIcon ?: notification.icon
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(settings.musicVisualizerColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        IslandMode.ScreenRecording -> {
            ScreenRecordingCollapsedGlyph(settings = settings)
        }
        IslandMode.Timer -> {
            TimerCollapsedGlyph(notification = notification, settings = settings)
        }
        IslandMode.Stopwatch -> {
            StopwatchCollapsedGlyph(settings = settings)
        }
        IslandMode.Notification, IslandMode.DownloadUpload, IslandMode.Empty -> {
            NotificationGlyph(notification = notification, settings = settings)
        }
    }
}

// Animation specs
internal const val EXPANDED_WIDTH_RATIO = 0.95f

internal fun calculateExpandedWidth(
    isLandscape: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    ratio: Float = EXPANDED_WIDTH_RATIO
): Float {
    val baseWidth = if (isLandscape) minOf(screenWidthDp, screenHeightDp) else screenWidthDp
    val desired = baseWidth * ratio
    val maxWidth = minOf(440f, screenWidthDp * ratio)
    return desired.coerceIn(minOf(340f, maxWidth), maxWidth)
}

internal fun calculateExpandedTopOffset(
    enableNotchMode: Boolean,
    hasCompanion: Boolean,
    statusBarHeightDp: Float,
    pillYOffsetDp: Float = 12f,
    notchHeightDp: Float = 35f,
    circleSizeDp: Float = 34f,
    compactGapDp: Float = COMPACT_INDICATOR_GAP_DP
): Float {
    return if (enableNotchMode) {
        maxOf(statusBarHeightDp, notchHeightDp) + 8f
    } else if (hasCompanion) {
        maxOf(statusBarHeightDp, circleSizeDp + compactGapDp)
    } else {
        maxOf(statusBarHeightDp, pillYOffsetDp)
    }
}

internal fun calculateSecondaryExpandedOffset(
    secondaryIsPill: Boolean,
    isCircleLeft: Boolean,
    isFullWidth: Boolean,
    expandedCompactX: Float,
    screenCenter: Float,
    miniPillWidth: Float,
    circleSize: Float,
    compactGap: Float
): Float {
    return if (secondaryIsPill) {
        val secCenter = expandedCompactX + miniPillWidth / 2f
        if (isFullWidth) secCenter - screenCenter else 0f
    } else if (isCircleLeft) {
        val secCenter = expandedCompactX - compactGap - circleSize / 2f
        if (isFullWidth) secCenter - screenCenter else -(miniPillWidth + compactGap) / 2f
    } else {
        val secCenter = expandedCompactX + miniPillWidth + compactGap + circleSize / 2f
        if (isFullWidth) secCenter - screenCenter else ((miniPillWidth + compactGap) / 2f)
    }
}
private const val SWIPE_THRESHOLD_DP = 35f
private const val PILL_SWIPE_THRESHOLD_DP = 16f
private const val DRAG_MAX_OFFSET_DP = 100f
private const val COMPACT_INDICATOR_GAP_DP = 8f
private const val HOLD_GESTURE_THRESHOLD_MS = 300L

private fun triggerHapticVibration(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vm?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
                return
            }
        }
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60L)
            }
        }
    }
}

internal enum class CompactNotificationShape { MiniPill, Circle }

internal fun defaultEstimatedHeightForMode(mode: IslandMode?): Dp {
    return when (mode) {
        IslandMode.Music -> 175.dp
        IslandMode.Notification -> 135.dp
        IslandMode.IncomingCall, IslandMode.Battery -> 115.dp
        IslandMode.LiveActivity, IslandMode.Navigation -> 180.dp
        IslandMode.DownloadUpload, IslandMode.Hotspot -> 160.dp
        IslandMode.Bluetooth, IslandMode.Flashlight, IslandMode.ScreenRecording,
        IslandMode.Timer, IslandMode.Stopwatch -> 115.dp
        IslandMode.Empty, null -> 135.dp
    }
}

internal fun compactNotificationShapes(
    notificationCount: Int,
    expanded: Boolean
): List<CompactNotificationShape> = when {
    notificationCount < 2 -> emptyList()
    !expanded -> listOf(CompactNotificationShape.Circle)
    notificationCount == 2 -> listOf(CompactNotificationShape.MiniPill)
    else -> listOf(CompactNotificationShape.MiniPill, CompactNotificationShape.Circle)
}

private fun trySkipMedia(
    context: Context?,
    notification: IslandNotification?,
    forward: Boolean
): Boolean {
    if (context == null) return false

    // 1. Direct notification action PendingIntent (e.g. Spotify, YouTube Music, podcasts)
    val actionSent = if (forward) {
        notification.trySendFirstAction(context, "next", "skip", "forward")
    } else {
        notification.trySendFirstAction(context, "previous", "prev", "rewind")
    }
    if (actionSent) return true

    // 2. Notification MediaSession token
    val token = notification?.mediaToken
    if (token != null) {
        val success = runCatching {
            val controller = MediaController(context, token)
            if (forward) controller.transportControls.skipToNext() else controller.transportControls.skipToPrevious()
            true
        }.getOrDefault(false)
        if (success) return true
    }

    // 3. Delegate to NotificationListenerService via AuraCommand
    runCatching {
        val repo = AuraRepositories.notificationRepository(context)
        if (forward) {
            repo.sendCommand(AuraCommand.SkipNext(notification?.packageName))
        } else {
            repo.sendCommand(AuraCommand.SkipPrevious(notification?.packageName))
        }
    }

    // 4. Fallback: AudioManager media key events
    runCatching {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val keyCode = if (forward) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(down)
        audioManager?.dispatchMediaKeyEvent(up)
    }

    return true
}

private fun tryPlayPauseMedia(
    context: Context?,
    notification: IslandNotification?
) {
    if (context == null) return

    // 1. Direct notification action PendingIntent
    val actionSent = notification.trySendFirstAction(context, "play", "pause", "resume", "toggle")
    if (actionSent) return

    // 2. Notification MediaSession token
    val token = notification?.mediaToken
    if (token != null) {
        val success = runCatching {
            val controller = MediaController(context, token)
            if (notification.mediaIsPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            true
        }.getOrDefault(false)
        if (success) return
    }

    // 3. Delegate to NotificationListenerService via AuraCommand
    runCatching {
        val repo = AuraRepositories.notificationRepository(context)
        repo.sendCommand(AuraCommand.PlayPause(notification?.packageName))
    }

    // 4. Fallback: AudioManager media key events
    runCatching {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(down)
        audioManager?.dispatchMediaKeyEvent(up)
    }
}

internal fun executeSwipeAction(
    action: SwipeAction,
    currentNotification: IslandNotification?,
    context: Context? = null,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
    onToggle: () -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit,
    onPageSelected: (Int) -> Unit,
    notificationsSize: Int,
    currentIndex: Int
) {
    if (context != null && action != SwipeAction.None) {
        triggerHapticVibration(context)
    }
    when (action) {
        SwipeAction.DismissCurrent -> onDismiss()
        SwipeAction.DismissAll -> onDismissAll()
        SwipeAction.Collapse -> onToggle()
        SwipeAction.Expand -> onToggle()
        SwipeAction.OpenApp -> {
            if (currentNotification != null) {
                onOpenNotification(currentNotification)
            }
        }
        SwipeAction.FloatingWindow -> onOpenFloatingWindow()
        SwipeAction.NotificationShade -> onOpenNotificationShade()
        SwipeAction.NextPrevious, SwipeAction.NextNotification -> {
            if (notificationsSize > 1) {
                val nextIndex = (currentIndex + 1) % notificationsSize
                onPageSelected(nextIndex)
            } else {
                trySkipMedia(context, currentNotification, forward = true)
            }
        }
        SwipeAction.PreviousNotification -> {
            if (notificationsSize > 1) {
                val prevIndex = (currentIndex - 1 + notificationsSize) % notificationsSize
                onPageSelected(prevIndex)
            } else {
                trySkipMedia(context, currentNotification, forward = false)
            }
        }
        SwipeAction.NextTrack -> {
            trySkipMedia(context, currentNotification, forward = true)
        }
        SwipeAction.PreviousTrack -> {
            trySkipMedia(context, currentNotification, forward = false)
        }
        SwipeAction.PlayPause -> {
            tryPlayPauseMedia(context, currentNotification)
        }
        SwipeAction.None -> {
            // Disabled / No action
        }
    }
}
