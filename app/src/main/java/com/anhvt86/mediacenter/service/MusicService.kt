package com.anhvt86.mediacenter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.repository.MediaRepository
import com.anhvt86.mediacenter.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * MediaBrowserService that exposes the media library as a browsable tree
 * and manages playback via PlaybackManager.
 *
 * - Allows system UI (media widget, instrument cluster) to browse the music library
 * - Hosts the MediaSession for playback state and transport controls
 * - Responds to hardware button events via MediaSession callbacks
 * - Integrates PlaybackManager for ExoPlayer-based playback
 * - Runs as a foreground service with a MediaStyle notification
 */
@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    companion object {
        private const val TAG = "MusicService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "media_playback"
    }

    @Inject lateinit var repository: MediaRepository
    @Inject lateinit var playbackManager: PlaybackManager

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService created")

        // Initialize PlaybackManager
        playbackManager.initialize()

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setPlaybackState(buildPlaybackState(PlaybackStateCompat.STATE_NONE, 0))
            setCallback(mediaSessionCallback)
            isActive = true
        }

        playbackManager.addPlaybackListener(playbackListener)

        // Set the session token so clients can connect
        sessionToken = mediaSession.sessionToken

        // Show foreground notification immediately so the OS won't kill us
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(null))

        // Trigger initial media scan (structured concurrency via serviceScope)
        serviceScope.launch(Dispatchers.IO) {
            repository.triggerScan()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Route hardware media button events (headset, steering wheel) to the session
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        playbackManager.release()
        mediaSession.release()
        Log.d(TAG, "MusicService destroyed")
    }

    // ── Foreground Notification ───────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW // Low = no sound, still visible
        ).apply {
            description = "Music playback controls"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(track: MediaItem?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            if (playbackManager.isPlaying) PlaybackStateCompat.ACTION_PAUSE
            else PlaybackStateCompat.ACTION_PLAY
        )
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        )

        val playPauseIcon = if (playbackManager.isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.artist ?: "")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this, PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
            .build()
    }

    private fun updateNotification(track: MediaItem?) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(track))
    }

    // ── Playback Listener → Updates MediaSession + Notification ──

    private val playbackListener = object : PlaybackManager.PlaybackListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                        else PlaybackStateCompat.STATE_PAUSED
            mediaSession.setPlaybackState(
                buildPlaybackState(state, playbackManager.getCurrentPosition())
            )
            updateNotification(playbackManager.getCurrentTrack())
        }

        override fun onTrackChanged(track: MediaItem?) {
            track?.let { updateMediaSessionMetadata(it) }
            updateNotification(track)
        }

        override fun onPositionChanged(position: Long, duration: Long) {
            // ExoPlayer just became ready — update metadata with the REAL clip duration.
            // track.duration from Deezer API is the full song length, not the 30s preview.
            // This corrects the seekbar max value as soon as the player knows the real duration.
            if (duration > 0) {
                val track = playbackManager.getCurrentTrack() ?: return
                updateMediaSessionMetadata(track, actualDuration = duration)
            }
        }

        override fun onQueueChanged(queue: List<MediaItem>, currentIndex: Int) {
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

        override fun onShuffleChanged(enabled: Boolean) {}
        override fun onRepeatModeChanged(mode: PlaybackManager.RepeatMode) {}
    }

    /**
     * @param actualDuration when provided (from ExoPlayer STATE_READY), overrides
     * the database-stored duration which for Deezer previews is the full song length.
     */
    private fun updateMediaSessionMetadata(track: MediaItem, actualDuration: Long = -1L) {
        val durationToSet = if (actualDuration > 0) actualDuration else -1L
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationToSet)
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

    // ── MediaBrowserService Overrides ─────────────────────────────

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
                BrowseTree.PLAYLISTS_ID -> buildPlaylistItems()
                else -> when {
                    parentId.startsWith(BrowseTree.ALBUM_PREFIX) ->
                        buildSongsForAlbum(BrowseTree.parseName(parentId))
                    parentId.startsWith(BrowseTree.ARTIST_PREFIX) ->
                        buildSongsForArtist(BrowseTree.parseName(parentId))
                    parentId.startsWith(BrowseTree.PLAYLIST_PREFIX) -> {
                        val playlistId = BrowseTree.parseName(parentId).toLongOrNull() ?: return@withContext emptyList()
                        buildSongsForPlaylist(playlistId)
                    }
                    else -> emptyList()
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
            MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
        }
    }

    private suspend fun buildAlbumItems(): List<MediaBrowserCompat.MediaItem> =
        repository.getAllSongsList().map { it.album }.distinct().sorted().map { album ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("${BrowseTree.ALBUM_PREFIX}$album").setTitle(album).build()
            MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
        }

    private suspend fun buildArtistItems(): List<MediaBrowserCompat.MediaItem> =
        repository.getAllSongsList().map { it.artist }.distinct().sorted().map { artist ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("${BrowseTree.ARTIST_PREFIX}$artist").setTitle(artist).build()
            MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
        }

    private suspend fun buildAllSongItems(): List<MediaBrowserCompat.MediaItem> =
        repository.getAllSongsList().map { it.toBrowserMediaItem() }

    private suspend fun buildPlaylistItems(): List<MediaBrowserCompat.MediaItem> =
        repository.getAllPlaylistsList().map { playlist ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("${BrowseTree.PLAYLIST_PREFIX}${playlist.id}")
                .setTitle(playlist.name)
                .build()
            MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
        }

    private suspend fun buildSongsForAlbum(album: String): List<MediaBrowserCompat.MediaItem> =
        repository.getSongsByAlbumList(album).map { it.toBrowserMediaItem() }

    private suspend fun buildSongsForArtist(artist: String): List<MediaBrowserCompat.MediaItem> =
        repository.getSongsByArtistList(artist).map { it.toBrowserMediaItem() }

    private suspend fun buildSongsForPlaylist(playlistId: Long): List<MediaBrowserCompat.MediaItem> =
        repository.getTracksInPlaylistList(playlistId).map { it.toBrowserMediaItem() }

    private fun MediaItem.toBrowserMediaItem(): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId("${BrowseTree.TRACK_PREFIX}$id")
            .setTitle(title).setSubtitle(artist).setDescription(album)
            .setIconUri(albumArtUri?.let { Uri.parse(it) })
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    // ── Media Session Callback ────────────────────────────────────

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { playbackManager.play() }
        override fun onPause() { playbackManager.pause() }
        override fun onStop() { playbackManager.stop(); stopSelf() }
        override fun onSkipToNext() { playbackManager.skipToNext() }
        override fun onSkipToPrevious() { playbackManager.skipToPrevious() }
        override fun onSeekTo(pos: Long) { playbackManager.seekTo(pos) }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: $mediaId")
            mediaId?.let { id ->
                val trackId = id.removePrefix(BrowseTree.TRACK_PREFIX).toLongOrNull() ?: return
                serviceScope.launch {
                    val allSongs = withContext(Dispatchers.IO) { repository.getAllSongsList() }
                    val index = allSongs.indexOfFirst { it.id == trackId }
                    if (index >= 0) playbackManager.setQueueAndPlay(allSongs, index)
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
