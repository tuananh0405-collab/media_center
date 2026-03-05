package com.anhvt86.mediacenter.ui.browse

import androidx.lifecycle.*
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.repository.MediaRepository

/**
 * ViewModel for the Browse screen.
 * Provides LiveData streams for all browse categories.
 */
class BrowseViewModel(private val repository: MediaRepository) : ViewModel() {

    val allSongs: LiveData<List<MediaItem>> = repository.getAllSongs()
    val albums: LiveData<List<String>> = repository.getAlbums()
    val artists: LiveData<List<String>> = repository.getArtists()

    fun getSongsByAlbum(album: String): LiveData<List<MediaItem>> =
        repository.getSongsByAlbum(album)

    fun getSongsByArtist(artist: String): LiveData<List<MediaItem>> =
        repository.getSongsByArtist(artist)

    fun search(query: String): LiveData<List<MediaItem>> =
        repository.search(query)

    /**
     * Factory for creating BrowseViewModel with repository dependency.
     */
    class Factory(private val repository: MediaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
                return BrowseViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
