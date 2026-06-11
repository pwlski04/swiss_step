package com.example.stepmap_v10.chains

import com.example.stepmap_v10.paths.Segment
import com.example.stepmap_v10.paths.SegmentIndex
import com.example.stepmap_v10.paths.colorForMovementType
import com.example.stepmap_v10.paths.pointToSegmentDistance
import com.example.stepmap_v10.paths.strokeWidthComputer
import com.example.stepmap_v10.tracking.MovementType
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import kotlinx.serialization.Serializable
import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap


/* DATA CLASSES */

data class PathChain(
    /* Saves the traveled segment as a list of points associated with a specific movement type*/
    val id: Long,
    val movementType: MovementType,
    val points: ArrayDeque<LatLong>,
    var dirty: Boolean = true
)

@Serializable
data class StoredChainCollection(
    val entries: List<StoredMovementEntry>
)

@Serializable
data class StoredMovementEntry(
    val movementType: MovementType,
    val chains: List<StoredPathChain>
)

@Serializable
data class StoredPathChain(
    val id: Long,
    val movementType: MovementType,
    val points: List<StoredPoint>
)

@Serializable
data class StoredPoint(
    val lat: Double,
    val lon: Double
)


private data class PathHypothesis(
    val id: Long,
    val chain: PathChain,
    var lastSegment: Segment,
    var timestamp: Long,
    var score: Double = 0.0,
    var missCount: Int = 0,
    var pointCount: Int = 0,
    val segmentHistory: ArrayDeque<Pair<Segment, Int>> = ArrayDeque()
)

//TODO: REMOVE
class RawGpsPointsLayer(
    private val context: Context,
    private val fileName: String
) : Layer() {

    private val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(android.graphics.Color.GREEN)
        setStrokeWidth(1f)
    }

    private var rawPoints: List<Pair<Double, Double>> = emptyList()

    fun loadPoints() {
        val json = Json { ignoreUnknownKeys = true }
        val text = File(context.filesDir, fileName).readText()
        val route = json.decodeFromString<RecordedRoute>(text)
        rawPoints = route.points.map { Pair(it.lat, it.lon) }
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)

        for ((lat, lon) in rawPoints) {
            val pixelX = MercatorProjection.longitudeToPixelX(lon, mapSize)
            val pixelY = MercatorProjection.latitudeToPixelY(lat, mapSize)
            val screenX = (pixelX - topLeftPoint.x).toInt()
            val screenY = (pixelY - topLeftPoint.y).toInt()

            if (screenX < -10 || screenX > canvas.width + 10 ||
                screenY < -10 || screenY > canvas.height + 10) continue

            canvas.drawCircle(screenX, screenY, 4, paint)
        }
    }
}
class DebugPointsLayer(private val pathStorage: PathStorage) : Layer() {

    private val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(android.graphics.Color.RED)
        setStrokeWidth(0f)
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)

        val allPoints = synchronized(pathStorage) {
            pathStorage.chains.values.flatten()
                .flatMap { chain -> synchronized(chain) { chain.points.toList() } }
        }

        for (point in allPoints) {
            val pixelX = MercatorProjection.longitudeToPixelX(point.longitude, mapSize)
            val pixelY = MercatorProjection.latitudeToPixelY(point.latitude, mapSize)
            val screenX = (pixelX - topLeftPoint.x).toInt()
            val screenY = (pixelY - topLeftPoint.y).toInt()

            if (screenX < -10 || screenX > canvas.width + 10 ||
                screenY < -10 || screenY > canvas.height + 10) continue

            canvas.drawCircle(screenX, screenY, 6, paint)
        }
    }
}

/* OVERLAY => Rendered over base map */
class PathOverlayLayer(private val pathStorage: PathStorage) : Layer() {
    private val paintCache = HashMap<Pair<MovementType, Byte>, Paint>()
    private val projectionCache = ConcurrentHashMap<Long, HashMap<Byte, List<Pair<Double, Double>>>>()        // Cache of pre-projected points per chain per zoom level

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()

        for (movementType in DRAW_ORDER) {      // (1)
            val chains = synchronized(pathStorage) {
                pathStorage.chains[movementType]?.toList()
            } ?: continue
            if (chains.isEmpty()) continue
            val paint = getPaint(movementType, zoomLevel)       // (2)

            for (chain in chains) {
                val zoomCache = projectionCache.getOrPut(chain.id) { HashMap() }
                if (chain.dirty || !zoomCache.containsKey(zoomLevel)){
                    zoomCache.clear()
                    zoomCache[zoomLevel] = projectChain(chain, zoomLevel)       // (3)
                    chain.dirty = false
                }

                drawProjectedChain(zoomCache[zoomLevel]!!, topLeftPoint, canvas, paint, w, h)       // Uses the cache
            }
        }
    }

    private fun drawProjectedChain(mapPoints: List<Pair<Double, Double>>, topLeftPoint: Point, canvas: Canvas, paint: Paint, w: Double, h: Double){
        for (i in 0 until mapPoints.size - 1) {
            val (ax, ay) = mapPoints[i]
            val (bx, by) = mapPoints[i + 1]

            // Convert map-space → screen-space by subtracting topLeftPoint
            val x1 = ax - topLeftPoint.x
            val y1 = ay - topLeftPoint.y
            val x2 = bx - topLeftPoint.x
            val y2 = by - topLeftPoint.y

            if (!segmentIntersectsViewport(x1, y1, x2, y2, w, h)) continue
            canvas.drawLine(x1.toInt(), y1.toInt(),x2.toInt(), y2.toInt(),paint)
        }
    }

    /* HELPERS */
    fun evictFromCache(chainId: Long){      // (*)
        projectionCache.remove(chainId)
    }

    companion object {      // (1)
        val DRAW_ORDER = listOf(
            // Drawn bottom-to-top; WALKING ends up on top
            MovementType.STILL,
            MovementType.TRANSPORT,
            MovementType.BIKING,
            MovementType.RUNNING,
            MovementType.WALKING,
        )
    }

    fun getPaint(movementType: MovementType, zoomLevel: Byte): Paint{       // (2)
        return paintCache.getOrPut(Pair(movementType, zoomLevel)) {
            AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = colorForMovementType(movementType)
                strokeWidth = strokeWidthComputer(zoomLevel.toFloat())
            }
        }
    }

    private fun projectChain(chain: PathChain, zoomLevel: Byte): List<Pair<Double, Double>> {       // (4)
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        val points = synchronized(chain) { chain.points.toList() }  // ← safe snapshot
        return points.map { latLong ->
            Pair(
                MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize),
                MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize)
            )
        }
    }

    private fun segmentIntersectsViewport(x1: Double, y1: Double, x2: Double, y2: Double, width: Double, height: Double): Boolean {     // (3)
        return maxOf(x1, x2) >= 0 && minOf(x1, x2) <= width &&
                maxOf(y1, y2) >= 0 && minOf(y1, y2) <= height
    }
}


/* RENDERED DATA => Saves the logic etc. behind the rendered point chains */

class PathStorage {

    // Only PRIMARY chains — rendered and saved
    val chains: MutableMap<MovementType, MutableList<PathChain>> =
        MovementType.entries.associateWith { mutableListOf<PathChain>() }.toMutableMap()

    val modifiedMovementTypes = mutableSetOf<MovementType>()
    private var nextChainId = 0L
    private var nextHypId   = 0L

    // One primary per movement type (shown on map)
    private val primary = HashMap<MovementType, PathHypothesis>()
    // Backups per movement type (NOT shown — chain NOT in chains[])
    private val backups = HashMap<MovementType, MutableList<PathHypothesis>>()

    private val LIVE_GAP_THRESHOLD_MS   = 30_000L
    private val MAX_CONTINUITY_DISTANCE = 0.0008
    private val MISS_THRESHOLD          = 5
    private val MAX_BACKUPS             = 3

    var onChainRemoved: ((Long) -> Unit)? = null
    var onChainsChanged: (() -> Unit)? = null
    var lastActiveMovementType: MovementType = MovementType.TRANSPORT

    private val FILE_NAME = "walked_chains.json"
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }


    private val HARD_RESET_THRESHOLD = 30
    private val BFS_SEARCH_DISTANCE = 0.005


    /* ── GPS entry point ── */
    // 1. Fix initial direction in spawnFreshPrimary
    private fun spawnFreshPrimary(
        gpsPoint: LatLong, movementType: MovementType,
        index: SegmentIndex, now: Long
    ) {
        val nearest = index.nearbySegments(gpsPoint.latitude, gpsPoint.longitude)
            .filter { pointToSegmentDistance(gpsPoint, it) < 0.0006 }
            .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway) }
            ?: return

        // Orient chain so GPS-closest end comes first
        val distToStart = gpsPoint.distanceTo(nearest.startingPoint)
        val distToEnd   = gpsPoint.distanceTo(nearest.endingPoint)
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
        val hyp = PathHypothesis(
            id = nextHypId++,
            chain = chain,
            lastSegment = nearest,
            timestamp = now
        )
        hyp.segmentHistory.addLast(Pair(nearest, chain.points.size))
        primary[movementType] = hyp
        modifiedMovementTypes.add(movementType)
        onChainsChanged?.invoke()
    }

    private fun extendChainWithPath(
        chain: PathChain,
        parentMap: Map<Segment, Segment?>,
        target: Segment,
        lastPointIn: LatLong
    ) {
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

            // Remove A→B→A spike: if last point equals third-to-last, we went backwards
            while (chain.points.size >= 3) {
                val n = chain.points.size
                if (chain.points[n - 1].distanceTo(chain.points[n - 3]) < 0.00002) {
                    chain.points.removeAt(n - 1)
                    chain.points.removeAt(n - 2)
                    if (chain.points.isNotEmpty()) {
                        lastPoint = chain.points.last()
                    }
                } else break
            }

            // Remove A→B→C→A triangle: if last point revisits an earlier point
            if (chain.points.size >= 4) {
                val last = chain.points.last()
                val searchFrom = maxOf(0, chain.points.size - 8)
                val revisitIdx = (searchFrom until chain.points.size - 1)
                    .lastOrNull { chain.points[it].distanceTo(last) < 0.00002 }
                if (revisitIdx != null) {
                    while (chain.points.size > revisitIdx + 1) {
                        chain.points.removeLast()
                    }
                    if (chain.points.isNotEmpty()) lastPoint = chain.points.last()
                }
            }
        }
        chain.dirty = true
    }

    // Simple single-segment extend (for backups at junctions — always direct neighbor)
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

    // 3. onGpsPoint with hard reset and simple extendChain
    @Synchronized
    fun onGpsPoint(gpsPoint: LatLong, movementType: MovementType, index: SegmentIndex) {
        if (movementType == MovementType.STILL) return
        lastActiveMovementType = movementType
        val now = System.currentTimeMillis()

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

        // BFS with parent map so we can trace intermediate segments
        val parentMap = getReachableSegmentsWithPath(currentPrimary.lastSegment, index)

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
                val historyEntry = currentPrimary.segmentHistory
                    .findLast { it.first === bestForPrimary }

                if (historyEntry != null) {
                    // Revisiting a past segment — trim the wrong detour
                    val targetLength = historyEntry.second
                    while (currentPrimary.chain.points.size > targetLength) {
                        currentPrimary.chain.points.removeLast()
                    }
                    while (currentPrimary.segmentHistory.isNotEmpty() &&
                        currentPrimary.segmentHistory.last().second > targetLength) {
                        currentPrimary.segmentHistory.removeLast()
                    }
                } else {
                    // New segment — trace ALL intermediate road nodes, no straight lines
                    extendChainWithPath(
                        currentPrimary.chain, parentMap,
                        bestForPrimary!!, currentPrimary.chain.points.last()
                    )
                    currentPrimary.segmentHistory.addLast(
                        Pair(bestForPrimary, currentPrimary.chain.points.size)
                    )
                    if (currentPrimary.segmentHistory.size > 50) {
                        currentPrimary.segmentHistory.removeFirst()
                    }
                }
                currentPrimary.lastSegment = bestForPrimary!!
                currentPrimary.chain.dirty = true
            }

            // Spawn backups at direct junctions (single hop — no path needed)
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
                        id = nextChainId++,
                        movementType = movementType,
                        points = ArrayDeque(currentPrimary.chain.points.toList()),
                        dirty = true
                    )
                    extendChain(backupChain, alt, backupChain.points.last())
                    val bHyp = PathHypothesis(
                        id = nextHypId++,
                        chain = backupChain,
                        lastSegment = alt,
                        timestamp = now,
                        score = currentPrimary.score,
                        pointCount = currentPrimary.pointCount
                    )
                    bkps.add(bHyp)
                }
            }

            // Discard backups that drifted too far
            bkps.removeAll { backup ->
                getReachableSegmentsWithPath(backup.lastSegment, index).keys
                    .none { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE * 2.0 }
            }

        } else {
            currentPrimary.missCount++

            // Hard reset — GPS moved far away permanently
            if (currentPrimary.missCount >= HARD_RESET_THRESHOLD) {
                removePrimary(movementType)
                bkps.clear()
                spawnFreshPrimary(gpsPoint, movementType, index, now)
                return
            }

            // Advance backups using path tracing
            for (backup in bkps) {
                val bParentMap = getReachableSegmentsWithPath(backup.lastSegment, index)
                val bestForBackup: Segment? = bParentMap.keys
                    .filter { pointToSegmentDistance(gpsPoint, it) < MAX_CONTINUITY_DISTANCE }
                    .minByOrNull { pointToSegmentDistance(gpsPoint, it) + highwayPenalty(it.highway) }

                if (bestForBackup != null) {
                    backup.pointCount++
                    backup.score += pointToSegmentDistance(gpsPoint, bestForBackup)
                    backup.timestamp = now
                    if (bestForBackup !== backup.lastSegment) {
                        extendChainWithPath(
                            backup.chain, bParentMap,
                            bestForBackup, backup.chain.points.last()
                        )
                        backup.lastSegment = bestForBackup
                    }
                    backup.missCount = 0
                } else {
                    backup.missCount++
                }
            }

            bkps.removeAll { it.missCount >= MISS_THRESHOLD }

            if (currentPrimary.missCount >= MISS_THRESHOLD) {
                val bestBackup = bkps
                    .filter { it.pointCount > 0 }
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

    private fun getReachableSegmentsWithPath(
        start: Segment,
        index: SegmentIndex
    ): Map<Segment, Segment?> {
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

    /* ── Helpers ── */
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

    private fun LatLong.matchesEndpoint(other: LatLong): Boolean =
        distanceTo(other) < 0.00005


    /* ── Clear / finalize ── */

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

    fun isEmpty(): Boolean = MovementType.entries.all { chains[it].isNullOrEmpty() }

    @Synchronized
    fun finalizeSession() {
        primary.clear()
        backups.clear()
        modifiedMovementTypes.clear()
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

    fun totalPointCount(): Int =
        chains.values.sumOf { list -> list.sumOf { it.points.size } }

    fun refreshHasChains(): Boolean =
        chains.values.any { it.isNotEmpty() }
}


/* HELPER FUNCTIONS */

fun LatLong.distanceTo(other: LatLong): Double {
    val dx = longitude - other.longitude
    val dy = latitude  - other.latitude
    return Math.sqrt(dx * dx + dy * dy)
}

private fun LatLong.matchesEndpoint(other: LatLong): Boolean {
    return distanceTo(other) < 0.00005  // ~2m — same OSM node
}

/** Returns the index of the point in this deque closest to [target]. */
fun ArrayDeque<LatLong>.indexOfClosestTo(target: LatLong): Int {
    var bestIdx  = 0
    var bestDist = Double.MAX_VALUE
    forEachIndexed { i, p ->
        val d = p.distanceTo(target)
        if (d < bestDist) { bestDist = d; bestIdx = i }
    }
    return bestIdx
}