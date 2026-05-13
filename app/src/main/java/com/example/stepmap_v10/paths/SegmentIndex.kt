package com.example.stepMap_v10.paths

import org.mapsforge.core.model.LatLong

class SegmentIndex(segments: List<Segment>, private val cellSizeDegrees: Double = 0.001) {
    private val grid = HashMap<Pair<Int, Int>, MutableList<Segment>>()          // Maps a cell to close-by segments

    private val endpointIndex = HashMap<Pair<Long, Long>, MutableList<Segment>>()       // Maps starting and ending points to their connected segments


    init {
        for (segment in segments){
            cellsForSegment(segment).forEach { cell ->
                grid.getOrPut(cell) { mutableListOf() }.add(segment)
            }

            indexEndpoint(segment.startingPoint, segment)
            indexEndpoint(segment.endingPoint, segment)
        }
    }

    fun connectedSegments(segment: Segment): List<Segment> {
        /* Get all segments sharing endpoints with this segment */
        val result = mutableListOf<Segment>()
        result += endpointIndex[quantizeEndpoint(segment.startingPoint)] ?: emptyList()
        result += endpointIndex[quantizeEndpoint(segment.endingPoint)] ?: emptyList()
        return result.filter { it !== segment }
    }

    private fun cellsForSegment(segment: Segment): Set<Pair<Int, Int>> {
        // The cell spans these coordinates
        val latMin = minOf(segment.startingPoint.latitude, segment.endingPoint.latitude)
        val latMax = maxOf(segment.startingPoint.latitude, segment.endingPoint.latitude)
        val lonMin = minOf(segment.startingPoint.longitude, segment.endingPoint.longitude)
        val lonMax = maxOf(segment.startingPoint.longitude, segment.endingPoint.longitude)

        // Pairs of the corner coordinates:
        return setOf(
            toCell(latMin, lonMin),
            toCell(latMin, lonMax),
            toCell(latMax, lonMin),
            toCell(latMax, lonMax)
        )
    }


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

    private fun quantizeEndpoint(ll: LatLong): Pair<Long, Long> {
        val q = 0.00001  // ~1m precision for endpoint matching
        return Pair((ll.latitude / q).toLong(), (ll.longitude / q).toLong())
    }

    private fun toCell(lat: Double, lon: Double): Pair<Int, Int> = Pair((lat / cellSizeDegrees).toInt(), (lon / cellSizeDegrees).toInt())
}