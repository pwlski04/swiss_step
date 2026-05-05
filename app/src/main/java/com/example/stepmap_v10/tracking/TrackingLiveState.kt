package com.example.stepMap_v10.tracking

import androidx.compose.runtime.mutableStateOf
import com.example.stepMap_v10.paths.PathPoint

object TrackingLiveState {
    var latestPoint = mutableStateOf<PathPoint?>(null)
    var movementType = mutableStateOf(MovementType.STILL)

    val isForegroundTracking = mutableStateOf(false)
}