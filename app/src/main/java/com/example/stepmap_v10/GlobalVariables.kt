package com.example.stepmap_v10

import com.example.stepmap_v10.tracking.MovementType
import android.graphics.Color
import androidx.compose.ui.graphics.Color as DirectColor
import androidx.compose.runtime.mutableStateMapOf

val defaultColorMap = mapOf(
    MovementType.STILL to Color.rgb(255, 180, 180),
    MovementType.WALKING to Color.rgb(255, 255, 165),
    MovementType.RUNNING to Color.rgb(255, 255, 0),
    MovementType.BIKING to Color.rgb(255, 0, 150),
    MovementType.TRANSPORT to Color.rgb(255, 120, 120)
)
var colorMap = mutableStateMapOf(
    MovementType.STILL to Color.rgb(255, 180, 180),
    MovementType.WALKING to Color.rgb(255, 255, 165),
    MovementType.RUNNING to Color.rgb(255, 255, 0),
    MovementType.BIKING to Color.rgb(255, 0, 150),
    MovementType.TRANSPORT to Color.rgb(255, 120, 120)
)

val accentColor1 = DirectColor(red = 236, green = 236, blue = 236, 200)  // DirectColor(211, 226, 225, 144)
val accentColor2 = DirectColor(red = 240, green = 240, blue = 240, 144)    // Color(red = 240, green = 240, blue = 240, 144)