package com.example.stepMap_v10.paths

import android.util.Log
import com.example.stepMap_v10.chains.PathChain
import com.example.stepMap_v10.chains.PathStorage
import org.mapsforge.core.graphics.Canvas
import com.example.stepMap_v10.tracking.MovementType
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline

// Traveled full paths
val traveledPathSegment = HashMap<Long, List<Segment>>()
fun recordTravel(segment: Segment, movementType: MovementType) {

}


/*HOW TO DISPLAY IT EFFICIENTLY:
- Have a separate routing layer for each movement type ? => TOO COMPLEX, START SIMPLY WITH ONLY DRAWING WALKING VS NOT WALKING, THEN EXPAND


 */


/* PATH UTILS */
fun List<Path>.toSegments(): List<Segment>{
    /* Splits a path into all its point-by-point segments */
    val segments = mutableListOf<Segment>()
    for (path in this){
        val points = path.points
        for (i in 0 until points.size - 1){
            segments.add(Segment(
                pathId = path.id,
                startingPoint = points[i],
                endingPoint = points[i-1],
                highway = path.highway,
                walkable = path.walkable,
                drivable = path.drivable,
                traveledBy = path.traveledBy
            ))
        }
    }

    return segments
}

/*
TOOD: FIX
fun findNearestSegment(lat: Double, lon: Double, index: SegmentIndex): Segment? {
    /* Scans the closest segments for the one with the smallest distance to the current point */

    val point = LatLong(lat, lon)
    val candidates = index.nearbySegments(lat, lon)
    return candidates.minByOrNull { pointToSegmentDistance(point, it) }
}*/


/* DRAW PATHS */

fun colorForMovementType(type: MovementType): Int {
    return when (type) {
        MovementType.STILL ->
            AndroidGraphicFactory.INSTANCE.createColor(255, 180, 180, 180)

        MovementType.WALKING ->
            AndroidGraphicFactory.INSTANCE.createColor(255, 255, 165, 0) // orange

        MovementType.RUNNING ->
            AndroidGraphicFactory.INSTANCE.createColor(255, 255, 0, 0) // red

        MovementType.BIKING ->
            AndroidGraphicFactory.INSTANCE.createColor(255, 0, 150, 255) // blue

        MovementType.TRANSPORT ->
            AndroidGraphicFactory.INSTANCE.createColor(255, 120, 120, 120) // gray
    }
}


fun walkedPathStrokeWidth(mapView: MapView, inputWidth: Float): Float {
    val zoom = mapView.model.mapViewPosition.zoomLevel.toFloat()

    Log.d("StepByStep_v1.0_TAG", "Current zoom = $zoom")

    //TODO: DELETE FUNCTION AND REPLACE WITH STROKEWIDTH
    return strokeWidthComputer(zoom)
}

fun strokeWidthComputer(zoom: Float): Float {
    val inputWidth = 20f

    return when (zoom) {
        13f -> 2.5f
        14f -> 5f
        15f -> 10f
        16f -> 15f
        17f -> 25f
        18f -> 35f
        19f -> 50f
        20f -> 75f
        else -> {
            inputWidth
        }
    }
}


/* HELPERS */
fun findNearestSegment(lat: Double, lon: Double, index: SegmentIndex): Segment? {
    val point = LatLong(lat, lon)
    val candidates = index.nearbySegments(lat, lon)
    return candidates.minByOrNull { pointToSegmentDistance(point, it) }
}

fun pointToSegmentDistance(point: LatLong, segment: Segment): Double {
    /* Computes the distance between the point and the segment */

    val startingLon = segment.startingPoint.longitude;  val startingLat = segment.startingPoint.latitude
    val endingLon = segment.endingPoint.longitude;    val endingLat = segment.endingPoint.latitude
    val pointLon = point.longitude;          val pointLat = point.latitude

    // Compute the squared difference between the starting and ending points of the segment
    val dLon = endingLon - startingLon;  val dLat = endingLat - startingLat
    val lenSq = dLon * dLon + dLat * dLat

    val t =
        if (lenSq == 0.0) 0.0
        else ((pointLon - startingLon) * dLon + (pointLat - startingLat) * dLat) / lenSq
    val tClamped = t.coerceIn(0.0, 1.0)

    val closestLon = startingLon + tClamped * dLon
    val closestLat = startingLat + tClamped * dLat

    val lonDiff = pointLon - closestLon;  val latDiff = pointLat - closestLat

    return Math.sqrt(lonDiff * lonDiff + latDiff * latDiff)
}
