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
import com.moksha.auraisland.data.NotificationHistoryRepository
import com.moksha.auraisland.data.AuraNotificationRepository
import com.moksha.auraisland.data.AuraSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): AuraSettingsRepository = AuraSettingsRepository(context)

    @Provides
    @Singleton
    fun provideNotificationRepository(): INotificationRepository =
        AuraNotificationRepository()

    @Provides
    @Singleton
    fun provideNotificationHistoryRepository(
        @ApplicationContext context: Context
    ): INotificationHistoryRepository = NotificationHistoryRepository(context)
}
