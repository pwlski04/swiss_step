package com.example.stepmap_v10.map

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

import kotlin.math.abs

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
        tileRendererLayer.setXmlRenderTheme(ExternalRenderTheme(themeFile))
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