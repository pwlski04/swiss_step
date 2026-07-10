package io.github.pwlski04.swissstep.paths

import org.mapsforge.core.model.LatLong

/*
Windowed spatial index over the (country-scale) segment network. Rather than
holding every segment in memory, it lazily pages segments in from a SegmentSource
around whichever points callers care about (live GPS position, a replayed route's
points, or historical chain-bridging points), and evicts segments that are no
longer near any of those points.

Multiple independent "windows" (keyed by an arbitrary caller-supplied windowKey,
e.g. "live" vs "replay") can be active at once - each tracks its own load center,
but a segment is only evicted once it's no longer near ANY active window, so two
callers watching different parts of the country don't evict each other's data.

nearbySegments()/connectedSegments() keep the exact same signatures/behavior as
before this class supported windowed loading - callers (PathStorage) are
unaffected by how segments enter/leave the cache.

Referential identity matters: PathStorage compares segments with `===`, not
structural equality (see lastSegment/pendingSegment/cachedForSegment). A segment
already resident must never be reconstructed as a second, different instance for
the same underlying DB row - segmentById is the canonical-instance cache that
guarantees this across repeat/overlapping window loads.
*/
class SegmentIndex(private val source: SegmentSource, private val cellSizeDegrees: Double = 0.001) {
    private val grid = HashMap<Pair<Int, Int>, MutableList<Segment>>()          // Maps a cell to close-by segments
    private val endpointIndex = HashMap<Pair<Long, Long>, MutableList<Segment>>()       // Maps starting and ending points to their connected segments

    private val segmentById = HashMap<Long, Segment>()                          // canonical instance per DB row id
    private val segmentHomeCells = HashMap<Long, Set<Pair<Int, Int>>>()          // every cell each segment is registered under

    private data class WindowState(var centerLat: Double, var centerLon: Double)
    private val windows = HashMap<String, WindowState>()

    companion object {
        private const val RELOAD_TRIGGER_DISTANCE = 0.003   // ~330m: how far a window must drift before requerying
        private const val LOAD_HALF_WIDTH = 0.012           // ~1.3km: exceeds PathStorage's BFS_SEARCH_DISTANCE (0.005) with margin
        private const val EVICT_HALF_WIDTH = 0.03           // ~3.3km: hysteresis margin beyond LOAD_HALF_WIDTH, stops load/evict thrashing
    }

    @Synchronized
    fun ensureLoaded(lat: Double, lon: Double, windowKey: String) {
        /*
        Pages segments in from `source` around (lat, lon) if this window hasn't
        already loaded that area recently, then evicts any segment no longer near
        any active window. Cheap no-op on most calls (every GPS tick) since it
        only hits the backing store once the window has actually drifted.
        */
        val existing = windows[windowKey]
        if (existing != null && distance(lat, lon, existing.centerLat, existing.centerLon) < RELOAD_TRIGGER_DISTANCE) {
            return
        }

        val (cx, cy) = toCell(lat, lon)
        val cellMargin = (LOAD_HALF_WIDTH / cellSizeDegrees).toInt() + 1
        val rows = source.querySegmentsInCellRange(cx - cellMargin, cx + cellMargin, cy - cellMargin, cy + cellMargin)

        for (row in rows) {
            if (segmentById.containsKey(row.id)) continue

            val segment = Segment(
                startingPoint = LatLong(row.startLat, row.startLon),
                endingPoint = LatLong(row.endLat, row.endLon),
                highway = row.highway,
                walkable = row.walkable,
                drivable = row.drivable
            )

            val homeCells = cellsForSegment(segment)
            segmentById[row.id] = segment
            segmentHomeCells[row.id] = homeCells
            homeCells.forEach { cell -> grid.getOrPut(cell) { mutableListOf() }.add(segment) }
            indexEndpoint(segment.startingPoint, segment)
            indexEndpoint(segment.endingPoint, segment)
        }

        windows.getOrPut(windowKey) { WindowState(lat, lon) }.let {
            it.centerLat = lat
            it.centerLon = lon
        }

        evictStale()
    }

    private fun evictStale() {
        /*
        Retires any segment that isn't near any currently active window. A segment
        is either fully resident (registered in grid under every home cell, both
        endpoints in endpointIndex, one entry in segmentById) or fully absent -
        never partially removed, so connectedSegments()/nearbySegments() never see
        a half-evicted segment.

        Evicted segments are simply dropped from this index, not reached into via
        any in-flight PathHypothesis - a hypothesis still holding one as its
        lastSegment just sees connectedSegments() return fewer/no neighbors from
        then on, which the existing BFS/Dijkstra in PathStorage already treats as
        a dead end. EVICT_HALF_WIDTH is sized to make that rare in practice.
        */
        val keepCells = HashSet<Pair<Int, Int>>()
        val margin = (EVICT_HALF_WIDTH / cellSizeDegrees).toInt() + 1
        for (window in windows.values) {
            val (cx, cy) = toCell(window.centerLat, window.centerLon)
            for (dx in -margin..margin) {
                for (dy in -margin..margin) {
                    keepCells.add(Pair(cx + dx, cy + dy))
                }
            }
        }

        val toRetire = segmentHomeCells.entries
            .filter { (_, homeCells) -> homeCells.none { it in keepCells } }
            .map { it.key }

        for (id in toRetire) {
            val segment = segmentById[id] ?: continue
            val homeCells = segmentHomeCells[id] ?: emptySet()

            for (cell in homeCells) {
                grid[cell]?.let { segments ->
                    segments.remove(segment)
                    if (segments.isEmpty()) grid.remove(cell)
                }
            }
            removeEndpoint(segment.startingPoint, segment)
            removeEndpoint(segment.endingPoint, segment)

            segmentById.remove(id)
            segmentHomeCells.remove(id)
        }
    }

    @Synchronized
    fun connectedSegments(segment: Segment): List<Segment> {
        /* Get all segments sharing endpoints with this segment */
        val result = mutableListOf<Segment>()
        result += endpointIndex[quantizeEndpoint(segment.startingPoint)] ?: emptyList()
        result += endpointIndex[quantizeEndpoint(segment.endingPoint)] ?: emptyList()
        return result.filter { it !== segment }
    }

    private fun cellsForSegment(segment: Segment): Set<Pair<Int, Int>> {
        /* Returns every spatial-grid cell this segment's bounding box overlaps - a long
        segment can span many cells, and all of them must be registered, or a
        nearbySegments() query against one of the "middle" cells would silently
        miss it (this used to only register the 4 corner cells). */
        val latMin = minOf(segment.startingPoint.latitude, segment.endingPoint.latitude)
        val latMax = maxOf(segment.startingPoint.latitude, segment.endingPoint.latitude)
        val lonMin = minOf(segment.startingPoint.longitude, segment.endingPoint.longitude)
        val lonMax = maxOf(segment.startingPoint.longitude, segment.endingPoint.longitude)

        val (x1, y1) = toCell(latMin, lonMin)
        val (x2, y2) = toCell(latMax, lonMax)

        val cells = mutableSetOf<Pair<Int, Int>>()
        for (x in x1..x2) {
            for (y in y1..y2) {
                cells.add(Pair(x, y))
            }
        }
        return cells
    }


    @Synchronized
    fun nearbySegments(lat: Double, lon: Double, radius: Int = 1): List<Segment> {
        val (cx, cy) = toCell(lat, lon)
        val result = mutableListOf<Segment>()

        // Every segment in the square of size radius around (lat, lon)
        for (dx in -radius..radius){
            for (dy in -radius..radius){
                grid[Pair(cx + dx, cy + dy)]?.let{ result.addAll(it) }
            }
        }
        return result
    }



    /* HElPERS */

    private fun indexEndpoint(ll: LatLong, seg: Segment) {
        endpointIndex.getOrPut(quantizeEndpoint(ll)) { mutableListOf() }.add(seg)
    }

    private fun removeEndpoint(ll: LatLong, seg: Segment) {
        val key = quantizeEndpoint(ll)
        endpointIndex[key]?.let { segments ->
            segments.remove(seg)
            if (segments.isEmpty()) endpointIndex.remove(key)
        }
    }

    private fun quantizeEndpoint(ll: LatLong): Pair<Long, Long> {
        val q = 0.00001  // ~1m precision for endpoint matching
        return Pair((ll.latitude / q).toLong(), (ll.longitude / q).toLong())
    }

    private fun toCell(lat: Double, lon: Double): Pair<Int, Int> = Pair((lat / cellSizeDegrees).toInt(), (lon / cellSizeDegrees).toInt())

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = lon1 - lon2
        return Math.sqrt(dLat * dLat + dLon * dLon)
    }
}
