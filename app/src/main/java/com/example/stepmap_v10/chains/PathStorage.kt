package com.example.stepmap_v10.chains

import com.example.stepmap_v10.paths.Segment
import com.example.stepmap_v10.paths.SegmentIndex
import com.example.stepmap_v10.paths.pointToSegmentDistance
import com.example.stepmap_v10.tracking.MovementType
import org.mapsforge.core.model.LatLong
import android.content.Context
import android.util.Log
import com.example.stepmap_v10.tracking.AppSegmentIndex
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

    private val COMMIT_THRESHOLD = 2

    var onChainRemoved: ((Long) -> Unit)? = null
    var onChainsChanged: (() -> Unit)? = null
    var lastActiveMovementType: MovementType = MovementType.TRANSPORT

    var isReplaying = false
    private val bufferedPoints = mutableListOf<Triple<LatLong, MovementType, Long>>()

    private val FILE_NAME = "walked_chains.json"
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }


    /* ── GPS entry point ── */

    private fun spawnFreshPrimary(gpsPoint: LatLong, movementType: MovementType, index: SegmentIndex, now: Long) {
        val nearest = index.nearbySegments(gpsPoint.latitude, gpsPoint.longitude)
            .filter { pointToSegmentDistance(gpsPoint, it) < 0.0006 }
            .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway) }
            ?: return

        val distToStart = gpsPoint.distanceTo(nearest.startingPoint)
        val distToEnd = gpsPoint.distanceTo(nearest.endingPoint)
        val (first, second) = if (distToStart <= distToEnd)
            Pair(nearest.startingPoint, nearest.endingPoint)
        else
            Pair(nearest.endingPoint, nearest.startingPoint)

        val chain = PathChain(
            id = nextChainId++,
            movementType = movementType,
            points = ArrayDeque(listOf(first, second)),
            dirty = true
        )
        chains[movementType]?.add(chain)
        val hyp = PathHypothesis(id = nextHypId++, chain = chain, lastSegment = nearest, timestamp = now)
        primary[movementType] = hyp
        modifiedMovementTypes.add(movementType)
        onChainsChanged?.invoke()
    }

    private fun extendChainWithPath(chain: PathChain, parentMap: Map<Segment, Segment?>, target: Segment, lastPointIn: LatLong) {
        val path = mutableListOf<Segment>()
        var current: Segment? = target
        while (current != null && parentMap[current] != null) {
            path.add(0, current)
            current = parentMap[current]
        }

        var lastPoint = lastPointIn
        for (seg in path) {
            val s = seg.startingPoint
            val e = seg.endingPoint
            val appendStart = lastPoint.distanceTo(s) <= lastPoint.distanceTo(e)
            if (appendStart) {
                if (lastPoint.distanceTo(s) > 0.00001) chain.points.addLast(s)
                chain.points.addLast(e)
                lastPoint = e
            } else {
                if (lastPoint.distanceTo(e) > 0.00001) chain.points.addLast(e)
                chain.points.addLast(s)
                lastPoint = s
            }

            while (chain.points.size >= 3) {
                val n = chain.points.size
                if (chain.points[n - 1].distanceTo(chain.points[n - 3]) < 0.00002) {
                    chain.points.removeAt(n - 1)
                    chain.points.removeAt(n - 2)
                    if (chain.points.isNotEmpty()) lastPoint = chain.points.last()
                } else break
            }

            if (chain.points.size >= 4) {
                val last = chain.points.last()
                val searchFrom = maxOf(0, chain.points.size - 8)
                val revisitIdx = (searchFrom until chain.points.size - 1)
                    .lastOrNull { chain.points[it].distanceTo(last) < 0.00002 }
                if (revisitIdx != null) {
                    while (chain.points.size > revisitIdx + 1) chain.points.removeLast()
                    if (chain.points.isNotEmpty()) lastPoint = chain.points.last()
                }
            }
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
    fun onGpsPoint(gpsPoint: LatLong, movementType: MovementType, index: SegmentIndex, isReplayPoint: Boolean = false) {
        if (movementType == MovementType.STILL) return

        lastActiveMovementType = movementType
        val now = System.currentTimeMillis()

        if(isReplaying && !isReplayPoint){
            bufferedPoints.add(Triple(gpsPoint, lastActiveMovementType, now))
            return
        }

        val bkps = backups.getOrPut(movementType) { mutableListOf() }

        if (primary[movementType]?.let { now - it.timestamp > LIVE_GAP_THRESHOLD_MS } == true) {
            removePrimary(movementType)
        }
        bkps.removeAll { now - it.timestamp > LIVE_GAP_THRESHOLD_MS }

        val currentPrimary = primary[movementType]
        if (currentPrimary == null) {
            spawnFreshPrimary(gpsPoint, movementType, index, now)
            return
        }

        val parentMap = getReachableCached(currentPrimary, index)

        val bestForPrimary: Segment? = parentMap.keys
            .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
            .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway) }

        val distToPrimary = bestForPrimary
            ?.let { pointToSegmentDistance(gpsPoint, it) }
            ?: Double.MAX_VALUE

        if (distToPrimary < MAX_CONTINUITY_DISTANCE) {
            currentPrimary.missCount = 0
            currentPrimary.pointCount++
            currentPrimary.score += distToPrimary
            currentPrimary.timestamp = now

            if (bestForPrimary !== currentPrimary.lastSegment) {
                if (bestForPrimary === currentPrimary.pendingSegment) {
                    currentPrimary.pendingCount++
                    if (currentPrimary.pendingCount >= COMMIT_THRESHOLD) {
                        // Commit — just extend, no history trimming
                        extendChainWithPath(
                            currentPrimary.chain, parentMap,
                            bestForPrimary!!, currentPrimary.chain.points.last()
                        )
                        currentPrimary.lastSegment = bestForPrimary!!
                        currentPrimary.chain.dirty = true
                        currentPrimary.pendingSegment = null
                        currentPrimary.pendingCount = 0
                    }
                } else {
                    currentPrimary.pendingSegment = bestForPrimary
                    currentPrimary.pendingCount = 1
                }
            } else {
                currentPrimary.pendingSegment = null
                currentPrimary.pendingCount = 0
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
                        timestamp = now, score = currentPrimary.score, pointCount = currentPrimary.pointCount
                    ))
                }
            }

            bkps.removeAll { backup ->
                getReachableCached(backup, index).keys
                    .none { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE * 2.0 }
            }

        } else {
            currentPrimary.missCount++
            currentPrimary.pendingSegment = null
            currentPrimary.pendingCount = 0

            if (currentPrimary.missCount >= HARD_RESET_THRESHOLD) {
                primary.remove(movementType)

                bkps.clear()
                spawnFreshPrimary(gpsPoint, movementType, index, now)
                return
            }

            for (backup in bkps) {
                val bParentMap = getReachableCached(backup, index)

                val bestForBackup: Segment? = bParentMap.keys
                    .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
                    .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway) }
                if (bestForBackup != null) {
                    backup.pointCount++
                    backup.score += pointToSegmentDistance(gpsPoint, bestForBackup)
                    backup.timestamp = now
                    if (bestForBackup !== backup.lastSegment) {
                        extendChainWithPath(backup.chain, bParentMap, bestForBackup, backup.chain.points.last())
                        backup.lastSegment = bestForBackup
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
                }
            }
        }

        modifiedMovementTypes.add(movementType)
        onChainsChanged?.invoke()
    }

    private fun getReachableSegmentsWithPath(start: Segment, index: SegmentIndex): Map<Segment, Segment?> {
        val parentMap = mutableMapOf<Segment, Segment?>()
        val queue     = ArrayDeque<Pair<Segment, Double>>()
        parentMap[start] = null
        queue.add(Pair(start, 0.0))
        while (queue.isNotEmpty()) {
            val (seg, dist) = queue.removeFirst()
            if (dist > BFS_SEARCH_DISTANCE) continue
            val segLen = seg.startingPoint.distanceTo(seg.endingPoint)
            for (connected in index.connectedSegments(seg)) {
                if (connected !in parentMap) {
                    parentMap[connected] = seg
                    queue.add(Pair(connected, dist + segLen))
                }
            }
        }
        return parentMap
    }

    private fun getReachableCached(hyp: PathHypothesis, index: SegmentIndex): Map<Segment, Segment?> {
        if (hyp.cachedForSegment === hyp.lastSegment && hyp.cachedReachable != null) {
            return hyp.cachedReachable!!
        }
        val result = getReachableSegmentsWithPath(hyp.lastSegment, index)
        hyp.cachedReachable = result
        hyp.cachedForSegment = hyp.lastSegment
        return result
    }

    private fun removePrimary(movementType: MovementType) {
        val pri = primary.remove(movementType) ?: return
        chains[movementType]?.remove(pri.chain)
        onChainRemoved?.invoke(pri.chain.id)
    }

    private fun highwayPenalty(highway: String): Double = when (highway) {
        "trunk", "trunk_link",
        "primary", "primary_link",
        "secondary", "secondary_link",
        "tertiary", "tertiary_link"   -> 0.0
        "residential", "unclassified",
        "living_street"               -> 0.0001
        "path", "track",
        "pedestrian", "steps"         -> 0.0002
        "service"                     -> 0.0005
        else                          -> 0.0001
    }

    private fun LatLong.matchesEndpoint(other: LatLong): Boolean = distanceTo(other) < 0.00005


    /* ── Finalization ── */

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
        val index = AppSegmentIndex.instance
        bridgeMovementTypeGaps(index)
        primary.clear()
        backups.clear()
        modifiedMovementTypes.clear()
    }

    fun mergeChainsByType() {
        for (movementType in MovementType.entries) {
            val chainList = chains[movementType] ?: continue
            if (chainList.size <= 1) continue

            val threshold = 0.00005
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
                        onChainRemoved?.invoke(b.id)
                        chain.dirty = true
                        merged = true; break
                    }

                    val headSlot = lookup(idx, chain.points.first().key(), chain)
                    if (headSlot != null) {
                        val b = headSlot.chain
                        if (!headSlot.isHead) b.points.forEach { chain.points.addFirst(it) }
                        else b.points.reversed().forEach { chain.points.addFirst(it) }
                        chainList.remove(b)
                        onChainRemoved?.invoke(b.id)
                        chain.dirty = true
                        merged = true; break
                    }
                }
            }
        }
        onChainsChanged?.invoke()
    }

    internal fun bridgeMovementTypeGaps(index: SegmentIndex?) {
        val threshold = 0.0008

        for (movementType in MovementType.entries) {
            val typeChains = chains[movementType] ?: continue
            for (chain in typeChains) {
                if (chain.points.size < 2) continue

                var bestDist = Double.MAX_VALUE
                var bestOtherPoint: LatLong? = null
                var bestChainPointIdx = 0

                for (otherType in MovementType.entries) {
                    if (otherType == movementType) continue
                    for (otherChain in chains[otherType] ?: emptyList()) {
                        for ((_, otherPoint) in otherChain.points.withIndex()) {
                            for (idx in 0 until minOf(5, chain.points.size)) {
                                val dist = otherPoint.distanceTo(chain.points[idx])
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestOtherPoint = otherPoint
                                    bestChainPointIdx = idx
                                }
                            }
                        }
                    }
                }

                Log.d("BRIDGE", "$movementType bestDist=$bestDist bestChainIdx=$bestChainPointIdx")
                if (bestDist !in 0.00002..threshold || bestOtherPoint == null) continue

                // Remove points before bestChainPointIdx
                repeat(bestChainPointIdx) { chain.points.removeFirst() }

                if (index != null) {
                    val bridgePath = findRoadPath(bestOtherPoint, chain.points.first(), index)
                    if (bridgePath != null && bridgePath.isNotEmpty()) {
                        var lastPoint: LatLong = bestOtherPoint
                        val pointsToAdd = mutableListOf<LatLong>()

                        pointsToAdd.add(bestOtherPoint)  // ← explicitly anchor to junction point

                        for (seg in bridgePath) {
                            val s = seg.startingPoint
                            val e = seg.endingPoint
                            if (lastPoint.distanceTo(s) <= lastPoint.distanceTo(e)) {
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
                    } else {
                        chain.points.addFirst(bestOtherPoint)
                        Log.d("BRIDGE", "Straight line bridge")
                    }
                }
                chain.dirty = true
            }
        }
    }

    private fun findRoadPath(from: LatLong, to: LatLong, index: SegmentIndex): List<Segment>? {
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

    /* Snapshotting and restoring */
    @Synchronized
    fun flushBufferedPoints(index: SegmentIndex) {
        val toProcess = bufferedPoints.toList()
        bufferedPoints.clear()
        for ((point, type, timestamp) in toProcess) {
            onGpsPoint(point, type, index)
        }
    }


    /* ── Persistence ── */

    @Synchronized
    fun save(context: Context) {
        val snapshot = synchronized(this) {
            chains.entries.map { (movementType, list) ->
                StoredMovementEntry(
                    movementType = movementType,
                    chains = list.map { chain ->
                        StoredPathChain(
                            id = chain.id,
                            movementType = chain.movementType,
                            points = synchronized(chain) { chain.points.toList() }
                                .map { StoredPoint(it.latitude, it.longitude) }
                        )
                    }
                )
            }
        }
        val text    = json.encodeToString(StoredChainCollection(snapshot))
        val file    = File(context.filesDir, FILE_NAME)
        val tmpFile = File(context.filesDir, "$FILE_NAME.tmp")
        tmpFile.writeText(text)
        if (file.exists()) file.delete()
        tmpFile.renameTo(file)
    }

    @Synchronized
    fun load(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        try {
            val text = file.readText()
            val collection = json.decodeFromString<StoredChainCollection>(text)
            clearSegments()
            for (entry in collection.entries) {
                chains[entry.movementType]?.addAll(
                    entry.chains.map { stored ->
                        PathChain(
                            id = stored.id,
                            movementType = stored.movementType,
                            points = ArrayDeque(stored.points.map { LatLong(it.lat, it.lon) }),
                            dirty = true
                        )
                    }
                )
            }
            nextChainId = chains.values.flatten().maxOfOrNull { it.id + 1 } ?: 0L
            onChainsChanged?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            deleteSaved(context)
        }
    }

    fun deleteSaved(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        File(context.filesDir, "$FILE_NAME.tmp").delete()
    }
}


/* HELPER FUNCTIONS */

fun LatLong.distanceTo(other: LatLong): Double {
    val dx = longitude - other.longitude
    val dy = latitude  - other.latitude
    return Math.sqrt(dx * dx + dy * dy)
}

fun ArrayDeque<LatLong>.indexOfClosestTo(target: LatLong): Int {
    var bestIdx  = 0
    var bestDist = Double.MAX_VALUE
    forEachIndexed { i, p ->
        val d = p.distanceTo(target)
        if (d < bestDist) { bestDist = d; bestIdx = i }
    }
    return bestIdx
}