package com.example.stepmap_v10.map

import android.graphics.Color
import com.example.stepmap_v10.chains.RouteRecorder
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer

class RawGpsPointsLayer(private val routeRecorder: RouteRecorder) : Layer() {
    private val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(Color.argb(180, 255, 200, 0))
        setStrokeWidth(0f)
    }

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        val points = routeRecorder.displayPoints

        for (point in points) {
            val ll = LatLong(point.lat, point.lon)
            if (!boundingBox.contains(ll)) continue
            val pixelX = MercatorProjection.longitudeToPixelX(point.lon, mapSize)
            val pixelY = MercatorProjection.latitudeToPixelY(point.lat, mapSize)
            val screenX = (pixelX - topLeftPoint.x).toInt()
            val screenY = (pixelY - topLeftPoint.y).toInt()
            canvas.drawCircle(screenX, screenY, 5, paint)
        }
    }
}