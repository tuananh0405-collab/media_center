package com.anhvt86.mediacenter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single audio track stored on the device.
 * Metadata is extracted from MediaStore during scanning.
 */
@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,         // Duration in milliseconds
    val albumArtUri: String?,   // Content URI for album art
    val filePath: String,       // Absolute file path
    val dateAdded: Long,        // Timestamp when added to DB
    val mediaStoreId: Long      // Original MediaStore ID for dedup
)
