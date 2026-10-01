/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.model.IslandMode
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationCooldownManagerTest {

    @Before
    fun setUp() {
        NotificationCooldownManager.clear()
    }

    @After
    fun tearDown() {
        NotificationCooldownManager.clear()
    }

    private fun createMockSbn(packageName: String, key: String = "$packageName|1|tag|100"): StatusBarNotification {
        val sbn = mockk<StatusBarNotification>()
        val notif = mockk<Notification>()
        every { sbn.packageName } returns packageName
        every { sbn.key } returns key
        every { sbn.notification } returns notif
        return sbn
    }

    @Test
    fun testDisabledCooldownNeverThrottles() {
        val settings = AuraSettings(
            enableNotificationCooldown = false,
            notificationCooldownThreshold = 3,
            notificationCooldownDurationMinutes = 2
        )
        val sbn = createMockSbn("com.example.chat")

        for (i in 1..10) {
            val throttled = NotificationCooldownManager.shouldThrottle(
                sbn = sbn,
                settings = settings,
                mode = IslandMode.Notification,
                currentTimeMs = 1000L + i * 100L
            )
            assertFalse("Alert $i should not be throttled when cooldown is disabled", throttled)
        }
        assertFalse(NotificationCooldownManager.isPackageInCooldown("com.example.chat", 3000L))
    }

    @Test
    fun testCooldownTriggersAfterThresholdAndThrottlesSubsequent() {
        val settings = AuraSettings(
            enableNotificationCooldown = true,
            notificationCooldownThreshold = 3,
            notificationCooldownDurationMinutes = 2
        )
        val sbn1 = createMockSbn("com.example.chat", "key1")
        val sbn2 = createMockSbn("com.example.chat", "key2")
        val sbn3 = createMockSbn("com.example.chat", "key3")
        val sbn4 = createMockSbn("com.example.chat", "key4")

        val baseTime = 10_000L

        // Alert 1 (t = 10s)
        assertFalse(NotificationCooldownManager.shouldThrottle(sbn1, settings, IslandMode.Notification, baseTime))
        // Alert 2 (t = 12s)
        assertFalse(NotificationCooldownManager.shouldThrottle(sbn2, settings, IslandMode.Notification, baseTime + 2000L))
        // Alert 3 (t = 14s) -> hits threshold 3 in window! Allowed through, but activates cooldown
        assertFalse(NotificationCooldownManager.shouldThrottle(sbn3, settings, IslandMode.Notification, baseTime + 4000L))

        // App should now be in cooldown!
        assertTrue(NotificationCooldownManager.isPackageInCooldown("com.example.chat", baseTime + 5000L))
        assertEquals(119, NotificationCooldownManager.getRemainingCooldownSeconds("com.example.chat", baseTime + 5000L))

        // Alert 4 (t = 15s) -> within cooldown, must be throttled!
        val throttled4 = NotificationCooldownManager.shouldThrottle(sbn4, settings, IslandMode.Notification, baseTime + 5000L)
        assertTrue("Subsequent alert during cooldown must be throttled", throttled4)

        // Buffered notification should be sbn4
        val buffered = NotificationCooldownManager.pollBufferedNotification("com.example.chat")
        assertNotNull(buffered)
        assertEquals("key4", buffered?.key)
    }

    @Test
    fun testExcludedAppsNeverThrottled() {
        val settings = AuraSettings(
            enableNotificationCooldown = true,
            notificationCooldownThreshold = 2,
            notificationCooldownDurationMinutes = 5,
            notificationCooldownExcludedPackages = setOf("com.whatsapp")
        )
        val sbn = createMockSbn("com.whatsapp")

        for (i in 1..8) {
            val throttled = NotificationCooldownManager.shouldThrottle(
                sbn = sbn,
                settings = settings,
                mode = IslandMode.Notification,
                currentTimeMs = 1000L + i * 500L
            )
            assertFalse("Excluded package must never be throttled", throttled)
        }
        assertFalse(NotificationCooldownManager.isPackageInCooldown("com.whatsapp", 10_000L))
    }

    @Test
    fun testExemptModesNeverThrottled() {
        val settings = AuraSettings(
            enableNotificationCooldown = true,
            notificationCooldownThreshold = 2,
            notificationCooldownDurationMinutes = 5
        )
        val sbn = createMockSbn("com.google.android.dialer")

        // IncomingCall
        for (i in 1..5) {
            val throttled = NotificationCooldownManager.shouldThrottle(
                sbn = sbn,
                settings = settings,
                mode = IslandMode.IncomingCall,
                currentTimeMs = 1000L + i * 200L
            )
            assertFalse("IncomingCall must never be throttled", throttled)
        }

        // Timer
        val timerSbn = createMockSbn("com.google.android.deskclock")
        for (i in 1..5) {
            val throttled = NotificationCooldownManager.shouldThrottle(
                sbn = timerSbn,
                settings = settings,
                mode = IslandMode.Timer,
                currentTimeMs = 1000L + i * 200L
            )
            assertFalse("Timer must never be throttled", throttled)
        }
    }

    @Test
    fun testCooldownExpirationAllowsNotificationsAgain() {
        val settings = AuraSettings(
            enableNotificationCooldown = true,
            notificationCooldownThreshold = 2,
            notificationCooldownDurationMinutes = 1 // 60 seconds
        )
        val sbn = createMockSbn("com.example.noisy")
        val startTime = 10_000L

        // Trigger cooldown with 2 rapid alerts
        assertFalse(NotificationCooldownManager.shouldThrottle(sbn, settings, IslandMode.Notification, startTime))
        assertFalse(NotificationCooldownManager.shouldThrottle(sbn, settings, IslandMode.Notification, startTime + 1000L))

        // In cooldown at t = startTime + 10s
        assertTrue(NotificationCooldownManager.isPackageInCooldown("com.example.noisy", startTime + 10_000L))

        // After cooldown expires at t = startTime + 65s (duration is 60s)
        val afterCooldown = startTime + 65_000L
        assertFalse(NotificationCooldownManager.isPackageInCooldown("com.example.noisy", afterCooldown))

        // New notification after cooldown passes through normally
        val nextSbn = createMockSbn("com.example.noisy", "new_key")
        val throttledAfter = NotificationCooldownManager.shouldThrottle(nextSbn, settings, IslandMode.Notification, afterCooldown)
        assertFalse("Notification after cooldown expiration should not be throttled", throttledAfter)
    }
}
