package com.anhvt86.mediacenter.data.scanner

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anhvt86.mediacenter.data.local.MediaDatabase

/**
 * WorkManager worker that performs background media scanning.
 * Scans device storage for audio files and upserts them into the Room database.
 * Also removes entries for files that no longer exist on storage.
 */
class ScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ScanWorker"
        const val WORK_NAME = "media_scan_work"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting media scan...")

            val scanner = MediaScanner(applicationContext)
            val scannedItems = scanner.scanMedia()

            Log.d(TAG, "Found ${scannedItems.size} audio files")

            val db = MediaDatabase.getInstance(applicationContext)
            val mediaDao = db.mediaDao()

            // Upsert all scanned items
            mediaDao.insertAll(scannedItems)

            // Remove items that no longer exist on storage
            val activeIds = scannedItems.map { it.mediaStoreId }
            if (activeIds.isNotEmpty()) {
                mediaDao.deleteRemovedItems(activeIds)
            }

            Log.d(TAG, "Media scan complete. ${scannedItems.size} tracks indexed.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Media scan failed", e)
            Result.retry()
        }
    }
}
