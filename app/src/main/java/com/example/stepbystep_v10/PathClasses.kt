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