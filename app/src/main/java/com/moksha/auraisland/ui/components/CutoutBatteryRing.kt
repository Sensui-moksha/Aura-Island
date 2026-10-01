/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moksha.auraisland.data.AuraSettings

/**
 * Hardware-aligned cutout battery indicator & charging visualizer ring.
 *
 * Implements bottom-origin dual-arc symmetry (+90° / 6 o'clock origin):
 * - Left Wing: sweeps CCW upwards [90° - sweep -> 90°]
 * - Right Wing: sweeps CW upwards [90° -> 90° + sweep]
 *
 * Features a 900ms reverse breathing pulse during active charging.
 */
@Composable
fun CutoutBatteryRing(
    batteryPct: Float,
    isCharging: Boolean,
    settings: AuraSettings,
    modifier: Modifier = Modifier,
    sizeDp: Dp = settings.cameraCircleSize.dp,
    showCenterIcon: Boolean = true
) {
    // 900ms Infinite reverse breathing pulse while charging (1800ms full breath)
    val infiniteTransition = rememberInfiniteTransition(label = "CutoutChargingPulse")
    val pulsePhase by if (isCharging && settings.batteryRingPulse) {
        infiniteTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ChargingPulsePhase"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }

    // Smooth transition between battery percentage changes
    val animatedPct by animateFloatAsState(
        targetValue = batteryPct.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "BatteryPctSmoothing"
    )

    val isLowBattery = animatedPct <= 20f && !isCharging
    val resolvedColor = when {
        isLowBattery -> Color(0xFFEF4444)
        isCharging -> Color(settings.batteryColor)
        else -> Color(settings.batteryColor)
    }

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidthPx = settings.batteryRingStrokeWidth.dp.toPx()
            val baseRadius = (size.minDimension / 2f) - (strokeWidthPx / 2f)
            val ringRadius = (baseRadius * settings.batteryRingGap.coerceIn(0.9f, 1.5f))
                .coerceAtMost(size.minDimension / 2f - 1f)

            val arcRect = Rect(
                left = center.x - ringRadius,
                top = center.y - ringRadius,
                right = center.x + ringRadius,
                bottom = center.y + ringRadius
            )

            // 1. Faint background guide ring track
            drawArc(
                color = resolvedColor.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = strokeWidthPx)
            )

            // 2. Dual-arc bottom-origin symmetric gauge
            val fraction = ((animatedPct / 100f).coerceIn(0f, 1f) * pulsePhase).coerceIn(0f, 1f)
            val sweepAngle = fraction * 180f

            if (sweepAngle > 0.5f) {
                // Left Wing: Starts at (90° - sweep) and sweeps clockwise by sweep to 90°
                drawArc(
                    color = resolvedColor,
                    startAngle = 90f - sweepAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )

                // Right Wing: Starts at 90° and sweeps clockwise by sweep to (90° + sweep)
                drawArc(
                    color = resolvedColor,
                    startAngle = 90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        // Center indicator (Lightning bolt when charging, or discreet percentage)
        if (showCenterIcon) {
            if (isCharging) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Charging",
                    tint = resolvedColor,
                    modifier = Modifier.size((sizeDp.value * 0.44f).dp)
                )
            } else {
                Text(
                    text = "${animatedPct.toInt()}%",
                    color = resolvedColor,
                    fontSize = (sizeDp.value * 0.26f).sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
