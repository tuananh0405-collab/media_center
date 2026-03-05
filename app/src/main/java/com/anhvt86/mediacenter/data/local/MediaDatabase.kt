package com.anhvt86.mediacenter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anhvt86.mediacenter.data.local.dao.MediaDao
import com.anhvt86.mediacenter.data.local.dao.PlaylistDao
import com.anhvt86.mediacenter.data.local.entity.MediaItem
import com.anhvt86.mediacenter.data.local.entity.Playlist
import com.anhvt86.mediacenter.data.local.entity.PlaylistTrackCrossRef

/**
 * Room database for the MediaCenter app.
 * Stores audio track metadata, playlists, and playlist-track relationships.
 */
@Database(
    entities = [
        MediaItem::class,
        Playlist::class,
        PlaylistTrackCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: MediaDatabase? = null

        fun getInstance(context: Context): MediaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MediaDatabase::class.java,
                    "media_center.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
