/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.di

import android.content.Context
import com.moksha.auraisland.data.INotificationHistoryRepository
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.data.AuraSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuraRepositoriesEntryPoint {
    fun settingsRepository(): AuraSettingsRepository
    fun notificationRepository(): INotificationRepository
    fun notificationHistoryRepository(): INotificationHistoryRepository
}

object AuraRepositories {
    fun settingsRepository(context: Context): AuraSettingsRepository =
        entryPoint(context).settingsRepository()

    fun notificationRepository(context: Context): INotificationRepository =
        entryPoint(context).notificationRepository()

    fun historyRepository(context: Context): INotificationHistoryRepository =
        entryPoint(context).notificationHistoryRepository()

    private fun entryPoint(context: Context): AuraRepositoriesEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AuraRepositoriesEntryPoint::class.java
        )
}
