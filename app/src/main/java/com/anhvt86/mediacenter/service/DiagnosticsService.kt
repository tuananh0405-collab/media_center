package com.anhvt86.mediacenter.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.anhvt86.mediacenter.IDiagnosticsService
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Bound service that exposes playback diagnostics via AIDL.
 * Protected by signature-level permission (com.anhvt86.mediacenter.permission.BIND_DIAGNOSTICS).
 *
 * Other privileged apps (signed with the same platform key) can bind to this service
 * to query playback statistics.
 */
class DiagnosticsService : Service() {

    companion object {
        private const val TAG = "DiagnosticsService"
        private const val MAX_ERRORS = 20
    }

    // ── Playback Statistics (thread-safe, updated by PlaybackManager) ──
    object Stats {
        val totalPlayTimeMs = AtomicLong(0L)
        val totalTracksPlayed = AtomicInteger(0)
        val totalSkips = AtomicInteger(0)
        @Volatile var currentTrackInfo: String = ""

        private val errorsLock = Any()
        private val _recentErrors: MutableList<String> = mutableListOf()
        val recentErrors: List<String>
            get() = synchronized(errorsLock) { _recentErrors.toList() }

        fun addError(error: String) = synchronized(errorsLock) {
            _recentErrors.add(0, error)
            if (_recentErrors.size > MAX_ERRORS) {
                _recentErrors.removeAt(_recentErrors.lastIndex)
            }
        }

        fun reset() {
            totalPlayTimeMs.set(0L)
            totalTracksPlayed.set(0)
            totalSkips.set(0)
            currentTrackInfo = ""
            synchronized(errorsLock) { _recentErrors.clear() }
        }
    }

    // ── AIDL Binder Implementation ────────────────────────────────

    private val binder = object : IDiagnosticsService.Stub() {

        override fun getTotalPlayTimeMs(): Long {
            val value = Stats.totalPlayTimeMs.get()
            Log.d(TAG, "getTotalPlayTimeMs: $value")
            return value
        }

        override fun getTotalTracksPlayed(): Int {
            val value = Stats.totalTracksPlayed.get()
            Log.d(TAG, "getTotalTracksPlayed: $value")
            return value
        }

        override fun getTotalSkips(): Int {
            val value = Stats.totalSkips.get()
            Log.d(TAG, "getTotalSkips: $value")
            return value
        }

        override fun getCurrentTrackInfo(): String {
            Log.d(TAG, "getCurrentTrackInfo: ${Stats.currentTrackInfo}")
            return Stats.currentTrackInfo
        }

        override fun getRecentErrors(): List<String> {
            val errors = Stats.recentErrors
            Log.d(TAG, "getRecentErrors: ${errors.size} errors")
            return errors
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Client bound to DiagnosticsService")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Client unbound from DiagnosticsService")
        return super.onUnbind(intent)
    }
}
