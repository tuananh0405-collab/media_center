package com.anhvt86.mediacenter.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.anhvt86.mediacenter.IDiagnosticsService

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

    // ── Playback Statistics (in-memory, updated by MusicService) ──
    // In a production app, these would be persisted or shared via a shared singleton.
    // For this mock project, we use companion object state.
    object Stats {
        var totalPlayTimeMs: Long = 0L
        var totalTracksPlayed: Int = 0
        var totalSkips: Int = 0
        var currentTrackInfo: String = ""
        val recentErrors: MutableList<String> = mutableListOf()

        fun addError(error: String) {
            recentErrors.add(0, error)
            if (recentErrors.size > MAX_ERRORS) {
                recentErrors.removeAt(recentErrors.lastIndex)
            }
        }

        fun reset() {
            totalPlayTimeMs = 0L
            totalTracksPlayed = 0
            totalSkips = 0
            currentTrackInfo = ""
            recentErrors.clear()
        }
    }

    // ── AIDL Binder Implementation ────────────────────────────────

    private val binder = object : IDiagnosticsService.Stub() {

        override fun getTotalPlayTimeMs(): Long {
            Log.d(TAG, "getTotalPlayTimeMs: ${Stats.totalPlayTimeMs}")
            return Stats.totalPlayTimeMs
        }

        override fun getTotalTracksPlayed(): Int {
            Log.d(TAG, "getTotalTracksPlayed: ${Stats.totalTracksPlayed}")
            return Stats.totalTracksPlayed
        }

        override fun getTotalSkips(): Int {
            Log.d(TAG, "getTotalSkips: ${Stats.totalSkips}")
            return Stats.totalSkips
        }

        override fun getCurrentTrackInfo(): String {
            Log.d(TAG, "getCurrentTrackInfo: ${Stats.currentTrackInfo}")
            return Stats.currentTrackInfo
        }

        override fun getRecentErrors(): List<String> {
            Log.d(TAG, "getRecentErrors: ${Stats.recentErrors.size} errors")
            return Stats.recentErrors.toList()
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
