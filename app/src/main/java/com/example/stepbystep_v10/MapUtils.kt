package com.example.stepbystep_v10

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

import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.overlay.Polyline

import android.graphics.Canvas
import android.graphics.Paint as AndroidPaint
import org.mapsforge.core.graphics.Bitmap as MapsforgeBitmap
import org.mapsforge.map.layer.overlay.Marker
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.core.graphics.createBitmap


private val drawnSegmentLayers = mutableMapOf<String, Polyline>()



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
    mapView.setCenter(LatLong(47.3769, 8.5417))
    mapView.setZoomLevel(13.toByte())

    return mapView
}



/* ADD OVERLAY DOTS: EXACT LCOATION FOR POSITION TRACKING */
fun createDotBitmap(context: Context, sizePx: Int, red: Int, green: Int, blue: Int): MapsforgeBitmap {
    val androidBitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(androidBitmap)

    val paint = AndroidPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(255, red, green, blue)
        style = AndroidPaint.Style.FILL
    }

    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius, paint)

    return AndroidGraphicFactory.convertToBitmap(BitmapDrawable(androidBitmap))
}

fun addLatestDotIfNeeded(context: Context, mapView: MapView, points: List<PathPoint>, walkedSegments: MutableMap<String, MovementType>, segmentIndex: SegmentGridIndex?, movementType: MovementType) {
    if (points.isEmpty()) return

    val latestPoint = points.last()

    val alreadyHasNearbyDot = points.dropLast(1).any { oldPoint ->
        distanceMeters(oldPoint.lat, oldPoint.lon, latestPoint.lat, latestPoint.lon) < 3.3
    }

    if (alreadyHasNearbyDot) return

    val latestIndex = points.size - 1
    val latestIsRed = latestIndex % 2 == 0

    val sizePx = 72

    val dotBitmap = if (latestIsRed) {
        createDotBitmap(context, sizePx, red = 255, green = 0, blue = 0)
    } else {
        createDotBitmap(context, sizePx, red = 0, green = 255, blue = 0)
    }

    val marker = Marker(
        LatLong(latestPoint.lat, latestPoint.lon),
        dotBitmap,
        0,
        0
    )

    segmentIndex?.let { index ->
        addPathIfNeeded(
            context,
            mapView,
            latestPoint,
            walkedSegments,
            segmentIndex,
            maxDistanceMeters = 3.0,
            movementType
        )
    }

    mapView.layerManager.layers.add(marker)
    mapView.layerManager.redrawLayers()
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

fun addPathIfNeeded(context: Context, mapView: MapView, point: PathPoint, walkedSegments: MutableMap<String, MovementType>, segmentIndex: SegmentGridIndex, maxDistanceMeters: Double, movementType: MovementType) {
    val currSegment = segmentIndex.findClosest(point, maxDistanceMeters) ?: return

    val segmentId = "${currSegment.path.id}:${currSegment.segmentIndex}"

    val oldMovementType = walkedSegments[segmentId]

    if (oldMovementType != null) {
        val newTypeIsSlower = isSlowerThanOrEqual(movementType, oldMovementType)

        if (!newTypeIsSlower) {
            return
        }
    }

    walkedSegments[segmentId] = movementType
    saveWalkedSegments(context, walkedSegments)

    val segStart = currSegment.path.points[currSegment.segmentIndex]
    val segEnd = currSegment.path.points[currSegment.segmentIndex + 1]

    drawOrReplaceSegment(mapView, segmentId, segStart, segEnd, movementType)
    mapView.layerManager.redrawLayers()
}

fun drawWalkedSegments(mapView: MapView, allPaths: List<Path>, walkedSegments: Map<String, MovementType>) {
    drawnSegmentLayers.clear()

    for (path in allPaths) {
        for (i in 0 until path.points.size - 1) {
            val segmentId = "${path.id}:$i"
            val movementType = walkedSegments[segmentId] ?: continue

            drawOrReplaceSegment(mapView, segmentId, path.points[i], path.points[i + 1], movementType)
        }
    }

    mapView.layerManager.redrawLayers()
}


fun drawOrReplaceSegment(
    mapView: MapView,
    segmentId: String,
    segStart: LatLong,
    segEnd: LatLong,
    movementType: MovementType
) {
    val oldLayer = drawnSegmentLayers[segmentId]

    if (oldLayer != null) {
        mapView.layerManager.layers.remove(oldLayer)
    }

    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        color = colorForMovementType(movementType)
        strokeWidth = 8f
        setStyle(org.mapsforge.core.graphics.Style.STROKE)
    }

    val polyline = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
        latLongs.add(segStart)
        latLongs.add(segEnd)
    }

    drawnSegmentLayers[segmentId] = polyline
    mapView.layerManager.layers.add(polyline)
}



/* REMOVE OVERLAYS: CURRENTLY ONLY THE DOTS */
fun removeRouteLayers(mapView: MapView){
    val layers = mapView.layerManager.layers
    val toRemove = mutableListOf<Layer>()

    for(layer in layers){
        if(layer is Marker){
            toRemove.add(layer)
        }
    }

    for(layer in toRemove){
        layers.remove(layer)
    }
}

fun removeWalkedRoutes(mapView: MapView){
    val layers = mapView.layerManager.layers

    val toRemove = layers.filter { layer ->
        layer is Marker || layer is Polyline
    }

    toRemove.forEach { layer ->
        layers.remove(layer)
    }

    drawnSegmentLayers.clear()

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

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    """ Stores and returns the distance in meters between point 1 (lat1, lon1) and point 2 (lat2, lon2) in result """

    val result = FloatArray(1)
    Location.distanceBetween(lat1,lon1,lat2,lon2,result)

    return result[0].toDouble()
}

fun distancePointToSegmentSquared(
    px: Double,
    py: Double,
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double
): Double {
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