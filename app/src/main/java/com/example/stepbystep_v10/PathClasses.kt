package com.example.stepbystep_v10

import org.mapsforge.core.model.LatLong


data class Path(
    //Store each path as an ID and a list of the coordinates belonging to it
    val id: Long,
    val points: List<LatLong>
)

data class PathPoint (
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val sessionId: Long
)

data class MatchedSegment(
    val path: Path,
    val segmentIndex: Int,
    val distanceMeters: Double
)

data class IndexedSegment(
    val path: Path,
    val segmentIndex: Int,
    val ax: Double,
    val ay: Double,
    val bx: Double,
    val by: Double,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
)
