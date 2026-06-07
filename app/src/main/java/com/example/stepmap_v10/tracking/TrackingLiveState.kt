package com.example.stepmap_v10.tracking

import androidx.compose.runtime.mutableStateOf
import com.example.stepmap_v10.paths.PathPoint
import kotlinx.coroutines.flow.MutableStateFlow

object TrackingLiveState {
    val latestPoint = MutableStateFlow<PathPoint?>(null)
    val movementType = MutableStateFlow(MovementType.STILL)
    val isDrawing = MutableStateFlow(false)

    val isForegroundTracking = mutableStateOf(false)
}