/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUtilsTest {

    @Test
    fun testBatteryOptimizationIgnoredWhenPowerManagerSaysTrue() {
        val context = mockk<Context>()
        val powerManager = mockk<PowerManager>()
        every { context.packageName } returns "com.moksha.auraisland"
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isIgnoringBatteryOptimizations("com.moksha.auraisland") } returns true

        val ignored = PermissionUtils.isBatteryOptimizationIgnored(context)
        assertTrue(ignored)
    }

    @Test
    fun testAccessibilityServiceEnabledWhenSystemConnected() {
        val context = mockk<Context>(relaxed = true)
        com.moksha.auraisland.service.AuraOverlayService.isSystemConnected = true

        val enabled = PermissionUtils.isAccessibilityServiceEnabled(context)
        assertTrue(enabled)

        com.moksha.auraisland.service.AuraOverlayService.isSystemConnected = false
    }

    @Test
    fun testNotificationListenerEnabledWhenSystemConnected() {
        val context = mockk<Context>(relaxed = true)
        com.moksha.auraisland.service.AuraNotificationListenerService.isSystemConnected = true

        val enabled = PermissionUtils.isNotificationListenerEnabled(context)
        assertTrue(enabled)

        com.moksha.auraisland.service.AuraNotificationListenerService.isSystemConnected = false
    }
}
