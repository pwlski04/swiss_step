package com.example.stepMap_v10.map

import android.content.Context
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import java.io.File

import org.mapsforge.map.rendertheme.ExternalRenderTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes

import org.mapsforge.map.layer.overlay.Polyline

import org.mapsforge.map.layer.overlay.Marker
import android.util.Log
import com.example.stepMap_v10.paths.MatchedSegment
import com.example.stepMap_v10.tracking.MovementType
import com.example.stepMap_v10.tracking.isSlowerThanOrEqual
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.paths.PathPoint
import com.example.stepMap_v10.paths.saveWalkedSegments
import org.mapsforge.core.graphics.Style
import kotlin.math.abs

private val drawnSegmentLayers = mutableMapOf<String, Polyline>()
private val partialSegmentLayers = mutableMapOf<String, Polyline>()
data class SegmentProgress(
    val minProgress: Double,
    val maxProgress: Double,
    val movementType: MovementType
)

data class LastMatchedPosition(
    val pathId: Long,
    val segmentIndex: Int,
    val progress: Double
)

private const val ZURICH_MIN_LAT = 47.32
private const val ZURICH_MAX_LAT = 47.43
private const val ZURICH_MIN_LON = 8.44
private const val ZURICH_MAX_LON = 8.63

private const val RUBBER_BAND_LAT = 0.0035
private const val RUBBER_BAND_LON = 0.0055


/* CREATE MAP: */
fun createMapView(context: Context, mapFilePath: String, themeFilePath: String): MapView {
    val mapFileOnDisk = File(mapFilePath)
    require(mapFileOnDisk.exists()) { "Map file does not exist: $mapFilePath" }
    require(mapFileOnDisk.length() > 0L) { "Map file is empty: $mapFilePath" }

    val mapDataStore = MapFile(mapFileOnDisk)

    val mapView = MapView(context)
    mapView.setBuiltInZoomControls(false)
    mapView.mapScaleBar.isVisible = false

    val tileCache: TileCache = AndroidUtil.createTileCache(
        context,
        "mapcache",
        mapView.model.displayModel.tileSize,
        1f,
        mapView.model.frameBufferModel.overdrawFactor
    )

    val tileRendererLayer = TileRendererLayer(
        tileCache,
        mapDataStore,
        mapView.model.mapViewPosition,
        AndroidGraphicFactory.INSTANCE
    )

    val themeFile = File(themeFilePath)
    require(themeFile.exists()) { "Theme file does not exist: $themeFilePath" }
    require(themeFile.length() > 0L) { "Theme file is empty: $themeFilePath" }

    try {
        tileRendererLayer.setXmlRenderTheme(ExternalRenderTheme(File(themeFilePath)))
    } catch (e: Exception) {
        e.printStackTrace()
        tileRendererLayer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
    }

    mapView.layerManager.layers.add(tileRendererLayer)

    mapView.setZoomLevelMin(13.toByte())
    mapView.setZoomLevelMax(20.toByte())
    mapView.setCenter(LatLong(47.3769, 8.5417))
    mapView.setZoomLevel(13.toByte())

    return mapView
}


fun applySmoothMapForceField(mapView: MapView) {
    val center = mapView.model.mapViewPosition.center ?: return

    val softMinLat = ZURICH_MIN_LAT - RUBBER_BAND_LAT
    val softMaxLat = ZURICH_MAX_LAT + RUBBER_BAND_LAT
    val softMinLon = ZURICH_MIN_LON - RUBBER_BAND_LON
    val softMaxLon = ZURICH_MAX_LON + RUBBER_BAND_LON

    /*
     * Hard limit the maximum overscroll.
     * This prevents fast swipes from flying far outside the map.
     */
    val limitedLat = center.latitude.coerceIn(softMinLat, softMaxLat)
    val limitedLon = center.longitude.coerceIn(softMinLon, softMaxLon)

    var targetLat = limitedLat
    var targetLon = limitedLon

    val outsideAmount = getOutsideAmount(
        LatLong(
            limitedLat,
            limitedLon
        )
    )

    /*
     * Dynamic force:
     * tiny outside = soft
     * far outside = strong resistance
     */
    val pullStrength = when {
        outsideAmount > 0.006 -> 0.55
        outsideAmount > 0.003 -> 0.35
        else -> 0.18
    }

    if (limitedLat < ZURICH_MIN_LAT) {
        targetLat = limitedLat + (ZURICH_MIN_LAT - limitedLat) * pullStrength
    } else if (limitedLat > ZURICH_MAX_LAT) {
        targetLat = limitedLat - (limitedLat - ZURICH_MAX_LAT) * pullStrength
    }

    if (limitedLon < ZURICH_MIN_LON) {
        targetLon = limitedLon + (ZURICH_MIN_LON - limitedLon) * pullStrength
    } else if (limitedLon > ZURICH_MAX_LON) {
        targetLon = limitedLon - (limitedLon - ZURICH_MAX_LON) * pullStrength
    }

    val needsCorrection =
        abs(targetLat - center.latitude) > 0.000003 ||
                abs(targetLon - center.longitude) > 0.000003

    if (needsCorrection) {
        mapView.setCenter(
            LatLong(
                targetLat,
                targetLon
            )
        )
    }
}

fun getOutsideAmount(center: LatLong): Double {
    val latOutside = when {
        center.latitude < ZURICH_MIN_LAT -> ZURICH_MIN_LAT - center.latitude
        center.latitude > ZURICH_MAX_LAT -> center.latitude - ZURICH_MAX_LAT
        else -> 0.0
    }

    val lonOutside = when {
        center.longitude < ZURICH_MIN_LON -> ZURICH_MIN_LON - center.longitude
        center.longitude > ZURICH_MAX_LON -> center.longitude - ZURICH_MAX_LON
        else -> 0.0
    }

    return maxOf(latOutside, lonOutside)
}

/* OVERLAY PATHS: DISPLAY WALKED PATHS */
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

fun updateWalkedPathFromCurrentLocation(context: Context, mapView: MapView, currentPoint: PathPoint, walkedSegments: MutableMap<String, MovementType>, partialProgress: MutableMap<String, SegmentProgress>, segmentIndex: SegmentGridIndex?, movementType: MovementType, pathWidth: Float, lastMatchedPosition: LastMatchedPosition?): LastMatchedPosition? {
    val index = segmentIndex ?: return lastMatchedPosition

    val currSegment = index.findClosest(
        currentPoint,
        maxDistanceMeters = 10.0
    ) ?: return lastMatchedPosition

    if (movementType != MovementType.TRANSPORT && !currSegment.path.walkable) {
        return lastMatchedPosition
    }

    if (movementType == MovementType.TRANSPORT && !currSegment.path.drivable) {
        return lastMatchedPosition
    }

    val currentProgress = progressOnSegment(
        point = currentPoint,
        segment = currSegment
    )

    val currentPosition = LastMatchedPosition(
        pathId = currSegment.path.id,
        segmentIndex = currSegment.segmentIndex,
        progress = currentProgress
    )

    fillGapBetweenMatches(context, mapView, currSegment.path, lastMatchedPosition, currentPosition, walkedSegments, partialProgress, movementType, pathWidth)

    mapView.layerManager.redrawLayers()

    return currentPosition
}

fun fillGapBetweenMatches(context: Context, mapView: MapView, path: Path, from: LastMatchedPosition?, to: LastMatchedPosition, walkedSegments: MutableMap<String, MovementType>, partialProgress: MutableMap<String, SegmentProgress>, movementType: MovementType, pathWidth: Float) {
    if (from == null || from.pathId != to.pathId) {
        drawProgressOnSegment(mapView, path, to.segmentIndex, to.progress, to.progress, partialProgress, movementType, pathWidth)
        return
    }

    val fromSegment = from.segmentIndex
    val toSegment = to.segmentIndex

    if (fromSegment == toSegment) {
        drawProgressOnSegment(mapView, path, toSegment, minOf(from.progress, to.progress), maxOf(from.progress, to.progress), partialProgress, movementType, pathWidth)
        return
    }

    if (toSegment > fromSegment) {
        drawProgressOnSegment(mapView, path, fromSegment, from.progress, 1.0, partialProgress, movementType, pathWidth)

        for (segmentIndex in fromSegment + 1 until toSegment) {
            completeSegment(context, mapView, path, segmentIndex, walkedSegments, partialProgress, movementType, pathWidth)
        }

        drawProgressOnSegment(mapView, path, toSegment, 0.0, to.progress, partialProgress, movementType, pathWidth)
    } else {
        drawProgressOnSegment(mapView, path, fromSegment, 0.0, from.progress, partialProgress, movementType, pathWidth)

        for (segmentIndex in toSegment + 1 until fromSegment) {
            completeSegment(context, mapView, path, segmentIndex, walkedSegments, partialProgress, movementType, pathWidth)
        }

        drawProgressOnSegment(mapView, path, toSegment, to.progress, 1.0, partialProgress, movementType, pathWidth)
    }
}

fun drawProgressOnSegment(mapView: MapView, path: Path, segmentIndex: Int, minProgress: Double, maxProgress: Double, partialProgress: MutableMap<String, SegmentProgress>, movementType: MovementType, pathWidth: Float) {
    val safeMinProgress = minProgress.coerceIn(0.0, 1.0)
    val safeMaxProgress = maxProgress.coerceIn(0.0, 1.0)

    val segmentId = "${path.id}:$segmentIndex"

    val oldProgress = partialProgress[segmentId]

    val newProgress =
        if (oldProgress == null) {
            SegmentProgress(
                minProgress = minOf(safeMinProgress, safeMaxProgress),
                maxProgress = maxOf(safeMinProgress, safeMaxProgress),
                movementType = movementType
            )
        } else {
            SegmentProgress(
                minProgress = minOf(oldProgress.minProgress, safeMinProgress, safeMaxProgress),
                maxProgress = maxOf(oldProgress.maxProgress, safeMinProgress, safeMaxProgress),
                movementType = movementType
            )
        }

    partialProgress[segmentId] = newProgress

    val segStart = path.points[segmentIndex]
    val segEnd = path.points[segmentIndex + 1]

    drawOrReplacePartialSegment(mapView, segmentId, segStart, segEnd, newProgress, pathWidth)
}

fun completeSegment(context: Context, mapView: MapView, path: Path, segmentIndex: Int, walkedSegments: MutableMap<String, MovementType>, partialProgress: MutableMap<String, SegmentProgress>, movementType: MovementType, pathWidth: Float) {
    val segmentId = "${path.id}:$segmentIndex"

    val oldMovementType = walkedSegments[segmentId]

    if (oldMovementType != null) {
        val newTypeIsSlower = isSlowerThanOrEqual(
            movementType,
            oldMovementType
        )

        if (!newTypeIsSlower) {
            return
        }
    }

    walkedSegments[segmentId] = movementType
    saveWalkedSegments(context, walkedSegments)

    partialProgress.remove(segmentId)

    val oldPartialLayer = partialSegmentLayers[segmentId]
    if (oldPartialLayer != null) {
        mapView.layerManager.layers.remove(oldPartialLayer)
        partialSegmentLayers.remove(segmentId)
    }

    val segStart = path.points[segmentIndex]
    val segEnd = path.points[segmentIndex + 1]

    drawOrReplaceSegment(mapView, segmentId, segStart, segEnd, movementType, pathWidth)
}

fun drawOrReplacePartialSegment(mapView: MapView, segmentId: String, segStart: LatLong, segEnd: LatLong, progress: SegmentProgress, pathWidth: Float) {
    val oldLayer = partialSegmentLayers[segmentId]

    if (oldLayer != null) {
        mapView.layerManager.layers.remove(oldLayer)
    }

    val partialStart = interpolateLatLong(segStart, segEnd, progress.minProgress)

    val partialEnd = interpolateLatLong(segStart, segEnd, progress.maxProgress)

    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        color = colorForMovementType(progress.movementType)
        strokeWidth = walkedPathStrokeWidth(mapView, pathWidth)
        setStyle(Style.STROKE)
    }

    val polyline = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
        latLongs.add(partialStart)
        latLongs.add(partialEnd)
    }

    partialSegmentLayers[segmentId] = polyline
    mapView.layerManager.layers.add(polyline)
}

fun walkedPathStrokeWidth(mapView: MapView, inputWidth: Float): Float {
    val zoom = mapView.model.mapViewPosition.zoomLevel.toFloat()

    Log.d("StepByStep_v1.0_TAG", "Current zoom = $zoom")

    return when (zoom){
        13f -> 2.5f
        14f -> 5f
        15f -> 10f
        16f -> 15f
        17f -> 25f
        18f -> 35f
        19f -> 50f
        20f -> 75f
        else -> {inputWidth}
    }
}


/* REMOVE OVERLAYS: DOTS AND PATHS */

fun removeWalkedRoutes(mapView: MapView){
    val layers = mapView.layerManager.layers

    val toRemove = layers.filter { layer ->
        layer is Marker || layer is Polyline
    }

    toRemove.forEach { layer ->
        layers.remove(layer)
    }

    drawnSegmentLayers.clear()
    partialSegmentLayers.clear()

    mapView.layerManager.redrawLayers()
}



/* HELPER FUNCTIONS */
fun copyAssetToInternalStorage(context: Context, assetName: String): String {
    """ Copies a file into the device's internal storage """

    // Create the file/directory
    val outFile = File(context.filesDir, assetName)

    // Copy the file over
    if (!outFile.exists() || outFile.length() == 0L) {
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    // Return the copied file
    return outFile.absolutePath
}

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

fun progressOnSegment(point: PathPoint, segment: MatchedSegment): Double {
    val segStart = segment.path.points[segment.segmentIndex]
    val segEnd = segment.path.points[segment.segmentIndex + 1]

    val originLat = 47.3769
    val latScale = 111_320.0
    val lonScale = 111_320.0 * kotlin.math.cos(Math.toRadians(originLat))

    val px = point.lon * lonScale
    val py = point.lat * latScale

    val ax = segStart.longitude * lonScale
    val ay = segStart.latitude * latScale

    val bx = segEnd.longitude * lonScale
    val by = segEnd.latitude * latScale

    val dx = bx - ax
    val dy = by - ay

    if (dx == 0.0 && dy == 0.0) return 0.0

    val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)

    return t.coerceIn(0.0, 1.0)
}