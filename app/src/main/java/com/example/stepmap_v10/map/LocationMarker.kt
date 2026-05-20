package com.example.stepMap_v10.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker

class LocationMarker {
    private var currentLocationMarker: Marker? = null
    private var lastMapView: MapView? = null

    fun update(mapView: MapView, lat: Double, lon: Double, isVisible: Boolean) {
        currentLocationMarker?.let { (lastMapView ?: mapView).layerManager.layers.remove(it) }
        currentLocationMarker = null
        lastMapView = null

        if(!isVisible) return

        val sizePx = 52
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 33, 150, 243)
            style = Paint.Style.FILL
        }

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 150, 243)
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val center = sizePx / 2f

        canvas.drawCircle(center, center, 24f, outerPaint)
        canvas.drawCircle(center, center, 12f, innerPaint)
        canvas.drawCircle(center, center, 12f, borderPaint)

        val marker = Marker(LatLong(lat, lon), AndroidBitmap(bitmap), 0, 0)
        currentLocationMarker = marker
        lastMapView = mapView

        mapView.layerManager.layers.add(marker)
        mapView.layerManager.redrawLayers()
    }

    fun hide() {
        val mv = lastMapView ?: return
        currentLocationMarker?.let {
            mv.layerManager.layers.remove(it)
            mv.layerManager.redrawLayers()
        }
        currentLocationMarker = null
        lastMapView = null
    }
}

/*
class LocationMarker : Layer() {
    private var position: LatLong? = null
    private val sizePx = 52f

    private val outerPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setStyle(Style.FILL)
        setColor(Color.argb(60, 33, 150, 243))  // use Mapsforge Color or ARGB int
    }
    private val innerPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setStyle(Style.FILL)
        setColor(Color.rgb(33, 150, 243))
    }
    private val borderPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setStyle(Style.STROKE)
        setColor(Color.WHITE)
        setStrokeWidth(4f)
    }

    fun update(mapView: MapView, lat: Double, lon: Double) {
        position = LatLong(lat, lon)
        // Add to map once
        if (!mapView.layerManager.layers.contains(this)) {
            mapView.layerManager.layers.add(this)
        }
        requestRedraw()
    }

    fun hide(mapView: MapView) {
        mapView.layerManager.layers.remove(this)
        position = null
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val pos = position ?: return
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)

        val pixelX = MercatorProjection.longitudeToPixelX(pos.longitude, mapSize)
        val pixelY = MercatorProjection.latitudeToPixelY(pos.latitude, mapSize)

        val screenX = (pixelX - topLeftPoint.x).toFloat()
        val screenY = (pixelY - topLeftPoint.y).toFloat()

        // Skip if offscreen
        if (screenX < -sizePx || screenX > canvas.width + sizePx ||
            screenY < -sizePx || screenY > canvas.height + sizePx) return

        canvas.drawCircle(screenX, screenY, sizePx / 2f * 0.9f, outerPaint)
        canvas.drawCircle(screenX, screenY, sizePx / 2f * 0.45f, innerPaint)
        canvas.drawCircle(screenX, screenY, sizePx / 2f * 0.45f, borderPaint)
    }
}
 */

/*
// In HomeEffects LaunchedEffect(mapView):
LaunchedEffect(mapView) {
    val mv = mapView ?: return@LaunchedEffect
    if (!mv.layerManager.layers.contains(pathOverlayLayer)) {
        mv.layerManager.layers.add(pathOverlayLayer)
    }
    if (!mv.layerManager.layers.contains(locationMarker)) {
        mv.layerManager.layers.add(locationMarker)  // ← add once
    }
}

// In GPS LaunchedEffect — just update position, no add/remove:
locationMarker.update(mv, point.lat, point.lon)
 */