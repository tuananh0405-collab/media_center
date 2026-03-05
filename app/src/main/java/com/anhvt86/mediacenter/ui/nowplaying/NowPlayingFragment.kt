package com.anhvt86.mediacenter.ui.nowplaying

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.databinding.FragmentNowPlayingBinding
import com.anhvt86.mediacenter.service.MusicService
import com.anhvt86.mediacenter.service.PlaybackManager
import com.bumptech.glide.Glide

/**
 * Now Playing fragment displaying the currently playing track with
 * large album art, track info, seek bar, and playback controls.
 * Matches the automotive-optimized landscape design.
 */
class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    // Polls playback position every 500ms
    private val positionUpdater = object : Runnable {
        override fun run() {
            updatePosition()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupControls()
        setupSeekBar()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        handler.post(positionUpdater)
        // Register listener for state changes
        MusicService.instance?.playbackManager?.addPlaybackListener(playbackListener)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(positionUpdater)
        MusicService.instance?.playbackManager?.removePlaybackListener(playbackListener)
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayPause.setOnClickListener {
            val pm = MusicService.instance?.playbackManager ?: return@setOnClickListener
            if (pm.isPlaying) pm.pause() else pm.play()
        }

        binding.btnNext.setOnClickListener {
            MusicService.instance?.playbackManager?.skipToNext()
        }

        binding.btnPrev.setOnClickListener {
            MusicService.instance?.playbackManager?.skipToPrevious()
        }

        binding.btnShuffle.setOnClickListener {
            MusicService.instance?.playbackManager?.toggleShuffle()
        }

        binding.btnRepeat.setOnClickListener {
            MusicService.instance?.playbackManager?.cycleRepeatMode()
        }
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pm = MusicService.instance?.playbackManager ?: return
                    val position = (progress.toLong() * pm.getDuration()) / 1000
                    binding.textPosition.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val pm = MusicService.instance?.playbackManager ?: return
                val progress = seekBar?.progress ?: return
                val position = (progress.toLong() * pm.getDuration()) / 1000
                pm.seekTo(position)
            }
        })
    }

    private fun updateUI() {
        val pm = MusicService.instance?.playbackManager ?: return
        val track = pm.getCurrentTrack()

        updateTrackInfo(track)
        updatePlayPauseButton(pm.isPlaying)
        updateShuffleButton(pm.shuffleEnabled)
        updateRepeatButton(pm.repeatMode)
    }

    private fun updateTrackInfo(track: MediaItem?) {
        if (track == null) {
            binding.textTrackTitle.text = "No track selected"
            binding.textTrackArtistAlbum.text = ""
            return
        }

        binding.textTrackTitle.text = track.title
        binding.textTrackArtistAlbum.text = "${track.artist} — ${track.album}"

        track.albumArtUri?.let { uri ->
            Glide.with(this)
                .load(Uri.parse(uri))
                .centerCrop()
                .into(binding.imageAlbumArt)
        }
    }

    private fun updatePosition() {
        if (isUserSeeking) return
        val pm = MusicService.instance?.playbackManager ?: return

        val position = pm.getCurrentPosition()
        val duration = pm.getDuration()

        binding.textPosition.text = formatTime(position)
        binding.textDuration.text = formatTime(duration)

        if (duration > 0) {
            binding.seekBar.progress = ((position * 1000) / duration).toInt()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateShuffleButton(enabled: Boolean) {
        binding.btnShuffle.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun updateRepeatButton(mode: PlaybackManager.RepeatMode) {
        binding.btnRepeat.alpha = when (mode) {
            PlaybackManager.RepeatMode.OFF -> 0.5f
            PlaybackManager.RepeatMode.ONE -> 1.0f
            PlaybackManager.RepeatMode.ALL -> 1.0f
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:%02d".format(seconds)
    }

    // ── Playback Listener ─────────────────────────────────────────

    private val playbackListener = object : PlaybackManager.PlaybackListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            activity?.runOnUiThread { updatePlayPauseButton(isPlaying) }
        }

        override fun onTrackChanged(track: MediaItem?) {
            activity?.runOnUiThread { updateTrackInfo(track) }
        }

        override fun onPositionChanged(position: Long, duration: Long) {
            // Handled by polling
        }

        override fun onQueueChanged(queue: List<MediaItem>, currentIndex: Int) {}

        override fun onShuffleChanged(enabled: Boolean) {
            activity?.runOnUiThread { updateShuffleButton(enabled) }
        }

        override fun onRepeatModeChanged(mode: PlaybackManager.RepeatMode) {
            activity?.runOnUiThread { updateRepeatButton(mode) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
