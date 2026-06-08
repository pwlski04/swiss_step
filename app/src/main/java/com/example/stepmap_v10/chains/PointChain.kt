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
import android.util.Log
import androidx.compose.foundation.style.Style
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

data class LiveTail(
    /* Saves the most recent tail during live tracking */
    val point: LatLong,
    val chain: PathChain,
    val timestamp: Long,  // System.currentTimeMillis()
    val lastSegment: Segment?
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

class PathStorage () {
    var lastActiveMovementType: MovementType = MovementType.TRANSPORT

    val chains: MutableMap<MovementType, MutableList<PathChain>> = MovementType.entries.associateWith { mutableListOf<PathChain>() }.toMutableMap()
    val modifiedMovementTypes = mutableSetOf<MovementType>()
    private var nextChainId = 0L

    private val liveTails = HashMap<MovementType, LiveTail>()
    private val LIVE_GAP_THRESHOLD_DEGREES = 0.0003  // ~30m
    private val LIVE_GAP_THRESHOLD_MS = 30_000L       // 30 seconds

    var onChainRemoved: ((Long) -> Unit)? = null        // (*)

    private val CONTINUITY_SEARCH_RADIUS = 3       // connected hops to search
    private val DRIFT_REJECT_DISTANCE = 0.0004     // ~40m: farther = likely drift
    private val MAX_CONTINUITY_DISTANCE = 0.0008   // ~80m: give up continuity beyond this

    /* Store and restore chains */
    private val FILE_NAME = "walked_chains.json"

    private val json = Json {prettyPrint = false; ignoreUnknownKeys = true }

    var onChainsChanged: (() -> Unit)? = null


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
        val text = json.encodeToString(StoredChainCollection(snapshot))
        val file = File(context.filesDir, FILE_NAME)
        val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
        tempFile.writeText(text)
        if (file.exists()) file.delete()
        tempFile.renameTo(file)
        /*val collection = StoredChainCollection(
            entries = chains.entries.map { (movementType, list) ->
                StoredMovementEntry(
                    movementType = movementType,
                    chains = list.map { chain ->
                        StoredPathChain(
                            id = chain.id,
                            movementType = chain.movementType,
                            points = chain.points.map { StoredPoint(it.latitude, it.longitude) }
                        )
                    }
                )
            }
        )

        val text = json.encodeToString(collection)
        val file = File(context.filesDir, FILE_NAME)
        val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
        tempFile.writeText(text)
        if (file.exists()) file.delete()
        tempFile.renameTo(file)*/
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
            // Corrupt file — delete and start fresh
            deleteSaved(context)
        }
    }

    fun deleteSaved(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        File(context.filesDir, "$FILE_NAME.tmp").delete()
    }

    private fun emptyChains(): MutableMap<MovementType, MutableList<PathChain>> {
        return MovementType.entries
            .associateWith { mutableListOf<PathChain>() }
            .toMutableMap()
    }

    /* Save a segment */
    @Synchronized
    fun recordSegment(segment: Segment, movementType: MovementType): PathChain {
        val movementChains = chains[movementType]!!
        val segStart = segment.startingPoint
        val segEnd   = segment.endingPoint

        // For creating the result:
        var headChain: PathChain? = null
        var tailChain: PathChain? = null
        var headIsReversed = false
        var tailIsReversed = false

        for (chain in movementChains) {
            when {
                // Tail connects to head -> can be merged in order (not reversed):
                chain.points.last()  == segStart -> { headChain = chain; headIsReversed = false }
                chain.points.first() == segEnd   -> { tailChain = chain; tailIsReversed = false }
                // Tail connects to tail or head to head -> must be reversed to merge:
                chain.points.last()  == segEnd   -> { headChain = chain; headIsReversed = true  }
                chain.points.first() == segStart -> { tailChain = chain; tailIsReversed = true  }
            }
        }

        modifiedMovementTypes.add(movementType)
        val result = when {
            // Case 1: Both ends connect two different chains -> merge them
            headChain != null && tailChain != null && headChain !== tailChain -> {
                headChain.points.addLast(if (headIsReversed) segStart else segEnd)
                headChain.dirty = true
                val tailPoints = if (tailIsReversed) tailChain.points.reversed()  else tailChain.points.toList()
                tailPoints.forEach { headChain.points.addLast(it) }
                movementChains.remove(tailChain)
                onChainRemoved?.invoke(tailChain.id)
                headChain
            }

            // Case 2: One end connects to an existing chain -> merge them
            headChain != null -> {
                headChain.points.addLast(if (headIsReversed) segStart else segEnd)
                headChain.dirty = true
                headChain
            }
            tailChain != null -> {
                tailChain.points.addFirst(if (tailIsReversed) segEnd else segStart)
                tailChain.dirty = true
                tailChain
            }

            // Case 3: No connection -> make it new chain
            else -> {
                val c = PathChain(id = nextChainId++, movementType = movementType, points = ArrayDeque(listOf(segStart, segEnd)), dirty = true)
                movementChains.add(c)
                c
            }
        }
        onChainsChanged?.invoke()
        return result

    }


    /* Delete all segments */
    fun clearSegments(){
        for (movementType in MovementType.entries){
            chains[movementType]?.forEach { onChainRemoved?.invoke(it.id) }
            chains[movementType]?.clear()
        }

        modifiedMovementTypes.clear()
        liveTails.clear()
        onChainsChanged?.invoke()
    }

    fun isEmpty(): Boolean {
        return MovementType.entries.all { movementType ->
            chains[movementType].isNullOrEmpty()
        }
    }

    /*
    FOR CONTINUOUS DRAWING:
    ALWAYS STORE THE LAST POINT WITH ACCESS TIME (IS THE TAIL OF THE CURRENT PATH); THEN COMPARE DISTANCE AND TIME TO CURRENT POINT (WITH CUTOFF) => IF CLOSE ENOUGH BRIDGE (NEW BECOMES NEW TAIL)
    - DIFFERENCE TO CURRENT: CURRENT ONLY BRIDGES DURING SESSION END, WHAT ABOUT LIVE TRACING GAPS?
    - CHALLENGE: BRIDGE HOW?
    */
    @Synchronized
    fun onGpsPoint(segment: Segment, movementType: MovementType, index: SegmentIndex) {
        val segStart = segment.startingPoint
        val segEnd   = segment.endingPoint
        val now = System.currentTimeMillis()
        val tail = liveTails[movementType]

        val bestSegment = if (tail?.lastSegment != null && now - tail.timestamp < LIVE_GAP_THRESHOLD_MS) {
            findBestConnectedSegment(segStart, tail.lastSegment!!, index) ?: segment
        } else {
            segment
        }

        val bestStart = bestSegment.startingPoint
        val bestEnd   = bestSegment.endingPoint

        when {
            tail == null || now - tail.timestamp > LIVE_GAP_THRESHOLD_MS ||
                    tail.point.distanceTo(bestStart) > LIVE_GAP_THRESHOLD_DEGREES -> {
                val affectedChain = recordSegment(bestSegment, movementType)
                liveTails[movementType] = LiveTail(bestEnd, affectedChain, now, bestSegment)
            }
            else -> {
                val appendStart = tail.point.distanceTo(bestStart) <= tail.point.distanceTo(bestEnd)
                if (appendStart) {
                    if (tail.point.distanceTo(bestStart) > 0.00001) tail.chain.points.addLast(bestStart)
                    tail.chain.points.addLast(bestEnd)
                    liveTails[movementType] = LiveTail(bestEnd, tail.chain, now, bestSegment)
                } else {
                    if (tail.point.distanceTo(bestEnd) > 0.00001) tail.chain.points.addLast(bestEnd)
                    tail.chain.points.addLast(bestStart)
                    liveTails[movementType] = LiveTail(bestStart, tail.chain, now, bestSegment)
                }
                tail.chain.dirty = true
                modifiedMovementTypes.add(movementType)
                onChainsChanged?.invoke()
            }
        }
    }

    private fun findBestConnectedSegment(gpsPoint: LatLong, lastSegment: Segment, index: SegmentIndex): Segment? {
        val visited = mutableSetOf<Segment>()
        var frontier = listOf(lastSegment)
        val options = mutableListOf<Segment>()

        repeat(CONTINUITY_SEARCH_RADIUS){
            val next = mutableListOf<Segment>()
            for (segment in frontier){
                if (!visited.add(segment)) continue
                options.add(segment)
                next += index.connectedSegments(segment)
            }
            frontier = next
        }

        val best = options.minByOrNull { pointToSegmentDistance(gpsPoint, it) } ?: return null
        val dist = pointToSegmentDistance(gpsPoint, best)


        return if (dist < MAX_CONTINUITY_DISTANCE) best else null
    }


    /* At the end of a session */
    @Synchronized
    fun finalizeSession(){
        modifiedMovementTypes.clear()
        liveTails.clear()
    }


    /* HELPERS */
    private fun mergeWithOverlapCheck(chainA: PathChain, chainB: PathChain, thresholdDegrees: Double = 0.00005) {       // (2): Append chainB onto chainA, skipping leading points that overlap chainA's tail
        val aEnd = chainA.points.last()
        var startIndex = 0
        // Skip points in B that are still "on top of" A's last point
        for (i in chainB.points.indices) {
            if (chainB.points[i].distanceTo(aEnd) < thresholdDegrees) startIndex = i + 1
            else break
        }
        for (i in startIndex until chainB.points.size) {
            chainA.points.addLast(chainB.points[i])
        }
    }

    fun runGapMerge(movementType: MovementType, threshold: Double = 0.0002) {       // (1): Full gap merge with overlap + reversal handling
        val chainList = chains[movementType]!!
        if (chainList.size < 2) return

        // Quantize to grid cells of size `threshold`
        fun quantize(v: Double) = (v / threshold).toLong()
        fun LatLong.key() = Pair(quantize(latitude), quantize(longitude))

        // Each endpoint slot: which chain owns it, and which end
        data class EndpointSlot(val chain: PathChain, val isHead: Boolean)

        fun buildIndex(): HashMap<Pair<Long, Long>, EndpointSlot> {
            val index = HashMap<Pair<Long, Long>, EndpointSlot>()
            for (chain in chainList) {
                index[chain.points.first().key()] = EndpointSlot(chain, isHead = true)
                index[chain.points.last().key()]  = EndpointSlot(chain, isHead = false)
            }
            return index
        }

        // Check neighboring cells too, since two points can be within `threshold` distance but quantize to adjacent cells
        fun lookupNearby(index: HashMap<Pair<Long, Long>, EndpointSlot>, key: Pair<Long, Long>): EndpointSlot? {
            for (dx in -1..1) for (dy in -1..1) {
                val candidate = index[Pair(key.first + dx, key.second + dy)]
                if (candidate != null) return candidate
            }
            return null
        }

        var merged = true
        while (merged) {
            merged = false
            val index = buildIndex()

            for (chain in chainList.toList()) {
                if (!chainList.contains(chain)) continue

                val aEnd   = chain.points.last()
                val aStart = chain.points.first()

                // Try to match chain's tail to any other chain's endpoint
                val matchFromTail = lookupNearby(index, aEnd.key())
                    ?.takeIf { it.chain !== chain }

                // Try to match chain's head to any other chain's endpoint
                val matchFromHead = lookupNearby(index, aStart.key())
                    ?.takeIf { it.chain !== chain }

                val matchingEndpoint = matchFromTail ?: matchFromHead ?: continue
                val isTailMatch = matchFromTail != null

                val a = chain
                val b = matchingEndpoint.chain

                when {
                    // a's tail matched b's head → append b onto a as-is
                    isTailMatch && matchingEndpoint.isHead -> {
                        mergeWithOverlapCheck(a, b, threshold)      // (2)
                        chainList.remove(b)
                        onChainRemoved?.invoke(b.id)
                        a.dirty = true
                    }
                    // a's tail matched b's tail → flip b then append
                    isTailMatch && !matchingEndpoint.isHead -> {
                        val flipped = PathChain(b.id, b.movementType,ArrayDeque(b.points.reversed()), dirty = true)
                        mergeWithOverlapCheck(a, flipped, threshold)
                        chainList.remove(b)
                        onChainRemoved?.invoke(b.id)
                        a.dirty = true
                    }
                    // a's head matched b's tail → append a onto b
                    !isTailMatch && !matchingEndpoint.isHead -> {
                        mergeWithOverlapCheck(b, a, threshold)
                        chainList.remove(a)
                        onChainRemoved?.invoke(a.id)
                        b.dirty = true
                    }
                    // a's head matched b's head → flip a then append onto b
                    !isTailMatch && matchingEndpoint.isHead -> {
                        val flipped = PathChain(a.id, a.movementType,ArrayDeque(a.points.reversed()), dirty = true)
                        mergeWithOverlapCheck(b, flipped, threshold)
                        chainList.remove(a)
                        onChainRemoved?.invoke(a.id)
                        b.dirty = true
                    }
                }

                merged = true
                break  // index is stale after any merge, rebuild
            }
        }
    }

    fun totalPointCount(): Int {
        return chains.values.sumOf { chainList ->
            chainList.sumOf { chain -> chain.points.size }
        }
    }
}


/* HELPER FUNCTIONS */

fun LatLong.distanceTo(other: LatLong): Double {
    val dx = longitude - other.longitude
    val dy = latitude  - other.latitude
    return Math.sqrt(dx * dx + dy * dy)
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