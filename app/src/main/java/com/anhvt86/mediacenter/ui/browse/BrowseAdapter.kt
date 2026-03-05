package com.anhvt86.mediacenter.ui.browse

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anhvt86.mediacenter.R
import com.anhvt86.mediacenter.databinding.ItemBrowseBinding
import com.bumptech.glide.Glide

/**
 * RecyclerView adapter for browse items (categories and tracks).
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
class BrowseAdapter(
    private val onItemClick: (BrowseItem) -> Unit
) : ListAdapter<BrowseItem, BrowseAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBrowseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemBrowseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BrowseItem) {
            binding.textTitle.text = item.title
            binding.textSubtitle.text = item.subtitle
            binding.textSubtitle.visibility =
                if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE

            // Show album art for tracks, category icon for categories
            when (item.type) {
                BrowseItem.Type.TRACK -> {
                    binding.imageArt.visibility = View.VISIBLE
                    binding.iconCategory.visibility = View.GONE
                    if (item.imageUri != null) {
                        Glide.with(binding.imageArt)
                            .load(Uri.parse(item.imageUri))
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .centerCrop()
                            .into(binding.imageArt)
                    } else {
                        binding.imageArt.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
                BrowseItem.Type.CATEGORY -> {
                    binding.imageArt.visibility = View.GONE
                    binding.iconCategory.visibility = View.VISIBLE
                }
            }

            // Chevron arrow for browsable items
            binding.iconChevron.visibility =
                if (item.type == BrowseItem.Type.CATEGORY) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<BrowseItem>() {
        override fun areItemsTheSame(oldItem: BrowseItem, newItem: BrowseItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BrowseItem, newItem: BrowseItem) =
            oldItem == newItem
    }
}
