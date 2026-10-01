/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.model

enum class SwipeAction(val id: String) {
    DismissCurrent("DismissCurrent"),
    DismissAll("DismissAll"),
    Collapse("Collapse"),
    OpenApp("OpenApp"),
    FloatingWindow("FloatingWindow"),
    NotificationShade("NotificationShade"),
    NextPrevious("NextPrevious"),
    NextNotification("NextNotification"),
    PreviousNotification("PreviousNotification"),
    NextTrack("NextTrack"),
    PreviousTrack("PreviousTrack"),
    PlayPause("PlayPause"),
    Expand("Expand"),
    None("None");

    companion object {
        fun fromId(id: String?, default: SwipeAction = None): SwipeAction {
            if (id.isNullOrBlank()) return default
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}
