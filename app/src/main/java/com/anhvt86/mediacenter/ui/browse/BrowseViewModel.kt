package com.anhvt86.mediacenter.ui.browse

import androidx.lifecycle.*
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.repository.MediaRepository

/**
 * ViewModel for the Browse and Search screens.
 * Provides LiveData streams for all browse categories.
 *
 * Uses switchMap to reactively load filtered data when a selection changes,
 * preventing LiveData observer leaks from registering new observers on each click.
 */
class BrowseViewModel(private val repository: MediaRepository) : ViewModel() {

    val allSongs: LiveData<List<MediaItem>> = repository.getAllSongs()
    val albums: LiveData<List<String>> = repository.getAlbums()
    val artists: LiveData<List<String>> = repository.getArtists()

    // ── Reactive Selection (prevents observer leaks) ──────────────

    private val _selectedAlbum = MutableLiveData<String>()
    val albumSongs: LiveData<List<MediaItem>> = _selectedAlbum.switchMap {
        repository.getSongsByAlbum(it)
    }

    private val _selectedArtist = MutableLiveData<String>()
    val artistSongs: LiveData<List<MediaItem>> = _selectedArtist.switchMap {
        repository.getSongsByArtist(it)
    }

    private val _searchQuery = MutableLiveData<String>()
    val searchResults: LiveData<List<MediaItem>> = _searchQuery.switchMap {
        repository.search(it)
    }

    fun selectAlbum(album: String) {
        _selectedAlbum.value = album
    }

    fun selectArtist(artist: String) {
        _selectedArtist.value = artist
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

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
