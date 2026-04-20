package com.example.stepbystep_v10

import android.content.Context
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.ExternalRenderTheme
import java.io.File

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

fun createMapsforgeView(
    context: Context, mapFilePath: String, themeFilePath: String
): MapView {
    val mapFile = File(mapFilePath)
    require(mapFile.exists()) { "Map file does not exist: $mapFilePath" }
    require(mapFile.length() > 0L) { "Map file is empty: $mapFilePath" }

    val themeFile = File(themeFilePath)
    require(themeFile.exists()) { "Theme file does not exist: $themeFilePath" }
    require(themeFile.length() > 0L) { "Theme file is empty: $themeFilePath" }

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

    val mapDataStore = MapFile(mapFile)

    val tileRendererLayer = TileRendererLayer(
        tileCache, mapDataStore, mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE
    ).apply {
        setXmlRenderTheme(ExternalRenderTheme(themeFile))
    }

    mapView.layerManager.layers.add(tileRendererLayer)
    mapView.setCenter(LatLong(47.3769, 8.5417))
    mapView.setZoomLevel(15.toByte())

    return mapView
}