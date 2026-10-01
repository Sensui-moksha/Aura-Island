/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.moksha.auraisland.BuildConfig
import com.moksha.auraisland.data.AppShortcutProvider
import com.moksha.auraisland.data.AuraSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnostic logger and process logcat aggregator for Aura troubleshooting.
 * Captures in-memory log events and disk buffer, with one-tap export to storage via SAF.
 */
object AppLogRecorder {
    private const val TAG = "AuraLog"
    private const val MAX_IN_MEMORY_LOGS = 3000
    private const val LOG_FILE_NAME = "aura_debug_log.txt"

    private val inMemoryLogs = ConcurrentLinkedQueue<String>()
    private val logCount = AtomicInteger(0)

    @Volatile
    var isRecording: Boolean = false
        private set

    fun updateRecordingState(@Suppress("UNUSED_PARAMETER") context: Context? = null, enabled: Boolean) {
        isRecording = enabled
        if (enabled) {
            record("I", TAG, "Diagnostic log recording started for Aura v${BuildConfig.VERSION_NAME}")
        } else {
            record("I", TAG, "Diagnostic log recording paused")
        }
    }

    fun record(level: String, tag: String, message: String, tr: Throwable? = null) {
        if (!isRecording) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val threadName = Thread.currentThread().name
        val entry = buildString {
            append("[$timestamp] [Thread: $threadName] [$level/$tag] $message")
            if (tr != null) {
                append("\n").append(Log.getStackTraceString(tr))
            }
        }

        inMemoryLogs.add(entry)
        val count = logCount.incrementAndGet()
        while (count > MAX_IN_MEMORY_LOGS) {
            inMemoryLogs.poll()
            logCount.decrementAndGet()
        }
    }

    @Suppress("unused")
    fun d(tag: String, message: String) = record("D", tag, message)
    @Suppress("unused")
    fun i(tag: String, message: String) = record("I", tag, message)
    @Suppress("unused")
    fun w(tag: String, message: String, tr: Throwable? = null) = record("W", tag, message, tr)
    @Suppress("unused")
    fun e(tag: String, message: String, tr: Throwable? = null) = record("E", tag, message, tr)

    fun getLogCount(): Int = logCount.get()

    fun clear(context: Context) {
        inMemoryLogs.clear()
        logCount.set(0)
        runCatching {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.delete()
        }
        record("I", TAG, "Log buffer cleared by user")
    }

    /**
     * Reads process-level logcat filtered to this application's PID.
     * Captures all standard android.util.Log messages and framework events.
     */
    fun getProcessLogcat(): String {
        return runCatching {
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuilder()
            var line: String?
            var lineCount = 0
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
                lineCount++
                if (lineCount >= 4000) break
            }
            reader.close()
            process.destroy()
            if (sb.isEmpty()) "No process logcat entries captured." else sb.toString()
        }.getOrElse { "Failed to read process logcat: ${it.localizedMessage}" }
    }

    /**
     * Compiles a comprehensive diagnostic report containing:
     * - System & Hardware Metadata
     * - Application State & Permissions
     * - Complete Settings Configuration JSON
     * - In-Memory Recorded Event Stream
     * - Full Process Logcat
     */
    suspend fun generateDiagnosticReport(context: Context, settings: AuraSettings): String = withContext(Dispatchers.IO) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val displayMetrics = context.resources.displayMetrics
        val overlayAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        val shizukuActive: Boolean = runCatching {
            ShizukuManager.isBinderAvailable() && ShizukuManager.hasPermission()
        }.getOrDefault(false)

        val rootStatus = RootManager.getDiagnosticStatus(context)

        buildString {
            appendLine("================================================================")
            appendLine(" AURA DIAGNOSTIC LOG REPORT")
            appendLine(" Generated at: $now")
            appendLine("================================================================")
            appendLine()
            appendLine("--- 1. APP & ENVIRONMENT ---")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (Code: ${BuildConfig.VERSION_CODE})")
            appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Package Name: ${context.packageName}")
            appendLine("Target SDK: ${context.applicationInfo.targetSdkVersion}")
            appendLine("Process PID: ${Process.myPid()}")
            appendLine()
            appendLine("--- 2. DEVICE & SYSTEM ---")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Android Release: ${Build.VERSION.RELEASE} (API Level: ${Build.VERSION.SDK_INT})")
            appendLine("Security Patch: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"}")
            appendLine("Display Metrics: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels} @ ${displayMetrics.densityDpi}dpi (density=${displayMetrics.density})")
            appendLine("OEM Protection Rules: ${OemDeviceRules.detectCurrentDevice()}")
            appendLine()
            appendLine("--- 3. PERMISSION & SERVICE STATUS ---")
            appendLine("Root Status: $rootStatus")
            appendLine("Can Draw Overlays: $overlayAllowed")
            appendLine("Shizuku Authorized: $shizukuActive")
            appendLine("Notification Listener Enabled: ${isNotificationListenerEnabled(context)}")
            appendLine("App Usage Access: ${AppShortcutProvider.hasUsageAccess(context)}")
            appendLine()
            appendLine("--- 4. AURA SETTINGS SNAPSHOT ---")
            appendLine(settings.toJson(appVersion = BuildConfig.VERSION_NAME))
            appendLine()
            appendLine("--- 5. IN-APP RECORDED LOG BUFFER (${inMemoryLogs.size} ENTRIES) ---")
            if (inMemoryLogs.isEmpty()) {
                appendLine("(No in-app recording entries. Ensure 'Record Logs' was enabled while reproducing the issue.)")
            } else {
                inMemoryLogs.forEach { entry ->
                    appendLine(entry)
                }
            }
            appendLine()
            appendLine("--- 6. PROCESS LOGCAT CAPTURE ---")
            appendLine(getProcessLogcat())
            appendLine()
            appendLine("================================================================")
            appendLine(" END OF REPORT")
            appendLine("================================================================")
        }
    }

    /**
     * Exports the diagnostic report to the user-selected Storage Access Framework URI.
     */
    suspend fun exportLogsToUri(
        context: Context,
        uri: Uri,
        settings: AuraSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val report = generateDiagnosticReport(context, settings)
            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                outStream.write(report.toByteArray(Charsets.UTF_8))
                outStream.flush()
            } ?: throw IllegalStateException("Unable to open output stream for selected storage destination")
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        return PermissionUtils.isNotificationListenerEnabled(context)
    }
}
