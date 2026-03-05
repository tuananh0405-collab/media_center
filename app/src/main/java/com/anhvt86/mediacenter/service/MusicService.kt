package com.anhvt86.mediacenter.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import com.anhvt86.mediacenter.data.local.MediaDatabase
import com.anhvt86.mediacenter.data.repository.MediaRepository
import kotlinx.coroutines.*

/**
 * MediaBrowserService that exposes the media library as a browsable tree.
 *
 * This service:
 * - Allows system UI (media widget, instrument cluster) to browse the music library
 * - Hosts the MediaSession for playback state and transport controls
 * - Responds to hardware button events via MediaSession callbacks
 *
 * The browse tree follows:
 *   ROOT → Albums / Artists / All Songs / Playlists → individual tracks
 */
class MusicService : MediaBrowserServiceCompat() {

    companion object {
        private const val TAG = "MusicService"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var repository: MediaRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService created")

        repository = MediaRepository(applicationContext)

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, TAG).apply {
            // Set initial playback state
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    )
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1f)
                    .build()
            )

            // Set callbacks for transport controls
            setCallback(mediaSessionCallback)
            isActive = true
        }

        // Set the session token so clients can connect
        sessionToken = mediaSession.sessionToken

        // Trigger initial media scan
        repository.triggerScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
        Log.d(TAG, "MusicService destroyed")
    }

    // ── MediaBrowserService Overrides ──────────────────────────────

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot: client=$clientPackageName")
        // Allow all clients to browse (for Phase 1; tighten in Phase 2)
        return BrowserRoot(BrowseTree.ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren: parentId=$parentId")

        // Detach result so we can load asynchronously
        result.detach()

        serviceScope.launch {
            val children = loadChildren(parentId)
            result.sendResult(children.toMutableList())
        }
    }

    /**
     * Loads children for a given parent media ID.
     */
    private suspend fun loadChildren(parentId: String): List<MediaBrowserCompat.MediaItem> {
        return withContext(Dispatchers.IO) {
            when (parentId) {
                BrowseTree.ROOT_ID -> buildRootItems()
                BrowseTree.ALBUMS_ID -> buildAlbumItems()
                BrowseTree.ARTISTS_ID -> buildArtistItems()
                BrowseTree.ALL_SONGS_ID -> buildAllSongItems()
                else -> {
                    when {
                        parentId.startsWith(BrowseTree.ALBUM_PREFIX) ->
                            buildSongsForAlbum(BrowseTree.parseName(parentId))
                        parentId.startsWith(BrowseTree.ARTIST_PREFIX) ->
                            buildSongsForArtist(BrowseTree.parseName(parentId))
                        else -> emptyList()
                    }
                }
            }
        }
    }

    // ── Tree Builders ─────────────────────────────────────────────

    private fun buildRootItems(): List<MediaBrowserCompat.MediaItem> {
        return BrowseTree.getRootCategories().map { categoryId ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(categoryId)
                .setTitle(BrowseTree.getCategoryTitle(categoryId))
                .build()
            MediaBrowserCompat.MediaItem(
                description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        }
    }

    private suspend fun buildAlbumItems(): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        val albums = db.mediaDao().getAllSongsList()
            .map { it.album }
            .distinct()
            .sorted()

        return albums.map { album ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("${BrowseTree.ALBUM_PREFIX}$album")
                .setTitle(album)
                .build()
            MediaBrowserCompat.MediaItem(
                description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        }
    }

    private suspend fun buildArtistItems(): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        val artists = db.mediaDao().getAllSongsList()
            .map { it.artist }
            .distinct()
            .sorted()

        return artists.map { artist ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("${BrowseTree.ARTIST_PREFIX}$artist")
                .setTitle(artist)
                .build()
            MediaBrowserCompat.MediaItem(
                description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        }
    }

    private suspend fun buildAllSongItems(): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        return db.mediaDao().getAllSongsList().map { it.toMediaItem() }
    }

    private suspend fun buildSongsForAlbum(album: String): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        return db.mediaDao().getSongsByAlbumList(album).map { it.toMediaItem() }
    }

    private suspend fun buildSongsForArtist(artist: String): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        return db.mediaDao().getSongsByArtistList(artist).map { it.toMediaItem() }
    }

    // ── Helpers ────────────────────────────────────────────────────

    /**
     * Convert a database MediaItem to a MediaBrowserCompat.MediaItem (playable).
     */
    private fun com.anhvt86.mediacenter.data.local.entity.MediaItem.toMediaItem():
            MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId("${BrowseTree.TRACK_PREFIX}$id")
            .setTitle(title)
            .setSubtitle(artist)
            .setDescription(album)
            .setIconUri(albumArtUri?.let { Uri.parse(it) })
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    // ── Media Session Callback (placeholder for Day 2) ────────────

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "onPlay")
            // TODO: Implement in Step 4 (Playback Engine)
        }

        override fun onPause() {
            Log.d(TAG, "onPause")
        }

        override fun onStop() {
            Log.d(TAG, "onStop")
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext")
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious")
        }

        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "onSeekTo: $pos")
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: $mediaId")
        }
    }
}
