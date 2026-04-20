package com.example.stepbystep_v10

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class StepByStepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidGraphicFactory.createInstance(this)
    }
}