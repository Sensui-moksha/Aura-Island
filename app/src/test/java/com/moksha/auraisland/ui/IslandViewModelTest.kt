/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui

import com.moksha.auraisland.data.AuraNotificationRepository
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.moksha.auraisland.util.OemDeviceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.mockk.mockk

@OptIn(ExperimentalCoroutinesApi::class)
class IslandViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMusicPillRemainsVisibleInForegroundWhenPlaying() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)

        val musicNotif = IslandNotification(
            key = "music_1",
            packageName = "com.spotify.music",
            appName = "Spotify",
            title = "Blinding Lights",
            text = "The Weeknd",
            mode = IslandMode.Music,
            mediaIsPlaying = true,
            timeMillis = System.currentTimeMillis()
        )

        notifRepo.postNotification(musicNotif)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially Spotify is in background -> music notification is visible
        assertEquals(1, viewModel.visibleNotifications.value.size)
        assertEquals(IslandMode.Music, viewModel.mode.value)

        // User opens Spotify (Spotify in foreground) -> music pill remains active so user can tap & control music!
        viewModel.foregroundPackage.value = "com.spotify.music"
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.visibleNotifications.value.size)
        assertEquals(IslandMode.Music, viewModel.mode.value)

        // User leaves Spotify (Chrome in foreground) -> music notification remains on pill
        viewModel.foregroundPackage.value = "com.android.chrome"
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.visibleNotifications.value.size)
        assertEquals(IslandMode.Music, viewModel.mode.value)
    }

    @Test
    fun testInputActivePausesAutoCollapseAndCollapseResetsInputActive() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)

        // Expand island
        viewModel.expand()
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.expanded.value)

        // Activate text input (e.g. user clicked reply)
        viewModel.setInputActive(true)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isInputActive.value)

        // Advance time past the 5000ms auto-collapse delay
        testDispatcher.scheduler.advanceTimeBy(6000L)
        testDispatcher.scheduler.runCurrent()

        // Island should STILL be expanded because input is active!
        assertTrue(viewModel.expanded.value)

        // Deactivate input
        viewModel.setInputActive(false)
        testDispatcher.scheduler.runCurrent()
        org.junit.Assert.assertFalse(viewModel.isInputActive.value)

        // Advance 5000ms now that input is inactive
        testDispatcher.scheduler.advanceTimeBy(5500L)
        testDispatcher.scheduler.runCurrent()

        // Island should now have auto-collapsed
        org.junit.Assert.assertFalse(viewModel.expanded.value)
    }

    @Test
    fun testExplicitCollapseResetsInputActive() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)

        viewModel.expand()
        viewModel.setInputActive(true)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isInputActive.value)

        viewModel.collapse()
        testDispatcher.scheduler.runCurrent()
        org.junit.Assert.assertFalse(viewModel.expanded.value)
        org.junit.Assert.assertFalse(viewModel.isInputActive.value)
    }

    @Test
    fun testAutoExpandEnabledExpandsIslandOnNewNotification() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        io.mockk.every { settingsRepo.settings } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.moksha.auraisland.data.AuraSettings(autoExpandOnNotification = true)
        )
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)
        testDispatcher.scheduler.runCurrent()

        val notif = IslandNotification(
            key = "msg_1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            text = "Hello!",
            mode = IslandMode.Notification,
            timeMillis = System.currentTimeMillis()
        )

        notifRepo.postNotification(notif, autoExpand = true)
        testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.expanded.value)
        assertEquals(0, viewModel.selectedIndex.value)
    }

    @Test
    fun testAutoExpandDisabledKeepsIslandCollapsedOnNewNotification() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        io.mockk.every { settingsRepo.settings } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.moksha.auraisland.data.AuraSettings(autoExpandOnNotification = false)
        )
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)
        testDispatcher.scheduler.runCurrent()

        val notif = IslandNotification(
            key = "msg_2",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Bob",
            text = "Hey there",
            mode = IslandMode.Notification,
            timeMillis = System.currentTimeMillis()
        )

        notifRepo.postNotification(notif, autoExpand = true)
        testDispatcher.scheduler.runCurrent()

        // Should NOT be expanded because autoExpandOnNotification is false
        org.junit.Assert.assertFalse(viewModel.expanded.value)
        // Notification is still captured and selected for compact pill
        assertEquals(1, viewModel.visibleNotifications.value.size)
        assertEquals(0, viewModel.selectedIndex.value)
    }

    @Test
    fun testInitialIdleStateDoesNotYieldWhenEmptyNotifications() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        io.mockk.every { settingsRepo.settings } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.moksha.auraisland.data.AuraSettings(
                yieldToSystemIsland = true,
                deviceType = OemDeviceType.VIVO_IQOO.name
            )
        )
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)
        testDispatcher.scheduler.runCurrent()

        // Initially with empty notifications, Aura Island must NOT yield to OEM!
        // It stays visible as default unless hideWhenIdle is set.
        org.junit.Assert.assertFalse(viewModel.isOemYielding.value)
    }

    @Test
    fun testOemYieldingActivatesOnNativeDynamicIslandAndRestoresWhenDismissed() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        io.mockk.every { settingsRepo.settings } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.moksha.auraisland.data.AuraSettings(
                yieldToSystemIsland = true,
                deviceType = OemDeviceType.VIVO_IQOO.name
            )
        )
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)
        testDispatcher.scheduler.runCurrent()

        // Initially false
        org.junit.Assert.assertFalse(viewModel.isOemYielding.value)

        // Native LiveAlert arrives
        val nativeNotif = IslandNotification(
            key = "native_1",
            packageName = "com.vivo.livealert",
            appName = "Live Alerts",
            title = "Timer",
            text = "00:05",
            mode = IslandMode.Timer,
            timeMillis = System.currentTimeMillis()
        )
        notifRepo.postNotification(nativeNotif)
        testDispatcher.scheduler.runCurrent()

        // Aura Island yields to native dynamic island popup
        assertTrue(viewModel.isOemYielding.value)
        assertTrue(viewModel.visibleNotifications.value.isEmpty())

        // Native popup dismisses
        notifRepo.removeNotification("native_1")
        testDispatcher.scheduler.runCurrent()

        // Aura Island stops yielding and restores immediately
        org.junit.Assert.assertFalse(viewModel.isOemYielding.value)
    }

    @Test
    fun testInstantYieldOnNativeIslandSignal() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        io.mockk.every { settingsRepo.settings } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.moksha.auraisland.data.AuraSettings(
                yieldToSystemIsland = true,
                deviceType = OemDeviceType.VIVO_IQOO.name
            )
        )
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)
        testDispatcher.scheduler.runCurrent()

        // Initially false
        org.junit.Assert.assertFalse(viewModel.isOemYielding.value)

        // Native island active signal fired (from charging popup or window overlay)
        notifRepo.setNativeIslandActive(true)
        testDispatcher.scheduler.runCurrent()

        // Aura Island instantly yields and hides
        assertTrue(viewModel.isOemYielding.value)
        assertTrue(viewModel.visibleNotifications.value.isEmpty())

        // Native island dismisses
        notifRepo.setNativeIslandActive(false)
        testDispatcher.scheduler.runCurrent()

        // Restores immediately
        org.junit.Assert.assertFalse(viewModel.isOemYielding.value)
    }

    @Test
    fun testDemoCallAndNavSwitchingWithoutConflict() = runTest {
        val settingsRepo = mockk<AuraSettingsRepository>(relaxed = true)
        val notifRepo = AuraNotificationRepository()
        val viewModel = IslandViewModel(settingsRepo, notifRepo)
        testDispatcher.scheduler.runCurrent()

        // 1. User tests Call alert
        notifRepo.showDemo(IslandMode.IncomingCall)
        testDispatcher.scheduler.runCurrent()

        // Call alert is visible (NOT yielding or black)
        assertEquals(1, viewModel.visibleNotifications.value.size)
        assertEquals(IslandMode.IncomingCall, viewModel.mode.value)
        assertEquals(0, viewModel.selectedIndex.value)

        // 2. User tests Nav alert while Call is still active
        notifRepo.showDemo(IslandMode.Navigation)
        testDispatcher.scheduler.runCurrent()

        // Both are in visibleNotifications, and Nav is automatically selected as the latest
        assertEquals(2, viewModel.visibleNotifications.value.size)
        assertEquals(IslandMode.Navigation, viewModel.mode.value)
        assertEquals(1, viewModel.selectedIndex.value)
    }
}

