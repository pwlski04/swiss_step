package com.example.stepbystep_v10.tracking

import androidx.compose.runtime.mutableStateOf
import com.example.stepbystep_v10.map.paths.PathPoint

object TrackingLiveState {
    var latestPoint = mutableStateOf<PathPoint?>(null)
    var movementType = mutableStateOf(MovementType.STILL)

    val isForegroundTracking = mutableStateOf(false)
}