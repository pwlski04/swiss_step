package com.example.stepMap_v10

import com.example.stepMap_v10.tracking.MovementType
import android.graphics.Color

var colorMap = mutableMapOf(
    MovementType.STILL to Color.rgb(255, 180, 180),
    MovementType.WALKING to Color.rgb(255, 255, 165),
    MovementType.RUNNING to Color.rgb(255, 255, 0),
    MovementType.BIKING to Color.rgb(255, 0, 150),
    MovementType.TRANSPORT to Color.rgb(255, 120, 120)
)