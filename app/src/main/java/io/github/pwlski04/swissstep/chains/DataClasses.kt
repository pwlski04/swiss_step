package io.github.pwlski04.swissstep.chains

import io.github.pwlski04.swissstep.paths.Segment
import io.github.pwlski04.swissstep.tracking.MovementType
import kotlinx.serialization.Serializable
import org.mapsforge.core.model.LatLong


/* DATA CLASSES */

data class PathChain(
    val id: Long,
    val movementType: MovementType,
    val points: ArrayDeque<LatLong>,
    var dirty: Boolean = true
)

@Serializable
data class StoredChainCollection(val entries: List<StoredMovementEntry>)

@Serializable
data class StoredMovementEntry(val movementType: MovementType, val chains: List<StoredPathChain>)

@Serializable
data class StoredPathChain(val id: Long, val movementType: MovementType, val points: List<StoredPoint>)

@Serializable
data class StoredPoint(val lat: Double, val lon: Double)


data class PathHypothesis(
    val id: Long,
    val chain: PathChain,
    var lastSegment: Segment,
    var timestamp: Long,

    var lastCommitGpsPoint: LatLong,
    var lastTouchedCounter: Long = 0,

    var score: Double = 0.0,
    var missCount: Int = 0,
    var pointCount: Int = 0,
    var pendingSegment: Segment? = null,
    var pendingCount: Int = 0,

    var cachedReachable: Map<Segment, Segment?>? = null,
    var cachedDistances: Map<Segment, Double>? = null,
    var cachedForSegment: Segment? = null
)