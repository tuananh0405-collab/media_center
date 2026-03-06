package com.anhvt86.mediacenter.ui.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anhvt86.mediacenter.databinding.FragmentQueueBinding
import com.anhvt86.mediacenter.service.PlaybackManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Play Queue screen showing upcoming tracks with:
 * - Currently playing track highlighted
 * - Drag-to-reorder (via drag handle + ItemTouchHelper)
 * - Swipe-to-remove
 * - Tap to jump to a track
 *
 * Listens to PlaybackManager for real-time queue updates.
 */
@AndroidEntryPoint
class QueueFragment : Fragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var playbackManager: PlaybackManager

    private lateinit var adapter: QueueAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupButtons()
        loadQueue()

        // Listen for live queue changes from PlaybackManager
        playbackManager.addPlaybackListener(playbackListener)
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter(
            onItemClicked = { index -> playbackManager.playAtIndex(index) },
            onDragStarted = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )

        binding.recyclerQueue.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@QueueFragment.adapter
        }

        // ItemTouchHelper for drag-to-reorder and swipe-to-remove
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,  // drag directions
            ItemTouchHelper.LEFT                          // swipe direction
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                playbackManager.moveQueueItem(from, to)
                loadQueue() // refresh list
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                playbackManager.removeFromQueue(position)
                loadQueue() // refresh list
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerQueue)
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadQueue() {
        val queue = playbackManager.queue
        val currentIndex = playbackManager.queue.indexOfFirst {
            it == playbackManager.getCurrentTrack()
        }.takeIf { it >= 0 } ?: -1

        val items = queue.mapIndexed { index, mediaItem ->
            QueueAdapter.QueueItem(mediaItem, index)
        }

        adapter.currentPlayingIndex = currentIndex
        adapter.submitList(items)

        // Update count and empty state
        binding.textQueueCount.text = "${queue.size} tracks"
        binding.textEmpty.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerQueue.visibility = if (queue.isEmpty()) View.GONE else View.VISIBLE

        // Scroll to currently playing track
        if (currentIndex >= 0) {
            binding.recyclerQueue.post {
                binding.recyclerQueue.scrollToPosition(currentIndex)
            }
        }
    }

    // Listen for real-time queue changes
    private val playbackListener = object : PlaybackManager.PlaybackListener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {}
        override fun onTrackChanged(track: com.anhvt86.mediacenter.data.local.entity.MediaItem?) {
            loadQueue()
        }
        override fun onPositionChanged(position: Long, duration: Long) {}
        override fun onQueueChanged(
            queue: List<com.anhvt86.mediacenter.data.local.entity.MediaItem>,
            currentIndex: Int
        ) {
            loadQueue()
        }
        override fun onShuffleChanged(enabled: Boolean) {}
        override fun onRepeatModeChanged(mode: PlaybackManager.RepeatMode) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackManager.removePlaybackListener(playbackListener)
        _binding = null
    }
}
