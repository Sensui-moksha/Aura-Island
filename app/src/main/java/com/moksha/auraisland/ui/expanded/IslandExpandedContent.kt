/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.moksha.auraisland.ui.expanded

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.ImageBitmap
import android.content.Context
import androidx.collection.LruCache
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.data.LaunchableApp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import com.moksha.auraisland.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope

object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun get(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
        if (bitmap != null) {
            cache.put(packageName, bitmap)
        }
        return bitmap
    }

    fun clear() {
        cache.evictAll()
    }
}

@Composable
fun IslandExpandedContent(
    notifications: List<IslandNotification>,
    launcherApps: List<LaunchableApp>?,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onLaunchApp: (String) -> Unit,
    onCollapse: () -> Unit,
    onHeightMeasured: (Dp) -> Unit,
    settings: AuraSettings,
    modifier: Modifier = Modifier,
    onReplyStateChanged: (Boolean) -> Unit = {},
    onConfigureShortcuts: () -> Unit = {},
    showAppLauncher: Boolean = false,
    onDismissNotification: (IslandNotification) -> Unit = {}
) {
    if (showAppLauncher || notifications.isEmpty()) {
        if (!settings.enableAppShortcuts && !settings.enableCutoutRingMode && notifications.isEmpty()) {
            return
        }
        val density = LocalDensity.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true)
                .onSizeChanged {
                    val measuredHeight = with(density) { it.height.toDp() }
                    if (measuredHeight > 0.dp) {
                        val clamped = measuredHeight.coerceIn(80.dp, 180.dp)
                        onHeightMeasured(clamped)
                    }
                }
        ) {
            if (settings.enableNotificationBackdrop) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(settings.notificationDotColor).copy(alpha = 0.22f),
                                    Color(settings.notificationDotColor).copy(alpha = 0.06f),
                                    Color.Transparent
                                ),
                                center = Offset(0f, 0f),
                                radius = 700f
                            )
                        )
                )
            }
            EmptyExpanded(
                settings = settings,
                apps = launcherApps,
                onLaunchApp = onLaunchApp,
                onConfigureShortcuts = onConfigureShortcuts
            )
        }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var pageHeights by remember { mutableStateOf(emptyMap<String, Dp>()) }

    // Clean up stale keys not present in notifications
    val activeKeys = remember(notifications) { notifications.map { it.key }.toSet() }
    LaunchedEffect(activeKeys) {
        pageHeights = pageHeights.filterKeys { it in activeKeys }
    }

    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, notifications.lastIndex),
        pageCount = { notifications.size }
    )

    // Sync external selectedIndex updates ONLY when not currently user-scrolling,
    // preventing gesture interrupts mid-swipe that cause half-page frozen states.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in notifications.indices &&
            !pagerState.isScrollInProgress &&
            pagerState.currentPage != selectedIndex
        ) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    // Sync settled page updates back to caller ONLY when scroll has settled
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage in notifications.indices) {
            onPageSelected(pagerState.settledPage)
        }
    }

    // Safety net: Ensure pager never stays stuck at a non-zero offset fraction when scroll finishes
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction != 0f) {
            val targetPage = pagerState.settledPage.coerceIn(0, (notifications.size - 1).coerceAtLeast(0))
            if (targetPage in notifications.indices) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    val bottomPadding = 16.dp

    Column(modifier = modifier.fillMaxWidth().wrapContentHeight()) {

        // Interpolate height between pages based on swipe progress
        // FIX: Clamp battery/call heights to match music/notification style to avoid
        // glitch where battery/call expand more (taller) than other modes.
        val currentPage = pagerState.currentPage
        val offsetFraction = pagerState.currentPageOffsetFraction
        val currentNotification = notifications.getOrNull(currentPage)

        fun clampHeightForMode(notif: IslandNotification?, height: Dp): Dp {
            return when (notif?.mode) {
                IslandMode.Battery, IslandMode.IncomingCall -> height.coerceIn(72.dp, 125.dp)
                IslandMode.Notification -> height.coerceIn(95.dp, 145.dp)
                IslandMode.Music -> height.coerceIn(115.dp, 180.dp)
                IslandMode.LiveActivity -> height.coerceIn(140.dp, 205.dp)
                IslandMode.Navigation -> height.coerceIn(135.dp, 195.dp)
                IslandMode.DownloadUpload -> height.coerceIn(120.dp, 195.dp)
                IslandMode.Hotspot -> height.coerceIn(120.dp, 195.dp)
                IslandMode.Bluetooth -> height.coerceIn(72.dp, 130.dp)
                IslandMode.Flashlight -> height.coerceIn(72.dp, 130.dp)
                IslandMode.ScreenRecording -> height.coerceIn(72.dp, 130.dp)
                IslandMode.Timer -> height.coerceIn(72.dp, 130.dp)
                IslandMode.Stopwatch -> height.coerceIn(72.dp, 130.dp)
                else -> height.coerceIn(80.dp, 160.dp)
            }
        }

        val currentPageHeightRaw = currentNotification?.let { pageHeights[it.key] }
        val currentPageHeight = currentPageHeightRaw?.let { clampHeightForMode(currentNotification, it) }
            ?: com.moksha.auraisland.ui.defaultEstimatedHeightForMode(currentNotification?.mode)

        val targetHeight = run {
            val nextPage = if (offsetFraction > 0f) {
                (currentPage + 1).coerceAtMost(notifications.lastIndex)
            } else if (offsetFraction < 0f) {
                (currentPage - 1).coerceAtLeast(0)
            } else {
                currentPage
            }
            val nextNotification = notifications.getOrNull(nextPage)
            val nextHeightRaw = nextNotification?.let { pageHeights[it.key] }
            val nextHeight = (nextHeightRaw?.let { clampHeightForMode(nextNotification, it) }
                ?: com.moksha.auraisland.ui.defaultEstimatedHeightForMode(nextNotification?.mode))
            val fraction = kotlin.math.abs(offsetFraction)
            (currentPageHeight + (nextHeight - currentPageHeight) * fraction).coerceIn(72.dp, 205.dp)
        }

        LaunchedEffect(targetHeight) {
            onHeightMeasured(targetHeight)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetHeight)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    // unbounded = true: pages measure at natural height even when parent Box has explicit height
                    .wrapContentHeight(unbounded = true)
            ) { page ->
                val notification = notifications.getOrNull(page)
                if (notification != null) {
                    val isDismissible = when (notification.mode) {
                        IslandMode.Notification,
                        IslandMode.LiveActivity,
                        IslandMode.DownloadUpload,
                        IslandMode.Bluetooth,
                        IslandMode.Hotspot -> true
                        IslandMode.Battery -> !settings.enableCutoutRingMode
                        else -> false
                    }
                    val isNonClickable = notification.mode == IslandMode.Battery && settings.enableCutoutRingMode

                    DismissibleNotificationCard(
                        notification = notification,
                        onOpenNotification = onOpenNotification,
                        onDismissNotification = onDismissNotification,
                        isDismissible = isDismissible,
                        isNonClickable = isNonClickable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .onSizeChanged { size ->
                                val heightDp = with(density) { size.height.toDp() }
                                if (pageHeights[notification.key] != heightDp) {
                                    pageHeights = pageHeights.toMutableMap().apply { put(notification.key, heightDp) }
                                }
                            }
                    ) {
                        if (settings.enableNotificationBackdrop && notification.mode != IslandMode.Music) {
                            NotificationBackdrop(
                                notification = notification,
                                settings = settings,
                                modifier = Modifier.matchParentSize()
                            )
                        }

                        when (notification.mode) {
                            IslandMode.Notification -> NotificationExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                showActions = settings.showNotificationActions,
                                settings = settings,
                                onReplyStateChanged = onReplyStateChanged
                            )
                            IslandMode.IncomingCall -> IncomingCallExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Music -> MusicExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                settings = settings
                            )
                            IslandMode.Battery -> BatteryExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                settings = settings
                            )
                            IslandMode.LiveActivity -> LiveActivityExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Navigation -> NavigationExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.DownloadUpload -> DownloadExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Hotspot -> HotspotExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Bluetooth -> BluetoothExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Flashlight -> FlashlightExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.ScreenRecording -> ScreenRecordingExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Timer -> TimerExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Stopwatch -> StopwatchExpanded(
                                notification = notification,
                                bottomPadding = bottomPadding,
                                onOpenNotification = { onOpenNotification(notification) },
                                onCollapse = onCollapse,
                                settings = settings
                            )
                            IslandMode.Empty -> EmptyExpanded(
                                settings = settings,
                                apps = launcherApps,
                                onLaunchApp = onLaunchApp,
                                onConfigureShortcuts = onConfigureShortcuts
                            )
                        }
                    }
                }
            }

            if (notifications.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notifications.indices.forEach { index ->
                        val isCurrent = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(4.dp)
                                .width(if (isCurrent) 14.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) Color(settings.notificationDotColor)
                                    else Color.White.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                        onPageSelected(index)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DismissibleNotificationCard(
    notification: IslandNotification,
    onOpenNotification: (IslandNotification) -> Unit,
    onDismissNotification: (IslandNotification) -> Unit,
    isDismissible: Boolean,
    isNonClickable: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 52.dp.toPx() }
    val tapSlopPx = with(density) { 12.dp.toPx() }

    val offsetX = remember(notification.key) { Animatable(0f) }
    val alphaAnim = remember(notification.key) { Animatable(1f) }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                val dragRatio = (kotlin.math.abs(offsetX.value) / (dismissThresholdPx * 2.5f)).coerceIn(0f, 0.65f)
                alpha = alphaAnim.value * (1f - dragRatio)
            }
            .then(
                when {
                    isNonClickable -> Modifier
                    isDismissible -> Modifier.pointerInput(notification.key) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isHorizontalDrag = false
                            var isVerticalDrag = false
                            val pointerId = down.id

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                if (change.changedToUp()) {
                                    if (isHorizontalDrag && kotlin.math.abs(offsetX.value) >= dismissThresholdPx) {
                                        change.consume()
                                        val targetX = if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat()
                                        scope.launch {
                                            launch { alphaAnim.animateTo(0f, tween(120)) }
                                            offsetX.animateTo(targetX, tween(120))
                                            onDismissNotification(notification)
                                        }
                                    } else if (!isHorizontalDrag && !isVerticalDrag && !change.isConsumed &&
                                               kotlin.math.abs(totalDragX) < tapSlopPx &&
                                               kotlin.math.abs(totalDragY) < tapSlopPx) {
                                        change.consume()
                                        onOpenNotification(notification)
                                    } else {
                                        if (isHorizontalDrag) {
                                            change.consume()
                                            scope.launch {
                                                offsetX.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    break
                                } else {
                                    val dragX = change.positionChange().x
                                    val dragY = change.positionChange().y
                                    totalDragX += dragX
                                    totalDragY += dragY

                                    if (!isHorizontalDrag && !isVerticalDrag) {
                                        val absX = kotlin.math.abs(totalDragX)
                                        val absY = kotlin.math.abs(totalDragY)
                                        if (absX > tapSlopPx && absX > absY) {
                                            isHorizontalDrag = true
                                        } else if (absY > tapSlopPx && absY > absX) {
                                            isVerticalDrag = true
                                        }
                                    }

                                    if (isHorizontalDrag) {
                                        change.consume()
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + dragX)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onOpenNotification(notification)
                    }
                }
            )
    ) {
        content()
    }
}

// ─── Premium Island Launcher ──────────────────────────────────────────────────

@Composable
private fun EmptyExpanded(
    settings: AuraSettings,
    apps: List<LaunchableApp>?,
    onLaunchApp: (String) -> Unit,
    onConfigureShortcuts: () -> Unit = {}
) {
    val context = LocalContext.current
    val hasConfiguration = settings.shortcutPackages.isNotEmpty() || settings.showRecentApps
    val accentColor = Color(settings.notificationDotColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(unbounded = true)
            .padding(top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            // Loading skeleton — keep height stable while data is fetched
            apps == null -> {
                LauncherHeader(accentColor = accentColor, modifier = Modifier.padding(horizontal = 16.dp))
                LauncherSkeleton(accentColor)
            }

            // No apps configured and no recent apps setting
            apps.isEmpty() && !hasConfiguration -> {
                LauncherEmptyState(
                    accentColor = accentColor,
                    onConfigure = onConfigureShortcuts
                )
            }

            // Configured but UsageStats returned nothing
            apps.isEmpty() -> {
                LauncherHeader(accentColor = accentColor, modifier = Modifier.padding(horizontal = 16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.quick_launch_apps_unavailable),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }
            }

            // Premium horizontal scrollable app row
            else -> {
                LauncherHeader(accentColor = accentColor, modifier = Modifier.padding(horizontal = 16.dp))
                LauncherRow(
                    apps = apps,
                    accentColor = accentColor,
                    onLaunchApp = onLaunchApp
                )
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
private fun LauncherHeader(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Quick Launch",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

// ── Horizontal Scrollable App Row ─────────────────────────────────────────────
@Composable
private fun LauncherRow(
    apps: List<LaunchableApp>,
    accentColor: Color,
    onLaunchApp: (String) -> Unit
) {
    val scrollState = rememberLazyListState()
    var lastScrollTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            lastScrollTimeMs = System.currentTimeMillis()
        }
    }

    LazyRow(
        state = scrollState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        flingBehavior = ScrollableDefaults.flingBehavior(),
        userScrollEnabled = true
    ) {
        itemsIndexed(
            items = apps,
            key = { _, app -> app.packageName }
        ) { index, app ->
            PremiumAppIcon(
                app = app,
                staggerIndex = index,
                accentColor = accentColor,
                onClick = {
                    val now = System.currentTimeMillis()
                    if (!scrollState.isScrollInProgress && now - lastScrollTimeMs > 250L) {
                        onLaunchApp(app.packageName)
                    }
                }
            )
        }
    }
}

// ── Individual App Icon ───────────────────────────────────────────────────────
@Composable
private fun PremiumAppIcon(
    app: LaunchableApp,
    staggerIndex: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        AppIconCache.get(context, app.packageName)
    }

    // Staggered entrance: each icon fades+scales in with a cascading delay (max 8 for initial viewport)
    val entranceAlpha = remember { Animatable(0f) }
    val entranceScale = remember { Animatable(0.65f) }
    LaunchedEffect(Unit) {
        delay(staggerIndex.coerceAtMost(8) * 35L)
        launch {
            entranceAlpha.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
        entranceScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    // Press interaction: smooth squish down then spring back
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            pressScale.animateTo(
                0.86f,
                spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        } else {
            pressScale.animateTo(
                1f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
    }

    Column(
        modifier = Modifier
            .width(58.dp)
            .graphicsLayer {
                alpha = entranceAlpha.value
                scaleX = entranceScale.value * pressScale.value
                scaleY = entranceScale.value * pressScale.value
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Glassy squircle backdrop with subtle gradient border
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f))
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Text(
            text = app.label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun LauncherEmptyState(
    accentColor: Color,
    onConfigure: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.shortcuts_title),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.shortcuts_desc),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        // Capsule CTA button
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.85f), accentColor)
                    ),
                    shape = RoundedCornerShape(50)
                )
                .clickable(onClick = onConfigure)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.quick_launch_open_settings),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Loading Skeleton ──────────────────────────────────────────────────────────
@Composable
private fun LauncherSkeleton(accentColor: Color) {
    val shimmer = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        while (true) {
            shimmer.animateTo(0.55f, tween(800))
            shimmer.animateTo(0.2f, tween(800))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(5) {
            Column(
                modifier = Modifier.width(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            Color.White.copy(alpha = shimmer.value),
                            RoundedCornerShape(14.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(7.dp)
                        .background(
                            Color.White.copy(alpha = shimmer.value * 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun NotificationBackdrop(
    notification: IslandNotification,
    settings: AuraSettings,
    modifier: Modifier = Modifier
) {
    val artwork = notification.largeIcon ?: notification.icon
    val imageBitmap = remember(artwork) {
        if (artwork != null && !artwork.isRecycled) {
            runCatching { artwork.asImageBitmap() }.getOrNull()
        } else null
    }

    val accentColor = when (notification.mode) {
        IslandMode.Battery -> Color(settings.batteryColor)
        IslandMode.IncomingCall -> Color(settings.callColor)
        IslandMode.LiveActivity -> Color(settings.liveActivityColor)
        IslandMode.Navigation -> Color(settings.navigationColor)
        IslandMode.DownloadUpload -> Color(settings.transferColor)
        IslandMode.Hotspot -> Color(settings.hotspotColor)
        IslandMode.Bluetooth -> Color(settings.bluetoothColor)
        IslandMode.Flashlight -> Color(settings.flashlightColor)
        IslandMode.ScreenRecording -> Color(settings.screenRecordingColor)
        IslandMode.Timer -> Color(settings.timerColor)
        IslandMode.Stopwatch -> Color(settings.stopwatchColor)
        else -> Color(settings.notificationDotColor)
    }

    Box(modifier = modifier) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.38f }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 700f
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.35f),
                                accentColor.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 750f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }
    }
}

