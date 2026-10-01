/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.util.Log
import com.moksha.auraisland.BuildConfig
import kotlinx.coroutines.CancellationException

inline fun <T> runCatchingLogged(tag: String, message: String = "Operation failed", block: () -> T): T? {
    return try {
        block()
    } catch (e: Exception) {
        try {
            AppLogRecorder.e(tag, message, e)
            if (BuildConfig.DEBUG) {
                Log.e(tag, message, e)
            }
        } catch (_: Throwable) { /* Unit test JVM stub */ }
        null
    }
}

suspend inline fun <T> runSuspendCatchingLogged(
    tag: String,
    message: String = "Operation failed",
    crossinline block: suspend () -> T
): T? {
    return try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        try {
            AppLogRecorder.e(tag, message, error)
            if (BuildConfig.DEBUG) {
                Log.e(tag, message, error)
            }
        } catch (_: Throwable) {
            // Unit-test Android stubs can throw from Log.
        }
        null
    }
}
