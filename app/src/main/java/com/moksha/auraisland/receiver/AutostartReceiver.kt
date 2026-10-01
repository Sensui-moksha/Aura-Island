/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moksha.auraisland.data.AuraSettingsRepository
import com.moksha.auraisland.util.RootManager
import com.moksha.auraisland.util.ShizukuManager
import com.moksha.auraisland.util.SystemServiceRecovery
import com.moksha.auraisland.util.runCatchingLogged
import com.moksha.auraisland.util.runSuspendCatchingLogged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutostartReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: AuraSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        runCatchingLogged("AutostartReceiver", "Autostart broadcast callback failed") {
            val action = intent.action ?: return@runCatchingLogged
            if (action != Intent.ACTION_BOOT_COMPLETED &&
                action != Intent.ACTION_MY_PACKAGE_REPLACED &&
                action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
                action != Intent.ACTION_USER_PRESENT
            ) return@runCatchingLogged

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    runSuspendCatchingLogged(
                        "AutostartReceiver",
                        "Failed handling autostart broadcast"
                    ) {
                        val settings = settingsRepository.settings.first()
                        if (settings.enabled) {
                            if (settings.rootModeEnabled && RootManager.isRootAvailable(context)) {
                                RootManager.autoGrantAllPermissions(context)
                            } else if (ShizukuManager.hasPermission()) {
                                ShizukuManager.autoGrantAllPermissions(context)
                            } else {
                                // Silent WRITE_SECURE_SETTINGS restore — no prompt needed
                                // when permission was previously granted via ADB/Shizuku/Root
                                SystemServiceRecovery.requestRecovery(context)
                            }
                        }
                    }
                } finally {
                    runCatchingLogged("AutostartReceiver", "goAsync finish failed") {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

