package com.anhvt86.mediacenter.ui.browse

/**
 * Data class representing an item in the browse list.
 * Used by BrowseAdapter, BrowseFragment, and SearchFragment.
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
