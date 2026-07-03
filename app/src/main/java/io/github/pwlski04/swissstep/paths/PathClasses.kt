package io.github.pwlski04.swissstep.paths

import io.github.pwlski04.swissstep.tracking.MovementType
import org.mapsforge.core.model.LatLong


data class Path(
    //Store each path as an ID and a list of the coordinates belonging to it
    val id: Long,
    val points: List<LatLong>,
    val highway: String,
    val walkable: Boolean,
    val drivable: Boolean,
    val traveledBy: Set<MovementType> = emptySet()
)

/* NEW */
data class Segment(
    //Store each path as an ID and a list of the coordinates belonging to it

    //TODO: if two paths are connected (close enough to each other) and the same color => combine
    val pathId: Long,
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