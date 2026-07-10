package io.github.pwlski04.swissstep.chains

import io.github.pwlski04.swissstep.paths.Segment
import io.github.pwlski04.swissstep.paths.SegmentIndex
import io.github.pwlski04.swissstep.paths.pointToSegmentDistance
import io.github.pwlski04.swissstep.tracking.MovementType
import org.mapsforge.core.model.LatLong
import android.content.Context
import android.util.Log
import io.github.pwlski04.swissstep.tracking.AppSegmentIndex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


/* PATH STORAGE */

class PathStorage {

    val chains: MutableMap<MovementType, MutableList<PathChain>> =
        MovementType.entries.associateWith { mutableListOf<PathChain>() }.toMutableMap()

    val modifiedMovementTypes = mutableSetOf<MovementType>()
    private var nextChainId = 0L
    private var nextHypId = 0L

    private val primary = HashMap<MovementType, PathHypothesis>()
    private val backups = HashMap<MovementType, MutableList<PathHypothesis>>()

    private val LIVE_GAP_THRESHOLD_MS = 30_000L
    private val MAX_CONTINUITY_DISTANCE = 0.0008
    private val MISS_THRESHOLD = 5
    private val MAX_BACKUPS = 3
    private val HARD_RESET_THRESHOLD = 30
    private val BFS_SEARCH_DISTANCE = 0.005
    private val OTHER_TYPE_STALE_THRESHOLD = 15
    private var globalCallCounter = 0L

    private val COMMIT_THRESHOLD = 1
    private val pendingSpawn = HashMap<MovementType, Int>()

    // Consecutive misses against the CURRENT (possibly long-stale/wrong) primary that each
    // independently found a plausible nearby segment, and stayed close to the previous one -
    // i.e. a coherent new trajectory forming while the old hypothesis is still technically
    // "active". Without this, those points are pure misses and get discarded outright; only
    // once missCount crosses HARD_RESET_THRESHOLD/MISS_THRESHOLD does a fresh chain start,
    // and only from whichever point happened to trigger that reset - silently losing every
    // consistent point that came before it.
    private data class MissBufferEntry(val point: LatLong, val timestamp: Long)
    private val missBuffers = HashMap<MovementType, MutableList<MissBufferEntry>>()
    private val MISS_BUFFER_CONSISTENCY_DISTANCE = 0.002
    private val MISS_BUFFER_TRIGGER_SIZE = 3

    var onChainRemoved: ((Long) -> Unit)? = null
    var onChainsChanged: (() -> Unit)? = null
    var lastActiveMovementType: MovementType = MovementType.TRANSPORT

    private val FILE_NAME = "walked_chains.json"
    private val LOG_FILE_NAME = "walked_chains.log"
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // Buffered LogAppend entries (new-point diffs + removal tombstones) waiting to be
    // flushed to LOG_FILE_NAME by the next save().
    private val pendingLogEntries = mutableListOf<LogAppend>()


    /* ── GPS entry point ── */

    private fun spawnFreshPrimary(gpsPoint: LatLong, movementType: MovementType, index: SegmentIndex, now: Long) {
        /*
        Starts a brand new chain + primary hypothesis anchored to the nearest segment to
        this GPS point, replacing whatever primary hypothesis (if any) previously existed
        for this movement type.
        */
        val nearest = index.nearbySegments(gpsPoint.latitude, gpsPoint.longitude)
            .filter { pointToSegmentDistance(gpsPoint, it) < 0.0006 }
            .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway, movementType) }
            ?: return

        val distToStart = gpsPoint.distanceTo(nearest.startingPoint)
        val distToEnd = gpsPoint.distanceTo(nearest.endingPoint)
        val closestEndpoint =
            if (distToStart <= distToEnd) nearest.startingPoint
            else nearest.endingPoint

        val chain = PathChain(
            id = nextChainId++,
            movementType = movementType,
            points = ArrayDeque(listOf(closestEndpoint)),
            dirty = true
        )
        chains[movementType]?.add(chain)
        val hyp = PathHypothesis(
            id = nextHypId++, chain = chain, lastSegment = nearest, timestamp = now,
            lastCommitGpsPoint = gpsPoint, lastTouchedCounter = globalCallCounter
        )
        primary[movementType] = hyp
        modifiedMovementTypes.add(movementType)
        onChainsChanged?.invoke()
    }

    private fun extendChainWithPath(chain: PathChain, parentMap: Map<Segment, Segment?>, target: Segment, lastPointIn: LatLong) {
        /*
        Appends every segment on the parent-map path from the chain's current end to
        `target`, then cleans up the appended points: collapses immediate back-and-forth
        jitter, and trims the chain back to an earlier point if it re-visits somewhere
        it's already been (avoids drawing little loops/spikes).
        */
        val path = mutableListOf<Segment>()
        var current: Segment? = target
        while (current != null && parentMap[current] != null) {
            path.add(0, current)
            current = parentMap[current]
        }

        // Revisit/collapse must never look further back than where THIS call started
        // appending: its job is to catch a single BFS path reconstruction doubling back on
        // itself (this call's own points), not to second-guess already-committed history
        // from earlier calls. A real, slow loop walk (someone wandering a small area) will
        // routinely pass within a couple meters of its own earlier path over many separate
        // commits - that's genuine progress, not a spike, and retroactively deleting it back
        // to the "revisited" point destroyed most of the walk on tight/slow routes.
        val callStartSize = chain.points.size

        // Appends one point, then immediately runs the collapse/revisit cleanup against it.
        // Must run after EVERY individual point, not once per segment: a segment can
        // contribute two points (its near and far endpoint), and if the first of those two
        // is itself the point that revisits earlier chain history, checking only the second
        // (final) point of the pair would miss it entirely - the real revisit had already
        // been buried under one more point by the time the check ran.
        fun appendAndCollapse(point: LatLong) {
            chain.points.addLast(point)

            while (chain.points.size - callStartSize >= 3) {
                val n = chain.points.size
                if (chain.points[n - 1].distanceTo(chain.points[n - 3]) < 0.00002) {
                    chain.points.removeAt(n - 1)
                    chain.points.removeAt(n - 2)
                } else break
            }

            if (chain.points.size - callStartSize >= 3) {
                val last = chain.points.last()
                val searchFrom = maxOf(callStartSize, chain.points.size - 8)
                val revisitIdx = (searchFrom until chain.points.size - 1)
                    .lastOrNull { chain.points[it].distanceTo(last) < 0.00002 }
                if (revisitIdx != null) {
                    while (chain.points.size > revisitIdx + 1) chain.points.removeLast()
                }
            }
        }

        var lastPoint = lastPointIn
        for (seg in path) {
            val s = seg.startingPoint
            val e = seg.endingPoint
            val appendStart = lastPoint.distanceTo(s) <= lastPoint.distanceTo(e)
            if (appendStart) {
                if (lastPoint.distanceTo(s) > 0.00001) appendAndCollapse(s)
                appendAndCollapse(e)
                lastPoint = e
            } else {
                if (lastPoint.distanceTo(e) > 0.00001) appendAndCollapse(e)
                appendAndCollapse(s)
                lastPoint = s
            }
            chain.points.lastOrNull()?.let { lastPoint = it }
        }
        chain.dirty = true
    }

    private fun extendChain(chain: PathChain, segment: Segment, lastPoint: LatLong) {
        val s = segment.startingPoint
        val e = segment.endingPoint
        val appendStart = lastPoint.distanceTo(s) <= lastPoint.distanceTo(e)
        if (appendStart) {
            if (lastPoint.distanceTo(s) > 0.00001) chain.points.addLast(s)
            chain.points.addLast(e)
        } else {
            if (lastPoint.distanceTo(e) > 0.00001) chain.points.addLast(e)
            chain.points.addLast(s)
        }
        chain.dirty = true
    }

    @Synchronized
    fun onGpsPoint(gpsPoint: LatLong, movementType: MovementType, index: SegmentIndex, now: Long = System.currentTimeMillis()) {
        /*
        Main entry point: feeds one GPS fix into the per-movement-type tracking state
        machine. Maintains a "primary" hypothesis (the segment/chain we believe we're
        currently on) plus a few "backup" hypotheses for nearby alternative segments, so
        that if the primary turns out to be wrong we can fall back to whichever backup
        matched best rather than losing the whole chain. Handles spawning a new chain when
        there's no primary yet, extending the primary chain when the point continues to fit
        it, growing/pruning backups, and re-anchoring or hard-resetting after too many
        consecutive misses.
        */
        globalCallCounter++
        if (movementType == MovementType.STILL) return

        lastActiveMovementType = movementType

        val bkps = backups.getOrPut(movementType) { mutableListOf() }
        bkps.removeAll { now - it.timestamp > LIVE_GAP_THRESHOLD_MS }

        var currentPrimary = primary[movementType]
        if (currentPrimary != null && globalCallCounter - currentPrimary.lastTouchedCounter > OTHER_TYPE_STALE_THRESHOLD) {
            val lastPoint = currentPrimary.chain.points.lastOrNull()
            val staleButNearby = lastPoint != null && gpsPoint.distanceTo(lastPoint) <= MAX_CONTINUITY_DISTANCE
            if (!staleButNearby) {
                primary.remove(movementType)
                bkps.clear()
                missBuffers[movementType]?.clear()
                currentPrimary = null
            }
        }

        if (currentPrimary == null) {
            val count = (pendingSpawn[movementType] ?: 0) + 1
            pendingSpawn[movementType] = count
            if (count >= COMMIT_THRESHOLD) {
                pendingSpawn.remove(movementType)
                spawnFreshPrimary(gpsPoint, movementType, index, now)
            }
            return
        }
        pendingSpawn.remove(movementType)
        currentPrimary.lastTouchedCounter = globalCallCounter

        val (parentMap, distMap) = getReachableCached(currentPrimary, index)

        val bestForPrimary: Segment? = parentMap.keys
            .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
            .filter { isPlausibleDetour(currentPrimary.lastCommitGpsPoint, it, distMap[it] ?: 0.0) }
            .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway, movementType) }

        val distToPrimary = bestForPrimary
            ?.let { pointToSegmentDistance(gpsPoint, it) }
            ?: Double.MAX_VALUE

        val timeStale = now - currentPrimary.timestamp > LIVE_GAP_THRESHOLD_MS  // Only time WITH spatial gap implausible

        if (distToPrimary < MAX_CONTINUITY_DISTANCE) {
            missBuffers[movementType]?.clear()
            currentPrimary.missCount = 0
            currentPrimary.pointCount++
            currentPrimary.score += distToPrimary
            currentPrimary.timestamp = now

            if (bestForPrimary === currentPrimary.previousSegment) {
                // Hold the current segment instead of oscillating
                currentPrimary.pendingSegment = null
                currentPrimary.pendingCount = 0
            } else if (bestForPrimary !== currentPrimary.lastSegment) {
                if (bestForPrimary !== currentPrimary.pendingSegment) {
                    currentPrimary.pendingSegment = bestForPrimary
                    currentPrimary.pendingCount = 0
                }
                currentPrimary.pendingCount++
                if (currentPrimary.pendingCount >= COMMIT_THRESHOLD) {
                    extendChainWithPath(
                        currentPrimary.chain, parentMap,
                        bestForPrimary!!, currentPrimary.chain.points.last()
                    )
                    currentPrimary.previousSegment = currentPrimary.lastSegment
                    currentPrimary.lastSegment = bestForPrimary!!
                    currentPrimary.lastCommitGpsPoint = gpsPoint
                    currentPrimary.chain.dirty = true
                    currentPrimary.pendingSegment = null
                    currentPrimary.pendingCount = 0
                }
            } else {
                currentPrimary.pendingSegment = null
                currentPrimary.pendingCount = 0

                val chainLast = currentPrimary.chain.points.lastOrNull()
                if (chainLast != null) {
                    val far = if (chainLast.distanceTo(bestForPrimary!!.startingPoint) < chainLast.distanceTo(bestForPrimary.endingPoint))
                        bestForPrimary.endingPoint else bestForPrimary.startingPoint
                    // On a long segment, GPS jitter can briefly make the endpoint we just came
                    // from look closer than the one we're heading to - naively comparing only
                    // gpsPoint's distance to "far" vs chainLast would then backtrack onto a
                    // point already visited, drawing a real there-and-back spike on the segment.
                    val cameFrom = currentPrimary.chain.points.getOrNull(currentPrimary.chain.points.size - 2)
                    val wouldBacktrack = cameFrom != null && cameFrom.distanceTo(far) < 0.00002
                    if (!wouldBacktrack && chainLast.distanceTo(far) > 0.00001 && gpsPoint.distanceTo(far) < gpsPoint.distanceTo(chainLast)) {
                        currentPrimary.chain.points.addLast(far)
                        currentPrimary.chain.dirty = true
                    }
                }
            }

            if (bkps.size < MAX_BACKUPS) {
                val directConnected = index.connectedSegments(currentPrimary.lastSegment).toSet()
                val alternatives = directConnected
                    .filter { seg ->
                        seg !== bestForPrimary &&
                                pointToSegmentDistance(gpsPoint, seg) < MAX_CONTINUITY_DISTANCE * 1.5 &&
                                bkps.none { it.lastSegment === seg }
                    }
                    .sortedBy { pointToSegmentDistance(gpsPoint, it) }
                    .take(MAX_BACKUPS - bkps.size)

                for (alt in alternatives) {
                    val backupChain = PathChain(
                        id = nextChainId++, movementType = movementType,
                        points = ArrayDeque(currentPrimary.chain.points.toList()), dirty = true
                    )
                    extendChain(backupChain, alt, backupChain.points.last())
                    bkps.add(PathHypothesis(
                        id = nextHypId++, chain = backupChain, lastSegment = alt,
                        timestamp = now, lastCommitGpsPoint = gpsPoint, lastTouchedCounter = globalCallCounter,
                        score = currentPrimary.score, pointCount = currentPrimary.pointCount
                    ))
                }
            }

            bkps.removeAll { backup ->
                getReachableCached(backup, index).first.keys
                    .none { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE * 2.0 }
            }

        } else if (timeStale) {
            // Unresolvable gap (spatially discontinuous + primary not updated fora  while) => spawn fresh immediately
            primary.remove(movementType)
            bkps.clear()
            missBuffers[movementType]?.clear()
            spawnFreshPrimary(gpsPoint, movementType, index, now)
            return
        } else {
            currentPrimary.missCount++
            currentPrimary.pendingSegment = null
            currentPrimary.pendingCount = 0

            // Three or more independently plausible misses in a row means a coherent new trajectory has already formed
            val missBuffer = missBuffers.getOrPut(movementType) { mutableListOf() }
            val independentCandidate = index.nearbySegments(gpsPoint.latitude, gpsPoint.longitude)
                .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
                .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway, movementType) }
            if (independentCandidate != null) {
                val lastBuffered = missBuffer.lastOrNull()
                if (lastBuffered != null && gpsPoint.distanceTo(lastBuffered.point) >= MISS_BUFFER_CONSISTENCY_DISTANCE) {
                    missBuffer.clear()
                }
                missBuffer.add(MissBufferEntry(gpsPoint, now))
            } else {
                missBuffer.clear()
            }

            if (missBuffer.size >= MISS_BUFFER_TRIGGER_SIZE) {
                val first = missBuffer.first()
                val rest = missBuffer.drop(1)
                missBuffer.clear()
                primary.remove(movementType)
                bkps.clear()
                spawnFreshPrimary(first.point, movementType, index, first.timestamp)
                for (entry in rest) {
                    onGpsPoint(entry.point, movementType, index, entry.timestamp)
                }
                return
            }

            if (currentPrimary.missCount >= HARD_RESET_THRESHOLD) {
                primary.remove(movementType)

                bkps.clear()
                spawnFreshPrimary(gpsPoint, movementType, index, now)
                return
            }

            val closeReanchor = index.nearbySegments(gpsPoint.latitude, gpsPoint.longitude)
                .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
                .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway, movementType) }

            if (closeReanchor != null && closeReanchor !== currentPrimary.previousSegment) {
                val lastPoint = currentPrimary.chain.points.last()
                val jumpDist = minOf(
                    lastPoint.distanceTo(closeReanchor.startingPoint),
                    lastPoint.distanceTo(closeReanchor.endingPoint)
                )
                if (jumpDist <= MAX_CONTINUITY_DISTANCE) {
                    extendChain(currentPrimary.chain, closeReanchor, lastPoint)
                    currentPrimary.previousSegment = currentPrimary.lastSegment
                    currentPrimary.lastSegment = closeReanchor
                    currentPrimary.lastCommitGpsPoint = gpsPoint
                    currentPrimary.missCount = 0
                    modifiedMovementTypes.add(movementType)
                    onChainsChanged?.invoke()
                    return
                }
            }

            for (backup in bkps) {
                val (bParentMap, bDistMap) = getReachableCached(backup, index)

                val bestForBackup: Segment? = bParentMap.keys
                    .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
                    .filter { isPlausibleDetour(backup.lastCommitGpsPoint, it, bDistMap[it] ?: 0.0) }
                    .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway, movementType) }
                if (bestForBackup != null) {
                    backup.pointCount++
                    backup.score += pointToSegmentDistance(gpsPoint, bestForBackup)
                    backup.timestamp = now

                    if (bestForBackup !== backup.lastSegment && bestForBackup !== backup.previousSegment) {
                        extendChainWithPath(backup.chain, bParentMap, bestForBackup, backup.chain.points.last())
                        backup.previousSegment = backup.lastSegment
                        backup.lastSegment = bestForBackup
                        backup.lastCommitGpsPoint = gpsPoint
                    }
                    backup.missCount = 0
                } else {
                    backup.missCount++
                }
            }

            bkps.removeAll { it.missCount >= MISS_THRESHOLD }

            if (currentPrimary.missCount >= MISS_THRESHOLD) {
                val bestBackup = bkps.filter { it.pointCount > 0 }
                    .minByOrNull { it.score / it.pointCount }
                if (bestBackup != null) {
                    removePrimary(movementType)
                    bkps.remove(bestBackup)
                    chains[movementType]?.add(bestBackup.chain)
                    primary[movementType] = bestBackup
                    bestBackup.missCount = 0
                } else {
                    val reanchor = index.nearbySegments(gpsPoint.latitude, gpsPoint.longitude)
                        .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
                        .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway, movementType) }
                    if (reanchor != null && reanchor !== currentPrimary.previousSegment) {
                        val lastPoint = currentPrimary.chain.points.last()
                        val jumpDist = minOf(
                            lastPoint.distanceTo(reanchor.startingPoint),
                            lastPoint.distanceTo(reanchor.endingPoint)
                        )
                        if (jumpDist <= MAX_CONTINUITY_DISTANCE) {
                            extendChain(currentPrimary.chain, reanchor, lastPoint)
                            currentPrimary.previousSegment = currentPrimary.lastSegment
                            currentPrimary.lastSegment = reanchor
                            currentPrimary.lastCommitGpsPoint = gpsPoint
                            currentPrimary.missCount = 0
                            currentPrimary.pendingSegment = null
                            currentPrimary.pendingCount = 0
                        } else {
                            primary.remove(movementType)
                            bkps.clear()
                            spawnFreshPrimary(gpsPoint, movementType, index, now)
                            return
                        }
                    }
                }
            }
        }

        modifiedMovementTypes.add(movementType)
        onChainsChanged?.invoke()
    }


    private fun getReachableSegmentsWithPath(start: Segment, index: SegmentIndex): Pair<Map<Segment, Segment?>, Map<Segment, Double>> {
        /*
        Returns both the parent chain (for path reconstruction) and the shortest cumulative path
        distance to each reachable segment. Uses Dijkstra (priority queue), where edges are weighted
        by segment length.
        */
        val parentMap = mutableMapOf<Segment, Segment?>()
        val distMap = mutableMapOf<Segment, Double>()
        val queue = java.util.PriorityQueue<Pair<Segment, Double>>(compareBy { it.second })
        parentMap[start] = null
        distMap[start] = 0.0
        queue.add(Pair(start, 0.0))
        while (queue.isNotEmpty()) {
            val (seg, dist) = queue.poll()!!
            if (dist > distMap[seg]!!) continue
            if (dist > BFS_SEARCH_DISTANCE) continue
            val segLen = seg.startingPoint.distanceTo(seg.endingPoint)
            for (connected in index.connectedSegments(seg)) {
                val newDist = dist + segLen
                if (newDist < (distMap[connected] ?: Double.MAX_VALUE)) {
                    distMap[connected] = newDist
                    parentMap[connected] = seg
                    queue.add(Pair(connected, newDist))
                }
            }
        }
        return parentMap to distMap
    }

    private fun getReachableCached(hyp: PathHypothesis, index: SegmentIndex): Pair<Map<Segment, Segment?>, Map<Segment, Double>> {
        /* Caches getReachableSegmentsWithPath() per hypothesis, reused as long as its lastSegment hasn't changed. */
        if (hyp.cachedForSegment === hyp.lastSegment && hyp.cachedReachable != null && hyp.cachedDistances != null) {
            return hyp.cachedReachable!! to hyp.cachedDistances!!
        }
        val (parentMap, distMap) = getReachableSegmentsWithPath(hyp.lastSegment, index)
        hyp.cachedReachable = parentMap
        hyp.cachedDistances = distMap
        hyp.cachedForSegment = hyp.lastSegment
        return parentMap to distMap
    }


    private fun isPlausibleDetour(fromPoint: LatLong, candidate: Segment, pathDist: Double): Boolean {
        /*
        Rejects candidate segments whose road-network path is unreasonably long compared to the
        straight-line distance (i.e. probably the wrong road entirely, not just a detour).
        */
        val straightLine = minOf(
            fromPoint.distanceTo(candidate.startingPoint),
            fromPoint.distanceTo(candidate.endingPoint)
        )
        return pathDist <= straightLine * 3.0 + 0.0001
    }

    private fun removePrimary(movementType: MovementType) {
        /* Discards the current primary hypothesis and its chain (e.g. before replacing it with a backup or a fresh spawn). */
        val pri = primary.remove(movementType) ?: return
        chains[movementType]?.remove(pri.chain)
        tombstoneIfLogged(pri.chain, movementType)
        onChainRemoved?.invoke(pri.chain.id)
    }

    private fun tombstoneIfLogged(chain: PathChain, movementType: MovementType) {
        /*
        Called whenever a chain is dropped/absorbed outside the normal per-tick save() flow
        (a stale hypothesis, or mergeChainsByType() folding it into another chain). If it had
        already reached the log/checkpoint, a bare removal from `chains` isn't enough - a
        crash before the next checkpoint would otherwise replay the log and resurrect it.
        */
        if (chain.loggedPointCount > 0) {
            pendingLogEntries.add(LogAppend(chainId = chain.id, movementType = movementType, removed = true))
        }
    }

    /*
    Extra "cost" added to a candidate segment based on how suitable its road type is for the
    given movement type, used to break ties when several segments are similarly close to a
    GPS point (e.g. prefer a footway over a trunk road when walking).
    */
    private fun highwayPenalty(highway: String, movementType: MovementType): Double = when (movementType) {
        MovementType.WALKING, MovementType.RUNNING -> when (highway) {
            "footway", "pedestrian", "path", "track", "living_street"          -> 0.0
            "residential", "unclassified"                                       -> 0.0001
            "service", "secondary", "secondary_link",
            "tertiary", "tertiary_link"                                         -> 0.0002
            "trunk", "trunk_link", "primary", "primary_link", "steps"          -> 0.0005
            "rail", "light_rail", "tram", "subway"                             -> 0.001
            else                                                                -> 0.0001
        }
        MovementType.BIKING -> when (highway) {
            "cycleway", "path", "track"                                         -> 0.0
            "residential", "living_street", "unclassified"                     -> 0.0001
            "secondary", "secondary_link", "tertiary", "tertiary_link"         -> 0.0002
            "trunk", "trunk_link", "primary", "primary_link"                   -> 0.0005
            "footway", "pedestrian", "steps"                                   -> 0.001
            "rail", "light_rail", "tram", "subway"                             -> 0.001
            else                                                                -> 0.0001
        }
        else -> when (highway) {
            "trunk", "trunk_link", "primary", "primary_link",
            "secondary", "secondary_link", "tertiary", "tertiary_link",
            "rail", "light_rail", "tram", "subway"                             -> 0.0
            "residential", "unclassified", "living_street"                     -> 0.0001
            "footway", "path", "track", "pedestrian", "steps"                  -> 0.0002
            "service"                                                           -> 0.0005
            else                                                                -> 0.0001
        }
    }

    /* ── Finalization ── */

    @Synchronized
    fun clearSegments() {
        for (movementType in MovementType.entries) {
            chains[movementType]?.forEach { onChainRemoved?.invoke(it.id) }
            chains[movementType]?.clear()
        }
        modifiedMovementTypes.clear()
        primary.clear()
        backups.clear()
        onChainsChanged?.invoke()
    }

    @Synchronized
    fun finalizeSession() {
        /*
        Called when tracking stops (or the app is closing). Bridges any remaining gaps
        between chains of different movement types, then drops all in-progress
        hypotheses; from this point on the stored chains are considered final until a
        new GPS point starts tracking again.
        */
        val index = AppSegmentIndex.instance
        bridgeMovementTypeGaps(index)
        primary.clear()
        backups.clear()
        modifiedMovementTypes.clear()
    }

    @Synchronized
    fun mergeChainsByType() {
        /*
        Fuses chains of the same movement type whose endpoints land in the same rounded
        lat/lon "bucket" (within `threshold`) into one continuous chain, repeatedly, until
        no more merges are possible. Cleans up chains that got split apart during live
        tracking but actually represent one continuous walk/ride.
        */
        for (movementType in MovementType.entries) {
            val chainList = chains[movementType] ?: continue
            if (chainList.size <= 1) continue

            val threshold = MAX_CONTINUITY_DISTANCE
            fun quantize(v: Double) = (v / threshold).toLong()
            fun LatLong.key() = Pair(quantize(latitude), quantize(longitude))

            data class Slot(val chain: PathChain, val isHead: Boolean)

            fun buildIndex(): HashMap<Pair<Long, Long>, Slot> {
                val idx = HashMap<Pair<Long, Long>, Slot>()
                for (chain in chainList) {
                    if (chain.points.isEmpty()) continue
                    idx[chain.points.first().key()] = Slot(chain, true)
                    idx[chain.points.last().key()]  = Slot(chain, false)
                }
                return idx
            }

            fun lookup(idx: HashMap<Pair<Long, Long>, Slot>, key: Pair<Long, Long>, exclude: PathChain): Slot? {
                for (dx in -1..1) for (dy in -1..1) {
                    val s = idx[Pair(key.first + dx, key.second + dy)]
                    if (s != null && s.chain !== exclude) return s
                }
                return null
            }

            var merged = true
            while (merged) {
                merged = false
                val idx = buildIndex()

                for (chain in chainList.toList()) {
                    if (!chainList.contains(chain)) continue

                    val tailSlot = lookup(idx, chain.points.last().key(), chain)
                    if (tailSlot != null) {
                        val b = tailSlot.chain
                        if (tailSlot.isHead) b.points.forEach { chain.points.addLast(it) }
                        else b.points.reversed().forEach { chain.points.addLast(it) }
                        chainList.remove(b)
                        tombstoneIfLogged(b, movementType)
                        onChainRemoved?.invoke(b.id)
                        chain.dirty = true
                        merged = true; break
                    }

                    val headSlot = lookup(idx, chain.points.first().key(), chain)
                    if (headSlot != null) {
                        val b = headSlot.chain
                        if (!headSlot.isHead) b.points.reversed().forEach { chain.points.addFirst(it) }
                        else b.points.forEach { chain.points.addFirst(it) }
                        chainList.remove(b)
                        tombstoneIfLogged(b, movementType)
                        onChainRemoved?.invoke(b.id)
                        chain.dirty = true
                        merged = true; break
                    }
                }
            }
        }
        onChainsChanged?.invoke()
    }

    private data class BridgeCandidate(
        val dist: Double,
        val otherPoint: LatLong,
        val otherChain: PathChain,
        val chainPointIdx: Int
    )

    private enum class BridgeDirection { HEAD, TAIL }
    private val BRIDGE_ID_WINDOW = 3

    private fun findBestBridgeCandidate(
        chain: PathChain,
        candidateTypes: List<MovementType>,
        chainPointsToCheck: List<Pair<Int, LatLong>>,
        direction: BridgeDirection,
        excludeChain: PathChain? = null
    ): BridgeCandidate? {
        /*
        Finds the closest point, among a small window of chains near this chain's ID
        (i.e. recorded around the same time), that this chain's head/tail could connect
        to — used to stitch together chains that were split because the movement type
        briefly changed (e.g. walking -> waiting for a tram -> walking again).
        */
        val candidates = candidateTypes.flatMap { chains[it] ?: emptyList() }
            .filter { it !== chain && it !== excludeChain && it.points.size >= 2 }
        val nearestChains = when (direction) {
            BridgeDirection.HEAD -> candidates.filter { it.id < chain.id }.sortedByDescending { it.id }
            BridgeDirection.TAIL -> candidates.filter { it.id > chain.id }.sortedBy { it.id }
        }.take(BRIDGE_ID_WINDOW)

        var best: BridgeCandidate? = null
        for (otherChain in nearestChains) {
            for (otherPoint in otherChain.points) {
                for ((idx, chainPoint) in chainPointsToCheck) {
                    val dist = otherPoint.distanceTo(chainPoint)
                    if (best == null || dist < best!!.dist) {
                        best = BridgeCandidate(dist, otherPoint, otherChain, idx)
                    }
                }
            }
        }
        return best
    }

    internal fun bridgeMovementTypeGaps(index: SegmentIndex?) {
        /*
        For every chain, tries to close the gap at its head and tail by connecting it to
        the nearest plausible chain of a different movement type, falling back to a chain of
        the same type if that's a better/only match (e.g. a signal dropout mid-walk, with no
        other-type chain nearby to bridge to at all). Prefers routing the bridge along the
        actual road network (findRoadPath); if no reasonable road path exists but the gap is small, falls back
        to just drawing a straight line between the two points.
        */
        val threshold = 0.0015
        val straightLineFallbackThreshold = threshold

        for (movementType in MovementType.entries) {
            val typeChains = chains[movementType] ?: continue
            val crossTypes = MovementType.entries.filter { it != movementType }
            for (chain in typeChains) {
                if (chain.points.size < 2) continue

                val headCheckPoints = (0 until minOf(5, chain.points.size)).map { it to chain.points[it] }
                var headCandidate = findBestBridgeCandidate(chain, crossTypes, headCheckPoints, BridgeDirection.HEAD)

                if (headCandidate == null || headCandidate.dist !in 0.00002..threshold) {
                    headCandidate = findBestBridgeCandidate(chain, listOf(movementType), headCheckPoints, BridgeDirection.HEAD)
                }

                val bestDist = headCandidate?.dist ?: Double.MAX_VALUE
                val bestOtherPoint = headCandidate?.otherPoint
                val bestOtherChain = headCandidate?.otherChain
                val bestChainPointIdx = headCandidate?.chainPointIdx ?: 0

                Log.d("BRIDGE", "$movementType bestDist=$bestDist bestChainIdx=$bestChainPointIdx")
                val headBridged = bestDist in 0.00002..threshold && bestOtherPoint != null

                if (headBridged) {
                    // Remove points before bestChainPointIdx
                    repeat(bestChainPointIdx) { chain.points.removeFirst() }

                    if (index != null) {
                        var bridgePath = findRoadPath(bestOtherPoint!!, chain.points.first(), index)
                        if (bridgePath != null && !isPlausibleBridgePath(bestDist, bridgePath)) {
                            bridgePath = null
                        }
                        if (bridgePath != null && bridgePath.isNotEmpty()) {
                            var lastPoint: LatLong = bestOtherPoint
                            val pointsToAdd = mutableListOf<LatLong>()

                            pointsToAdd.add(bestOtherPoint)  // ← explicitly anchor to junction point

                            for ((segIdx, seg) in bridgePath.withIndex()) {
                                val s = seg.startingPoint
                                val e = seg.endingPoint
                                if (segIdx == bridgePath.size - 1) {
                                    val target = chain.points.first()
                                    val nearer = if (s.distanceTo(target) <= e.distanceTo(target)) s else e
                                    if (lastPoint.distanceTo(nearer) > 0.00001) pointsToAdd.add(nearer)
                                    lastPoint = nearer
                                } else if (lastPoint.distanceTo(s) <= lastPoint.distanceTo(e)) {
                                    if (lastPoint.distanceTo(s) > 0.00001) pointsToAdd.add(s)
                                    pointsToAdd.add(e)
                                    lastPoint = e
                                } else {
                                    if (lastPoint.distanceTo(e) > 0.00001) pointsToAdd.add(e)
                                    pointsToAdd.add(s)
                                    lastPoint = s
                                }
                            }

                            if (lastPoint.distanceTo(chain.points.first()) > 0.00002) {
                                pointsToAdd.add(chain.points.first())
                                chain.points.removeFirst()
                            }

                            for (pt in pointsToAdd.reversed()) chain.points.addFirst(pt)
                            Log.d("BRIDGE", "Road bridged with ${pointsToAdd.size} points, starts at ${chain.points.first()}")
                        } else if (bestDist <= straightLineFallbackThreshold) {
                            chain.points.addFirst(bestOtherPoint)
                            Log.d("BRIDGE", "Straight line bridge")
                        }
                    }
                    chain.dirty = true
                }

                /*
                Symmetric pass: the above only ever pulls a chain's HEAD backward to meet
                something earlier. Uses only the literal last point
                 */
                val tailPoint = chain.points.last()
                val excludeChain = if (headBridged) bestOtherChain else null
                val tailCheckPoints = listOf(0 to tailPoint)

                var tailCandidate = findBestBridgeCandidate(chain, crossTypes, tailCheckPoints, BridgeDirection.TAIL, excludeChain)
                if (tailCandidate == null || tailCandidate.dist !in 0.00002..threshold) {
                    tailCandidate = findBestBridgeCandidate(chain, listOf(movementType), tailCheckPoints, BridgeDirection.TAIL, excludeChain)
                }
                val bestTailDist = tailCandidate?.dist ?: Double.MAX_VALUE
                val bestTailOtherPoint = tailCandidate?.otherPoint

                if (bestTailDist in 0.00002..threshold && bestTailOtherPoint != null) {
                    if (index != null) {
                        var tailBridgePath = findRoadPath(chain.points.last(), bestTailOtherPoint, index)
                        if (tailBridgePath != null && !isPlausibleBridgePath(bestTailDist, tailBridgePath)) {
                            tailBridgePath = null
                        }
                        if (tailBridgePath != null && tailBridgePath.isNotEmpty()) {
                            var lastPoint: LatLong = chain.points.last()
                            for ((segIdx, seg) in tailBridgePath.withIndex()) {
                                val s = seg.startingPoint
                                val e = seg.endingPoint
                                if (segIdx == tailBridgePath.size - 1) {
                                    // Same overshoot guard as the head bridge above - only add
                                    // whichever endpoint is actually closer to the target.
                                    val nearer = if (s.distanceTo(bestTailOtherPoint) <= e.distanceTo(bestTailOtherPoint)) s else e
                                    if (lastPoint.distanceTo(nearer) > 0.00001) chain.points.addLast(nearer)
                                    lastPoint = nearer
                                } else if (lastPoint.distanceTo(s) <= lastPoint.distanceTo(e)) {
                                    if (lastPoint.distanceTo(s) > 0.00001) chain.points.addLast(s)
                                    chain.points.addLast(e)
                                    lastPoint = e
                                } else {
                                    if (lastPoint.distanceTo(e) > 0.00001) chain.points.addLast(e)
                                    chain.points.addLast(s)
                                    lastPoint = s
                                }
                            }
                            if (lastPoint.distanceTo(bestTailOtherPoint) > 0.00002) {
                                chain.points.addLast(bestTailOtherPoint)
                            }
                            Log.d("BRIDGE", "Tail road bridged, ends at ${chain.points.last()}")
                        } else if (bestTailDist <= straightLineFallbackThreshold) {
                            chain.points.addLast(bestTailOtherPoint)
                            Log.d("BRIDGE", "Tail straight line bridge")
                        }
                    }
                    chain.dirty = true
                }
            }
        }
    }

    private fun findRoadPath(from: LatLong, to: LatLong, index: SegmentIndex): List<Segment>? {
        /*
        BFS over the segment graph from the segment nearest `from` to the one nearest `to`, capped
        at 3x the straight-line distance so it doesn't wander arbitrarily far looking for a connection.
        `from`/`to` are historical chain bridge points, not necessarily near wherever the live/replay
        window currently is, so the windowed index needs an explicit nudge to page segments in around
        them before it can find anything nearby.
        */
        index.ensureLoaded(from.latitude, from.longitude, "finalize")
        index.ensureLoaded(to.latitude, to.longitude, "finalize")

        val fromSeg = index.nearbySegments(from.latitude, from.longitude)
            .minByOrNull { pointToSegmentDistance(from, it) } ?: return null
        val toSeg = index.nearbySegments(to.latitude, to.longitude)
            .minByOrNull { pointToSegmentDistance(to, it) } ?: return null

        if (fromSeg === toSeg) return listOf(toSeg)

        val directDist  = from.distanceTo(to)
        val maxRoadDist = directDist * 3.0

        val parentMap = mutableMapOf<Segment, Segment?>()
        val queue     = ArrayDeque<Pair<Segment, Double>>()
        parentMap[fromSeg] = null
        queue.add(Pair(fromSeg, 0.0))

        while (queue.isNotEmpty()) {
            val (current, dist) = queue.removeFirst()
            if (dist > maxRoadDist) continue
            if (current === toSeg) {
                val path = mutableListOf<Segment>()
                var seg: Segment? = current
                while (seg != null && parentMap[seg] != null) {
                    path.add(0, seg)
                    seg = parentMap[seg]
                }
                Log.d("BRIDGE", "findRoadPath: found ${path.size} segments")
                return path
            }
            val segLen = current.startingPoint.distanceTo(current.endingPoint)
            for (neighbor in index.connectedSegments(current)) {
                if (neighbor !in parentMap) {
                    parentMap[neighbor] = current
                    queue.add(Pair(neighbor, dist + segLen))
                }
            }
        }
        Log.d("BRIDGE", "findRoadPath: no path found")
        return null
    }

    private fun isPlausibleBridgePath(straightDist: Double, path: List<Segment>): Boolean {
        /*
        Sanity-checks a road-network bridge path isn't a wild detour compared to the direct distance
        it's supposed to cover.
        */
        if (straightDist <= 0.0) return true
        val pathLen = path.sumOf { it.startingPoint.distanceTo(it.endingPoint) }
        val ratio = pathLen / straightDist
        val extra = pathLen - straightDist
        return ratio <= 1.5 || extra <= 0.0018
    }

    /* ── Persistence ── */

    @Synchronized
    fun save(context: Context) {
        /*
        Cheap, frequent persistence for live tracking: appends only what changed since the
        last save/checkpoint (new points on a chain's tail, or a chain being dropped) to
        LOG_FILE_NAME, instead of re-serializing the entire lifetime history every tick.

        Falls back to a full checkpoint() if a chain's point count ever drops below what's
        already been logged - that only happens via the revisit-trim cleanup in
        extendChainWithPath() or a chain merge, neither of which is a pure tail-append and
        so can't be represented as one.
        */
        var needsCheckpoint = false

        synchronized(this) {
            for ((movementType, list) in chains) {
                for (chain in list) {
                    val points = synchronized(chain) { chain.points.toList() }
                    when {
                        points.size > chain.loggedPointCount -> {
                            pendingLogEntries.add(
                                LogAppend(
                                    chainId = chain.id,
                                    movementType = movementType,
                                    newPoints = points.subList(chain.loggedPointCount, points.size)
                                        .map { StoredPoint(it.latitude, it.longitude) }
                                )
                            )
                            chain.loggedPointCount = points.size
                        }
                        points.size < chain.loggedPointCount -> needsCheckpoint = true
                    }
                }
            }
        }

        if (needsCheckpoint) {
            checkpoint(context)
            return
        }

        if (pendingLogEntries.isEmpty()) return

        val logFile = File(context.filesDir, LOG_FILE_NAME)
        logFile.appendText(pendingLogEntries.joinToString("") { json.encodeToString(it) + "\n" })
        pendingLogEntries.clear()
    }

    @Synchronized
    fun checkpoint(context: Context) {
        /*
        The expensive, infrequent full save: re-serializes every chain into the canonical
        walked_chains.json snapshot, then clears the append log since everything in it is
        now folded into that snapshot. Call this at natural "safe to consolidate" points
        (session end, app foreground) rather than on every GPS tick.

        Writes to a temp file and renames it over the real file, so a crash/kill mid-write
        can't leave a corrupted save.
        */
        val snapshot = synchronized(this) {
            chains.entries.map { (movementType, list) ->
                StoredMovementEntry(
                    movementType = movementType,
                    chains = list.map { chain ->
                        val points = synchronized(chain) { chain.points.toList() }
                        chain.loggedPointCount = points.size
                        StoredPathChain(
                            id = chain.id,
                            movementType = chain.movementType,
                            points = points.map { StoredPoint(it.latitude, it.longitude) }
                        )
                    }
                )
            }
        }
        val text = json.encodeToString(StoredChainCollection(snapshot))
        val file = File(context.filesDir, FILE_NAME)
        val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
        tmpFile.writeText(text)
        if (file.exists()) file.delete()
        tmpFile.renameTo(file)

        File(context.filesDir, LOG_FILE_NAME).delete()
        pendingLogEntries.clear()
    }

    @Synchronized
    fun load(context: Context) {
        /*
        Loads chains from the last checkpoint, then replays any append-log entries on top
        of it (the durability window since that checkpoint; e.g. the process was killed
        before the next one ran). If the checkpoint file is unreadable/corrupt, deletes it.
        */
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        try {
            val text = file.readText()
            val collection = json.decodeFromString<StoredChainCollection>(text)
            clearSegments()
            val chainsById = HashMap<Long, PathChain>()
            for (entry in collection.entries) {
                for (stored in entry.chains) {
                    val chain = PathChain(
                        id = stored.id,
                        movementType = stored.movementType,
                        points = ArrayDeque(stored.points.map { LatLong(it.lat, it.lon) }),
                        dirty = true
                    ).also { it.loggedPointCount = it.points.size }
                    chains[entry.movementType]?.add(chain)
                    chainsById[chain.id] = chain
                }
            }
            replayLog(context, chainsById)
            nextChainId = chains.values.flatten().maxOfOrNull { it.id + 1 } ?: 0L
            onChainsChanged?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            deleteSaved(context)
        }
    }

    private fun replayLog(context: Context, chainsById: MutableMap<Long, PathChain>) {
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        if (!logFile.exists()) return
        try {
            logFile.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val entry = json.decodeFromString<LogAppend>(line)
                if (entry.removed) {
                    chainsById.remove(entry.chainId)?.let { chain -> chains[entry.movementType]?.remove(chain) }
                    return@forEachLine
                }
                val chain = chainsById.getOrPut(entry.chainId) {
                    PathChain(id = entry.chainId, movementType = entry.movementType, points = ArrayDeque(), dirty = true)
                        .also { chains[entry.movementType]?.add(it) }
                }
                entry.newPoints.forEach { chain.points.addLast(LatLong(it.lat, it.lon)) }
                chain.loggedPointCount = chain.points.size
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logFile.delete()
        }
    }

    fun deleteSaved(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        File(context.filesDir, "$FILE_NAME.tmp").delete()
        File(context.filesDir, LOG_FILE_NAME).delete()
        pendingLogEntries.clear()
    }
}


/* HELPER FUNCTIONS */

fun LatLong.distanceTo(other: LatLong): Double {
    /*
    Flat Euclidean distance in raw degrees. Only valid for comparing distances at roughly the same latitude.
    */
    val dx = longitude - other.longitude
    val dy = latitude  - other.latitude
    return Math.sqrt(dx * dx + dy * dy)
}
