package com.example.stepMap_v10.paths

class SegmentIndex(segments: List<Segment>, private val cellSizeDegrees: Double = 0.001) {
    private val grid = HashMap<Pair<Int, Int>, MutableList<Segment>>()          // Maps a cell to close-by segments

    init {
        for (segment in segments){
            cellsForSegment(segment).forEach { cell ->
                grid.getOrPut(cell) { mutableListOf() }.add(segment)
            }
        }
    }

    private fun toCell(lat: Double, lon: Double): Pair<Int, Int> = Pair((lat / cellSizeDegrees).toInt(), (lon / cellSizeDegrees).toInt())

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
}