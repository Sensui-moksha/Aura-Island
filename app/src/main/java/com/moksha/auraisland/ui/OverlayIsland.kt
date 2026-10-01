/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.moksha.auraisland.data.AppShortcutProvider
import com.moksha.auraisland.data.LaunchableApp
import com.moksha.auraisland.model.IslandNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OverlayIsland(
    viewModel: IslandViewModel,
    statusBarHeight: Float,
    onOpenNotification: (IslandNotification) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onOpenNotificationShade: () -> Unit = {},
    onConfigureShortcuts: () -> Unit = {},
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = true,
    windowXOffsetDp: Float = 0f,
    windowYOffsetDp: Float = 0f
) {
    val settings by viewModel.settings.collectAsState()
    val expanded by viewModel.expanded.collectAsState()
    val showAppLauncher by viewModel.showAppLauncher.collectAsState()
    val notifications by viewModel.visibleNotifications.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isInputActive by viewModel.isInputActive.collectAsState()
    val isAutoHidden by viewModel.isAutoHidden.collectAsState()
    val isOemYielding by viewModel.isOemYielding.collectAsState()
    val isCustomizing by viewModel.isCustomizing.collectAsState()
    val context = LocalContext.current

    val isContentRedacted = isLocked && settings.lockScreenPrivacy == "AppIconOnly"
    val processedNotifications = remember(notifications, isContentRedacted) {
        if (isContentRedacted) {
            notifications.map { notif ->
                if (notif.mode == com.moksha.auraisland.model.IslandMode.Notification) {
                    notif.copy(
                        title = notif.appName,
                        text = "Contents hidden",
                        actionIntents = emptyList()
                    )
                } else {
                    notif
                }
            }
        } else {
            notifications
        }
    }

    val selectedApps = remember(settings.shortcutPackages, settings.enableAppShortcuts) {
        if (settings.enableAppShortcuts) {
            AppShortcutProvider.selectedApps(context, settings.shortcutPackages)
        } else {
            emptyList()
        }
    }
    val launcherApps by produceState(
        initialValue = when {
            !settings.enableAppShortcuts -> emptyList()
            selectedApps.isNotEmpty() -> selectedApps
            settings.shortcutPackages.isEmpty() && !settings.showRecentApps -> emptyList()
            !AppShortcutProvider.hasUsageAccess(context) -> emptyList()
            else -> null
        },
        settings.enableAppShortcuts,
        settings.shortcutPackages,
        settings.showRecentApps
    ) {
        value = if (!settings.enableAppShortcuts) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                AppShortcutProvider.shortcuts(
                    context = context,
                    selectedPackages = settings.shortcutPackages,
                    includeRecent = settings.showRecentApps
                )
            }
        }
    }

    IslandOverlayView(
        settings = settings,
        expanded = expanded,
        showAppLauncher = showAppLauncher,
        notifications = processedNotifications,
        selectedIndex = selectedIndex,
        launcherApps = launcherApps,
        onPageSelected = { index -> viewModel.setSelectedNotificationIndex(index) },
        onOpenNotification = onOpenNotification,
        onLaunchApp = onLaunchApp,
        onToggleExpanded = { viewModel.toggleExpanded() },
        onOpenAppLauncher = { viewModel.openAppLauncher() },
        onDismissNotification = { viewModel.dismissCurrentNotification() },
        onDismissAllNotifications = { viewModel.dismissAllNotifications() },
        onOpenFloatingWindow = onOpenFloatingWindow,
        onOpenNotificationShade = onOpenNotificationShade,
        onConfigureShortcuts = onConfigureShortcuts,
        statusBarHeight = statusBarHeight,
        isInputActive = isInputActive,
        isAutoHidden = isAutoHidden,
        onAutoHiddenChanged = { viewModel.setAutoHidden(it) },
        onReplyStateChanged = { viewModel.setInputActive(it) },
        isFullWidth = isFullWidth || expanded,
        windowXOffsetDp = if (isFullWidth || expanded) 0f else windowXOffsetDp,
        windowYOffsetDp = if (isFullWidth || expanded) 0f else windowYOffsetDp,
        isOemYielding = isOemYielding,
        isCustomizing = isCustomizing,
        isLocked = isLocked,
        modifier = modifier
    )
}
