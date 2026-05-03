package com.example.stepbystep_v10.map

import com.example.stepbystep_v10.map.paths.IndexedSegment
import com.example.stepbystep_v10.map.paths.MatchedSegment
import com.example.stepbystep_v10.map.paths.Path
import com.example.stepbystep_v10.map.paths.PathPoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

class SegmentGridIndex(
    paths: List<Path>,
    private val projector: LocalProjector,
    private val cellSizeMeters: Double = 20.0
) {
    private val grid = HashMap<Pair<Int, Int>, MutableList<IndexedSegment>>()

    private fun cell(value: Double): Int {
        return floor(value / cellSizeMeters).toInt()
    }

    init {
        for (path in paths) {
            for (i in 0 until path.points.size - 1) {
                val a = path.points[i]
                val b = path.points[i + 1]

                val ax = projector.x(a.longitude)
                val ay = projector.y(a.latitude)
                val bx = projector.x(b.longitude)
                val by = projector.y(b.latitude)

                val seg = IndexedSegment(
                    path = path,
                    segmentIndex = i,
                    ax = ax,
                    ay = ay,
                    bx = bx,
                    by = by,
                    minX = minOf(ax, bx),
                    maxX = maxOf(ax, bx),
                    minY = minOf(ay, by),
                    maxY = maxOf(ay, by)
                )

                val minCellX = cell(seg.minX)
                val maxCellX = cell(seg.maxX)
                val minCellY = cell(seg.minY)
                val maxCellY = cell(seg.maxY)

                for (cx in minCellX..maxCellX) {
                    for (cy in minCellY..maxCellY) {
                        grid.getOrPut(cx to cy) { mutableListOf() }.add(seg)
                    }
                }
            }
        }
    }

    fun findClosest(point: PathPoint, maxDistanceMeters: Double): MatchedSegment? {
        val px = projector.x(point.lon)
        val py = projector.y(point.lat)

        val cx = cell(px)
        val cy = cell(py)

        val radiusCells = ceil(maxDistanceMeters / cellSizeMeters).toInt() + 1

        var bestSegment: IndexedSegment? = null
        var bestDistanceSquared = maxDistanceMeters * maxDistanceMeters

        for (dx in -radiusCells..radiusCells) {
            for (dy in -radiusCells..radiusCells) {
                val candidates = grid[cx + dx to cy + dy] ?: continue

                for (seg in candidates) {
                    val distSq = distancePointToSegmentSquared(
                        px, py,
                        seg.ax, seg.ay,
                        seg.bx, seg.by
                    )

                    if (distSq < bestDistanceSquared) {
                        bestDistanceSquared = distSq
                        bestSegment = seg
                    }
                }
            }
        }

        return bestSegment?.let {
            MatchedSegment(
                path = it.path,
                segmentIndex = it.segmentIndex,
                distanceMeters = sqrt(bestDistanceSquared)
            )
        }
    }
}