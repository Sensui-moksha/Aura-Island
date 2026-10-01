/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland

import com.moksha.auraisland.model.SwipeAction
import com.moksha.auraisland.ui.executeSwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillGestureActionTest {

    @Test
    fun nextOrPreviousNotificationDoesNotDismissOnSingleNotification() {
        var dismissed = false
        var pageSelected = -1

        executeSwipeAction(
            action = SwipeAction.NextNotification,
            currentNotification = null,
            context = null,
            onDismiss = { dismissed = true },
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { pageSelected = it },
            notificationsSize = 1,
            currentIndex = 0
        )

        assertFalse("Swipe NextNotification on single notification must not dismiss", dismissed)
        assertEquals(-1, pageSelected)

        executeSwipeAction(
            action = SwipeAction.PreviousNotification,
            currentNotification = null,
            context = null,
            onDismiss = { dismissed = true },
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { pageSelected = it },
            notificationsSize = 1,
            currentIndex = 0
        )

        assertFalse("Swipe PreviousNotification on single notification must not dismiss", dismissed)
        assertEquals(-1, pageSelected)
    }

    @Test
    fun dismissCurrentCallsOnDismiss() {
        var dismissed = false

        executeSwipeAction(
            action = SwipeAction.DismissCurrent,
            currentNotification = null,
            context = null,
            onDismiss = { dismissed = true },
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = {},
            notificationsSize = 1,
            currentIndex = 0
        )

        assertTrue("Swipe DismissCurrent must trigger onDismiss", dismissed)
    }

    @Test
    fun nextNotificationCyclesIndexWhenMultipleNotificationsExist() {
        var selectedPage = -1

        executeSwipeAction(
            action = SwipeAction.NextNotification,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { selectedPage = it },
            notificationsSize = 3,
            currentIndex = 0
        )

        assertEquals(1, selectedPage)

        executeSwipeAction(
            action = SwipeAction.PreviousNotification,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { selectedPage = it },
            notificationsSize = 3,
            currentIndex = 0
        )

        assertEquals(2, selectedPage)
    }

    @Test
    fun expandActionTriggersToggle() {
        var toggled = false

        executeSwipeAction(
            action = SwipeAction.Expand,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = { toggled = true },
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = {},
            notificationsSize = 1,
            currentIndex = 0
        )

        assertTrue("Swipe Expand must trigger onToggle", toggled)
    }

    @Test
    fun nextNotificationCyclesEndlesslyBothDirections() {
        var selectedPage = -1

        // 2 notifications, currently at index 1 -> swipe next should wrap to index 0
        executeSwipeAction(
            action = SwipeAction.NextNotification,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { selectedPage = it },
            notificationsSize = 2,
            currentIndex = 1
        )
        assertEquals(0, selectedPage)

        // 2 notifications, currently at index 0 -> swipe prev should wrap to index 1
        executeSwipeAction(
            action = SwipeAction.PreviousNotification,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { selectedPage = it },
            notificationsSize = 2,
            currentIndex = 0
        )
        assertEquals(1, selectedPage)
    }

    @Test
    fun nextTrackAndPreviousTrackDoNotChangeNotificationIndex() {
        var selectedPage = -1

        executeSwipeAction(
            action = SwipeAction.NextTrack,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { selectedPage = it },
            notificationsSize = 3,
            currentIndex = 0
        )
        assertEquals("NextTrack must not switch notifications", -1, selectedPage)

        executeSwipeAction(
            action = SwipeAction.PreviousTrack,
            currentNotification = null,
            context = null,
            onDismiss = {},
            onDismissAll = {},
            onToggle = {},
            onOpenNotification = {},
            onOpenFloatingWindow = {},
            onOpenNotificationShade = {},
            onPageSelected = { selectedPage = it },
            notificationsSize = 3,
            currentIndex = 1
        )
        assertEquals("PreviousTrack must not switch notifications", -1, selectedPage)
    }
}
