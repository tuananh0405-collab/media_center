package com.anhvt86.mediacenter.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.anhvt86.mediacenter.data.local.entity.MediaItem

/**
 * Manages audio playback using ExoPlayer.
 * Handles:
 * - ExoPlayer lifecycle
 * - Audio focus management (duck, pause, resume)
 * - Play queue (shuffle, repeat modes)
 * - Playback state broadcasting
 */
class PlaybackManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackManager"
    }

    // ── ExoPlayer ─────────────────────────────────────────────────
    private var exoPlayer: ExoPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── Queue ─────────────────────────────────────────────────────
    private val _queue = mutableListOf<MediaItem>()
    val queue: List<MediaItem> get() = _queue
    private var currentIndex: Int = -1

    // ── State ─────────────────────────────────────────────────────
    var isPlaying: Boolean = false
        private set
    var shuffleEnabled: Boolean = false
        private set
    var repeatMode: RepeatMode = RepeatMode.OFF
        private set

    enum class RepeatMode { OFF, ONE, ALL }

    // ── Listener ──────────────────────────────────────────────────
    var listener: PlaybackListener? = null

    interface PlaybackListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onTrackChanged(track: MediaItem?)
        fun onPositionChanged(position: Long, duration: Long)
        fun onQueueChanged(queue: List<MediaItem>, currentIndex: Int)
        fun onShuffleChanged(enabled: Boolean)
        fun onRepeatModeChanged(mode: RepeatMode)
    }

    // ── Audio Focus ───────────────────────────────────────────────
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playOnFocusGain = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                exoPlayer?.volume = 1.0f
                if (playOnFocusGain) {
                    exoPlayer?.play()
                    isPlaying = true
                    listener?.onPlaybackStateChanged(true)
                    playOnFocusGain = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                playOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently")
                if (isPlaying) {
                    playOnFocusGain = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus ducking")
                exoPlayer?.volume = 0.3f
            }
        }
    }

    // ── Initialization ────────────────────────────────────────────

    fun initialize() {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)

            // Set repeat mode based on current setting
            this.repeatMode = when (this@PlaybackManager.repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
            this.shuffleModeEnabled = shuffleEnabled
        }
        Log.d(TAG, "ExoPlayer initialized")
    }

    fun release() {
        abandonAudioFocus()
        exoPlayer?.release()
        exoPlayer = null
        Log.d(TAG, "ExoPlayer released")
    }

    // ── Player Listener ───────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            listener?.onPlaybackStateChanged(playing)

            // Update diagnostics stats
            if (playing) {
                DiagnosticsService.Stats.totalTracksPlayed++
                getCurrentTrack()?.let {
                    DiagnosticsService.Stats.currentTrackInfo = "${it.title} - ${it.artist}"
                }
            }
        }

        override fun onMediaItemTransition(
            mediaItem: ExoMediaItem?,
            reason: Int
        ) {
            // Update current index based on ExoPlayer's current media item index
            val player = exoPlayer ?: return
            currentIndex = player.currentMediaItemIndex
            listener?.onTrackChanged(getCurrentTrack())
            listener?.onQueueChanged(_queue, currentIndex)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback ended")
                    isPlaying = false
                    listener?.onPlaybackStateChanged(false)
                }
                Player.STATE_READY -> {
                    val player = exoPlayer ?: return
                    listener?.onPositionChanged(player.currentPosition, player.duration)
                }
            }
        }
    }

    // ── Queue Management ──────────────────────────────────────────

    /**
     * Set the play queue and start playing from the given index.
     */
    fun setQueueAndPlay(tracks: List<MediaItem>, startIndex: Int = 0) {
        _queue.clear()
        _queue.addAll(tracks)
        currentIndex = startIndex

        val player = exoPlayer ?: return

        // Build ExoPlayer media items from queue
        val exoItems = tracks.map { track ->
            ExoMediaItem.Builder()
                .setUri(Uri.parse(track.filePath))
                .setMediaId(track.id.toString())
                .build()
        }

        player.setMediaItems(exoItems, startIndex, 0)
        player.prepare()

        if (requestAudioFocus()) {
            player.play()
            isPlaying = true
            listener?.onPlaybackStateChanged(true)
        }

        listener?.onQueueChanged(_queue, currentIndex)
        listener?.onTrackChanged(getCurrentTrack())
    }

    /**
     * Add a track to the end of the queue.
     */
    fun addToQueue(track: MediaItem) {
        _queue.add(track)
        val exoItem = ExoMediaItem.Builder()
            .setUri(Uri.parse(track.filePath))
            .setMediaId(track.id.toString())
            .build()
        exoPlayer?.addMediaItem(exoItem)
        listener?.onQueueChanged(_queue, currentIndex)
    }

    // ── Transport Controls ────────────────────────────────────────

    fun play() {
        val player = exoPlayer ?: return
        if (requestAudioFocus()) {
            player.play()
            isPlaying = true
            listener?.onPlaybackStateChanged(true)
        }
    }

    fun pause() {
        exoPlayer?.pause()
        isPlaying = false
        listener?.onPlaybackStateChanged(false)
    }

    fun stop() {
        exoPlayer?.stop()
        isPlaying = false
        abandonAudioFocus()
        listener?.onPlaybackStateChanged(false)
    }

    fun skipToNext() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            DiagnosticsService.Stats.totalSkips++
        }
    }

    fun skipToPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000) {
            // If >3s into the track, restart it
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun playAtIndex(index: Int) {
        val player = exoPlayer ?: return
        if (index in _queue.indices) {
            currentIndex = index
            player.seekTo(index, 0)
            play()
        }
    }

    // ── Shuffle & Repeat ──────────────────────────────────────────

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        exoPlayer?.shuffleModeEnabled = shuffleEnabled
        listener?.onShuffleChanged(shuffleEnabled)
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        exoPlayer?.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        listener?.onRepeatModeChanged(repeatMode)
    }

    // ── Getters ───────────────────────────────────────────────────

    fun getCurrentTrack(): MediaItem? {
        return if (currentIndex in _queue.indices) _queue[currentIndex] else null
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration ?: 0L

    // ── Audio Focus Helpers ───────────────────────────────────────

    private fun requestAudioFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setWillPauseWhenDucked(false)
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
    }
}
