package com.anhvt86.mediacenter.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.anhvt86.mediacenter.MediaCenterApp
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.databinding.ActivityMainBinding
import com.anhvt86.mediacenter.service.MusicService
import com.anhvt86.mediacenter.service.PlaybackManager
import com.anhvt86.mediacenter.ui.browse.BrowseFragment
import com.anhvt86.mediacenter.ui.nowplaying.NowPlayingFragment
import com.bumptech.glide.Glide

/**
 * Main entry point for the AAOS Multimedia Center.
 * Hosts fragment navigation and the persistent mini-player bar at the bottom.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start MusicService (which triggers the API fetch internally)
        startService(Intent(this, MusicService::class.java))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BrowseFragment())
                .commit()
        }

        setupMiniPlayer()
    }

    override fun onResume() {
        super.onResume()
        // Register for playback state updates to update mini-player
        MusicService.instance?.playbackManager?.addPlaybackListener(miniPlayerListener)
        updateMiniPlayer()
    }

    override fun onPause() {
        super.onPause()
        MusicService.instance?.playbackManager?.removePlaybackListener(miniPlayerListener)
    }

    private fun setupMiniPlayer() {
        binding.miniPlayerPlayPause.setOnClickListener {
            val pm = MusicService.instance?.playbackManager ?: return@setOnClickListener
            if (pm.isPlaying) pm.pause() else pm.play()
        }

        binding.miniPlayerPrev.setOnClickListener {
            MusicService.instance?.playbackManager?.skipToPrevious()
        }

        binding.miniPlayerNext.setOnClickListener {
            MusicService.instance?.playbackManager?.skipToNext()
        }

        // Tap on mini-player (not on controls) opens Now Playing
        binding.miniPlayer.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NowPlayingFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun updateMiniPlayer() {
        val pm = MusicService.instance?.playbackManager
        val track = pm?.getCurrentTrack()

        if (track != null) {
            binding.miniPlayer.visibility = View.VISIBLE
            binding.miniPlayerTitle.text = track.title
            binding.miniPlayerArtist.text = track.artist

            track.albumArtUri?.let { uri ->
                Glide.with(this)
                    .load(Uri.parse(uri))
                    .centerCrop()
                    .into(binding.miniPlayerArt)
            }

            updateMiniPlayerPlayButton(pm.isPlaying)
        } else {
            binding.miniPlayer.visibility = View.GONE
        }
    }

    private fun updateMiniPlayerPlayButton(isPlaying: Boolean) {
        binding.miniPlayerPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    // ── Mini Player Listener ──────────────────────────────────────

    private val miniPlayerListener = object : PlaybackManager.PlaybackListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            runOnUiThread { updateMiniPlayerPlayButton(isPlaying) }
        }

        override fun onTrackChanged(track: MediaItem?) {
            runOnUiThread { updateMiniPlayer() }
        }

        override fun onPositionChanged(position: Long, duration: Long) {}
        override fun onQueueChanged(queue: List<MediaItem>, currentIndex: Int) {}
        override fun onShuffleChanged(enabled: Boolean) {}
        override fun onRepeatModeChanged(mode: PlaybackManager.RepeatMode) {}
    }
}
