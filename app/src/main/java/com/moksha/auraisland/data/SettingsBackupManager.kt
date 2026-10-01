/*
 * Aura (2026)
 * Author / Developer: Sensui-moksha
 * https://github.com/Sensui-moksha
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.moksha.auraisland.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.moksha.auraisland.util.RootManager
import com.moksha.auraisland.util.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages persistent settings backup to external storage that survives app reinstalls.
 *
 * Backup location: /sdcard/Documents/AuraIsland/aura_settings_backup.json
 * This location persists across app installs/uninstalls (unlike app-specific directories).
 *
 * On first launch after reinstall, the app checks for this backup and offers to restore it.
 * Uses a resilient multi-tier storage engine (Direct File I/O + MediaStore + Shizuku/Root shell).
 */
object SettingsBackupManager {

    private const val TAG = "SettingsBackupManager"
    private const val BACKUP_DIR_NAME = "AuraIsland"
    private const val BACKUP_FILE_NAME = "aura_settings_backup.json"
    private const val MARKER_FILE_NAME = ".aura_installed"

    /**
     * Returns the persistent backup directory: /sdcard/Documents/AuraIsland/
     */
    fun getBackupDir(): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documentsDir, BACKUP_DIR_NAME)
    }

    /**
     * Returns the backup file path.
     */
    fun getBackupFile(): File = File(getBackupDir(), BACKUP_FILE_NAME)

    /**
     * Saves the current settings as a persistent backup to external storage.
     * Called automatically whenever settings change.
     */
    suspend fun saveBackup(settings: AuraSettings, appVersion: String = "", context: Context? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val json = settings.toJson(appVersion)
            var saved = false

            // Strategy 1: Direct File Write
            try {
                val dir = getBackupDir()
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val backupFile = getBackupFile()
                val tempFile = File(dir, "${BACKUP_FILE_NAME}.tmp")
                tempFile.writeText(json, Charsets.UTF_8)
                if (tempFile.renameTo(backupFile)) {
                    saved = true
                    Log.d(TAG, "Settings backup saved directly to: ${backupFile.absolutePath}")
                } else {
                    backupFile.writeText(json, Charsets.UTF_8)
                    tempFile.delete()
                    saved = true
                    Log.d(TAG, "Settings backup saved (direct fallback) to: ${backupFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct file save failed (likely scoped storage restrictions): ${e.message}")
            }

            // Strategy 2: MediaStore Documents Provider (Android 10+)
            if (!saved && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(BACKUP_FILE_NAME, "%$BACKUP_DIR_NAME%")

                    val existingUri = resolver.query(
                        collection,
                        arrayOf(MediaStore.MediaColumns._ID),
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            ContentUris.withAppendedId(collection, id)
                        } else null
                    }

                    val targetUri = existingUri ?: run {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILE_NAME)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR_NAME")
                        }
                        resolver.insert(collection, values)
                    }

                    if (targetUri != null) {
                        resolver.openOutputStream(targetUri, "wt")?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        saved = true
                        Log.d(TAG, "Settings backup saved via MediaStore: $targetUri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore save failed: ${e.message}")
                }
            }

            // Strategy 3: Privileged Shell via Shizuku or Root
            if (!saved && context != null) {
                try {
                    val cacheFile = File(context.cacheDir, BACKUP_FILE_NAME)
                    cacheFile.writeText(json, Charsets.UTF_8)
                    val dirPath = getBackupDir().absolutePath
                    val filePath = getBackupFile().absolutePath
                    val cmd = "mkdir -p '$dirPath' && cp '${cacheFile.absolutePath}' '$filePath' && chmod 666 '$filePath'"

                    if (ShizukuManager.hasPermission()) {
                        val res = ShizukuManager.runCommand(cmd)
                        if (res.isSuccess) {
                            saved = true
                            Log.d(TAG, "Settings backup saved via Shizuku: $filePath")
                        }
                    } else if (RootManager.isRootAvailable(context)) {
                        val res = RootManager.runRootCommands(listOf(cmd))
                        if (res.isSuccess) {
                            saved = true
                            Log.d(TAG, "Settings backup saved via Root: $filePath")
                        }
                    }
                    cacheFile.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Privileged shell save failed: ${e.message}")
                }
            }

            saved
        }
    }

    /**
     * Reads the raw JSON text from persistent backup storage using all available strategies.
     */
    suspend fun loadBackupJson(context: Context? = null): String? {
        return withContext(Dispatchers.IO) {
            val backupFile = getBackupFile()

            // Strategy 1: Direct File read
            try {
                if (backupFile.exists() && backupFile.canRead()) {
                    val content = backupFile.readText(Charsets.UTF_8)
                    if (content.isNotBlank()) {
                        Log.d(TAG, "Settings backup loaded directly from: ${backupFile.absolutePath}")
                        return@withContext content
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct file read failed: ${e.message}")
            }

            // Strategy 2: MediaStore read (Android 10+)
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(BACKUP_FILE_NAME, "%$BACKUP_DIR_NAME%")

                    resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            val uri = ContentUris.withAppendedId(collection, id)
                            val content = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            if (!content.isNullOrBlank()) {
                                Log.d(TAG, "Settings backup loaded via MediaStore: $uri")
                                return@withContext content
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore read failed: ${e.message}")
                }
            }

            // Strategy 3: Privileged Shell read via Shizuku or Root
            try {
                val filePath = getBackupFile().absolutePath
                val cmd = "cat '$filePath' 2>/dev/null"
                if (ShizukuManager.hasPermission()) {
                    val res = ShizukuManager.runCommand(cmd)
                    if (res.isSuccess && res.getOrNull()?.isNotBlank() == true) {
                        Log.d(TAG, "Settings backup loaded via Shizuku: $filePath")
                        return@withContext res.getOrNull()
                    }
                }
                if (context != null && RootManager.isRootAvailable(context)) {
                    val res = RootManager.runRootCommands(listOf(cmd))
                    if (res.isSuccess && res.getOrNull()?.isNotBlank() == true) {
                        Log.d(TAG, "Settings backup loaded via Root: $filePath")
                        return@withContext res.getOrNull()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Privileged shell read failed: ${e.message}")
            }

            null
        }
    }

    /**
     * Reads the persistent backup from external storage and returns the settings.
     * Returns null if no backup exists or it is corrupted.
     */
    suspend fun loadBackup(context: Context? = null): AuraSettings? {
        val json = loadBackupJson(context) ?: return null
        return try {
            AuraSettings.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse settings backup JSON", e)
            null
        }
    }

    /**
     * Reads the backup metadata without fully deserializing.
     */
    suspend fun loadBackupMetadata(context: Context? = null): AuraSettings.BackupMetadata? {
        val json = loadBackupJson(context) ?: return null
        return try {
            AuraSettings.parseBackupMetadata(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse backup metadata", e)
            null
        }
    }

    /**
     * Checks if a persistent backup exists on external storage.
     */
    fun hasBackup(context: Context? = null): Boolean {
        try {
            val backupFile = getBackupFile()
            if (backupFile.exists() && backupFile.length() > 0) return true
        } catch (_: Exception) {}

        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(BACKUP_FILE_NAME, "%$BACKUP_DIR_NAME%")
                resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.count > 0) return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Deletes the persistent backup from external storage.
     * Called when the user explicitly wants to remove all data.
     */
    suspend fun deleteBackup(context: Context? = null): Boolean {
        return withContext(Dispatchers.IO) {
            var deleted = false
            try {
                val backupFile = getBackupFile()
                val tempFile = File(getBackupDir(), "${BACKUP_FILE_NAME}.tmp")
                val markerFile = File(getBackupDir(), MARKER_FILE_NAME)
                tempFile.delete()
                markerFile.delete()
                if (backupFile.delete()) {
                    deleted = true
                }
                val dir = getBackupDir()
                if (dir.exists() && dir.listFiles()?.isEmpty() == true) {
                    dir.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct file deletion failed: ${e.message}")
            }

            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val resolver = context.contentResolver
                    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(BACKUP_FILE_NAME, "%$BACKUP_DIR_NAME%")
                    val rows = resolver.delete(collection, selection, selectionArgs)
                    if (rows > 0) deleted = true
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore deletion failed: ${e.message}")
                }
            }

            if (context != null) {
                try {
                    val filePath = getBackupFile().absolutePath
                    val dirPath = getBackupDir().absolutePath
                    val cmd = "rm -f '$filePath' '$filePath.tmp'; rmdir '$dirPath' 2>/dev/null || true"
                    if (ShizukuManager.hasPermission()) {
                        ShizukuManager.runCommand(cmd)
                        deleted = true
                    } else if (RootManager.isRootAvailable(context)) {
                        RootManager.runRootCommands(listOf(cmd))
                        deleted = true
                    }
                } catch (_: Exception) {}
            }

            deleted
        }
    }

    /**
     * Checks if this is a fresh install (no install marker).
     * Used to detect reinstall scenarios.
     */
    fun isFreshInstall(context: Context): Boolean {
        val markerFile = File(context.filesDir, MARKER_FILE_NAME)
        return !markerFile.exists()
    }

    /**
     * Marks the current install so future launches know it's not a fresh install.
     */
    fun markInstalled(context: Context) {
        try {
            val markerFile = File(context.filesDir, MARKER_FILE_NAME)
            if (!markerFile.exists()) {
                markerFile.writeText(System.currentTimeMillis().toString())
                Log.d(TAG, "Install marker written")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write install marker", e)
        }
    }

    /**
     * Checks whether there's a backup available and this is a fresh install.
     * Used to prompt the user for restore on first launch after reinstall.
     */
    fun shouldOfferRestore(context: Context): Boolean {
        return isFreshInstall(context) && hasBackup(context)
    }
}
