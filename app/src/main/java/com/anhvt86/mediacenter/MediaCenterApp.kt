package com.anhvt86.mediacenter

import android.app.Application
import com.anhvt86.mediacenter.data.repository.MediaRepository
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for MediaCenter.
 * Annotated with @HiltAndroidApp to trigger Hilt code generation.
 */
@HiltAndroidApp
class MediaCenterApp : Application()
