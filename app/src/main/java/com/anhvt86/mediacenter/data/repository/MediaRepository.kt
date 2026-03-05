package com.anhvt86.mediacenter.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import com.anhvt86.mediacenter.data.local.MediaDatabase
import com.anhvt86.mediacenter.data.local.dao.MediaDao
import com.anhvt86.mediacenter.data.local.dao.PlaylistDao
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.local.entity.Playlist
import com.anhvt86.mediacenter.data.local.entity.PlaylistTrackCrossRef
import com.anhvt86.mediacenter.data.scanner.ScanWorker
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for media data.
 * Coordinates between Room database (via DAOs) and the background media scanner.
 * Provides LiveData streams for UI observation and suspend functions for one-shot queries.
 */
class MediaRepository(context: Context) {

    private val database = MediaDatabase.getInstance(context)
    private val mediaDao: MediaDao = database.mediaDao()
    private val playlistDao: PlaylistDao = database.playlistDao()
    private val workManager = WorkManager.getInstance(context)

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
    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(Playlist(name = name))
    suspend fun deletePlaylist(playlist: Playlist) = playlistDao.deletePlaylist(playlist)
    suspend fun addTrackToPlaylist(playlistId: Long, mediaItemId: Long, order: Int) =
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, mediaItemId, order))
    suspend fun removeTrackFromPlaylist(playlistId: Long, mediaItemId: Long) =
        playlistDao.removeTrackFromPlaylist(PlaylistTrackCrossRef(playlistId, mediaItemId))
    fun getTracksInPlaylist(playlistId: Long): LiveData<List<MediaItem>> =
        playlistDao.getTracksInPlaylist(playlistId)

    // ── Media Scanning ─────────────────────────────────────────────

    /**
     * Trigger a one-time media scan immediately.
     */
    fun triggerScan() {
        val scanRequest = OneTimeWorkRequestBuilder<ScanWorker>()
            .addTag(ScanWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            ScanWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            scanRequest
        )
    }

    /**
     * Schedule periodic media scanning (every 6 hours).
     */
    fun schedulePeriodicScan() {
        val periodicRequest = PeriodicWorkRequestBuilder<ScanWorker>(
            6, TimeUnit.HOURS
        )
            .addTag(ScanWorker.TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            "${ScanWorker.WORK_NAME}_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    // ── Count ──────────────────────────────────────────────────────
    suspend fun getTrackCount(): Int = mediaDao.getCount()
}
