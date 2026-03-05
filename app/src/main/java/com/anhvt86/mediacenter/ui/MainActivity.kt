package com.anhvt86.mediacenter.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "Storage permission granted")
            onPermissionGranted()
        } else {
            Log.w(TAG, "Storage permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start MusicService
        startService(Intent(this, MusicService::class.java))

        checkAndRequestPermissions()

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
        MusicService.instance?.playbackManager?.listener = miniPlayerListener
        updateMiniPlayer()
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionGranted()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun onPermissionGranted() {
        val app = application as MediaCenterApp
        app.repository.triggerScan()
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
