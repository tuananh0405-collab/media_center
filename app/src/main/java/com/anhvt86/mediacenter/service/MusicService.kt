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
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.repository.MediaRepository
import kotlinx.coroutines.*

/**
 * MediaBrowserService that exposes the media library as a browsable tree
 * and manages playback via PlaybackManager.
 *
 * This service:
 * - Allows system UI (media widget, instrument cluster) to browse the music library
 * - Hosts the MediaSession for playback state and transport controls
 * - Responds to hardware button events via MediaSession callbacks
 * - Integrates PlaybackManager for ExoPlayer-based playback
 */
class MusicService : MediaBrowserServiceCompat() {

    companion object {
        private const val TAG = "MusicService"

        // Singleton reference for UI to access playback state
        @Volatile
        var instance: MusicService? = null
            private set
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var repository: MediaRepository
    lateinit var playbackManager: PlaybackManager
        private set
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService created")
        instance = this

        repository = MediaRepository(applicationContext)

        // Initialize PlaybackManager
        playbackManager = PlaybackManager(applicationContext)
        playbackManager.initialize()
        playbackManager.listener = playbackListener

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_NONE, 0))
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
        instance = null
        serviceScope.cancel()
        playbackManager.release()
        mediaSession.release()
        Log.d(TAG, "MusicService destroyed")
    }

    // ── Playback Listener → Updates MediaSession ──────────────────

    private val playbackListener = object : PlaybackManager.PlaybackListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                        else PlaybackStateCompat.STATE_PAUSED
            mediaSession.setPlaybackState(
                buildPlaybackState(state, playbackManager.getCurrentPosition())
            )
        }

        override fun onTrackChanged(track: MediaItem?) {
            track?.let { updateMediaSessionMetadata(it) }
        }

        override fun onPositionChanged(position: Long, duration: Long) {
            // Position updates are handled via polling in the UI
        }

        override fun onQueueChanged(queue: List<MediaItem>, currentIndex: Int) {
            // Update media session queue
            val queueItems = queue.mapIndexed { index, item ->
                MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(item.id.toString())
                        .setTitle(item.title)
                        .setSubtitle(item.artist)
                        .setIconUri(item.albumArtUri?.let { Uri.parse(it) })
                        .build(),
                    index.toLong()
                )
            }
            mediaSession.setQueue(queueItems)
        }

        override fun onShuffleChanged(enabled: Boolean) {
            // Reflected in UI via PlaybackManager state
        }

        override fun onRepeatModeChanged(mode: PlaybackManager.RepeatMode) {
            // Reflected in UI via PlaybackManager state
        }
    }

    private fun updateMediaSessionMetadata(track: MediaItem) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
            .apply {
                track.albumArtUri?.let {
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it)
                }
            }
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun buildPlaybackState(state: Int, position: Long): PlaybackStateCompat {
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            .setState(state, position, 1f)
            .build()
    }

    // ── MediaBrowserService Overrides ──────────────────────────────

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot: client=$clientPackageName")
        return BrowserRoot(BrowseTree.ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren: parentId=$parentId")
        result.detach()

        serviceScope.launch {
            val children = loadChildren(parentId)
            result.sendResult(children.toMutableList())
        }
    }

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
        return db.mediaDao().getAllSongsList().map { it.toBrowserMediaItem() }
    }

    private suspend fun buildSongsForAlbum(album: String): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        return db.mediaDao().getSongsByAlbumList(album).map { it.toBrowserMediaItem() }
    }

    private suspend fun buildSongsForArtist(artist: String): List<MediaBrowserCompat.MediaItem> {
        val db = MediaDatabase.getInstance(applicationContext)
        return db.mediaDao().getSongsByArtistList(artist).map { it.toBrowserMediaItem() }
    }

    private fun MediaItem.toBrowserMediaItem(): MediaBrowserCompat.MediaItem {
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

    // ── Media Session Callback ────────────────────────────────────

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "onPlay")
            playbackManager.play()
        }

        override fun onPause() {
            Log.d(TAG, "onPause")
            playbackManager.pause()
        }

        override fun onStop() {
            Log.d(TAG, "onStop")
            playbackManager.stop()
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext")
            playbackManager.skipToNext()
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious")
            playbackManager.skipToPrevious()
        }

        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "onSeekTo: $pos")
            playbackManager.seekTo(pos)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: $mediaId")
            mediaId?.let { id ->
                val trackId = id.removePrefix(BrowseTree.TRACK_PREFIX).toLongOrNull() ?: return
                serviceScope.launch {
                    val allSongs = withContext(Dispatchers.IO) {
                        MediaDatabase.getInstance(applicationContext).mediaDao().getAllSongsList()
                    }
                    val index = allSongs.indexOfFirst { it.id == trackId }
                    if (index >= 0) {
                        playbackManager.setQueueAndPlay(allSongs, index)
                    }
                }
            }
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                "TOGGLE_SHUFFLE" -> playbackManager.toggleShuffle()
                "CYCLE_REPEAT" -> playbackManager.cycleRepeatMode()
            }
        }
    }
}
