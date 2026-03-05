package com.anhvt86.mediacenter.service

/**
 * Defines the browse tree structure for MediaBrowserService.
 * Organizes media content into categories that can be browsed
 * by the system media widget and the app UI.
 *
 * Tree structure:
 *   ROOT
 *   ├── ALBUMS     → list of album names → tracks per album
 *   ├── ARTISTS    → list of artist names → tracks per artist
 *   ├── ALL_SONGS  → flat list of all tracks
 *   └── PLAYLISTS  → user playlists → tracks per playlist
 */
object BrowseTree {

    // ── Media ID Constants ─────────────────────────────────────────
    const val ROOT_ID = "ROOT"
    const val ALBUMS_ID = "ALBUMS"
    const val ARTISTS_ID = "ARTISTS"
    const val ALL_SONGS_ID = "ALL_SONGS"
    const val PLAYLISTS_ID = "PLAYLISTS"

    // Prefixes for dynamic children
    const val ALBUM_PREFIX = "ALBUM:"
    const val ARTIST_PREFIX = "ARTIST:"
    const val PLAYLIST_PREFIX = "PLAYLIST:"
    const val TRACK_PREFIX = "TRACK:"

    /**
     * Returns the list of root-level category IDs.
     */
    fun getRootCategories(): List<String> = listOf(
        ALBUMS_ID,
        ARTISTS_ID,
        ALL_SONGS_ID,
        PLAYLISTS_ID
    )

    /**
     * Returns a human-readable title for a category ID.
     */
    fun getCategoryTitle(mediaId: String): String = when (mediaId) {
        ALBUMS_ID -> "Albums"
        ARTISTS_ID -> "Artists"
        ALL_SONGS_ID -> "All Songs"
        PLAYLISTS_ID -> "Playlists"
        else -> "Unknown"
    }

    /**
     * Parse the dynamic name from a prefixed media ID.
     * e.g., "ALBUM:Abbey Road" → "Abbey Road"
     */
    fun parseName(mediaId: String): String {
        return mediaId.substringAfter(":", "")
    }
}
