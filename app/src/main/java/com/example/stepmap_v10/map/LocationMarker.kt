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

    fun update(mapView: MapView, lat: Double, lon: Double, isVisible: Boolean) {
        if(!isVisible){
            hide(mapView)
            return
        }

        val oldMarker = currentLocationMarker

        if (oldMarker != null) {
            mapView.layerManager.layers.remove(oldMarker)
        }

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

        val mapsforgeBitmap = AndroidBitmap(bitmap)

        val marker = Marker(
            LatLong(lat, lon),
            mapsforgeBitmap,
            0,
            0
        )

        currentLocationMarker = marker

        mapView.layerManager.layers.add(marker)
        mapView.layerManager.redrawLayers()
    }

    fun hide(mapView: MapView){
        val oldMarker = currentLocationMarker

        if (oldMarker != null){
            mapView.layerManager.layers.remove(oldMarker)
            currentLocationMarker = null
            mapView.layerManager.redrawLayers()
        }
    }
}