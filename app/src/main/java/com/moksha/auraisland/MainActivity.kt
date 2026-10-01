/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moksha.auraisland.data.INotificationRepository
import com.moksha.auraisland.data.AuraSettings
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.ui.AuraHomeScreen
import com.moksha.auraisland.ui.AuraTheme
import com.moksha.auraisland.util.SystemServiceRecovery
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: AuraSettingsRepository
    @Inject lateinit var notificationRepository: INotificationRepository

    private var destinationSection by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destinationSection = intent?.getStringExtra("extra_destination_section")
        SystemServiceRecovery.requestRecovery(this)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AuraSettings.Default)
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (settings.themeMode) {
                AuraSettings.THEME_MODE_DARK -> true
                AuraSettings.THEME_MODE_LIGHT -> false
                else -> isSystemDark
            }

            AuraTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settings.useDynamicColor
            ) {
                AuraHomeScreen(
                    repository = settingsRepository,
                    notificationRepository = notificationRepository,
                    initialDetailSection = destinationSection,
                    onClearInitialDetailSection = { destinationSection = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("extra_destination_section")?.let {
            destinationSection = it
        }
    }

    override fun onResume() {
        super.onResume()
        SystemServiceRecovery.requestRecovery(this)
    }

    override fun onStop() {
        super.onStop()
        notificationRepository.setCustomizing(false)
    }
}
