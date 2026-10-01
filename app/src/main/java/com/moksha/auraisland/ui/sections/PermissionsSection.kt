/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui.sections

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.moksha.auraisland.R
import com.moksha.auraisland.ui.PermissionCard
import com.moksha.auraisland.util.HeadsUpNotificationController
import com.moksha.auraisland.util.OemAutostartUtil
import com.moksha.auraisland.util.RootManager
import com.moksha.auraisland.util.ShizukuManager
import com.moksha.auraisland.util.safeStartActivity
import kotlinx.coroutines.launch

@Composable
fun PermissionsSection(
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    batteryIgnored: Boolean = false,
    rootModeEnabled: Boolean = true,
    onToggleRootMode: (Boolean) -> Unit = {},
    onOverlayClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onRefreshPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExecutingRoot by remember { mutableStateOf(false) }
    var isExecutingShizuku by remember { mutableStateOf(false) }
    var isOemAutostartEnabled by remember { mutableStateOf(batteryIgnored) }
    var isOverlayWarningDisabled by remember { mutableStateOf(false) }
    var showNonRootVersion by remember { mutableStateOf(false) }
    var isHeadsUpSuppressed by remember { mutableStateOf(HeadsUpNotificationController.isHeadsUpSuppressed(context)) }

    val isRootAvailable = remember { RootManager.isRootAvailable(context) }
    var rootFlavor by remember { mutableStateOf(RootManager.detectRootFlavor(context)) }
    var isRootGranted by remember { mutableStateOf(false) }

    LaunchedEffect(isRootAvailable, rootModeEnabled) {
        if (isRootAvailable) {
            val granted = RootManager.isRootGranted()
            isRootGranted = granted
            rootFlavor = RootManager.detectRootFlavor(context)
            if (granted && rootModeEnabled) {
                isOemAutostartEnabled = true
                isOverlayWarningDisabled = true
                if (!overlayGranted || !notificationGranted || !batteryIgnored) {
                    val result = RootManager.autoGrantAllPermissions(context)
                    if (result.isSuccess) {
                        onRefreshPermissions()
                    }
                }
            }
        }
    }

    val allPermissionsGranted = overlayGranted && notificationGranted && batteryIgnored

    val handleOverlayClick: () -> Unit = {
        if (rootModeEnabled && isRootGranted) {
            scope.launch {
                val res = RootManager.grantAccessibility(context)
                if (res.isSuccess) {
                    onRefreshPermissions()
                    Toast.makeText(context, "Accessibility granted via Root!", Toast.LENGTH_SHORT).show()
                } else {
                    onOverlayClick()
                }
            }
        } else {
            onOverlayClick()
        }
    }

    val handleNotificationClick: () -> Unit = {
        if (rootModeEnabled && isRootGranted) {
            scope.launch {
                val res = RootManager.grantNotificationListener(context)
                if (res.isSuccess) {
                    onRefreshPermissions()
                    Toast.makeText(context, "Notification Listener granted via Root!", Toast.LENGTH_SHORT).show()
                } else {
                    onNotificationClick()
                }
            }
        } else {
            onNotificationClick()
        }
    }

    val handleBatteryClick: () -> Unit = {
        if (rootModeEnabled && isRootGranted) {
            scope.launch {
                val res = RootManager.grantBatteryOptimization(context)
                if (res.isSuccess) {
                    onRefreshPermissions()
                    Toast.makeText(context, "Battery optimization ignored via Root!", Toast.LENGTH_SHORT).show()
                } else {
                    onBatteryClick()
                }
            }
        } else {
            onBatteryClick()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Superuser / Root 1-Tap Auto Setup Card (Magisk, KernelSU, SUSFS, APatch, RootFS)
        if (rootModeEnabled && (isRootAvailable || rootFlavor != RootManager.RootFlavor.NONE)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.root_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val rootFlavorLabel = if (rootFlavor != RootManager.RootFlavor.NONE) {
                                    rootFlavor.displayName
                                } else "Root Available"

                                val rootStatusText = when {
                                    isExecutingRoot -> stringResource(R.string.root_btn_running)
                                    isRootGranted -> stringResource(R.string.root_status_granted)
                                    else -> "$rootFlavorLabel • Tap to Grant"
                                }
                                Text(
                                    text = rootStatusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isRootGranted) Color(0xFF0F9F6E) else MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            enabled = !isExecutingRoot,
                            onClick = {
                                isExecutingRoot = true
                                scope.launch {
                                    val result = RootManager.autoGrantAllPermissions(context)
                                    isExecutingRoot = false
                                    result.onSuccess {
                                        isRootGranted = true
                                        isOemAutostartEnabled = true
                                        isOverlayWarningDisabled = true
                                        Toast.makeText(context, context.getString(R.string.root_success), Toast.LENGTH_LONG).show()
                                        onRefreshPermissions()
                                    }.onFailure { err ->
                                        Toast.makeText(context, context.getString(R.string.root_failed, err.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(
                                text = if (isExecutingRoot) stringResource(R.string.root_btn_running) else if (isRootGranted) "Re-grant" else stringResource(R.string.root_btn_grant),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.root_card_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Root Mode Switcher & Status Banner (Shown when Root is available on the device)
        if (isRootAvailable || rootFlavor != RootManager.RootFlavor.NONE) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val isRootActive = rootModeEnabled && isRootGranted
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isRootActive) Color(0xFF0F9F6E).copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRootActive) Icons.Rounded.CheckCircle else Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = if (isRootActive) Color(0xFF0F9F6E) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isRootActive) stringResource(R.string.root_mode_active_title)
                                else stringResource(R.string.root_mode_disabled_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRootActive) {
                                    if (allPermissionsGranted) stringResource(R.string.root_mode_active_desc)
                                    else "Superuser access granted. Please ensure individual permissions below are active."
                                } else {
                                    stringResource(R.string.root_mode_disabled_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val newMode = !rootModeEnabled
                            onToggleRootMode(newMode)
                            val toastMsg = if (newMode) {
                                context.getString(R.string.toast_switched_to_root)
                            } else {
                                context.getString(R.string.toast_switched_to_non_root)
                            }
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            if (newMode) {
                                onRefreshPermissions()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (rootModeEnabled && isRootGranted) stringResource(R.string.btn_shift_to_non_root)
                            else stringResource(R.string.btn_enable_root),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Shizuku 1-Tap Auto Setup Card (Non-Root ADB Alternative) - Hidden when Root is granted unless shifted to Non-Root
        if (!rootModeEnabled || !isRootGranted || showNonRootVersion) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FlashOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.shizuku_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val shizukuStateText = when {
                                !ShizukuManager.isInstalled(context) -> "Not Installed"
                                !ShizukuManager.isBinderAvailable() -> "Shizuku Not Running"
                                !ShizukuManager.hasPermission() -> "Permission Required"
                                else -> "Ready to Auto-Grant"
                            }
                            Text(
                                text = shizukuStateText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ShizukuManager.hasPermission()) Color(0xFF0F9F6E) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = !isExecutingShizuku,
                        onClick = {
                            when {
                                !ShizukuManager.isInstalled(context) -> {
                                    Toast.makeText(context, context.getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
                                }
                                !ShizukuManager.isBinderAvailable() -> {
                                    Toast.makeText(context, context.getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
                                }
                                !ShizukuManager.hasPermission() -> {
                                    ShizukuManager.requestPermission()
                                }
                                else -> {
                                    isExecutingShizuku = true
                                    scope.launch {
                                        val result = ShizukuManager.autoGrantAllPermissions(context)
                                        isExecutingShizuku = false
                                        result.onSuccess { msg ->
                                            Toast.makeText(context, context.getString(R.string.shizuku_success), Toast.LENGTH_LONG).show()
                                            isOemAutostartEnabled = true
                                            isOverlayWarningDisabled = true
                                            onRefreshPermissions()
                                        }.onFailure { err ->
                                            Toast.makeText(context, context.getString(R.string.shizuku_failed, err.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isExecutingShizuku) stringResource(R.string.shizuku_btn_running) else stringResource(R.string.shizuku_btn_run),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.shizuku_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            }
        }

        // Standard / Individual Permission Cards
        // ALWAYS SHOWN IF ANY PERMISSION IS NOT YET GRANTED, OR IF NOT ROOTED, OR IF SHIFTED TO NON-ROOT!
        if (!rootModeEnabled || !isRootGranted || showNonRootVersion || !allPermissionsGranted) {
            // Required Permission 1: Accessibility
            PermissionCard(
                title = stringResource(R.string.perm_accessibility_title),
                description = stringResource(R.string.perm_accessibility_desc),
                granted = overlayGranted,
                buttonText = if (rootModeEnabled && isRootGranted) "Grant via Root" else stringResource(R.string.btn_grant),
                onClick = handleOverlayClick
            )

            // Required Permission 2: Notification Listener
            PermissionCard(
                title = stringResource(R.string.perm_notification_title),
                description = stringResource(R.string.perm_notification_desc),
                granted = notificationGranted,
                buttonText = if (rootModeEnabled && isRootGranted) "Grant via Root" else stringResource(R.string.btn_grant),
                onClick = handleNotificationClick
            )

            // Recommended Permission 3: Battery Optimization
            PermissionCard(
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                granted = batteryIgnored,
                buttonText = if (rootModeEnabled && isRootGranted) "Grant via Root" else stringResource(R.string.btn_grant),
                onClick = handleBatteryClick
            )

            // Optional Permission 4: Suppress System Heads-Up Floating Banners
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val hunColor = if (isHeadsUpSuppressed) Color(0xFF0F9F6E) else Color(0xFFE88C25)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(hunColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isHeadsUpSuppressed) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = hunColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.perm_heads_up_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isHeadsUpSuppressed) Color(0xFF0F9F6E).copy(alpha = 0.12f)
                                            else Color(0xFFE88C25).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isHeadsUpSuppressed) "Active" else "Not Suppressed",
                                        color = if (isHeadsUpSuppressed) Color(0xFF0F9F6E) else Color(0xFFE88C25),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // 1-tap grant via Root or Shizuku
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!isHeadsUpSuppressed) {
                                if (rootModeEnabled && isRootGranted) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val ok = HeadsUpNotificationController.applySuppression(context, true)
                                                if (ok) {
                                                    isHeadsUpSuppressed = true
                                                    Toast.makeText(context, "Pop-ups suppressed via Root!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Text("Root", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (ShizukuManager.hasPermission()) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val ok = HeadsUpNotificationController.applySuppression(context, true)
                                                if (ok) {
                                                    isHeadsUpSuppressed = true
                                                    Toast.makeText(context, "Pop-ups suppressed via Shizuku!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("Shizuku", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val ok = HeadsUpNotificationController.applySuppression(context, false)
                                            if (ok) isHeadsUpSuppressed = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.perm_heads_up_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    // ADB manual fallback command
                    if (!isHeadsUpSuppressed && !HeadsUpNotificationController.canToggleHeadsUp(context)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Manual ADB setup (once via PC):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = HeadsUpNotificationController.ADB_COMMAND_PERMISSION,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = HeadsUpNotificationController.ADB_COMMAND_SETTING,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

        // Overlay System Warning Card
        val warningIconColor = if (isOverlayWarningDisabled) Color(0xFF0F9F6E) else MaterialTheme.colorScheme.onSurfaceVariant
        val warningBgColor = if (isOverlayWarningDisabled) Color(0xFF0F9F6E).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(warningBgColor, shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOverlayWarningDisabled) Icons.Rounded.CheckCircle else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = warningIconColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Suppress System Overlay Warning",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isOverlayWarningDisabled) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0F9F6E).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Configured",
                                        color = Color(0xFF0F9F6E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            isOverlayWarningDisabled = true
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, "android")
                            }
                            context.safeStartActivity(
                                intent,
                                "Cannot open app notification settings on this device."
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(if (isOverlayWarningDisabled) "Open" else "Hide Alert", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Opens Android system notification channels to hide the persistent \"Aura is displaying over other apps\" banner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        // OEM Autostart & Kill Protection Card
        val oemIconColor = if (isOemAutostartEnabled) Color(0xFF0F9F6E) else MaterialTheme.colorScheme.tertiary
        val oemBgColor = if (isOemAutostartEnabled) Color(0xFF0F9F6E).copy(alpha = 0.12f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(oemBgColor, shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOemAutostartEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Build,
                                contentDescription = null,
                                tint = oemIconColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "OEM Autostart & Kill Protection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isOemAutostartEnabled) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0F9F6E).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Configured",
                                        color = Color(0xFF0F9F6E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isRootAvailable && !isOemAutostartEnabled) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val result = RootManager.grantOemAutostartAndKillProtection(context)
                                        result.onSuccess {
                                            isOemAutostartEnabled = true
                                            Toast.makeText(context, "OEM autostart granted via Root!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Error: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text("Root", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (ShizukuManager.hasPermission() && !isOemAutostartEnabled) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val result = ShizukuManager.grantOemAutostartAndKillProtection(context)
                                        result.onSuccess {
                                            isOemAutostartEnabled = true
                                            Toast.makeText(context, "OEM autostart granted via Shizuku!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Error: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("Shizuku", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                isOemAutostartEnabled = true
                                OemAutostartUtil.openAutostartSettings(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Fix Kills", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "On Xiaomi/HyperOS, Samsung OneUI, OPPO ColorOS, and Vivo OriginOS, enable Autostart to prevent custom OEM task killers from terminating Aura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        }

        // Root Mode Active: Show Root Privileges & Capabilities Card
        if (rootModeEnabled && isRootGranted) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF0F9F6E).copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = Color(0xFF0F9F6E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.root_capabilities_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.root_capabilities_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                RootPrivilegeItem(
                    icon = Icons.Rounded.Layers,
                    title = stringResource(R.string.root_feature_accessibility),
                    subtitle = stringResource(R.string.root_feature_active),
                    active = overlayGranted
                )
                RootPrivilegeItem(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.root_feature_notifications),
                    subtitle = stringResource(R.string.root_feature_active),
                    active = notificationGranted
                )
                RootPrivilegeItem(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.root_feature_overlay),
                    subtitle = stringResource(R.string.root_feature_active),
                    active = isOverlayWarningDisabled
                )
                RootPrivilegeItem(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.root_feature_battery),
                    subtitle = stringResource(R.string.root_feature_active),
                    active = batteryIgnored
                )
                RootPrivilegeItem(
                    icon = Icons.Rounded.Build,
                    title = stringResource(R.string.root_feature_oem),
                    subtitle = stringResource(R.string.root_feature_active),
                    active = isOemAutostartEnabled
                )
            }
        }
    }
}
}

@Composable
private fun RootPrivilegeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean
) {
    val statusColor = if (active) Color(0xFF0F9F6E) else Color(0xFFE88C25)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (active) subtitle else "Action Required",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Icon(
            imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
