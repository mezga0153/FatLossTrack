package com.fatlosstrack

import android.app.Application
import com.fatlosstrack.widget.KcalWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class FatLossTrackApp : Application() {

    @Inject
    lateinit var kcalWidgetUpdater: KcalWidgetUpdater

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.US)
        kcalWidgetUpdater.start()
    }
}
