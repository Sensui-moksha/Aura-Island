/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.data

import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface INotificationRepository {
    val notifications: StateFlow<List<IslandNotification>>
    val autoExpandEvent: SharedFlow<String>
    val resetTimerEvent: SharedFlow<Unit>
    val commands: SharedFlow<AuraCommand>

    val isNativeIslandActive: StateFlow<Boolean>
    fun setNativeIslandActive(active: Boolean)

    val isCustomizing: StateFlow<Boolean>
    fun setCustomizing(customizing: Boolean)

    fun postNotification(notification: IslandNotification, autoExpand: Boolean = false)
    fun removeNotification(key: String)
    fun removeNotificationsForPackage(packageName: String)
    fun removeAllNotifications()
    fun resetTimer()
    fun sendCommand(command: AuraCommand)
    fun showDemo(mode: IslandMode)
    fun clearTestNotifications()
}
