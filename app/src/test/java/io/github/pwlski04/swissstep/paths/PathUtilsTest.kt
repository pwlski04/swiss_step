package io.github.pwlski04.swissstep.paths

import org.mapsforge.core.model.LatLong
import org.junit.Assert.assertEquals
import org.junit.Test

class PathUtilsTest {

    private fun segment(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double
    ) = Segment(
        pathId = 0L,
        startingPoint = LatLong(startLat, startLon),
        endingPoint = LatLong(endLat, endLon),
        highway = "footway",
        walkable = true,
        drivable = false
    )

    @Test
    fun `point on the segment has zero distance`() {
        val seg = segment(0.0, 0.0, 0.0, 1.0)
        val point = LatLong(0.0, 0.5)

        assertEquals(0.0, pointToSegmentDistance(point, seg), 1e-9)
    }

    @Test
    fun `point beyond the segment clamps to the nearest endpoint`() {
        val seg = segment(0.0, 0.0, 0.0, 1.0)
        val point = LatLong(0.0, 2.0) // past the "end" endpoint

        // Clamped to (0.0, 1.0), so the distance is just the overshoot past the endpoint
        assertEquals(1.0, pointToSegmentDistance(point, seg), 1e-9)
    }

    @Test
    fun `perpendicular offset from the segment equals the offset distance`() {
        val seg = segment(0.0, 0.0, 0.0, 1.0) // horizontal segment along longitude
        val point = LatLong(0.5, 0.5) // directly "above" the midpoint

        assertEquals(0.5, pointToSegmentDistance(point, seg), 1e-9)
    }

    @Test
    fun `zero length segment measures distance to its single point`() {
        val seg = segment(0.0, 0.0, 0.0, 0.0)
        val point = LatLong(3.0, 4.0)

        assertEquals(5.0, pointToSegmentDistance(point, seg), 1e-9)
    }

    @Test
    fun `stroke width follows the fixed lookup table for known zoom levels`() {
        assertEquals(2.5f, strokeWidthComputer(13f), 0f)
        assertEquals(5f, strokeWidthComputer(14f), 0f)
        assertEquals(10f, strokeWidthComputer(15f), 0f)
        assertEquals(15f, strokeWidthComputer(16f), 0f)
        assertEquals(25f, strokeWidthComputer(17f), 0f)
        assertEquals(35f, strokeWidthComputer(18f), 0f)
        assertEquals(50f, strokeWidthComputer(19f), 0f)
        assertEquals(75f, strokeWidthComputer(20f), 0f)
    }

    @Test
    fun `stroke width falls back to the default outside the known zoom levels`() {
        assertEquals(20f, strokeWidthComputer(5f), 0f)
        assertEquals(20f, strokeWidthComputer(21f), 0f)
    }
}
