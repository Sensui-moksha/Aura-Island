/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moksha.auraisland.model.IslandMode
import com.moksha.auraisland.model.IslandNotification
import com.moksha.auraisland.ui.IslandCollapsedContent
import com.moksha.auraisland.ui.AuraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IslandOverlaySmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun collapsed_renders_battery() {
        compose.setContent {
            AuraTheme {
                IslandCollapsedContent(
                    mode = IslandMode.Battery,
                    notification = IslandNotification(
                        key = "t", packageName = "x", appName = "x",
                        title = "Charging", text = "49%", timeMillis = 0,
                        mode = IslandMode.Battery
                    ),
                    collapsedAlpha = 1f
                )
            }
        }
        compose.onNodeWithText("49%").assertExists()
    }
}
