package io.github.pwlski04.swissstep.paths

import io.github.pwlski04.swissstep.tracking.MovementType
import org.mapsforge.core.model.LatLong


data class Segment(
    val startingPoint: LatLong,
    val endingPoint: LatLong,
    val highway: String,
    val walkable: Boolean,
    val drivable: Boolean,
    val traveledBy: Set<MovementType> = emptySet()
)


/* OLD: */

data class PathPoint (
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val sessionId: Long,
    val movementType: MovementType
)