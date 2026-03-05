package com.anhvt86.mediacenter.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anhvt86.mediacenter.MediaCenterApp
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.databinding.FragmentSearchBinding
import com.anhvt86.mediacenter.service.MusicService
import com.anhvt86.mediacenter.ui.browse.BrowseAdapter
import com.anhvt86.mediacenter.ui.browse.BrowseItem
import com.anhvt86.mediacenter.ui.browse.BrowseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search screen with teal-bordered search input and results list.
 * Matches the design mockup.
 *
 * Uses debounced search (300ms) and a single switchMap-based observer
 * to prevent LiveData leaks and excessive queries.
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BrowseViewModel
    private lateinit var adapter: BrowseAdapter

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
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
        setupSearchInput()
        observeSearchResults()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = BrowseAdapter { item ->
            if (item.type == BrowseItem.Type.TRACK) {
                // Play the track using cached allSongs value (no new observer!)
                val pm = MusicService.instance?.playbackManager ?: return@BrowseAdapter
                val songs = viewModel.allSongs.value ?: return@BrowseAdapter
                val index = songs.indexOfFirst { it.id.toString() == item.id }
                if (index >= 0) {
                    pm.setQueueAndPlay(songs, index)
                }
            }
        }
        binding.recyclerResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchFragment.adapter
        }
    }

    /**
     * Observe search results via switchMap — registered ONCE,
     * reacts automatically when viewModel.setSearchQuery() is called.
     */
    private fun observeSearchResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            val items = results.map {
                BrowseItem(
                    id = it.id.toString(),
                    title = it.title,
                    subtitle = it.artist,
                    imageUri = it.albumArtUri,
                    type = BrowseItem.Type.TRACK
                )
            }
            adapter.submitList(items)

            binding.textNoResults.visibility =
                if (items.isEmpty() && binding.editSearch.text.isNotEmpty()) View.VISIBLE
                else View.GONE
        }
    }

    private fun setupSearchInput() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                if (query.isNotEmpty()) {
                    // Debounce: cancel previous search and wait 300ms before firing
                    searchJob?.cancel()
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(300)
                        viewModel.setSearchQuery(query)
                    }
                } else {
                    searchJob?.cancel()
                    adapter.submitList(emptyList())
                    binding.textNoResults.visibility = View.GONE
                }
            }
        })

        binding.btnClear.setOnClickListener {
            binding.editSearch.text.clear()
        }

        // Request focus on the search input
        binding.editSearch.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}
