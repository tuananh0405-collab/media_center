package com.anhvt86.mediacenter.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.anhvt86.mediacenter.data.local.entity.Playlist
import com.anhvt86.mediacenter.data.local.entity.PlaylistTrackCrossRef
import com.anhvt86.mediacenter.data.local.entity.MediaItem

/**
 * Data Access Object for playlist operations.
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): LiveData<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsList(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Delete
    suspend fun removeTrackFromPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query("""
        SELECT m.* FROM media_items m
        INNER JOIN playlist_track_cross_ref p ON m.id = p.mediaItemId
        WHERE p.playlistId = :playlistId
        ORDER BY p.trackOrder ASC
    """)
    fun getTracksInPlaylist(playlistId: Long): LiveData<List<MediaItem>>

    @Query("""
        SELECT m.* FROM media_items m
        INNER JOIN playlist_track_cross_ref p ON m.id = p.mediaItemId
        WHERE p.playlistId = :playlistId
        ORDER BY p.trackOrder ASC
    """)
    suspend fun getTracksInPlaylistList(playlistId: Long): List<MediaItem>
}
