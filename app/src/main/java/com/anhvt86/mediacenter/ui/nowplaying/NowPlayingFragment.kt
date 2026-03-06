package com.anhvt86.mediacenter.ui.nowplaying

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.anhvt86.mediacenter.databinding.FragmentNowPlayingBinding
import com.anhvt86.mediacenter.ui.MediaBrowserViewModel
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint

/**
 * Now Playing fragment displaying the currently playing track with
 * large album art, track info, seek bar, and playback controls.
 * Matches the automotive-optimized landscape design.
 *
 * Commands go through MediaControllerCompat.transportControls.
 * UI state driven by MediaBrowserViewModel (metadata + playbackState LiveData).
 */
@AndroidEntryPoint
class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val mediaBrowserVm: MediaBrowserViewModel by activityViewModels()

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
        observePlaybackState()
    }

    override fun onResume() {
        super.onResume()
        handler.post(positionUpdater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(positionUpdater)
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayPause.setOnClickListener {
            val ctrl = mediaBrowserVm.mediaController.value ?: return@setOnClickListener
            val state = mediaBrowserVm.playbackState.value?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) ctrl.transportControls.pause()
            else ctrl.transportControls.play()
        }

        binding.btnNext.setOnClickListener {
            mediaBrowserVm.mediaController.value?.transportControls?.skipToNext()
        }

        binding.btnPrev.setOnClickListener {
            mediaBrowserVm.mediaController.value?.transportControls?.skipToPrevious()
        }

        binding.btnShuffle.setOnClickListener {
            mediaBrowserVm.mediaController.value?.transportControls
                ?.sendCustomAction("TOGGLE_SHUFFLE", null)
        }

        binding.btnRepeat.setOnClickListener {
            mediaBrowserVm.mediaController.value?.transportControls
                ?.sendCustomAction("CYCLE_REPEAT", null)
        }
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaBrowserVm.nowPlaying.value
                        ?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                    val position = (progress.toLong() * duration) / 1000
                    binding.textPosition.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val duration = mediaBrowserVm.nowPlaying.value
                    ?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                val progress = seekBar?.progress ?: return
                val position = (progress.toLong() * duration) / 1000
                mediaBrowserVm.mediaController.value?.transportControls?.seekTo(position)
            }
        })
    }

    private fun observePlaybackState() {
        // Track metadata → update title, artist, album art
        mediaBrowserVm.nowPlaying.observe(viewLifecycleOwner) { metadata ->
            updateTrackInfo(metadata)
        }

        // Playback state → update play/pause button
        mediaBrowserVm.playbackState.observe(viewLifecycleOwner) { state ->
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            updatePlayPauseButton(isPlaying)
        }
    }

    private fun updateTrackInfo(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            binding.textTrackTitle.text = "No track selected"
            binding.textTrackArtistAlbum.text = ""
            return
        }

        binding.textTrackTitle.text = metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE)
        binding.textTrackArtistAlbum.text = buildString {
            append(metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "")
            append(" — ")
            append(metadata.getText(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "")
        }

        val artUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
        if (artUri != null) {
            Glide.with(this)
                .load(Uri.parse(artUri))
                .centerCrop()
                .into(binding.imageAlbumArt)
        }
    }

    private fun updatePosition() {
        if (isUserSeeking) return
        val ctrl = mediaBrowserVm.mediaController.value ?: return
        val position = ctrl.playbackState?.position ?: 0L
        val duration = mediaBrowserVm.nowPlaying.value
            ?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

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

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:%02d".format(seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
