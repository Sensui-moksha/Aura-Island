/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moksha.auraisland.BuildConfig
import com.moksha.auraisland.R
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.data.SettingsBackupManager
import com.moksha.auraisland.di.AuraRepositories
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.service.AuraNotificationListenerService
import com.moksha.auraisland.ui.sections.AboutSection
import com.moksha.auraisland.ui.sections.AppShortcutsSection
import com.moksha.auraisland.ui.sections.BackupRestoreSection
import com.moksha.auraisland.ui.sections.CustomizationsSection
import com.moksha.auraisland.ui.sections.DeveloperOptionsSection
import com.moksha.auraisland.ui.sections.GesturesSection
import com.moksha.auraisland.ui.sections.NotificationHistorySection
import com.moksha.auraisland.ui.sections.NotificationsAndPrivacySection
import com.moksha.auraisland.ui.sections.PermissionsSection
import com.moksha.auraisland.ui.sections.PositionsSection
import com.moksha.auraisland.ui.sections.SupportSection
import com.moksha.auraisland.ui.sections.ThemeSettingsSection
import com.moksha.auraisland.util.PermissionUtils
import com.moksha.auraisland.util.RootManager
import com.moksha.auraisland.util.SystemServiceRecovery
import com.moksha.auraisland.util.runCatchingLogged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StudioTab {
    Studio,
    Position,
    Settings
}

private enum class FeatureDetailSection {
    NotificationRules,
    AppShortcuts,
    NotificationHistory,
    ThemeSettings,
    ColorStudio,
    GesturesGuide,
    PermissionsCenter,
    BackupRestore,
    AboutApp,
    SupportCommunity,
    DeveloperOptions
}

@SuppressLint("BatteryLife")
@Composable
fun AuraHomeScreen(
    repository: AuraSettingsRepository? = null,
    notificationRepository: INotificationRepository? = null,
    initialDetailSection: String? = null,
    onClearInitialDetailSection: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val resolvedRepository = remember(repository, context) {
        repository ?: runCatching {
            AuraRepositories.settingsRepository(context)
        }.getOrElse {
            AuraSettingsRepository(context.applicationContext)
        }
    }
    val resolvedNotificationRepository = remember(notificationRepository, context) {
        notificationRepository ?: runCatching {
            AuraRepositories.notificationRepository(context)
        }.getOrNull()
    }

    val settings by resolvedRepository.settings.collectAsStateWithLifecycle(initialValue = AuraSettings.Default)
    val scope = rememberCoroutineScope()

    var showWelcomeDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    var overlayGranted by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context)) }
    var notificationGranted by remember { mutableStateOf(PermissionUtils.isNotificationListenerEnabled(context)) }
    var batteryIgnored by remember { mutableStateOf(PermissionUtils.isBatteryOptimizationIgnored(context)) }

    val isRootEffectivelyEnabled = settings.rootModeEnabled && RootManager.isRootAvailable(context)

    LaunchedEffect(settings.rootModeEnabled) {
        if (!resolvedRepository.isWelcomeDialogShown()) {
            // Check if there's a persistent backup from a previous install
            if (SettingsBackupManager.shouldOfferRestore(context)) {
                showRestoreDialog = true
            } else {
                showWelcomeDialog = true
            }
        }
        // Mark this install so future launches don't re-trigger restore
        SettingsBackupManager.markInstalled(context)

        if (isRootEffectivelyEnabled) {
            withContext(Dispatchers.IO) {
                if (RootManager.isRootGranted()) {
                    if (!PermissionUtils.isAccessibilityServiceEnabled(context) ||
                        !PermissionUtils.isNotificationListenerEnabled(context) ||
                        !PermissionUtils.isBatteryOptimizationIgnored(context)
                    ) {
                        RootManager.autoGrantAllPermissions(context)
                    }
                }
            }
            overlayGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
            notificationGranted = PermissionUtils.isNotificationListenerEnabled(context)
            batteryIgnored = PermissionUtils.isBatteryOptimizationIgnored(context)
            SystemServiceRecovery.requestRecovery(context)
        }
    }

    if (showRestoreDialog) {
        RestoreBackupDialog(
            onRestore = {
                showRestoreDialog = false
                scope.launch {
                    val backup = SettingsBackupManager.loadBackup(context)
                    if (backup != null) {
                        resolvedRepository.restoreSettings(backup.copy(welcomeDialogShown = true))
                        Toast.makeText(context, "Settings restored from backup", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Backup could not be read", Toast.LENGTH_SHORT).show()
                        showWelcomeDialog = true
                    }
                }
            },
            onSkip = {
                showRestoreDialog = false
                showWelcomeDialog = true
            }
        )
    }

    if (showWelcomeDialog) {
        WelcomeDialog(
            onDismiss = {
                showWelcomeDialog = false
                scope.launch { resolvedRepository.setWelcomeDialogShown(true) }
            }
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
                notificationGranted = PermissionUtils.isNotificationListenerEnabled(context)
                batteryIgnored = PermissionUtils.isBatteryOptimizationIgnored(context)
                SystemServiceRecovery.requestRecovery(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by remember { mutableStateOf(StudioTab.Studio) }
    var activeDetailSection by remember { mutableStateOf<FeatureDetailSection?>(null) }
    var transitionDirection by remember { mutableIntStateOf(1) } // 1 = forward, -1 = backward

    LaunchedEffect(initialDetailSection) {
        if (initialDetailSection == "AppShortcuts") {
            transitionDirection = 1
            activeDetailSection = FeatureDetailSection.AppShortcuts
            onClearInitialDetailSection()
        }
    }

    // Active preview mode for interactive live preview
    var previewMode by remember { mutableStateOf(IslandMode.Music) }

    val canEnable = overlayGranted && notificationGranted && batteryIgnored

    BackHandler(enabled = activeDetailSection != null) {
        transitionDirection = -1
        activeDetailSection = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (activeDetailSection == null) {
                StudioBottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        transitionDirection = if (tab.ordinal > selectedTab.ordinal) 1 else -1
                        selectedTab = tab
                    }
                )
            }
        }
    ) { scaffoldPadding ->
        AnimatedContent(
            targetState = activeDetailSection,
            modifier = Modifier.padding(scaffoldPadding),
            transitionSpec = {
                if (transitionDirection == 1) {
                    (slideInHorizontally(initialOffsetX = { it }) + fadeIn())
                        .togetherWith(slideOutHorizontally(targetOffsetX = { -it }) + fadeOut())
                } else {
                    (slideInHorizontally(initialOffsetX = { -it }) + fadeIn())
                        .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut())
                }
            },
            label = "ScreenTransition"
        ) { detailSection ->
            if (detailSection == null) {
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val horizontalPad = when {
                    screenWidthDp < 360 -> 14.dp
                    screenWidthDp > 600 -> 28.dp
                    else -> 20.dp
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = horizontalPad,
                            end = horizontalPad,
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                            bottom = 32.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Studio Top Header
                    StudioTopHeader(
                        isIslandEnabled = settings.enabled,
                        canEnable = canEnable,
                        onHealthClick = {
                            transitionDirection = 1
                            activeDetailSection = FeatureDetailSection.PermissionsCenter
                        }
                    )

                    when (selectedTab) {
                        StudioTab.Studio -> {
                            // 1. Master Power Switch Card
                            MasterPowerCard(
                                enabled = settings.enabled,
                                canEnable = canEnable,
                                onCheckedChange = { turnOn ->
                                    if (turnOn) {
                                        SystemServiceRecovery.requestRecovery(context)
                                    }
                                    scope.launch { resolvedRepository.setEnabled(turnOn) }
                                },
                                onSetupPermissionsClick = {
                                    if (isRootEffectivelyEnabled) {
                                        scope.launch {
                                            if (RootManager.isRootGranted()) {
                                                RootManager.autoGrantAllPermissions(context)
                                                overlayGranted = PermissionUtils.isAccessibilityServiceEnabled(context)
                                                notificationGranted = PermissionUtils.isNotificationListenerEnabled(context)
                                                batteryIgnored = PermissionUtils.isBatteryOptimizationIgnored(context)
                                                SystemServiceRecovery.requestRecovery(context)
                                                if (overlayGranted && notificationGranted) {
                                                    resolvedRepository.setEnabled(true)
                                                    Toast.makeText(context, "Permissions auto-granted via Root! Aura enabled.", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                            }
                                            transitionDirection = 1
                                            activeDetailSection = FeatureDetailSection.PermissionsCenter
                                        }
                                    } else {
                                        transitionDirection = 1
                                        activeDetailSection = FeatureDetailSection.PermissionsCenter
                                    }
                                }
                            )

                            // 2. Interactive Simulation Lab
                            SimulationLabCard(
                                activeMode = previewMode,
                                onModeSelect = { mode ->
                                    previewMode = mode
                                    resolvedNotificationRepository?.showDemo(mode)
                                },
                                onClearAll = {
                                    resolvedNotificationRepository?.clearTestNotifications()
                                    Toast.makeText(context, context.getString(R.string.toast_cleared_test_notifications), Toast.LENGTH_SHORT).show()
                                }
                            )

                        }

                        StudioTab.Position -> {
                            PositionsSection(
                                settings = settings,
                                repository = resolvedRepository,
                                notificationRepository = resolvedNotificationRepository,
                                onNavigateToBackup = {
                                    transitionDirection = 1
                                    activeDetailSection = FeatureDetailSection.BackupRestore
                                }
                            )
                        }

                        StudioTab.Settings -> {
                            SettingsOverviewSection(
                                settings = settings,
                                overlayGranted = overlayGranted,
                                notificationGranted = notificationGranted,
                                batteryIgnored = batteryIgnored,
                                onNavigateTo = { section ->
                                    transitionDirection = 1
                                    activeDetailSection = section
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.made_by),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                DetailScreenHost(
                    section = detailSection,
                    settings = settings,
                    repository = resolvedRepository,
                    overlayGranted = overlayGranted,
                    notificationGranted = notificationGranted,
                    batteryIgnored = batteryIgnored,
                    onBack = {
                        transitionDirection = -1
                        activeDetailSection = null
                    },
                    onRefreshPermissions = {
                        overlayGranted = isAccessibilityServiceEnabled(context)
                        notificationGranted = isNotificationListenerEnabled(context)
                        batteryIgnored = isBatteryOptimizationIgnored(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun StudioTopHeader(
    isIslandEnabled: Boolean,
    canEnable: Boolean,
    onHealthClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "Aura Logo",
                modifier = Modifier.size(44.dp)
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (BuildConfig.DEBUG) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFEF4444).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEBUG BUILD",
                                color = Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Health Status Badge
        val statusColor = when {
            !canEnable -> Color(0xFFE88C25) // Action required
            isIslandEnabled -> Color(0xFF0F9F6E) // Active
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) // Ready/Off
        }
        val statusText = when {
            !canEnable -> stringResource(R.string.status_setup_needed)
            isIslandEnabled -> stringResource(R.string.status_active)
            else -> stringResource(R.string.status_ready)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .clickable(onClick = onHealthClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MasterPowerCard(
    enabled: Boolean,
    canEnable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSetupPermissionsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.master_switch_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (canEnable) {
                        if (enabled) stringResource(R.string.master_switch_active_desc)
                        else stringResource(R.string.master_switch_ready_desc)
                    } else stringResource(R.string.master_switch_missing_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                enabled = canEnable || enabled,
                onCheckedChange = onCheckedChange
            )
        }

        if (!canEnable) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSetupPermissionsClick)
                    .background(Color(0xFFE88C25).copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE88C25),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.btn_grant_required_permissions),
                        color = Color(0xFFE88C25),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SimulationLabCard(
    activeMode: IslandMode,
    onModeSelect: (IslandMode) -> Unit,
    onClearAll: () -> Unit
) {
    data class ModeItem(val mode: IslandMode, val icon: ImageVector)
    val modes = remember {
        listOf(
            ModeItem(IslandMode.Music, Icons.Rounded.MusicNote),
            ModeItem(IslandMode.IncomingCall, Icons.Rounded.Call),
            ModeItem(IslandMode.Notification, Icons.Rounded.Notifications),
            ModeItem(IslandMode.Battery, Icons.Rounded.BatteryChargingFull),
            ModeItem(IslandMode.LiveActivity, Icons.Rounded.Navigation),
            ModeItem(IslandMode.Navigation, Icons.Rounded.Explore),
            ModeItem(IslandMode.DownloadUpload, Icons.Rounded.FileDownload),
            ModeItem(IslandMode.Hotspot, Icons.Rounded.WifiTethering),
            ModeItem(IslandMode.Bluetooth, Icons.Rounded.BluetoothConnected),
            ModeItem(IslandMode.Flashlight, Icons.Rounded.FlashlightOn),
            ModeItem(IslandMode.ScreenRecording, Icons.Rounded.Videocam),
            ModeItem(IslandMode.Timer, Icons.Rounded.HourglassBottom),
            ModeItem(IslandMode.Stopwatch, Icons.Rounded.AvTimer)
        )
    }
    // Resolve labels via composable context
    val modeLabels = listOf(
        stringResource(R.string.mode_music_player),
        stringResource(R.string.mode_incoming_call),
        stringResource(R.string.mode_notification),
        stringResource(R.string.mode_battery_charge),
        stringResource(R.string.mode_live_activity),
        stringResource(R.string.mode_turn_navigation),
        stringResource(R.string.mode_file_transfer),
        stringResource(R.string.mode_hotspot_share),
        stringResource(R.string.mode_bluetooth),
        stringResource(R.string.mode_flashlight),
        stringResource(R.string.mode_screen_recording),
        stringResource(R.string.mode_timer),
        stringResource(R.string.mode_stopwatch)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.simulation_lab_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.simulation_lab_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.btn_clear_all_test_notifications),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 330.dp -> 3
                    maxWidth >= 210.dp -> 2
                    else -> 1
                }
                val chunkedIndices = remember(modes.size, columns) {
                    modes.indices.chunked(columns)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunkedIndices.forEach { rowIndices ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowIndices.forEach { index ->
                                val item = modes[index]
                                ModeChipButton(
                                    label = modeLabels[index],
                                    icon = item.icon,
                                    isSelected = activeMode == item.mode,
                                    onClick = { onModeSelect(item.mode) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowIndices.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChipButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) iconTint.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) iconTint.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp)
            )
            .bounceClick(onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) iconTint else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsOverviewSection(
    settings: AuraSettings,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    batteryIgnored: Boolean,
    onNavigateTo: (FeatureDetailSection) -> Unit
) {
    val canEnable = overlayGranted && notificationGranted && batteryIgnored

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section 1: Features
        SettingsCategoryGroup(title = stringResource(R.string.category_behaviors_launcher)) {
            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_notifications_privacy_title),
                icon = Icons.Rounded.Notifications,
                statusText = if (settings.showOnLockScreen) stringResource(R.string.card_notifications_privacy_status_lock) else null,
                onClick = { onNavigateTo(FeatureDetailSection.NotificationRules) }
            )

            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_app_shortcuts_title),
                icon = Icons.Rounded.Apps,
                statusText = if (settings.enableAppShortcuts) {
                    stringResource(R.string.card_app_shortcuts_status, settings.shortcutPackages.size)
                } else null,
                statusColor = if (settings.enableAppShortcuts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onNavigateTo(FeatureDetailSection.AppShortcuts) }
            )

            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_notification_history_title),
                icon = Icons.Rounded.History,
                statusText = if (settings.enableNotificationHistory) stringResource(R.string.card_notification_history_status_active) else null,
                statusColor = if (settings.enableNotificationHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onNavigateTo(FeatureDetailSection.NotificationHistory) }
            )
        }

        // Section 2: Appearance
        SettingsCategoryGroup(title = stringResource(R.string.category_appearance_controls)) {
            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_theme_settings_title),
                icon = Icons.Rounded.DarkMode,
                statusText = when (settings.themeMode) {
                    AuraSettings.THEME_MODE_DARK -> if (settings.useDynamicColor) "Dynamic Dark" else "Dark (B&W)"
                    AuraSettings.THEME_MODE_LIGHT -> if (settings.useDynamicColor) "Dynamic Light" else "Light (B&W)"
                    else -> if (settings.useDynamicColor) "Dynamic System" else "System (B&W)"
                },
                onClick = { onNavigateTo(FeatureDetailSection.ThemeSettings) }
            )

            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_color_studio_title),
                icon = Icons.Rounded.Palette,
                statusText = stringResource(R.string.card_color_studio_status, (settings.opacity * 100).toInt()),
                onClick = { onNavigateTo(FeatureDetailSection.ColorStudio) }
            )

            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_gestures_guide_title),
                icon = Icons.Rounded.Gesture,
                onClick = { onNavigateTo(FeatureDetailSection.GesturesGuide) }
            )
        }

        // Section 3: System
        SettingsCategoryGroup(title = stringResource(R.string.category_system_core)) {
            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_permissions_setup_title),
                icon = Icons.Rounded.Shield,
                statusText = if (!canEnable) stringResource(R.string.status_action_required) else null,
                statusColor = if (canEnable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                onClick = { onNavigateTo(FeatureDetailSection.PermissionsCenter) }
            )

            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_backup_restore_title),
                icon = Icons.Rounded.SettingsBackupRestore,
                onClick = { onNavigateTo(FeatureDetailSection.BackupRestore) }
            )
        }

        // Section 4: About
        SettingsCategoryGroup(title = stringResource(R.string.category_about_community)) {
            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_about_app_title),
                icon = Icons.Rounded.Info,
                statusText = "v${BuildConfig.VERSION_NAME}",
                onClick = { onNavigateTo(FeatureDetailSection.AboutApp) }
            )

            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_support_requests_title),
                icon = Icons.Rounded.People,
                onClick = { onNavigateTo(FeatureDetailSection.SupportCommunity) }
            )
        }

        SettingsCategoryGroup(title = stringResource(R.string.category_developer_options)) {
            FeatureStudioNavigationCard(
                title = stringResource(R.string.card_developer_options_title),
                icon = Icons.Rounded.Tune,
                statusText = if (settings.recordLogs) stringResource(R.string.status_recording_active) else null,
                statusColor = if (settings.recordLogs) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                onClick = { onNavigateTo(FeatureDetailSection.DeveloperOptions) }
            )
        }
    }
}

@Composable
private fun SettingsCategoryGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        content()
    }
}

@Composable
private fun FeatureStudioNavigationCard(
    title: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    statusText: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .bounceClick(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (statusText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StudioBottomNavigationBar(
    selectedTab: StudioTab,
    onTabSelected: (StudioTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == StudioTab.Studio,
            onClick = { onTabSelected(StudioTab.Studio) },
            icon = { Icon(Icons.Rounded.FlashOn, contentDescription = stringResource(R.string.tab_studio)) },
            label = { Text(stringResource(R.string.tab_studio), fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )
        NavigationBarItem(
            selected = selectedTab == StudioTab.Position,
            onClick = { onTabSelected(StudioTab.Position) },
            icon = { Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.tab_position)) },
            label = { Text(stringResource(R.string.tab_position), fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )
        NavigationBarItem(
            selected = selectedTab == StudioTab.Settings,
            onClick = { onTabSelected(StudioTab.Settings) },
            icon = { Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.tab_settings)) },
            label = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun DetailScreenHost(
    section: FeatureDetailSection,
    settings: AuraSettings,
    repository: AuraSettingsRepository,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    batteryIgnored: Boolean,
    onBack: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isScrollableParent = section != FeatureDetailSection.NotificationHistory
    val scrollModifier = if (isScrollableParent) Modifier.verticalScroll(rememberScrollState()) else Modifier
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPad = when {
        screenWidthDp < 360 -> 14.dp
        screenWidthDp > 600 -> 28.dp
        else -> 20.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(scrollModifier)
            .padding(
                start = horizontalPad,
                end = horizontalPad,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = if (isScrollableParent) 28.dp else 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val title = when (section) {
                FeatureDetailSection.NotificationRules -> stringResource(R.string.detail_title_notifications_privacy)
                FeatureDetailSection.AppShortcuts -> stringResource(R.string.detail_title_app_shortcuts)
                FeatureDetailSection.NotificationHistory -> stringResource(R.string.detail_title_notification_history)
                FeatureDetailSection.ThemeSettings -> stringResource(R.string.card_theme_settings_title)
                FeatureDetailSection.ColorStudio -> stringResource(R.string.detail_title_color_studio)
                FeatureDetailSection.GesturesGuide -> stringResource(R.string.detail_title_gestures_guide)
                FeatureDetailSection.PermissionsCenter -> stringResource(R.string.detail_title_permissions_center)
                FeatureDetailSection.BackupRestore -> stringResource(R.string.detail_title_backup_restore)
                FeatureDetailSection.AboutApp -> stringResource(R.string.detail_title_about_app)
                FeatureDetailSection.SupportCommunity -> stringResource(R.string.detail_title_support_community)
                FeatureDetailSection.DeveloperOptions -> stringResource(R.string.detail_title_developer_options)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        when (section) {
            FeatureDetailSection.NotificationRules -> {
                NotificationsAndPrivacySection(settings = settings, repository = repository)
            }
            FeatureDetailSection.AppShortcuts -> {
                AppShortcutsSection(settings = settings, repository = repository)
            }
            FeatureDetailSection.NotificationHistory -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    NotificationHistorySection(settings = settings, repository = repository)
                }
            }
            FeatureDetailSection.ThemeSettings -> {
                ThemeSettingsSection(settings = settings, repository = repository)
            }
            FeatureDetailSection.ColorStudio -> {
                CustomizationsSection(settings = settings, repository = repository)
            }
            FeatureDetailSection.GesturesGuide -> {
                GesturesSection(settings = settings, repository = repository)
            }
            FeatureDetailSection.PermissionsCenter -> {
                PermissionsSection(
                    overlayGranted = overlayGranted,
                    notificationGranted = notificationGranted,
                    batteryIgnored = batteryIgnored,
                    rootModeEnabled = settings.rootModeEnabled,
                    onToggleRootMode = { enabled ->
                        scope.launch {
                            repository.setRootModeEnabled(enabled)
                            if (enabled) {
                                SystemServiceRecovery.requestRecovery(context)
                            }
                            onRefreshPermissions()
                        }
                    },
                    onOverlayClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onNotificationClick = {
                        val detailIntent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS").apply {
                            val component = ComponentName(context, AuraNotificationListenerService::class.java)
                            putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", component.flattenToString())
                        }
                        runCatching {
                            context.startActivity(detailIntent)
                        }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    onBatteryClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    onRefreshPermissions = onRefreshPermissions
                )
            }
            FeatureDetailSection.BackupRestore -> {
                BackupRestoreSection(settings = settings, repository = repository)
            }
            FeatureDetailSection.AboutApp -> {
                AboutSection()
            }
            FeatureDetailSection.SupportCommunity -> {
                SupportSection()
            }
            FeatureDetailSection.DeveloperOptions -> {
                DeveloperOptionsSection(settings = settings, repository = repository)
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean =
    PermissionUtils.isNotificationListenerEnabled(context)

private fun isAccessibilityServiceEnabled(context: Context): Boolean =
    PermissionUtils.isAccessibilityServiceEnabled(context)

private fun isBatteryOptimizationIgnored(context: Context): Boolean =
    PermissionUtils.isBatteryOptimizationIgnored(context)

@Composable
private fun WelcomeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        runCatchingLogged("WelcomeDialog", "Failed to get app icon") {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = "Aura Logo",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SI", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.welcome_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                ElevatedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.btn_get_started), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RestoreBackupDialog(
    onRestore: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var metadata by remember { mutableStateOf<AuraSettings.BackupMetadata?>(null) }

    LaunchedEffect(Unit) {
        metadata = SettingsBackupManager.loadBackupMetadata(context)
    }

    val dateFormatted = remember(metadata) {
        metadata?.let {
            try {
                java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))
            } catch (_: Exception) { "Recent Backup" }
        } ?: "Saved Configuration"
    }

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsBackupRestore,
                        contentDescription = "Restore Backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Previous Settings Found",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (metadata != null) {
                        "Aura detected a previous configuration backup ($dateFormatted, v${metadata?.appVersion ?: "1.0"}).\n\nWould you like to restore your custom notch dimensions, color themes, and rules?"
                    } else {
                        "Aura detected a saved settings backup from a previous installation. Would you like to restore your custom configurations?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                ElevatedButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsBackupRestore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Settings", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Start Fresh", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
private fun AuraHomeScreenLightPreview() {
    AuraTheme(darkTheme = false) {
        AuraHomeScreen()
    }
}

@Preview(showBackground = true, name = "Dark Mode")
@Composable
private fun AuraHomeScreenDarkPreview() {
    AuraTheme(darkTheme = true) {
        AuraHomeScreen()
    }
}
