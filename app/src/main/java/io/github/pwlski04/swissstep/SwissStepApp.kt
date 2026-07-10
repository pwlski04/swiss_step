package io.github.pwlski04.swissstep

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class SwissStepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidGraphicFactory.createInstance(this)
    }
}