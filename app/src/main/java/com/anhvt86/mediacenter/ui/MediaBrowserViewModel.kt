package com.anhvt86.mediacenter.ui

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.anhvt86.mediacenter.service.MusicService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Shared ViewModel holding the MediaBrowserCompat connection and MediaController.
 *
 * Scoped to the Activity, so Browse, NowPlaying, and Search fragments all share
 * the same controller. Replaces the fragile MusicService.instance singleton.
 *
 * Usage in Fragment:
 *   val mediaBrowserVm: MediaBrowserViewModel by activityViewModels()
 *   mediaBrowserVm.mediaController.observe(viewLifecycleOwner) { controller -> ... }
 */
@HiltViewModel
class MediaBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "MediaBrowserViewModel"
    }

    private val _mediaController = MutableLiveData<MediaControllerCompat?>()
    val mediaController: LiveData<MediaControllerCompat?> = _mediaController

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _playbackState = MutableLiveData<PlaybackStateCompat?>()
    val playbackState: LiveData<PlaybackStateCompat?> = _playbackState

    private val _nowPlaying = MutableLiveData<MediaMetadataCompat?>()
    val nowPlaying: LiveData<MediaMetadataCompat?> = _nowPlaying

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            _playbackState.postValue(state)
        }
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            _nowPlaying.postValue(metadata)
        }
    }

    // connectionCallback must be declared before mediaBrowser to avoid forward reference
    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "MediaBrowser connected")
            val controller = MediaControllerCompat(context, mediaBrowser.sessionToken)
            controller.registerCallback(controllerCallback)
            _mediaController.postValue(controller)
            _isConnected.postValue(true)
            // Emit current state immediately
            _playbackState.postValue(controller.playbackState)
            _nowPlaying.postValue(controller.metadata)
        }

        override fun onConnectionSuspended() {
            Log.w(TAG, "MediaBrowser connection suspended")
            _isConnected.postValue(false)
            _mediaController.postValue(null)
        }

        override fun onConnectionFailed() {
            Log.e(TAG, "MediaBrowser connection failed")
            _isConnected.postValue(false)
        }
    }

    private val mediaBrowser: MediaBrowserCompat = MediaBrowserCompat(
        context,
        ComponentName(context, MusicService::class.java),
        connectionCallback,
        null
    )

    init {
        mediaBrowser.connect()
        Log.d(TAG, "MediaBrowser connecting…")
    }

    override fun onCleared() {
        super.onCleared()
        _mediaController.value?.unregisterCallback(controllerCallback)
        if (mediaBrowser.isConnected) mediaBrowser.disconnect()
        Log.d(TAG, "MediaBrowserViewModel cleared")
    }
}
