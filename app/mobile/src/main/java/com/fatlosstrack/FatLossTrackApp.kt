package com.fatlosstrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale

@HiltAndroidApp
class FatLossTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.US)
    }
}
