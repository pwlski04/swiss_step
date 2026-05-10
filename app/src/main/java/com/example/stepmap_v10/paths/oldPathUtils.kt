package com.example.stepMap_v10.paths

import com.example.stepMap_v10.tracking.MovementType
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import kotlin.collections.set



/* OLD

private val drawnSegmentLayers = mutableMapOf<String, Polyline>()
private val partialSegmentLayers = mutableMapOf<String, Polyline>()



fun drawWalkedSegments(mapView: MapView, allPaths: List<Path>, walkedSegments: Map<String, MovementType>, pathWidth: Float) {
    drawnSegmentLayers.values.toList().forEach { oldLayer ->
        mapView.layerManager.layers.remove(oldLayer)
    }

    drawnSegmentLayers.clear()

    for (path in allPaths) {
        for (i in 0 until path.points.size - 1) {
            val segmentId = "${path.id}:$i"
            val movementType = walkedSegments[segmentId] ?: continue

            drawOrReplaceSegment(mapView, segmentId, path.points[i], path.points[i + 1], movementType, pathWidth)
        }
    }

    mapView.layerManager.redrawLayers()
}


fun drawOrReplaceSegment(mapView: MapView, segmentId: String, segStart: LatLong, segEnd: LatLong, movementType: MovementType, pathWidth: Float) {
    val oldLayer = drawnSegmentLayers[segmentId]

    if (oldLayer != null) {
        mapView.layerManager.layers.remove(oldLayer)
    }

    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        color = colorForMovementType(movementType)
        strokeWidth = walkedPathStrokeWidth(mapView, pathWidth)
        setStyle(Style.STROKE)
    }

    val polyline = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
        latLongs.add(segStart)
        latLongs.add(segEnd)
    }

    drawnSegmentLayers[segmentId] = polyline
    mapView.layerManager.layers.add(polyline)
}

fun removeWalkedRoutes(mapView: MapView){
    val layers = mapView.layerManager.layers

    val toRemove = layers.filter { layer ->
        layer is Marker || layer is Polyline
    }

    toRemove.forEach { layer ->
        layers.remove(layer)
    }

    //traveledPaths.clear()
    mapView.layerManager.redrawLayers()
}


/* OTHER */

fun distancePointToSegmentSquared(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val abx = bx - ax
    val aby = by - ay
    val apx = px - ax
    val apy = py - ay

    val abLenSq = abx * abx + aby * aby
    if (abLenSq == 0.0) {
        val dx = px - ax
        val dy = py - ay
        return dx * dx + dy * dy
    }

    val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)

    val closestX = ax + t * abx
    val closestY = ay + t * aby

    val dx = px - closestX
    val dy = py - closestY

    return dx * dx + dy * dy
}

fun interpolateLatLong(start: LatLong, end: LatLong, progress: Double): LatLong {
    val t = progress.coerceIn(0.0, 1.0)

    val lat = start.latitude + (end.latitude - start.latitude) * t
    val lon = start.longitude + (end.longitude - start.longitude) * t

    return LatLong(lat, lon)
}
*/