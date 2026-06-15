package com.example.stepmap_v10.chains

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer


/* DEBUG LAYER */

class LocationPointsLayer(private val pathStorage: PathStorage) : Layer() {
    private val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(android.graphics.Color.argb(25, 0, 0, 0))     // 90% transparent
        setStrokeWidth(0f)
    }

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        val allPoints = synchronized(pathStorage) {
            pathStorage.chains.values.flatten()
                .flatMap { chain -> synchronized(chain) { chain.points.toList() } }
        }
        for (point in allPoints) {
            val pixelX = MercatorProjection.longitudeToPixelX(point.longitude, mapSize)
            val pixelY = MercatorProjection.latitudeToPixelY(point.latitude, mapSize)
            val screenX = (pixelX - topLeftPoint.x).toInt()
            val screenY = (pixelY - topLeftPoint.y).toInt()
            if (screenX < -10 || screenX > canvas.width + 10 ||
                screenY < -10 || screenY > canvas.height + 10) continue
            canvas.drawCircle(screenX, screenY, 4, paint)
        }
    }
}