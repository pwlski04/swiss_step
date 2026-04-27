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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint as AndroidPaint
import org.mapsforge.core.graphics.Bitmap as MapsforgeBitmap
import org.mapsforge.map.layer.overlay.Marker
import android.graphics.drawable.BitmapDrawable
import android.location.Location



/* HELPER FUNCTIONS: */

fun copyAssetToInternalStorage(
    context: Context,
    assetName: String
): String {
    val outFile = File(context.filesDir, assetName)

    if (!outFile.exists() || outFile.length() == 0L) {
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    return outFile.absolutePath
}

fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val result = FloatArray(1)

    Location.distanceBetween(
        lat1,
        lon1,
        lat2,
        lon2,
        result
    )

    return result[0].toDouble()
}



/* CREATE MAP: */

fun createMapsforgeView(
    context: Context,
    mapFilePath: String,
    themeFilePath: String
): MapView {
    val mapFileOnDisk = File(mapFilePath)
    require(mapFileOnDisk.exists()) { "Map file does not exist: $mapFilePath" }
    require(mapFileOnDisk.length() > 0L) { "Map file is empty: $mapFilePath" }

    val mapDataStore = MapFile(mapFileOnDisk)

    val mapView = MapView(context)
    mapView.setBuiltInZoomControls(true)
    mapView.mapScaleBar.isVisible = true

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



/* MODIFY MAP ELEMENTS: */

fun createDotBitmap(
    context: Context,
    sizePx: Int,
    red: Int,
    green: Int,
    blue: Int
): MapsforgeBitmap {
    val androidBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
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

fun addLatestDotIfNeeded(
    context: Context,
    mapView: MapView,
    points: List<PathPoint>
) {
    if (points.isEmpty()) return

    val latestPoint = points.last()

    val alreadyHasNearbyDot = points.dropLast(1).any { oldPoint ->
        distanceMeters(
            oldPoint.lat,
            oldPoint.lon,
            latestPoint.lat,
            latestPoint.lon
        ) < 3.3
    }

    if (alreadyHasNearbyDot) {
        return
    }

    val latestIndex = points.size - 1
    val latestIsRed = latestIndex % 2 == 0

    val sameDotAlreadyExists = points.dropLast(1).withIndex().any { (index, oldPoint) ->
        val oldIsRed = index % 2 == 0

        oldPoint.lat == latestPoint.lat && oldPoint.lon == latestPoint.lon && oldIsRed == latestIsRed
    }

    if (sameDotAlreadyExists) {
        return
    }

    val sizePx = 72

    val dotBitmap = if (latestIsRed) {
        createDotBitmap(context = context, sizePx = sizePx, red = 255, green = 0, blue = 0)
    } else {
        createDotBitmap(context = context, sizePx = sizePx, red = 0, green = 255, blue = 0)
    }

    val marker = Marker(
        LatLong(latestPoint.lat, latestPoint.lon),
        dotBitmap,
        -sizePx / 2,
        -sizePx / 2
    )

    mapView.layerManager.layers.add(marker)
}

fun removeRouteLayers(mapView: MapView){
    val layers = mapView.layerManager.layers
    val toRemove = mutableListOf<Layer>()

    for(layer in layers){
        if(layer is Polyline || layer is Marker){
            toRemove.add(layer)
        }
    }

    for(layer in toRemove){
        layers.remove(layer)
    }
}




/* OLD, UNUSED FUNCTIONS: */
fun removeExistingPolylines(mapView: MapView) {
    val layers = mapView.layerManager.layers
    val toRemove = mutableListOf<Layer>()

    for (layer in layers) {
        if (layer is Polyline) {
            toRemove.add(layer)
        }
    }

    for (layer in toRemove) {
        layers.remove(layer)
    }
}

fun addRouteOverlay(
    context: Context,
    mapView: MapView,
    points: List<PathPoint>
) {
    if (points.isEmpty()) return

    for ((index, point) in points.withIndex()) {

        val dotBitmap = if (index % 2 == 0) {
            createDotBitmap(
                context = context,
                sizePx = 48,
                red = 255,
                green = 0,
                blue = 0
            )
        } else {
            createDotBitmap(
                context = context,
                sizePx = 48,
                red = 0,
                green = 255,
                blue = 0
            )
        }

        val marker = Marker(
            LatLong(point.lat, point.lon),
            dotBitmap,
            -24,
            -24
        )

        mapView.layerManager.layers.add(marker)
    }

    mapView.layerManager.redrawLayers()
}