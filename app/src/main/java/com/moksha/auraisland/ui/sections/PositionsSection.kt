/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui.sections

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitScreen

import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moksha.auraisland.R
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.ui.SliderSettingItem
import com.moksha.auraisland.ui.bounceClick
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PositionsSection(
    settings: AuraSettings,
    repository: AuraSettingsRepository,
    notificationRepository: INotificationRepository? = null,
    onNavigateToBackup: () -> Unit = {}
) {
    DisposableEffect(notificationRepository) {
        notificationRepository?.setCustomizing(true)
        onDispose {
            notificationRepository?.setCustomizing(false)
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var localWidth by remember(settings.width) { mutableFloatStateOf(settings.width) }
    var localHeight by remember(settings.height) { mutableFloatStateOf(settings.height) }
    var localXOffset by remember(settings.xOffset) { mutableFloatStateOf(settings.xOffset) }
    var localYOffset by remember(settings.yOffset) { mutableFloatStateOf(settings.yOffset) }
    var localCornerRadius by remember(settings.cornerRadius) { mutableFloatStateOf(settings.cornerRadius) }
    var localOpacity by remember(settings.opacity) { mutableFloatStateOf(settings.opacity) }
    var localShadowElevation by remember(settings.shadowElevation) { mutableFloatStateOf(settings.shadowElevation) }
    var localCameraCircleSize by remember(settings.cameraCircleSize) { mutableFloatStateOf(settings.cameraCircleSize) }
    var localCameraCircleOffsetX by remember(settings.cameraCircleOffsetX) { mutableFloatStateOf(settings.cameraCircleOffsetX) }
    var localCameraCircleOffsetY by remember(settings.cameraCircleOffsetY) { mutableFloatStateOf(settings.cameraCircleOffsetY) }
    var localBatteryRingStrokeWidth by remember(settings.batteryRingStrokeWidth) { mutableFloatStateOf(settings.batteryRingStrokeWidth) }
    var localBatteryRingGap by remember(settings.batteryRingGap) { mutableFloatStateOf(settings.batteryRingGap) }
    var localOutlineWidth by remember(settings.outlineWidth) { mutableFloatStateOf(settings.outlineWidth) }
    var showPillColorDialog by remember { mutableStateOf(false) }
    var showOutlineColorDialog by remember { mutableStateOf(false) }

    if (showPillColorDialog) {
        RgbColorPickerDialog(
            title = stringResource(R.string.color_pill_background),
            initialColor = settings.pillColor,
            onDismiss = { showPillColorDialog = false },
            onSave = { color ->
                showPillColorDialog = false
                scope.launch { repository.setPillColor(color) }
            }
        )
    }

    if (showOutlineColorDialog) {
        RgbColorPickerDialog(
            title = stringResource(R.string.outline_color_title),
            initialColor = settings.outlineColor,
            onDismiss = { showOutlineColorDialog = false },
            onSave = { color ->
                showOutlineColorDialog = false
                scope.launch { repository.setOutlineColor(color) }
            }
        )
    }

    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val calculatedLeftX = (-(screenWidthDp / 2f - 40f)).coerceIn(
        AuraSettings.MIN_X_OFFSET,
        -30f
    )
    val calculatedRightX = ((screenWidthDp / 2f - 40f)).coerceIn(
        30f,
        AuraSettings.MAX_X_OFFSET
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sleek Mode Switcher (Pill vs Notch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isNotch = settings.enableNotchMode
            // Pill Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isNotch) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .bounceClick {
                        if (isNotch) {
                            scope.launch {
                                repository.setEnableNotchMode(false)
                                repository.setPosition(
                                    width = 112f,
                                    height = 34f,
                                    xOffset = 0f,
                                    yOffset = 10f
                                )
                                repository.setCornerRadius(20f)
                            }
                            Toast.makeText(context, context.getString(R.string.toast_notch_mode_disabled), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = null,
                        tint = if (!isNotch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.preset_compact_pill),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!isNotch) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isNotch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Notch Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isNotch) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .bounceClick {
                        if (!isNotch) {
                            scope.launch {
                                repository.setEnableNotchMode(true)
                                repository.setPosition(
                                    width = 175f,
                                    height = 35f,
                                    xOffset = 0f,
                                    yOffset = 0f
                                )
                                repository.setCornerRadius(20f)
                            }
                            Toast.makeText(context, context.getString(R.string.toast_notch_mode_enabled), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FitScreen,
                        contentDescription = null,
                        tint = if (isNotch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.notch_mode_card_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isNotch) FontWeight.Bold else FontWeight.Medium,
                        color = if (isNotch) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Quick Layout Presets Grid
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
                    Text(
                        text = stringResource(R.string.presets_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${screenWidthDp.toInt()} dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Row 1: Center Hole & Wide Island
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCenterSelected = abs(settings.xOffset) < 5f && abs(settings.width - 112f) < 8f
                    PresetChipItem(
                        title = stringResource(R.string.preset_center_hole),
                        icon = Icons.Rounded.CenterFocusStrong,
                        isSelected = isCenterSelected,
                        onClick = {
                            scope.launch {
                                repository.setPosition(
                                    width = 112f,
                                    height = 34f,
                                    xOffset = 0f,
                                    yOffset = 10f
                                )
                                repository.setCornerRadius(20f)
                            }
                            Toast.makeText(context, context.getString(R.string.toast_applied_center_preset), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    val isWideSelected = abs(settings.xOffset) < 5f && abs(settings.width - 150f) < 8f
                    PresetChipItem(
                        title = stringResource(R.string.preset_wide_island),
                        icon = Icons.Rounded.FitScreen,
                        isSelected = isWideSelected,
                        onClick = {
                            scope.launch {
                                repository.setPosition(
                                    width = 150f,
                                    height = 38f,
                                    xOffset = 0f,
                                    yOffset = 12f
                                )
                                repository.setCornerRadius(22f)
                            }
                            Toast.makeText(context, context.getString(R.string.toast_applied_wide_preset), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Left Corner & Right Corner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isLeftSelected = settings.xOffset < -30f
                    PresetChipItem(
                        title = stringResource(R.string.preset_left_corner),
                        icon = Icons.AutoMirrored.Rounded.AlignHorizontalLeft,
                        isSelected = isLeftSelected,
                        onClick = {
                            scope.launch {
                                repository.setPosition(
                                    width = 105f,
                                    height = 34f,
                                    xOffset = calculatedLeftX,
                                    yOffset = 10f
                                )
                                repository.setCornerRadius(20f)
                            }
                            Toast.makeText(context, context.getString(R.string.toast_applied_left_preset, calculatedLeftX.toInt()), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    val isRightSelected = settings.xOffset > 30f
                    PresetChipItem(
                        title = stringResource(R.string.preset_right_corner),
                        icon = Icons.AutoMirrored.Rounded.AlignHorizontalRight,
                        isSelected = isRightSelected,
                        onClick = {
                            scope.launch {
                                repository.setPosition(
                                    width = 105f,
                                    height = 34f,
                                    xOffset = calculatedRightX,
                                    yOffset = 10f
                                )
                                repository.setCornerRadius(20f)
                            }
                            Toast.makeText(context, context.getString(R.string.toast_applied_right_preset, calculatedRightX.toInt()), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Companion Circle Position (Pill Mode only)
        if (!settings.enableNotchMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.circle_position_card_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.circle_position_card_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val isLeft = settings.circlePosition == AuraSettings.CIRCLE_POSITION_LEFT
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isLeft) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .bounceClick {
                                    scope.launch { repository.setCirclePosition(AuraSettings.CIRCLE_POSITION_LEFT) }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.circle_position_left),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLeft) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isLeft) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .bounceClick {
                                    scope.launch { repository.setCirclePosition(AuraSettings.CIRCLE_POSITION_RIGHT) }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.circle_position_right),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!isLeft) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Precision Tuning Sliders Card
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
                Text(
                    text = stringResource(R.string.precision_tuning_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                SliderSettingItem(
                    label = stringResource(R.string.slider_island_width),
                    value = localWidth,
                    range = AuraSettings.MIN_WIDTH..AuraSettings.MAX_WIDTH,
                    onValueChange = { localWidth = it },
                    onValueChangeFinished = { scope.launch { repository.setWidth(localWidth) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                SliderSettingItem(
                    label = stringResource(R.string.slider_island_height),
                    value = localHeight,
                    range = AuraSettings.MIN_HEIGHT..AuraSettings.MAX_HEIGHT,
                    onValueChange = { localHeight = it },
                    onValueChangeFinished = { scope.launch { repository.setHeight(localHeight) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                SliderSettingItem(
                    label = stringResource(R.string.slider_horizontal_offset),
                    value = localXOffset,
                    range = AuraSettings.MIN_X_OFFSET..AuraSettings.MAX_X_OFFSET,
                    onValueChange = { localXOffset = it },
                    onValueChangeFinished = { scope.launch { repository.setXOffset(localXOffset) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                if (settings.enableNotchMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.slider_vertical_offset),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.y_offset_locked_notch_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "0 dp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    SliderSettingItem(
                        label = stringResource(R.string.slider_vertical_offset),
                        value = localYOffset,
                        range = AuraSettings.MIN_Y_OFFSET..AuraSettings.MAX_Y_OFFSET,
                        onValueChange = { localYOffset = it },
                        onValueChangeFinished = { scope.launch { repository.setYOffset(localYOffset) } }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                SliderSettingItem(
                    label = stringResource(R.string.slider_corner_radius),
                    value = localCornerRadius,
                    range = AuraSettings.MIN_CORNER_RADIUS..AuraSettings.MAX_CORNER_RADIUS,
                    onValueChange = { localCornerRadius = it },
                    onValueChangeFinished = { scope.launch { repository.setCornerRadius(localCornerRadius) } }
                )

                if (!settings.enableNotchMode) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.enable_cutout_ring_mode_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.enable_cutout_ring_mode_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enableCutoutRingMode,
                            onCheckedChange = { isEnabled ->
                                scope.launch { repository.setEnableCutoutRingMode(isEnabled) }
                            }
                        )
                    }
                }
            }
        }

        // 4b. Camera Cutout Hardware Calibration Card (Always Visible & Independent)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Camera Cutout Calibration".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Calibrate the exact physical size and position of your phone's front camera punch-hole.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Cutout Presets (Center, Left, Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isCenterCutout = abs(settings.cameraCircleOffsetX) < 5f
                    PresetChipItem(
                        title = "Center Hole",
                        icon = Icons.Rounded.CenterFocusStrong,
                        isSelected = isCenterCutout,
                        onClick = {
                            scope.launch {
                                repository.setCameraCircleOffsetX(0f)
                                repository.setCameraCircleOffsetY(10f)
                                repository.setCameraCircleSize(34f)
                            }
                            Toast.makeText(context, "Applied Center Cutout", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    val isLeftCutout = settings.cameraCircleOffsetX < -30f
                    PresetChipItem(
                        title = "Left Hole",
                        icon = Icons.AutoMirrored.Rounded.AlignHorizontalLeft,
                        isSelected = isLeftCutout,
                        onClick = {
                            scope.launch {
                                repository.setCameraCircleOffsetX(calculatedLeftX)
                                repository.setCameraCircleOffsetY(10f)
                                repository.setCameraCircleSize(34f)
                            }
                            Toast.makeText(context, "Applied Left Cutout", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    val isRightCutout = settings.cameraCircleOffsetX > 30f
                    PresetChipItem(
                        title = "Right Hole",
                        icon = Icons.AutoMirrored.Rounded.AlignHorizontalRight,
                        isSelected = isRightCutout,
                        onClick = {
                            scope.launch {
                                repository.setCameraCircleOffsetX(calculatedRightX)
                                repository.setCameraCircleOffsetY(10f)
                                repository.setCameraCircleSize(34f)
                            }
                            Toast.makeText(context, "Applied Right Cutout", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                SliderSettingItem(
                    label = stringResource(R.string.camera_circle_size_title),
                    value = localCameraCircleSize,
                    range = AuraSettings.MIN_CAMERA_CIRCLE_SIZE..AuraSettings.MAX_CAMERA_CIRCLE_SIZE,
                    onValueChange = { localCameraCircleSize = it },
                    onValueChangeFinished = { scope.launch { repository.setCameraCircleSize(localCameraCircleSize) } }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                SliderSettingItem(
                    label = stringResource(R.string.camera_circle_offset_x_title),
                    value = localCameraCircleOffsetX,
                    range = AuraSettings.MIN_CAMERA_CIRCLE_X..AuraSettings.MAX_CAMERA_CIRCLE_X,
                    onValueChange = { localCameraCircleOffsetX = it },
                    onValueChangeFinished = { scope.launch { repository.setCameraCircleOffsetX(localCameraCircleOffsetX) } }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                SliderSettingItem(
                    label = stringResource(R.string.camera_circle_offset_y_title),
                    value = localCameraCircleOffsetY,
                    range = AuraSettings.MIN_CAMERA_CIRCLE_Y..AuraSettings.MAX_CAMERA_CIRCLE_Y,
                    onValueChange = { localCameraCircleOffsetY = it },
                    onValueChangeFinished = { scope.launch { repository.setCameraCircleOffsetY(localCameraCircleOffsetY) } }
                )
            }
        }

        // 4c. Cutout Ring & Battery Gauge Features Card
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
                Text(
                    text = "Hardware Ring & Battery Gauge".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )



                // Toggle 2: Charging Ring
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.charging_pulse_card_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.charging_pulse_card_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.enableBatteryRing,
                        onCheckedChange = { isEnabled ->
                            scope.launch { repository.setEnableBatteryRing(isEnabled) }
                        }
                    )
                }

                // Sub-toggles for Charging Ring
                AnimatedVisibility(visible = settings.enableBatteryRing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Sub-toggle: Pulse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.battery_ring_pulse_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.battery_ring_pulse_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.batteryRingPulse,
                                onCheckedChange = { isEnabled ->
                                    scope.launch { repository.setBatteryRingPulse(isEnabled) }
                                }
                            )
                        }

                        // Sub-toggle: Lock Screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.battery_ring_lock_screen_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.battery_ring_lock_screen_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.batteryRingOnLockScreen,
                                onCheckedChange = { isEnabled ->
                                    scope.launch { repository.setBatteryRingOnLockScreen(isEnabled) }
                                }
                            )
                        }

                        // Sub-toggle: Center Icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.battery_ring_center_icon_title),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.battery_ring_center_icon_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.batteryRingShowCenterIcon,
                                onCheckedChange = { isEnabled ->
                                    scope.launch { repository.setBatteryRingShowCenterIcon(isEnabled) }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Toggle 3: Battery Indicator (always-on static ring)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.battery_ring_indicator_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.battery_ring_indicator_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showBatteryIndicator,
                        onCheckedChange = { isEnabled ->
                            scope.launch { repository.setShowBatteryIndicator(isEnabled) }
                        }
                    )
                }

                // Ring Appearance Sliders (visible if any ring feature is active)
                val anyRingActive = settings.enableBatteryRing || settings.showBatteryIndicator
                AnimatedVisibility(visible = anyRingActive) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        Text(
                            text = "Ring Thickness & Distance".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )

                        SliderSettingItem(
                            label = stringResource(R.string.battery_ring_stroke_title),
                            value = localBatteryRingStrokeWidth,
                            range = AuraSettings.MIN_BATTERY_RING_STROKE..AuraSettings.MAX_BATTERY_RING_STROKE,
                            onValueChange = { localBatteryRingStrokeWidth = it },
                            onValueChangeFinished = { scope.launch { repository.setBatteryRingStrokeWidth(localBatteryRingStrokeWidth) } }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        SliderSettingItem(
                            label = stringResource(R.string.battery_ring_gap_title),
                            value = localBatteryRingGap,
                            range = AuraSettings.MIN_BATTERY_RING_GAP..AuraSettings.MAX_BATTERY_RING_GAP,
                            onValueChange = { localBatteryRingGap = it },
                            onValueChangeFinished = { scope.launch { repository.setBatteryRingGap(localBatteryRingGap) } }
                        )
                    }
                }
            }
        }

        // 4c. Island & Cutout Outline Card
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
                // Header + Master Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.outline_border_card_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.outline_border_card_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.enableOutline,
                        onCheckedChange = { isEnabled ->
                            scope.launch { repository.setEnableOutline(isEnabled) }
                        }
                    )
                }

                AnimatedVisibility(visible = settings.enableOutline) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Only During Customization Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.outline_only_in_customization_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.outline_only_in_customization_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = settings.outlineOnlyInCustomization,
                                onCheckedChange = { isEnabled ->
                                    scope.launch { repository.setOutlineOnlyInCustomization(isEnabled) }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Outline Color Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.outline_color_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.outline_color_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))

                            val outlineColorObj = Color(settings.outlineColor)
                            val hexLabel = String.format("#%08X", settings.outlineColor)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = outlineColorObj,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.bounceClick { showOutlineColorDialog = true }
                            ) {
                                Text(
                                    text = hexLabel,
                                    color = if (outlineColorObj.red * 0.299 + outlineColorObj.green * 0.587 + outlineColorObj.blue * 0.114 > 0.5) Color.Black else Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Quick Palette Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0x4DFFFFFFL to "Frosted",
                                0xFFFFFFFFL to "White",
                                0xFF38BDF8L to "Sky",
                                0xFF10B981L to "Emerald",
                                0xFFF59E0BL to "Amber",
                                0xFFFF6B9AL to "Pink"
                            ).forEach { (colorVal, label) ->
                                val isSelected = settings.outlineColor == colorVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .bounceClick {
                                            scope.launch { repository.setOutlineColor(colorVal) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Outline Width Slider + Quick Chips
                        SliderSettingItem(
                            label = stringResource(R.string.slider_outline_width),
                            value = localOutlineWidth,
                            range = AuraSettings.MIN_OUTLINE_WIDTH..AuraSettings.MAX_OUTLINE_WIDTH,
                            suffix = " dp",
                            step = 0.25f,
                            onValueChange = { localOutlineWidth = it },
                            onValueChangeFinished = {
                                scope.launch { repository.setOutlineWidth(localOutlineWidth) }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0.75f to "Thin",
                                1.25f to "Medium",
                                2.0f to "Bold",
                                3.0f to "Thick"
                            ).forEach { (targetVal, label) ->
                                val isSelected = abs(localOutlineWidth - targetVal) < 0.15f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .bounceClick {
                                            localOutlineWidth = targetVal
                                            scope.launch { repository.setOutlineWidth(targetVal) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Appearance & Behavior Card
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
                Text(
                    text = stringResource(R.string.category_appearance_controls).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Opacity Slider + Quick Chips
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SliderSettingItem(
                        label = stringResource(R.string.slider_island_opacity),
                        value = (localOpacity * 100f),
                        range = (AuraSettings.MIN_OPACITY * 100f)..(AuraSettings.MAX_OPACITY * 100f),
                        suffix = "%",
                        step = 5f,
                        onValueChange = { localOpacity = (it / 100f).coerceIn(AuraSettings.MIN_OPACITY, AuraSettings.MAX_OPACITY) },
                        onValueChangeFinished = { scope.launch { repository.setOpacity(localOpacity) } }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1.0f to "100%",
                            0.85f to "85%",
                            0.70f to "70%",
                            0.50f to "50%"
                        ).forEach { (targetVal, label) ->
                            val isSelected = abs(localOpacity - targetVal) < 0.04f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .bounceClick {
                                        localOpacity = targetVal
                                        scope.launch { repository.setOpacity(targetVal) }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                // Pill Background Color
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.color_pill_background),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.color_pill_background_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))

                    val pillColorObj = Color(settings.pillColor)
                    val hexLabel = String.format("#%06X", (settings.pillColor and 0xFFFFFFL))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = pillColorObj,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .bounceClick { showPillColorDialog = true }
                    ) {
                        Text(
                            text = hexLabel,
                            color = if (pillColorObj.red * 0.299 + pillColorObj.green * 0.587 + pillColorObj.blue * 0.114 > 0.5) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Shadow Toggle & Elevation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.toggle_drop_shadow_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.toggle_drop_shadow_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.enableShadow,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setEnableShadow(checked) }
                        }
                    )
                }

                AnimatedVisibility(visible = settings.enableShadow) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SliderSettingItem(
                            label = stringResource(R.string.slider_shadow_elevation),
                            value = localShadowElevation,
                            range = AuraSettings.MIN_SHADOW_ELEVATION..AuraSettings.MAX_SHADOW_ELEVATION,
                            suffix = " dp",
                            step = 1f,
                            onValueChange = { localShadowElevation = it },
                            onValueChangeFinished = {
                                scope.launch { repository.setShadowElevation(localShadowElevation) }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                6f to stringResource(R.string.shadow_subtle),
                                10f to stringResource(R.string.shadow_medium),
                                14f to stringResource(R.string.shadow_standard),
                                22f to stringResource(R.string.shadow_deep)
                            ).forEach { (targetVal, label) ->
                                val isSelected = abs(localShadowElevation - targetVal) < 0.5f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .bounceClick {
                                            localShadowElevation = targetVal
                                            scope.launch { repository.setShadowElevation(targetVal) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${targetVal.toInt()}dp",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Landscape Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.toggle_show_in_landscape_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.toggle_show_in_landscape_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showInLandscape,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setShowInLandscape(checked) }
                        }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Auto-Expand Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.toggle_auto_expand_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.toggle_auto_expand_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoExpandOnNotification,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setAutoExpandOnNotification(checked) }
                        }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Backup & Restore Shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_backup_restore_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.card_backup_restore_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onNavigateToBackup) {
                        Text(stringResource(R.string.btn_backup_restore_shortcut))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChipItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        label = "presetBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        label = "presetBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "presetIconTint"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(12.dp))
            .bounceClick(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
