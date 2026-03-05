package com.anhvt86.mediacenter.di

import android.content.Context
import com.anhvt86.mediacenter.data.local.MediaDatabase
import com.anhvt86.mediacenter.data.local.dao.MediaDao
import com.anhvt86.mediacenter.data.local.dao.PlaylistDao
import com.anhvt86.mediacenter.data.repository.MediaRepository
import com.anhvt86.mediacenter.service.PlaybackManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing application-scoped dependencies.
 * Centralizes all dependency creation that was previously scattered across
 * Application, Service, and Fragment classes.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMediaDatabase(@ApplicationContext context: Context): MediaDatabase {
        return MediaDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMediaDao(database: MediaDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: MediaDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideMediaRepository(@ApplicationContext context: Context): MediaRepository {
        return MediaRepository(context)
    }

    @Provides
    @Singleton
    fun providePlaybackManager(@ApplicationContext context: Context): PlaybackManager {
        return PlaybackManager(context)
    }
}
