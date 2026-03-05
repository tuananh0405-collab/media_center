package com.anhvt86.mediacenter.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.anhvt86.mediacenter.MediaCenterApp
import com.anhvt86.mediacenter.databinding.FragmentBrowseBinding

/**
 * Browse screen fragment.
 * Displays media library categories (Albums, Artists, All Songs) and individual tracks.
 * Follows the AAOS distraction-compliance guidelines.
 */
class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BrowseViewModel
    private lateinit var adapter: BrowseAdapter

    // Current browse level
    private var currentCategory: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as MediaCenterApp
        viewModel = ViewModelProvider(
            this,
            BrowseViewModel.Factory(app.repository)
        )[BrowseViewModel::class.java]

        setupRecyclerView()
        observeData()

        // Start at root level — show categories
        showCategories()
    }

    private fun setupRecyclerView() {
        adapter = BrowseAdapter { item ->
            onItemClicked(item)
        }
        binding.recyclerBrowse.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BrowseFragment.adapter
        }
    }

    private fun observeData() {
        // Observe all songs for when "All Songs" category is selected
        viewModel.allSongs.observe(viewLifecycleOwner) { songs ->
            if (currentCategory == "ALL_SONGS") {
                adapter.submitList(songs.map {
                    BrowseItem(
                        id = it.id.toString(),
                        title = it.title,
                        subtitle = "${it.artist} • ${it.album}",
                        imageUri = it.albumArtUri,
                        type = BrowseItem.Type.TRACK
                    )
                })
                binding.textTitle.text = getString(com.anhvt86.mediacenter.R.string.category_all_songs)
                updateEmptyState(songs.isEmpty())
            }
        }

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            if (currentCategory == "ALBUMS") {
                adapter.submitList(albums.map {
                    BrowseItem(
                        id = "ALBUM:$it",
                        title = it,
                        subtitle = null,
                        imageUri = null,
                        type = BrowseItem.Type.CATEGORY
                    )
                })
                binding.textTitle.text = getString(com.anhvt86.mediacenter.R.string.category_albums)
                updateEmptyState(albums.isEmpty())
            }
        }

        viewModel.artists.observe(viewLifecycleOwner) { artists ->
            if (currentCategory == "ARTISTS") {
                adapter.submitList(artists.map {
                    BrowseItem(
                        id = "ARTIST:$it",
                        title = it,
                        subtitle = null,
                        imageUri = null,
                        type = BrowseItem.Type.CATEGORY
                    )
                })
                binding.textTitle.text = getString(com.anhvt86.mediacenter.R.string.category_artists)
                updateEmptyState(artists.isEmpty())
            }
        }
    }

    private fun showCategories() {
        currentCategory = null
        binding.textTitle.text = getString(com.anhvt86.mediacenter.R.string.app_name)
        binding.btnBack.visibility = View.GONE

        val categories = listOf(
            BrowseItem("ALBUMS", getString(com.anhvt86.mediacenter.R.string.category_albums), null, null, BrowseItem.Type.CATEGORY),
            BrowseItem("ARTISTS", getString(com.anhvt86.mediacenter.R.string.category_artists), null, null, BrowseItem.Type.CATEGORY),
            BrowseItem("ALL_SONGS", getString(com.anhvt86.mediacenter.R.string.category_all_songs), null, null, BrowseItem.Type.CATEGORY),
        )
        adapter.submitList(categories)
        updateEmptyState(false)
    }

    private fun onItemClicked(item: BrowseItem) {
        when {
            // Root category clicked
            item.id == "ALBUMS" -> {
                currentCategory = "ALBUMS"
                binding.btnBack.visibility = View.VISIBLE
            }
            item.id == "ARTISTS" -> {
                currentCategory = "ARTISTS"
                binding.btnBack.visibility = View.VISIBLE
            }
            item.id == "ALL_SONGS" -> {
                currentCategory = "ALL_SONGS"
                binding.btnBack.visibility = View.VISIBLE
            }
            // Album/Artist sub-category clicked
            item.id.startsWith("ALBUM:") -> {
                val albumName = item.id.removePrefix("ALBUM:")
                currentCategory = "ALBUM_DETAIL"
                binding.textTitle.text = albumName
                binding.btnBack.visibility = View.VISIBLE
                viewModel.getSongsByAlbum(albumName).observe(viewLifecycleOwner) { songs ->
                    if (currentCategory == "ALBUM_DETAIL") {
                        adapter.submitList(songs.map {
                            BrowseItem(it.id.toString(), it.title, it.artist, it.albumArtUri, BrowseItem.Type.TRACK)
                        })
                        updateEmptyState(songs.isEmpty())
                    }
                }
            }
            item.id.startsWith("ARTIST:") -> {
                val artistName = item.id.removePrefix("ARTIST:")
                currentCategory = "ARTIST_DETAIL"
                binding.textTitle.text = artistName
                binding.btnBack.visibility = View.VISIBLE
                viewModel.getSongsByArtist(artistName).observe(viewLifecycleOwner) { songs ->
                    if (currentCategory == "ARTIST_DETAIL") {
                        adapter.submitList(songs.map {
                            BrowseItem(it.id.toString(), it.title, "${it.artist} • ${it.album}", it.albumArtUri, BrowseItem.Type.TRACK)
                        })
                        updateEmptyState(songs.isEmpty())
                    }
                }
            }
            // Track clicked — TODO: navigate to Now Playing (Step 4)
            item.type == BrowseItem.Type.TRACK -> {
                // Placeholder: will play track in Step 4
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.textEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerBrowse.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Data class representing an item in the browse list.
 */
data class BrowseItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUri: String?,
    val type: Type
) {
    enum class Type { CATEGORY, TRACK }
}
