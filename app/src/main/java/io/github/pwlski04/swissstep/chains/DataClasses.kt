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
    var dirty: Boolean = true,
    var loggedPointCount: Int = 0        // how many leading points are already flushed to the append log/checkpoint
)

@Serializable
data class StoredChainCollection(val entries: List<StoredMovementEntry>)

@Serializable
data class StoredMovementEntry(val movementType: MovementType, val chains: List<StoredPathChain>)

@Serializable
data class StoredPathChain(val id: Long, val movementType: MovementType, val points: List<StoredPoint>)

@Serializable
data class StoredPoint(val lat: Double, val lon: Double)

/*
One line of the append-only walked_chains.log: either new points tacked onto the end of
a chain since the last save/checkpoint, or a tombstone marking a chain that was dropped
(e.g. a stale hypothesis discarded via removePrimary) before it ever reached a checkpoint.
*/
@Serializable
data class LogAppend(
    val chainId: Long,
    val movementType: MovementType,
    val newPoints: List<StoredPoint> = emptyList(),
    val removed: Boolean = false
)


data class PathHypothesis(
    val id: Long,
    val chain: PathChain,
    var lastSegment: Segment,
    var previousSegment: Segment? = null,
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
