package io.github.pwlski04.swissstep.tracking

import androidx.compose.runtime.mutableStateOf
import io.github.pwlski04.swissstep.paths.PathPoint
import kotlinx.coroutines.flow.MutableStateFlow

object TrackingLiveState {
    val latestPoint = MutableStateFlow<PathPoint?>(null)
    val movementType = MutableStateFlow(MovementType.STILL)
    val isDrawing = MutableStateFlow(false)

    val isForegroundTracking = mutableStateOf(false)
}