package io.github.pwlski04.swissstep.map

import android.content.Context
import android.view.MotionEvent
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

private const val ZURICH_MIN_LAT = 47.32
private const val ZURICH_MAX_LAT = 47.43
private const val ZURICH_MIN_LON = 8.44
private const val ZURICH_MAX_LON = 8.63


/* CREATE MAP: */
fun createMapView(context: Context, mapFilePath: String, themeFilePath: String): MapView {
    /* Builds a Mapsforge MapView backed by the given offline .map file and render theme, centered on Zurich and constrained to its bounds. */
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

    constrainMapBounds(mapView)

    return mapView
}

fun constrainMapBounds(mapView: MapView) {
    val boundingBox = org.mapsforge.core.model.BoundingBox(
        ZURICH_MIN_LAT, ZURICH_MIN_LON,
        ZURICH_MAX_LAT, ZURICH_MAX_LON
    )

    mapView.model.mapViewPosition.setMapLimit(boundingBox)
}


fun MapView?.centerMap(point: LatLong){
    /* Recenters the map on `point`, first faking a touch-down-then-cancel so any in-progress fling/pan animation is stopped rather than fighting the recenter. No-ops if the view isn't laid out yet. */
    if (this == null) return
    if (width == 0 || height == 0) return
    if (model?.mapViewPosition == null) return

    // ACTION_DOWN cancels fling in any scrollable view
    val downEvent = MotionEvent.obtain(
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        MotionEvent.ACTION_DOWN,
        width / 2f,
        height / 2f,
        0
    )
    val cancelEvent = MotionEvent.obtain(
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        MotionEvent.ACTION_CANCEL,
        width / 2f,
        height / 2f,
        0
    )
    dispatchTouchEvent(downEvent)
    dispatchTouchEvent(cancelEvent)
    downEvent.recycle()
    cancelEvent.recycle()

    model.mapViewPosition.setCenter(point)
}


/* HELPER FUNCTIONS */

fun copyAssetToInternalStorage(context: Context, assetName: String): String {
    /* Mapsforge needs a real filesystem File (it can't read directly from the APK's AssetManager stream), so bundled map/theme assets get copied out to internal storage on first use. */
    val outFile = File(context.filesDir, assetName)
    // Always copy — ensures updated assets are used
    context.assets.open(assetName).use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
    }
    return outFile.absolutePath
}