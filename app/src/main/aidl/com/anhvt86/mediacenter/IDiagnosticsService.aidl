// IDiagnosticsService.aidl
package com.anhvt86.mediacenter;

/**
 * AIDL interface for the Diagnostics Service.
 * Exposes playback statistics to other privileged apps.
 * Protected by signature-level permission.
 */
interface IDiagnosticsService {
    /** Total playback time in milliseconds across all sessions. */
    long getTotalPlayTimeMs();

    /** Total number of tracks that have been played. */
    int getTotalTracksPlayed();

    /** Total number of skip actions performed. */
    int getTotalSkips();

    /** Current track info as "Title - Artist" string, or empty if nothing playing. */
    String getCurrentTrackInfo();

    /** List of recent error messages (up to last 20). */
    List<String> getRecentErrors();
}
