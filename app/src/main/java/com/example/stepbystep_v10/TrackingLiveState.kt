package com.example.stepbystep_v10

import androidx.compose.runtime.mutableStateOf

object TrackingLiveState {
    var latestPoint = mutableStateOf<PathPoint?>(null)
    var movementType = mutableStateOf(MovementType.STILL)

    val isForegroundTracking = mutableStateOf(false)
}