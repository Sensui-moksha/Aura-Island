/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.app.Notification
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemDeviceRulesTest {

    @Test
    fun testResolveEffectiveDevice() {
        assertEquals(OemDeviceType.SAMSUNG, OemDeviceRules.resolveEffectiveDevice("SAMSUNG"))
        assertEquals(OemDeviceType.XIAOMI_REDMI_POCO, OemDeviceRules.resolveEffectiveDevice("XIAOMI_REDMI_POCO"))
        assertEquals(OemDeviceType.VIVO_IQOO, OemDeviceRules.resolveEffectiveDevice("VIVO_IQOO"))
        assertEquals(OemDeviceType.REALME_OPPO_ONEPLUS, OemDeviceRules.resolveEffectiveDevice("REALME_OPPO_ONEPLUS"))
        assertEquals(OemDeviceType.GOOGLE_PIXEL, OemDeviceRules.resolveEffectiveDevice("GOOGLE_PIXEL"))
        assertEquals(OemDeviceType.HUAWEI_HONOR, OemDeviceRules.resolveEffectiveDevice("HUAWEI_HONOR"))
        assertEquals(OemDeviceType.MOTOROLA, OemDeviceRules.resolveEffectiveDevice("MOTOROLA"))
        assertEquals(OemDeviceType.TRANSSION, OemDeviceRules.resolveEffectiveDevice("TRANSSION"))
        assertEquals(OemDeviceType.NUBIA, OemDeviceRules.resolveEffectiveDevice("NUBIA"))
    }

    @Test
    fun testSamsungScreenRecordingIdentification() {
        assertTrue(OemDeviceRules.isScreenRecording("com.sec.android.app.screenrecorder", "Recording screen", OemDeviceType.SAMSUNG))
        assertTrue(OemDeviceRules.isScreenRecording("com.sec.android.app.smartcapture", "Smart Capture", OemDeviceType.SAMSUNG))
        assertTrue(OemDeviceRules.isScreenRecording("com.sec.android.app.voicenote", "Voice recording", OemDeviceType.SAMSUNG))
        assertTrue(OemDeviceRules.isScreenRecording("com.samsung.android.app.screenrecorder", "Screen recorder", OemDeviceType.SAMSUNG))
    }

    @Test
    fun testXiaomiScreenRecordingIdentification() {
        assertTrue(OemDeviceRules.isScreenRecording("com.miui.screenrecorder", "Screen Recorder", OemDeviceType.XIAOMI_REDMI_POCO))
        assertTrue(OemDeviceRules.isScreenRecording("com.miui.soundrecorder", "Sound Recorder", OemDeviceType.XIAOMI_REDMI_POCO))
    }

    @Test
    fun testVivoScreenRecordingIdentification() {
        assertTrue(OemDeviceRules.isScreenRecording("com.vivo.screenrecorder", "S-Capture", OemDeviceType.VIVO_IQOO))
        assertTrue(OemDeviceRules.isScreenRecording("com.vivo.videocapture", "Video capture", OemDeviceType.VIVO_IQOO))
        assertTrue(OemDeviceRules.isScreenRecording("com.vivo.smartshot", "Smart shot", OemDeviceType.VIVO_IQOO))
    }

    @Test
    fun testRealmeOppoScreenRecordingIdentification() {
        assertTrue(OemDeviceRules.isScreenRecording("com.oplus.screenrecorder", "Screen recording", OemDeviceType.REALME_OPPO_ONEPLUS))
        assertTrue(OemDeviceRules.isScreenRecording("com.coloros.screenrecorder", "Screen recorder", OemDeviceType.REALME_OPPO_ONEPLUS))
        assertTrue(OemDeviceRules.isScreenRecording("com.realme.screenrecorder", "Screen recording", OemDeviceType.REALME_OPPO_ONEPLUS))
    }

    @Test
    fun testNubiaDeviceRules() {
        assertTrue(OemDeviceRules.isScreenRecording("cn.nubia.screenrecorder", "Screen Recorder", OemDeviceType.NUBIA))
        assertTrue(OemDeviceRules.isScreenRecording("com.zte.recorder", "Sound Recorder", OemDeviceType.NUBIA))
        assertTrue(OemDeviceRules.isInCallPackage("cn.nubia.incallui", OemDeviceType.NUBIA))
        assertTrue(OemDeviceRules.isInCallPackage("cn.nubia.dialer", OemDeviceType.NUBIA))
    }

    @Test
    fun testInCallPackagesForDifferentOems() {
        assertTrue(OemDeviceRules.isInCallPackage("com.samsung.android.incallui", OemDeviceType.SAMSUNG))
        assertTrue(OemDeviceRules.isInCallPackage("com.miui.incallui", OemDeviceType.XIAOMI_REDMI_POCO))
        assertTrue(OemDeviceRules.isInCallPackage("com.vivo.incallui", OemDeviceType.VIVO_IQOO))
        assertTrue(OemDeviceRules.isInCallPackage("com.oplus.incallui", OemDeviceType.REALME_OPPO_ONEPLUS))
        assertTrue(OemDeviceRules.isInCallPackage("com.google.android.dialer", OemDeviceType.GOOGLE_PIXEL))
        assertFalse(OemDeviceRules.isInCallPackage("com.whatsapp.camera", OemDeviceType.SAMSUNG))
    }

    @Test
    fun testHotspotDetection() {
        assertTrue(OemDeviceRules.isHotspot("com.samsung.android.app.mobilehotspot", "Hotspot active"))
        assertTrue(OemDeviceRules.isHotspot("com.android.settings", "Portable hotspot enabled"))
        assertTrue(OemDeviceRules.isHotspot("com.miui.securitycenter", "Tethering active"))
        assertFalse(OemDeviceRules.isHotspot("com.spotify.music", "Playing music"))
    }

    @Test
    fun testIsNonNavigationContent() {
        assertTrue(OemDeviceRules.isNonNavigationContent("com.sec.android.app.screenrecorder", "Recording screen 05:20 left"))
        assertTrue(OemDeviceRules.isNonNavigationContent("com.samsung.android.incallui", "Incoming call from Mom"))
        assertTrue(OemDeviceRules.isNonNavigationContent("com.android.settings", "Mobile hotspot active"))
        assertFalse(OemDeviceRules.isNonNavigationContent("com.google.android.apps.maps", "In 300 m turn left onto Main St"))
    }

    @Test
    fun testScreenRecordingCompletion() {
        val completedNotif = mockk<Notification>()
        completedNotif.flags = 0 // Not ongoing
        assertTrue(OemDeviceRules.isScreenRecordingComplete(completedNotif, "Recording saved • Tap to view"))

        val activeNotif = mockk<Notification>()
        activeNotif.flags = Notification.FLAG_ONGOING_EVENT
        assertFalse(OemDeviceRules.isScreenRecordingComplete(activeNotif, "Recording screen • 01:23"))
    }

    @Test
    fun testShouldYieldToNativeIsland_Music() {
        // Vivo/iQOO OriginOS with yieldMusic = true
        assertTrue(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Music,
                packageName = "app.morphe.android.apps.youtube.music",
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        // Vivo/iQOO OriginOS with yieldMusic = false (User wants Aura's player)
        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Music,
                packageName = "app.morphe.android.apps.youtube.music",
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = false
            )
        )

        // Standard Android (No native island)
        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Music,
                packageName = "com.spotify.music",
                device = OemDeviceType.GOOGLE_PIXEL,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        // Always Show mode overrides yielding
        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Music,
                packageName = "app.morphe.android.apps.youtube.music",
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_ALWAYS_SHOW,
                yieldMusic = true
            )
        )

        // Standard 3rd-party notification (WhatsApp) should NOT yield
        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Notification,
                packageName = "com.whatsapp",
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        // Battery charging does NOT yield because native charging island is only a transient pop-up
        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Battery,
                packageName = "com.android.systemui",
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )
    }

    @Test
    fun testIsNativeIslandActive_BatteryAndMusic() {
        val batteryNotif = com.moksha.auraisland.model.IslandNotification(
            key = "system_battery",
            packageName = "com.android.systemui",
            appName = "System",
            title = "Charging",
            text = "85%",
            timeMillis = System.currentTimeMillis(),
            mode = com.moksha.auraisland.model.IslandMode.Battery
        )

        val playingMusicNotif = com.moksha.auraisland.model.IslandNotification(
            key = "music_track",
            packageName = "com.spotify.music",
            appName = "Spotify",
            title = "Song Title",
            text = "Artist Name",
            timeMillis = System.currentTimeMillis(),
            mediaIsPlaying = true,
            mode = com.moksha.auraisland.model.IslandMode.Music
        )

        val pausedMusicNotif = com.moksha.auraisland.model.IslandNotification(
            key = "music_track",
            packageName = "com.spotify.music",
            appName = "Spotify",
            title = "Song Title",
            text = "Artist Name",
            timeMillis = System.currentTimeMillis(),
            mediaIsPlaying = false,
            mode = com.moksha.auraisland.model.IslandMode.Music
        )

        // When only battery charging is active (no music), native island is NOT active
        // Aura Island will display the battery island
        assertFalse(
            OemDeviceRules.isNativeIslandActive(
                notifications = listOf(batteryNotif),
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        // When music is PAUSED, native dynamic island is NOT active
        // Aura Island can display the battery island
        assertFalse(
            OemDeviceRules.isNativeIslandActive(
                notifications = listOf(batteryNotif, pausedMusicNotif),
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        // When music IS actively PLAYING alongside battery charging, native island is ACTIVE
        // Aura Island will completely yield/hide so native Origin Island has 100% priority
        assertTrue(
            OemDeviceRules.isNativeIslandActive(
                notifications = listOf(batteryNotif, playingMusicNotif),
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        // When yieldMusic is false, music won't trigger native island active
        assertFalse(
            OemDeviceRules.isNativeIslandActive(
                notifications = listOf(batteryNotif, playingMusicNotif),
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = false
            )
        )
    }

    @Test
    fun testHasNativeSystemIsland_DevicesWithoutNativeIsland() {
        assertFalse(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.SAMSUNG))
        assertFalse(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.GOOGLE_PIXEL))
        assertFalse(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.MOTOROLA))
        assertFalse(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.NUBIA))
        assertFalse(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.GENERIC))

        assertTrue(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.VIVO_IQOO))
        assertTrue(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.REALME_OPPO_ONEPLUS))
        assertTrue(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.XIAOMI_REDMI_POCO))
        assertTrue(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.HUAWEI_HONOR))
        assertTrue(OemDeviceRules.hasNativeSystemIsland(OemDeviceType.TRANSSION))
    }

    @Test
    fun testSamsungDoesNotYieldForMusicOrTimers() {
        val playingMusicNotif = com.moksha.auraisland.model.IslandNotification(
            key = "music_track",
            packageName = "com.spotify.music",
            appName = "Spotify",
            title = "Song Title",
            text = "Artist Name",
            timeMillis = System.currentTimeMillis(),
            mediaIsPlaying = true,
            mode = com.moksha.auraisland.model.IslandMode.Music
        )

        // Samsung has no native dynamic island at punch-hole, so it should NEVER yield
        assertFalse(
            OemDeviceRules.isNativeIslandActive(
                notifications = listOf(playingMusicNotif),
                device = OemDeviceType.SAMSUNG,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.Music,
                packageName = "com.spotify.music",
                device = OemDeviceType.SAMSUNG,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )
    }

    @Test
    fun testVivoUpslideNotTreatedAsNativeIsland() {
        assertFalse(OemDeviceRules.isNativeSystemIslandPackage("com.vivo.upslide"))
        assertFalse(OemDeviceRules.isNativeSystemIslandPackage("com.vivo.assistant"))
        assertFalse(OemDeviceRules.isNativeSystemIslandPackage("com.vivo.daemonService"))
    }

    @Test
    fun testDemoAlertsNeverYieldToNativeSystemIsland() {
        val demoCall = com.moksha.auraisland.model.IslandNotification(
            key = "demo_call",
            packageName = "com.google.android.dialer",
            appName = "Phone",
            title = "John Doe",
            text = "Incoming Call",
            timeMillis = System.currentTimeMillis(),
            mode = com.moksha.auraisland.model.IslandMode.IncomingCall
        )

        assertFalse(
            OemDeviceRules.isNativeIslandActive(
                notifications = listOf(demoCall),
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )

        assertFalse(
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = com.moksha.auraisland.model.IslandMode.IncomingCall,
                packageName = "demo_call",
                device = OemDeviceType.VIVO_IQOO,
                yieldEnabled = true,
                handlingMode = com.moksha.auraisland.data.AuraSettings.OEM_ISLAND_MODE_SMART_YIELD,
                yieldMusic = true
            )
        )
    }
}
