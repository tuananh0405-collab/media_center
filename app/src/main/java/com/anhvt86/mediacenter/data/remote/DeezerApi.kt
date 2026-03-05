package com.anhvt86.mediacenter.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Deezer public API interface.
 * Returns 30-second preview tracks and rich metadata with no API key needed.
 */
interface DeezerApi {

    /**
     * Get tracks from the top charts.
     */
    @GET("chart/0/tracks")
    suspend fun getChartTracks(@Query("limit") limit: Int = 30): DeezerChartResponse

    /**
     * Search for tracks.
     */
    @GET("search")
    suspend fun searchTracks(@Query("q") query: String, @Query("limit") limit: Int = 30): DeezerSearchResponse

}

// ── Models ────────────────────────────────────────────────────────

data class DeezerChartResponse(
    @SerializedName("data") val data: List<DeezerTrack>
)

data class DeezerSearchResponse(
    @SerializedName("data") val data: List<DeezerTrack>
)

data class DeezerTrack(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("duration") val durationSeconds: Long, // Deezer provides seconds
    @SerializedName("preview") val previewUrl: String,     // The 30s MP3 URL
    @SerializedName("artist") val artist: DeezerArtist,
    @SerializedName("album") val album: DeezerAlbum
)

data class DeezerArtist(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("picture_medium") val pictureMedium: String?
)

data class DeezerAlbum(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("cover_xl") val coverXl: String? // High res album cover
)
