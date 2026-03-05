package com.anhvt86.mediacenter

import android.app.Application
import com.anhvt86.mediacenter.data.repository.MediaRepository

/**
 * Application class for MediaCenter.
 * Initializes the MediaRepository and triggers the first media scan.
 */
class MediaCenterApp : Application() {

    lateinit var repository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MediaRepository(this)

        // Trigger initial scan and schedule periodic scans
        repository.triggerScan()
        repository.schedulePeriodicScan()
    }
}
