package com.anhvt86.mediacenter.ui.queue

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.databinding.ItemQueueBinding
import com.bumptech.glide.Glide

/**
 * RecyclerView adapter for the play queue.
 * Highlights the currently playing track and provides drag-handle touch feedback.
 *
 * @param onItemClicked  Called when a queue item is tapped (play from that index).
 * @param onDragStarted  Called when the drag handle is touched (start drag via ItemTouchHelper).
 */
class QueueAdapter(
    private val onItemClicked: (Int) -> Unit,
    private val onDragStarted: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<QueueAdapter.QueueItem, QueueAdapter.ViewHolder>(DIFF) {

    /** Index of the currently playing track, used for highlighting */
    var currentPlayingIndex: Int = -1
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    data class QueueItem(
        val mediaItem: MediaItem,
        val index: Int
    )

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<QueueItem>() {
            override fun areItemsTheSame(a: QueueItem, b: QueueItem) =
                a.mediaItem.id == b.mediaItem.id && a.index == b.index
            override fun areContentsTheSame(a: QueueItem, b: QueueItem) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position == currentPlayingIndex)

        holder.itemView.setOnClickListener { onItemClicked(item.index) }

        // Start drag when the drag handle is touched
        holder.binding.iconDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onDragStarted(holder)
            }
            false
        }
    }

    class ViewHolder(val binding: ItemQueueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueueItem, isCurrentlyPlaying: Boolean) {
            binding.textTitle.text = item.mediaItem.title
            binding.textArtist.text = item.mediaItem.artist

            // Album art
            val artUri = item.mediaItem.albumArtUri
            if (artUri != null) {
                Glide.with(binding.root.context)
                    .load(Uri.parse(artUri))
                    .centerCrop()
                    .into(binding.imageArt)
            } else {
                binding.imageArt.setImageResource(R.drawable.ic_launcher_foreground)
            }

            // Highlight currently playing track
            binding.iconNowPlaying.visibility =
                if (isCurrentlyPlaying) View.VISIBLE else View.GONE

            // Tint title for currently playing
            val titleColor = if (isCurrentlyPlaying) {
                binding.root.context.getColor(R.color.primary)
            } else {
                binding.root.context.getColor(R.color.text_primary)
            }
            binding.textTitle.setTextColor(titleColor)
        }
    }
}
