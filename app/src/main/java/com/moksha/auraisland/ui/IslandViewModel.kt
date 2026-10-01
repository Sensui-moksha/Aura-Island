/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.data.AuraCommand
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import com.moksha.auraisland.util.OemDeviceRules
import com.moksha.auraisland.util.runSuspendCatchingLogged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class IslandViewModel(
    settingsRepo: AuraSettingsRepository,
    val notificationRepo: INotificationRepository
) : ViewModel() {

    val settings = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuraSettings.Default
    )

    val notifications = notificationRepo.notifications
    val isCustomizing = notificationRepo.isCustomizing
    val foregroundPackage = MutableStateFlow<String?>(null)

    val visibleNotifications: StateFlow<List<IslandNotification>> = combine(
        notifications,
        foregroundPackage,
        settings,
        notificationRepo.isNativeIslandActive
    ) { list, fgPkg, s, nativeSignalActive ->
        val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(s.deviceType)
        val hasNativeIsland = OemDeviceRules.hasNativeSystemIsland(effectiveDevice)

        // When Smart Yield or Hide Aura is enabled, if the native system dynamic island is active,
        // totally hide the entire Aura Island overlay to give 100% unobstructed priority to the native island!
        val isNativeActive = (s.yieldToSystemIsland && hasNativeIsland && nativeSignalActive) ||
            OemDeviceRules.isNativeIslandActive(
                notifications = list,
                device = effectiveDevice,
                yieldEnabled = s.yieldToSystemIsland,
                handlingMode = s.oemIslandHandlingMode,
                yieldMusic = s.yieldMusicToSystemIsland
            )
        if (isNativeActive) {
            return@combine emptyList()
        }

        list.filterNot { notif ->
            (!s.enableBatteryMode && notif.mode == IslandMode.Battery) ||
            OemDeviceRules.shouldYieldToNativeIsland(
                mode = notif.mode,
                packageName = notif.packageName,
                device = effectiveDevice,
                yieldEnabled = s.yieldToSystemIsland,
                handlingMode = s.oemIslandHandlingMode,
                yieldMusic = s.yieldMusicToSystemIsland
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val isOemYielding: StateFlow<Boolean> = combine(
        notifications,
        settings,
        notificationRepo.isNativeIslandActive
    ) { list, s, nativeSignalActive ->
        val effectiveDevice = OemDeviceRules.resolveEffectiveDevice(s.deviceType)
        val hasNativeIsland = OemDeviceRules.hasNativeSystemIsland(effectiveDevice)
        if (!s.yieldToSystemIsland || !hasNativeIsland) {
            false
        } else {
            nativeSignalActive || OemDeviceRules.isNativeIslandActive(
                notifications = list,
                device = effectiveDevice,
                yieldEnabled = s.yieldToSystemIsland,
                handlingMode = s.oemIslandHandlingMode,
                yieldMusic = s.yieldMusicToSystemIsland
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val expanded = MutableStateFlow(false)
    val showAppLauncher = MutableStateFlow(false)
    val selectedIndex = MutableStateFlow(0)
    val isLocked = MutableStateFlow(false)
    val isInputActive = MutableStateFlow(false)
    val isAutoHidden = MutableStateFlow(false)

    val mode: StateFlow<IslandMode> = combine(visibleNotifications, selectedIndex) { list, idx ->
        list.getOrNull(idx)?.mode ?: IslandMode.Empty
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = IslandMode.Empty
    )

    private var autoCollapseJob: Job? = null

    init {
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "isOemYielding collector failed") {
                isOemYielding.collect { yielding ->
                    if (yielding) {
                        collapse()
                    }
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Notifications collector failed") {
                var previousKeys = emptyList<String>()
                visibleNotifications.collect { list ->
                    val currentKeys = list.map { it.key }
                    val newlyAdded = currentKeys.filter { it !in previousKeys }
                    if (newlyAdded.isNotEmpty()) {
                        // Prefer actively-playing music notification over battery/other modes so
                        // that the music pill is always the primary island when music is playing.
                        val musicIdx = list.indexOfFirst {
                            it.mode == IslandMode.Music && it.mediaIsPlaying
                        }
                        if (musicIdx >= 0) {
                            selectedIndex.value = musicIdx
                        } else {
                            // Focus the latest notification so the dynamic island always displays the newest event
                            val latestKey = newlyAdded.last()
                            val latestIndex = list.indexOfFirst { it.key == latestKey }
                            if (latestIndex >= 0) {
                                selectedIndex.value = latestIndex
                            }
                        }
                    } else {
                        selectedIndex.update { currentSelected ->
                            currentSelected.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                        }
                    }
                    previousKeys = currentKeys
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Auto-expand collector failed") {
                notificationRepo.autoExpandEvent.collect { key ->
                    val notif = notifications.value.firstOrNull { it.key == key } ?: return@collect
                    val isFgMusic = notif.mode == IslandMode.Music &&
                        !foregroundPackage.value.isNullOrEmpty() &&
                        notif.packageName == foregroundPackage.value
                    if (isFgMusic) return@collect

                    val visibleIdx = visibleNotifications.value.indexOfFirst { it.key == key }
                    if (visibleIdx >= 0) {
                        selectedIndex.value = visibleIdx
                    } else {
                        val notifIdx = notifications.value.indexOfFirst { it.key == key }
                        if (notifIdx >= 0) {
                            selectedIndex.value = notifIdx
                        }
                    }

                    val isDemo = key.startsWith("demo_")
                    if (settings.value.autoExpandOnNotification || isDemo) {
                        expand()
                        startAutoCollapseTimer()
                    }
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Timer-reset collector failed") {
                notificationRepo.resetTimerEvent.collect {
                    resetAutoCollapseTimer()
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Expanded-state collector failed") {
                expanded.collect { isExpanded ->
                    if (isExpanded) {
                        if (!isInputActive.value) {
                            startAutoCollapseTimer()
                        }
                    } else {
                        stopAutoCollapseTimer()
                        isInputActive.value = false
                    }
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatchingLogged(TAG, "Input-active collector failed") {
                isInputActive.collect { active ->
                    if (active) {
                        stopAutoCollapseTimer()
                    } else if (expanded.value) {
                        startAutoCollapseTimer()
                    }
                }
            }
        }
    }

    fun expand(showLauncher: Boolean = false) {
        showAppLauncher.value = showLauncher
        expanded.value = true
    }

    fun collapse() {
        isInputActive.value = false
        showAppLauncher.value = false
        expanded.value = false
    }

    fun setInputActive(active: Boolean) {
        if (isInputActive.value != active) {
            isInputActive.value = active
        }
    }

    fun setAutoHidden(hidden: Boolean) {
        if (isAutoHidden.value != hidden) {
            isAutoHidden.value = hidden
        }
    }

    private var lastToggleTimeMs = 0L

    fun toggleExpanded(forceLauncher: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastToggleTimeMs < 350L) return
        lastToggleTimeMs = now
        if (expanded.value) {
            if (forceLauncher && !showAppLauncher.value) {
                // If viewing a notification card, hold & drag down switches to App Launcher
                showAppLauncher.value = true
                resetAutoCollapseTimer()
            } else {
                collapse()
            }
        } else {
            val hasEvents = visibleNotifications.value.isNotEmpty()
            showAppLauncher.value = forceLauncher || !hasEvents
            expanded.value = true
        }
    }

    fun openAppLauncher() {
        val now = System.currentTimeMillis()
        if (now - lastToggleTimeMs < 200L) return
        lastToggleTimeMs = now
        showAppLauncher.value = true
        expanded.value = true
        resetAutoCollapseTimer()
    }

    private fun startAutoCollapseTimer() {
        autoCollapseJob?.cancel()
        if (isInputActive.value) return
        autoCollapseJob = viewModelScope.launch {
            delay(AUTO_COLLAPSE_DELAY_MS)
            collapse()
        }
    }

    private fun stopAutoCollapseTimer() {
        autoCollapseJob?.cancel()
        autoCollapseJob = null
    }

    fun resetAutoCollapseTimer() {
        if (expanded.value) {
            startAutoCollapseTimer()
        }
    }

    fun setSelectedNotificationIndex(index: Int) {
        val list = visibleNotifications.value
        if (index in list.indices) {
            selectedIndex.value = index
            resetAutoCollapseTimer()
        }
    }

    fun dismissCurrentNotification() {
        val list = visibleNotifications.value
        val index = selectedIndex.value
        if (index in list.indices) {
            dismissNotification(list[index])
        } else {
            collapse()
        }
    }

    fun dismissNotification(notification: IslandNotification) {
        notificationRepo.removeNotification(notification.key)
        notificationRepo.sendCommand(AuraCommand.CancelNotification(notification.key))
        val currentList = visibleNotifications.value
        val remaining = currentList.filter { it.key != notification.key }
        if (remaining.isEmpty()) {
            collapse()
        } else {
            val currIdx = selectedIndex.value
            if (currIdx >= remaining.size) {
                selectedIndex.value = remaining.lastIndex
            }
            resetAutoCollapseTimer()
        }
    }

    fun dismissAllNotifications() {
        val list = visibleNotifications.value
        for (notification in list) {
            notificationRepo.sendCommand(AuraCommand.CancelNotification(notification.key))
        }
        notificationRepo.removeAllNotifications()
        collapse()
    }

    companion object {
        private const val TAG = "IslandViewModel"
        private const val AUTO_COLLAPSE_DELAY_MS = 5000L

        fun provideFactory(
            settingsRepo: AuraSettingsRepository,
            notificationRepo: INotificationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return IslandViewModel(settingsRepo, notificationRepo) as T
            }
        }
    }
}
