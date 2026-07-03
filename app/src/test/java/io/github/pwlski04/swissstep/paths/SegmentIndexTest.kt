package io.github.pwlski04.swissstep.paths

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSegmentSource(private val rows: List<SegmentRow>) : SegmentSource {
    var queryCount = 0
        private set

    override fun querySegmentsInCellRange(
        minCellX: Int, maxCellX: Int,
        minCellY: Int, maxCellY: Int
    ): List<SegmentRow> {
        queryCount++
        val cellSize = 0.001
        return rows.filter { row ->
            val cells = cellsFor(row.startLat, row.startLon, row.endLat, row.endLon, cellSize)
            cells.any { (cx, cy) -> cx in minCellX..maxCellX && cy in minCellY..maxCellY }
        }
    }

    private fun cellsFor(lat1: Double, lon1: Double, lat2: Double, lon2: Double, cellSize: Double): List<Pair<Int, Int>> {
        val x1 = (minOf(lat1, lat2) / cellSize).toInt()
        val x2 = (maxOf(lat1, lat2) / cellSize).toInt()
        val y1 = (minOf(lon1, lon2) / cellSize).toInt()
        val y2 = (maxOf(lon1, lon2) / cellSize).toInt()
        return (x1..x2).flatMap { x -> (y1..y2).map { y -> x to y } }
    }
}

class SegmentIndexTest {

    // A long diagonal segment whose bounding box spans several 0.001-degree cells,
    // not just the 4 corners - regression test for cellsForSegment only registering
    // the corners.
    private val longSegmentRow = SegmentRow(
        id = 1L,
        startLat = 47.000, startLon = 8.000,
        endLat = 47.005, endLon = 8.005,
        highway = "path", walkable = true, drivable = false
    )

    @Test
    fun `a long segment is discoverable from a middle cell, not just its corners`() {
        val index = SegmentIndex(FakeSegmentSource(listOf(longSegmentRow)))

        index.ensureLoaded(47.000, 8.000, "test")

        // The segment's bbox runs from (47.000,8.000) to (47.005,8.005) - a middle
        // point along the diagonal, away from either endpoint's own cell.
        val fromMiddleCell = index.nearbySegments(47.0025, 8.0025, radius = 0)
        assertTrue(
            "expected the long segment to be registered under a middle cell it overlaps",
            fromMiddleCell.isNotEmpty()
        )
    }

    @Test
    fun `reloading an already-resident segment returns the same instance`() {
        val source = FakeSegmentSource(listOf(longSegmentRow))
        val index = SegmentIndex(source)

        // nearbySegments() can legitimately return the same segment more than once
        // (it's registered under every cell its bbox touches, and radius=1 scans a
        // 3x3 block of cells) - that's pre-existing, tolerated-by-callers behavior,
        // not what this test is checking. This test only cares that every instance
        // returned for this row's id is the exact same object.
        index.ensureLoaded(47.000, 8.000, "test")
        val firstBatch = index.nearbySegments(47.000, 8.000)
        val first = firstBatch.first()
        assertTrue(firstBatch.all { it === first })

        // Move past RELOAD_TRIGGER_DISTANCE but stay within the segment's own bbox,
        // so this forces a requery that overlaps the same segment's cells again.
        index.ensureLoaded(47.004, 8.004, "test")
        val secondBatch = index.nearbySegments(47.000, 8.000)
        val second = secondBatch.first()
        assertTrue(secondBatch.all { it === second })

        assertSame(
            "PathStorage compares segments with ===; the same DB row must always yield the same instance",
            first, second
        )
    }

    @Test
    fun `a segment far from every active window is evicted`() {
        val nearRow = SegmentRow(
            id = 1L,
            startLat = 47.000, startLon = 8.000, endLat = 47.0001, endLon = 8.0001,
            highway = "path", walkable = true, drivable = false
        )
        // Far enough away (~1 degree, >> EVICT_HALF_WIDTH) that it should never be
        // considered resident once the window has moved away from it.
        val farRow = SegmentRow(
            id = 2L,
            startLat = 46.000, startLon = 7.000, endLat = 46.0001, endLon = 7.0001,
            highway = "path", walkable = true, drivable = false
        )
        val index = SegmentIndex(FakeSegmentSource(listOf(nearRow, farRow)))

        index.ensureLoaded(47.000, 8.000, "test")
        assertTrue(index.nearbySegments(47.000, 8.000).isNotEmpty())

        // Move the same window far away repeatedly (each call must exceed
        // RELOAD_TRIGGER_DISTANCE to force a requery+evict pass).
        var lat = 47.000
        var lon = 8.000
        repeat(50) {
            lat -= 0.01
            lon -= 0.01
            index.ensureLoaded(lat, lon, "test")
        }

        assertTrue(
            "segment far from the window's new location should have been evicted",
            index.nearbySegments(47.000, 8.000).isEmpty()
        )
    }

    @Test
    fun `two independent windows keep each other's segments resident`() {
        val bernRow = SegmentRow(
            id = 1L,
            startLat = 46.948, startLon = 7.447, endLat = 46.9481, endLon = 7.4471,
            highway = "path", walkable = true, drivable = false
        )
        val zurichRow = SegmentRow(
            id = 2L,
            startLat = 47.376, startLon = 8.541, endLat = 47.3761, endLon = 8.5411,
            highway = "path", walkable = true, drivable = false
        )
        val index = SegmentIndex(FakeSegmentSource(listOf(bernRow, zurichRow)))

        index.ensureLoaded(46.948, 7.447, "live")
        index.ensureLoaded(47.376, 8.541, "replay")

        assertTrue(
            "the 'live' window's segment must survive a 'replay' window being active elsewhere",
            index.nearbySegments(46.948, 7.447).isNotEmpty()
        )
        assertTrue(
            "the 'replay' window's segment must survive the 'live' window being active elsewhere",
            index.nearbySegments(47.376, 8.541).isNotEmpty()
        )
    }

    @Test
    fun `ensureLoaded is a no-op once a window hasn't moved past the reload trigger`() {
        val source = FakeSegmentSource(listOf(longSegmentRow))
        val index = SegmentIndex(source)

        index.ensureLoaded(47.000, 8.000, "test")
        val queriesAfterFirstLoad = source.queryCount

        // Well within RELOAD_TRIGGER_DISTANCE (~330m / 0.003 degrees).
        index.ensureLoaded(47.0001, 8.0001, "test")

        assertEquals(
            "a tiny move shouldn't trigger another DB query",
            queriesAfterFirstLoad, source.queryCount
        )
    }
}
