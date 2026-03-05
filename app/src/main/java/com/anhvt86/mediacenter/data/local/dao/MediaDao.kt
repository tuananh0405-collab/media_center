package com.anhvt86.mediacenter.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.anhvt86.mediacenter.data.local.entity.MediaItem

/**
 * Data Access Object for media track queries.
 */
@Dao
interface MediaDao {

    // ── All Songs ──────────────────────────────────────────────────
    @Query("SELECT * FROM media_items ORDER BY title ASC")
    fun getAllSongs(): LiveData<List<MediaItem>>

    @Query("SELECT * FROM media_items ORDER BY title ASC")
    suspend fun getAllSongsList(): List<MediaItem>

    // ── By Album ───────────────────────────────────────────────────
    @Query("SELECT DISTINCT album FROM media_items ORDER BY album ASC")
    fun getAlbums(): LiveData<List<String>>

    @Query("SELECT * FROM media_items WHERE album = :album ORDER BY title ASC")
    fun getSongsByAlbum(album: String): LiveData<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE album = :album ORDER BY title ASC")
    suspend fun getSongsByAlbumList(album: String): List<MediaItem>

    // ── By Artist ──────────────────────────────────────────────────
    @Query("SELECT DISTINCT artist FROM media_items ORDER BY artist ASC")
    fun getArtists(): LiveData<List<String>>

    @Query("SELECT * FROM media_items WHERE artist = :artist ORDER BY album ASC, title ASC")
    fun getSongsByArtist(artist: String): LiveData<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE artist = :artist ORDER BY album ASC, title ASC")
    suspend fun getSongsByArtistList(artist: String): List<MediaItem>

    // ── Search ─────────────────────────────────────────────────────
    @Query("""
        SELECT * FROM media_items 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun search(query: String): LiveData<List<MediaItem>>

    // ── Insert / Delete ────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItem)

    @Delete
    suspend fun delete(item: MediaItem)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    // ── By MediaStore ID (for incremental scan) ────────────────────
    @Query("SELECT mediaStoreId FROM media_items")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Query("DELETE FROM media_items WHERE mediaStoreId NOT IN (:activeIds)")
    suspend fun deleteRemovedItems(activeIds: List<Long>)

    // ── Count ──────────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getCount(): Int
}
