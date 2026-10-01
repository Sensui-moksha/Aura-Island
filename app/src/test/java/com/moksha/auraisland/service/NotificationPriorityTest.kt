/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.service

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.util.NotificationFilter
import com.moksha.auraisland.util.toIslandMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationPriorityTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun testShouldIgnoreForAuraSystemCategory() {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val pm = mockk<PackageManager>()
        
        notification.category = Notification.CATEGORY_SYSTEM
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.example.app"

        assertTrue(NotificationFilter.shouldSuppressFromIsland(sbn, pm))
    }

    @Test
    fun testShouldIgnoreForAuraSystemPackage() {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val pm = mockk<PackageManager>()
        
        notification.category = Notification.CATEGORY_MESSAGE
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.android.systemui"

        assertTrue(NotificationFilter.shouldSuppressFromIsland(sbn, pm))
    }

    @Test
    fun testShouldIgnoreForAuraSystemFlagPackage() {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val pm = mockk<PackageManager>()
        val appInfo = mockk<ApplicationInfo>()
        
        notification.category = Notification.CATEGORY_MESSAGE
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.android.customsettings"
        
        appInfo.flags = ApplicationInfo.FLAG_SYSTEM
        every { pm.getApplicationInfo("com.android.customsettings", 0) } returns appInfo

        assertTrue(NotificationFilter.shouldSuppressFromIsland(sbn, pm))
    }

    @Test
    fun testNonSystemIsNotIgnored() {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val pm = mockk<PackageManager>()
        val appInfo = mockk<ApplicationInfo>()
        
        notification.category = Notification.CATEGORY_MESSAGE
        notification.flags = 0
        val extras = mockk<android.os.Bundle>()
        every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns "Title"
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "Text"
        every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
        every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
        every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns false
        every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 0
        every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 0
        every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
        notification.extras = extras
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.example.chat"
        
        appInfo.flags = 0
        every { pm.getApplicationInfo("com.example.chat", 0) } returns appInfo

        assertFalse(NotificationFilter.shouldSuppressFromIsland(sbn, pm))
    }

    @Test
    fun testNonSystemPackageInfoFailureIsNotIgnored() {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val pm = mockk<PackageManager>()
        
        notification.category = Notification.CATEGORY_MESSAGE
        notification.flags = 0
        val extras = mockk<android.os.Bundle>()
        every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns "Title"
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "Text"
        every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
        every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
        every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns false
        every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 0
        every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 0
        every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
        notification.extras = extras
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.example.chat"
        
        every { pm.getApplicationInfo("com.example.chat", 0) } throws PackageManager.NameNotFoundException()

        assertFalse(NotificationFilter.shouldSuppressFromIsland(sbn, pm))
    }

    @Test
    fun testOngoingProgressNotificationIsNotIgnored() {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val pm = mockk<PackageManager>()
        val appInfo = mockk<ApplicationInfo>()
        val extras = mockk<android.os.Bundle>()

        notification.category = Notification.CATEGORY_PROGRESS
        notification.flags = Notification.FLAG_ONGOING_EVENT
        notification.extras = extras
        every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns "Downloading"
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "42%"
        every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
        every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
        every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns false
        every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 100
        every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 42
        every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
        notification.actions = null
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.example.downloader"
        appInfo.flags = 0
        every { pm.getApplicationInfo("com.example.downloader", 0) } returns appInfo

        assertFalse(NotificationFilter.shouldSuppressFromIsland(sbn, pm))
    }

    @Test
    fun testGenericMediaLabelsWithoutMediaSessionRemainNotifications() {
        val testLabels = listOf("PAUSE", "Next Track", "PLAY", "previous song")
        for (label in testLabels) {
            val notification = mockk<Notification>()
            notification.category = Notification.CATEGORY_MESSAGE
            notification.flags = 0
            val action = mockk<Notification.Action>()
            action.title = label
            notification.actions = arrayOf(action)

            val extras = mockk<android.os.Bundle>()
            every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
            every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns false
            every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 0
            every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 0
            every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
            every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns null
            every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns null
            every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
            every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
            notification.extras = extras

            val mode = notification.toIslandMode()
            assertEquals(IslandMode.Notification, mode)
        }
    }

    @Test
    fun testMusicNotificationIsNotIslandOnlyToPreventPausing() {
        val service = AuraNotificationListenerService()
        val notification = mockk<Notification>()
        assertFalse(service.shouldBeIslandOnly(notification, IslandMode.Music))
    }

    @Test
    fun testDndOffAllowsNotifications() {
        val service = AuraNotificationListenerService()
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.whatsapp"
        every { sbn.key } returns "0|com.whatsapp|1|null|1000"
        val extras = mockk<Bundle>()
        every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
        every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns false
        every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 0
        every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 0
        every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
        every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns "Alice"
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "Hey there"
        every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_INFO_TEXT) } returns null
        notification.extras = extras
        notification.category = Notification.CATEGORY_MESSAGE
        notification.flags = 0
        notification.actions = emptyArray()
        notification.tickerText = null

        assertFalse(service.isBlockedByDoNotDisturb(sbn, filterOverride = NotificationListenerService.INTERRUPTION_FILTER_ALL))
    }

    @Test
    fun testDndTotalSilenceBlocksNotifications() {
        val service = AuraNotificationListenerService()
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.whatsapp"
        every { sbn.key } returns "0|com.whatsapp|1|null|1000"
        val extras = mockk<Bundle>()
        every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
        every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns false
        every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 0
        every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 0
        every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
        every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns "Alice"
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "Hey there"
        every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_INFO_TEXT) } returns null
        notification.extras = extras
        notification.category = Notification.CATEGORY_MESSAGE
        notification.flags = 0
        notification.actions = emptyArray()
        notification.tickerText = null

        assertTrue(service.isBlockedByDoNotDisturb(sbn, filterOverride = NotificationListenerService.INTERRUPTION_FILTER_NONE))
    }

    @Test
    fun testDndMusicPlaybackAlwaysAllowed() {
        val service = AuraNotificationListenerService()
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.spotify.music"
        every { sbn.key } returns "0|com.spotify.music|1|null|1000"
        val extras = mockk<Bundle>()
        every { extras.getString(Notification.EXTRA_TEMPLATE) } returns null
        every { extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } returns true
        every { extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) } returns 0
        every { extras.getInt(Notification.EXTRA_PROGRESS, 0) } returns 0
        every { extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) } returns false
        every { extras.getCharSequence(Notification.EXTRA_TITLE) } returns "Song Title"
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "Artist"
        every { extras.getCharSequence(Notification.EXTRA_BIG_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_SUB_TEXT) } returns null
        every { extras.getCharSequence(Notification.EXTRA_INFO_TEXT) } returns null
        notification.extras = extras
        notification.category = Notification.CATEGORY_TRANSPORT
        notification.flags = Notification.FLAG_ONGOING_EVENT
        notification.actions = emptyArray()
        notification.tickerText = null

        assertFalse(service.isBlockedByDoNotDisturb(sbn, filterOverride = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY))
        assertFalse(service.isBlockedByDoNotDisturb(sbn, filterOverride = NotificationListenerService.INTERRUPTION_FILTER_NONE))
    }
}
