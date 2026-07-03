package io.github.pwlski04.swissstep.paths

import io.github.pwlski04.swissstep.tracking.MovementType
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import io.github.pwlski04.swissstep.colorMap
import io.github.pwlski04.swissstep.defaultColorMap

/* DRAW PATHS */

fun colorForMovementType(movementType: MovementType, useCustomColors: Boolean): Int {
    return if (useCustomColors) {
        colorMap[movementType] ?: defaultColorMap[movementType] ?: AndroidGraphicFactory.INSTANCE.createColor(255, 255, 255, 255)
    } else {
        defaultColorMap[movementType] ?: AndroidGraphicFactory.INSTANCE.createColor(255, 255, 255, 255)
    }
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
        else -> { inputWidth }
    }
}


/* HELPERS */
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
