package com.anhvt86.mediacenter.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.anhvt86.mediacenter.MediaCenterApp
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.databinding.FragmentBrowseBinding
import com.anhvt86.mediacenter.service.MusicService
import com.anhvt86.mediacenter.ui.nowplaying.NowPlayingFragment
import com.anhvt86.mediacenter.ui.search.SearchFragment

/**
 * Browse screen matching the design mockup.
 * - Root level: horizontal scrolling category cards (Albums, Artists, Playlists, All Songs)
 * - Drill-down: vertical list of albums/artists/tracks
 */
class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BrowseViewModel
    private lateinit var adapter: BrowseAdapter

    private var currentCategory: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        setupCategoryCards()
        setupButtons()
        observeData()

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

    private fun setupCategoryCards() {
        val categories = listOf(
            CategoryInfo("ALBUMS", getString(R.string.category_albums)),
            CategoryInfo("ARTISTS", getString(R.string.category_artists)),
            CategoryInfo("PLAYLISTS", getString(R.string.category_playlists)),
            CategoryInfo("ALL_SONGS", getString(R.string.category_all_songs))
        )

        val container = binding.categoryContainer
        container.removeAllViews()

        for (cat in categories) {
            val cardView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_card, container, false)

            cardView.findViewById<TextView>(R.id.text_category_name).text = cat.title
            // Category images will be placeholder for now (dark card)
            cardView.setOnClickListener {
                navigateToCategory(cat.id)
            }
            container.addView(cardView)
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            showCategories()
        }

        binding.btnSearch.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SearchFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observeData() {
        viewModel.allSongs.observe(viewLifecycleOwner) { songs ->
            if (currentCategory == "ALL_SONGS") {
                adapter.submitList(songs.map {
                    BrowseItem(it.id.toString(), it.title, "${it.artist} • ${it.album}",
                        it.albumArtUri, BrowseItem.Type.TRACK)
                })
                updateEmptyState(songs.isEmpty())
            }
        }

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            if (currentCategory == "ALBUMS") {
                adapter.submitList(albums.map {
                    BrowseItem("ALBUM:$it", it, null, null, BrowseItem.Type.CATEGORY)
                })
                updateEmptyState(albums.isEmpty())
            }
        }

        viewModel.artists.observe(viewLifecycleOwner) { artists ->
            if (currentCategory == "ARTISTS") {
                adapter.submitList(artists.map {
                    BrowseItem("ARTIST:$it", it, null, null, BrowseItem.Type.CATEGORY)
                })
                updateEmptyState(artists.isEmpty())
            }
        }
    }

    private fun showCategories() {
        currentCategory = null
        binding.textTitle.text = "Multimedia"
        binding.btnBack.visibility = View.GONE
        binding.btnSearch.visibility = View.VISIBLE
        binding.categoryScroll.visibility = View.VISIBLE
        binding.recyclerBrowse.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE
    }

    private fun navigateToCategory(categoryId: String) {
        currentCategory = categoryId
        binding.btnBack.visibility = View.VISIBLE
        binding.btnSearch.visibility = View.GONE
        binding.categoryScroll.visibility = View.GONE
        binding.recyclerBrowse.visibility = View.VISIBLE

        when (categoryId) {
            "ALBUMS" -> binding.textTitle.text = getString(R.string.category_albums)
            "ARTISTS" -> binding.textTitle.text = getString(R.string.category_artists)
            "ALL_SONGS" -> binding.textTitle.text = getString(R.string.category_all_songs)
            "PLAYLISTS" -> binding.textTitle.text = getString(R.string.category_playlists)
        }
    }

    private fun onItemClicked(item: BrowseItem) {
        when {
            item.id.startsWith("ALBUM:") -> {
                val albumName = item.id.removePrefix("ALBUM:")
                currentCategory = "ALBUM_DETAIL"
                binding.textTitle.text = albumName
                viewModel.getSongsByAlbum(albumName).observe(viewLifecycleOwner) { songs ->
                    if (currentCategory == "ALBUM_DETAIL") {
                        adapter.submitList(songs.map {
                            BrowseItem(it.id.toString(), it.title, it.artist,
                                it.albumArtUri, BrowseItem.Type.TRACK)
                        })
                        updateEmptyState(songs.isEmpty())
                    }
                }
            }
            item.id.startsWith("ARTIST:") -> {
                val artistName = item.id.removePrefix("ARTIST:")
                currentCategory = "ARTIST_DETAIL"
                binding.textTitle.text = artistName
                viewModel.getSongsByArtist(artistName).observe(viewLifecycleOwner) { songs ->
                    if (currentCategory == "ARTIST_DETAIL") {
                        adapter.submitList(songs.map {
                            BrowseItem(it.id.toString(), it.title,
                                "${it.artist} • ${it.album}", it.albumArtUri, BrowseItem.Type.TRACK)
                        })
                        updateEmptyState(songs.isEmpty())
                    }
                }
            }
            item.type == BrowseItem.Type.TRACK -> {
                // Play the track and navigate to Now Playing
                val pm = MusicService.instance?.playbackManager ?: return
                viewModel.allSongs.value?.let { songs ->
                    val index = songs.indexOfFirst { it.id.toString() == item.id }
                    if (index >= 0) {
                        pm.setQueueAndPlay(songs, index)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, NowPlayingFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                }
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

    private data class CategoryInfo(val id: String, val title: String)
}
