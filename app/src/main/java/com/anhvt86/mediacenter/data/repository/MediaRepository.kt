package com.anhvt86.mediacenter.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.anhvt86.mediacenter.data.local.MediaDatabase
import com.anhvt86.mediacenter.data.local.dao.MediaDao
import com.anhvt86.mediacenter.data.local.dao.PlaylistDao
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.local.entity.Playlist
import com.anhvt86.mediacenter.data.local.entity.PlaylistTrackCrossRef
import com.anhvt86.mediacenter.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for media data.
 * Coordinates between Room database (via DAOs) and the Deezer API.
 * Provides LiveData streams for UI observation and suspend functions for one-shot queries.
 */
class MediaRepository(context: Context) {

    private val database = MediaDatabase.getInstance(context)
    private val mediaDao: MediaDao = database.mediaDao()
    private val playlistDao: PlaylistDao = database.playlistDao()

    // ── Scan State ─────────────────────────────────────────────────
    sealed class ScanState {
        object Idle : ScanState()
        object Loading : ScanState()
        data class Success(val count: Int) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // ── All Songs ──────────────────────────────────────────────────
    fun getAllSongs(): LiveData<List<MediaItem>> = mediaDao.getAllSongs()
    suspend fun getAllSongsList(): List<MediaItem> = mediaDao.getAllSongsList()

    // ── Albums ─────────────────────────────────────────────────────
    fun getAlbums(): LiveData<List<String>> = mediaDao.getAlbums()
    fun getSongsByAlbum(album: String): LiveData<List<MediaItem>> = mediaDao.getSongsByAlbum(album)
    suspend fun getSongsByAlbumList(album: String): List<MediaItem> = mediaDao.getSongsByAlbumList(album)

    // ── Artists ────────────────────────────────────────────────────
    fun getArtists(): LiveData<List<String>> = mediaDao.getArtists()
    fun getSongsByArtist(artist: String): LiveData<List<MediaItem>> = mediaDao.getSongsByArtist(artist)
    suspend fun getSongsByArtistList(artist: String): List<MediaItem> = mediaDao.getSongsByArtistList(artist)

    // ── Search ─────────────────────────────────────────────────────
    fun search(query: String): LiveData<List<MediaItem>> = mediaDao.search(query)

    // ── Playlists ──────────────────────────────────────────────────
    fun getAllPlaylists(): LiveData<List<Playlist>> = playlistDao.getAllPlaylists()
    suspend fun getAllPlaylistsList(): List<Playlist> = playlistDao.getAllPlaylistsList()
    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(Playlist(name = name))
    suspend fun deletePlaylist(playlist: Playlist) = playlistDao.deletePlaylist(playlist)
    suspend fun addTrackToPlaylist(playlistId: Long, mediaItemId: Long, order: Int) =
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, mediaItemId, order))
    suspend fun removeTrackFromPlaylist(playlistId: Long, mediaItemId: Long) =
        playlistDao.removeTrackFromPlaylist(PlaylistTrackCrossRef(playlistId, mediaItemId))
    fun getTracksInPlaylist(playlistId: Long): LiveData<List<MediaItem>> =
        playlistDao.getTracksInPlaylist(playlistId)
    suspend fun getTracksInPlaylistList(playlistId: Long): List<MediaItem> =
        playlistDao.getTracksInPlaylistList(playlistId)

    // ── Media Fetching (API) ───────────────────────────────────────

    /**
     * Fetch music from Deezer API instead of scanning local storage.
     * This is now a suspend function — callers must invoke it from a coroutine scope
     * they own (e.g. serviceScope, viewModelScope), ensuring structured concurrency.
     */
    suspend fun triggerScan() {
        _scanState.value = ScanState.Loading
        try {
            Log.d("MediaRepository", "Fetching music from Deezer API...")
            val response = RetrofitClient.api.getChartTracks(limit = 40)
            val tracks = response.data

            // Fetch existing tracks from the database to prevent duplicates
            val existingTracks = mediaDao.getAllSongsList().associateBy { it.mediaStoreId }

            val mediaItems = tracks.map { track ->
                val existing = existingTracks[track.id]
                if (existing != null) {
                    // Reuse the primary key to update the existing track instead of creating a new duplicate
                    existing.copy(
                        title = track.title,
                        artist = track.artist.name,
                        album = track.album.title,
                        duration = track.durationSeconds * 1000,
                        albumArtUri = track.album.coverXl ?: track.artist.pictureMedium,
                        filePath = track.previewUrl
                    )
                } else {
                    MediaItem(
                        title = track.title,
                        artist = track.artist.name,
                        album = track.album.title,
                        duration = track.durationSeconds * 1000,
                        albumArtUri = track.album.coverXl ?: track.artist.pictureMedium,
                        filePath = track.previewUrl,
                        dateAdded = System.currentTimeMillis(),
                        mediaStoreId = track.id
                    )
                }
            }

            if (mediaItems.isNotEmpty()) {
                mediaDao.insertAll(mediaItems)
                Log.d("MediaRepository", "Saved ${mediaItems.size} tracks to database")
            }
            _scanState.value = ScanState.Success(mediaItems.size)
        } catch (e: Exception) {
            Log.e("MediaRepository", "Failed to fetch music from API", e)
            _scanState.value = ScanState.Error(e.message ?: "Unknown error")
        }
    }

    // ── Count ──────────────────────────────────────────────────────
    suspend fun getTrackCount(): Int = mediaDao.getCount()
}
