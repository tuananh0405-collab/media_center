package com.anhvt86.mediacenter.data.local.entity

import androidx.room.Entity

/**
 * Junction table for the many-to-many relationship between Playlist and MediaItem.
 */
@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "mediaItemId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val mediaItemId: Long,
    val trackOrder: Int = 0
)
