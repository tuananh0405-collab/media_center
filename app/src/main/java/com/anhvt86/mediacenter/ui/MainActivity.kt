package com.anhvt86.mediacenter.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.databinding.ActivityMainBinding
import com.anhvt86.mediacenter.service.MusicService
import com.anhvt86.mediacenter.ui.browse.BrowseFragment
import com.anhvt86.mediacenter.ui.nowplaying.NowPlayingFragment
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for the AAOS Multimedia Center.
 * Hosts fragment navigation and the persistent mini-player bar at the bottom.
 *
 * Connects to MusicService via MediaBrowserViewModel (no more static singleton).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    // Shared ViewModel that holds the MediaBrowserCompat / MediaControllerCompat connection
    private val mediaBrowserVm: MediaBrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start MusicService so it runs even if no client is bound
        startService(Intent(this, MusicService::class.java))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BrowseFragment())
                .commit()
        }

        setupMiniPlayer()
        observePlaybackState()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayerPlayPause.setOnClickListener {
            val controller = mediaBrowserVm.mediaController.value ?: return@setOnClickListener
            val state = mediaBrowserVm.playbackState.value?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }

        binding.miniPlayerPrev.setOnClickListener {
            mediaBrowserVm.mediaController.value?.transportControls?.skipToPrevious()
        }

        binding.miniPlayerNext.setOnClickListener {
            mediaBrowserVm.mediaController.value?.transportControls?.skipToNext()
        }

        binding.miniPlayer.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NowPlayingFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observePlaybackState() {
        // Update mini-player when track metadata changes
        mediaBrowserVm.nowPlaying.observe(this) { metadata ->
            if (metadata != null) {
                binding.miniPlayer.visibility = View.VISIBLE
                binding.miniPlayerTitle.text =
                    metadata.getText(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE)
                binding.miniPlayerArtist.text =
                    metadata.getText(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST)

                val artUri = metadata.getString(
                    android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI
                )
                if (artUri != null) {
                    Glide.with(this)
                        .load(Uri.parse(artUri))
                        .centerCrop()
                        .into(binding.miniPlayerArt)
                }
            } else {
                binding.miniPlayer.visibility = View.GONE
            }
        }

        // Update play/pause button icon based on state
        mediaBrowserVm.playbackState.observe(this) { state ->
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            binding.miniPlayerPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }

        Log.d(TAG, "Observing playback state")
    }
}
