package com.example.stepMap_v10.chains

import com.example.stepMap_v10.paths.Segment
import com.example.stepMap_v10.paths.colorForMovementType
import com.example.stepMap_v10.paths.strokeWidthComputer
import com.example.stepMap_v10.tracking.MovementType
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import kotlin.collections.remove


/* INITIALIZATION
val pathStorage = PathStorage()
val overlayLayer = PathOverlayLayer(pathStorage)
pathStorage.onChainRemoved = { id -> overlayLayer.evictFromCache(id) }
 */

/* DATA CLASSES */

data class PathChain(
    /* Saves the traveled segment as a list of points associated with a specific movement type*/
    val id: Long,
    val movementType: MovementType,
    val points: ArrayDeque<LatLong>,
    var dirty: Boolean = true
)

data class ChainEndpoint(
    /* To save both ends of the chain */
    val chain: PathChain,
    val isHead: Boolean
) {
    val point get() = if (isHead) chain.points.first() else chain.points.last()
}

data class LiveTail(
    /* Saves the most recent tail during live tracking */
    val point: LatLong,
    val chain: PathChain,
    val timestamp: Long  // System.currentTimeMillis()
)


/* OVERLAY => Rendered over base map */

class PathOverlayLayer(private val pathStorage: PathStorage) : Layer() {
    private val paintCache = HashMap<Pair<MovementType, Byte>, Paint>()
    private val projectionCache = HashMap<Long, HashMap<Byte, List<Pair<Double, Double>>>>()        // Cache of pre-projected points per chain per zoom level

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()

        for (movementType in DRAW_ORDER) {      // (1)
            val chains = pathStorage.chains[movementType] ?: continue
            if (chains.isEmpty()) continue
            val paint = getPaint(movementType, zoomLevel)       // (2)

            for (chain in chains) {
                val zoomCache = projectionCache.getOrPut(chain.id) { HashMap() }
                if (chain.dirty || !zoomCache.containsKey(zoomLevel)){
                    zoomCache.clear()
                    zoomCache[zoomLevel] = projectChain(chain, zoomLevel)       // (3)
                    chain.dirty = false
                }

                //drawChain(chain, canvas, topLeftPoint, zoomLevel, paint, boundingBox)
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

    private fun drawChain(chain: PathChain, canvas: Canvas, topLeftPoint: Point, zoomLevel: Byte, paint: Paint, boundingBox: BoundingBox) {
        val points = chain.points
        if (points.size < 2) return

        val tileSize = displayModel.tileSize
        val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)

        // Pre-convert all points to screen coordinates (Double) by 1. projecting and 2. subtracting topLeftPoint
        val screenPoints = points.map { latLong ->
            val pixelX = MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize)
            val pixelY = MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize)
            Pair(pixelX - topLeftPoint.x, pixelY - topLeftPoint.y)
        }

        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()

        for (i in 0 until screenPoints.size - 1) {
            val (x1, y1) = screenPoints[i]
            val (x2, y2) = screenPoints[i + 1]

            // Skip fully offscreen segments
            if (!segmentIntersectsViewport(x1, y1, x2, y2, w, h)) continue      // (4)

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
        return chain.points.map { latLong ->
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
    val chains: MutableMap<MovementType, MutableList<PathChain>> = MovementType.entries.associateWith { mutableListOf<PathChain>() }.toMutableMap()
    val modifiedMovementTypes = mutableSetOf<MovementType>()
    private var nextChainId = 0L

    private val liveTails = HashMap<MovementType, LiveTail>()
    private val LIVE_GAP_THRESHOLD_DEGREES = 0.0003  // ~30m
    private val LIVE_GAP_THRESHOLD_MS = 30_000L       // 30 seconds

    var onChainRemoved: ((Long) -> Unit)? = null        // (*)


    /* Save a segment */
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

        return when {
            // Case 1: Both ends connect two different chains -> merge them
            headChain != null && tailChain != null && headChain !== tailChain -> {
                headChain.points.addLast(if (headIsReversed) segStart else segEnd)
                headChain.dirty = true
                val tailPoints = if (tailIsReversed) tailChain.points.reversed()
                else tailChain.points.toList()
                tailPoints.forEach { headChain.points.addLast(it) }
                movementChains.remove(tailChain)
                onChainRemoved?.invoke(tailChain.id)
                headChain
            }

            // Case 2: One end connects to an existing chain -> merge them
            headChain != null ->{
                headChain.points.addLast(if (headIsReversed) segStart else segEnd)
                headChain.dirty = true
                headChain
            }
            tailChain != null ->{
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

    }


    /* Delete all segments */
    fun clearSegments(){
        for (movementType in MovementType.entries){
            chains[movementType]?.forEach { onChainRemoved?.invoke(it.id) }
            chains[movementType]?.clear()
        }

        modifiedMovementTypes.clear()
        liveTails.clear()
    }


    /*
    NEW IDEA FOR CONTINUOUS DRAWING:
    ALWAYS STORE THE LAST POINT WITH ACCESS TIME (IS THE TAIL OF THE CURRENT PATH); THEN COMPARE DISTANCE AND TIME TO CURRENT POINT (WITH CUTOFF) => IF CLOSE ENOUGH BRIDGE (NEW BECOMES NEW TAIL)
    - DIFFERENCE TO CURRENT: CURRENT ONLY BRIDGES DURING SESSION END, WHAT ABOUT LIVE TRACING GAPS?
    - CHALLENGE: BRIDGE HOW?
    */
    fun onGpsPoint(segment: Segment, movementType: MovementType) {
        val segStart = segment.startingPoint
        val segEnd   = segment.endingPoint
        val now = System.currentTimeMillis()
        val tail = liveTails[movementType]

        when {
            // No tail yet → record normally, set tail to segment end
            tail == null -> {
                val affectedChain = recordSegment(segment, movementType)
                liveTails[movementType] = LiveTail(segEnd, affectedChain, now)
            }

            // Tail is stale (too old or too far) → start fresh chain
            now - tail.timestamp > LIVE_GAP_THRESHOLD_MS || tail.point.distanceTo(segStart) > LIVE_GAP_THRESHOLD_DEGREES -> {
                val affectedChain = recordSegment(segment, movementType)
                liveTails[movementType] = LiveTail(segEnd, affectedChain, now)
            }

            // Tail is close and recent → bridge the gap directly
            else -> {
                // Append bridge point + new segment end onto the live chain
                if (tail.point.distanceTo(segStart) > 0.00001) {
                    tail.chain.points.addLast(segStart)  // bridge
                }
                tail.chain.points.addLast(segEnd)
                tail.chain.dirty = true
                modifiedMovementTypes.add(movementType)
                liveTails[movementType] = LiveTail(segEnd, tail.chain, now)
            }
        }
    }


    /* At the end of a session */
    fun finalizeSession(){
        for (movementType in modifiedMovementTypes){     // TODO: Call for each MODIFIED MovementType
            runFinalization(movementType)
        }

        modifiedMovementTypes.clear()
        liveTails.clear()
    }

    fun runFinalization(movementType: MovementType) {
        runGapMerge(movementType)       // (1) => second pass for newly exposed gaps
        runGapMerge(movementType)
    }


    /* HELPERS */
    fun runGapMerge(movementType: MovementType) {       // (1): Full gap merge with overlap + reversal handling
        val chainList = chains[movementType]!!
        if (chainList.size < 2) return

        val threshold = 0.0002

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
                        val flipped = PathChain(b.id, b.movementType,ArrayDeque(b.points.reversed()))
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
                        val flipped = PathChain(a.id, a.movementType,ArrayDeque(a.points.reversed()))
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