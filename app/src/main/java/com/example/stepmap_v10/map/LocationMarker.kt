package com.example.stepmap_v10.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.model.common.Observer


@Composable
fun LocationMarkerOverlay(
    mapView: MapView?,
    position: LatLong?,
    modifier: Modifier = Modifier
) {
    if (mapView == null || position == null) return

    var screenPos by remember { mutableStateOf<Offset?>(null) }
    val isZoomingState = remember { mutableStateOf(false) }

    fun recalculate() {
        val zoomLevel = mapView.model.mapViewPosition.zoomLevel
        val center = mapView.model.mapViewPosition.center ?: return
        val tileSize = mapView.model.displayModel.tileSize
        val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)

        val centerPixelX = MercatorProjection.longitudeToPixelX(center.longitude, mapSize)
        val centerPixelY = MercatorProjection.latitudeToPixelY(center.latitude, mapSize)
        val posPixelX = MercatorProjection.longitudeToPixelX(position.longitude, mapSize)
        val posPixelY = MercatorProjection.latitudeToPixelY(position.latitude, mapSize)

        val halfW = mapView.width / 2f
        val halfH = mapView.height / 2f

        screenPos = Offset(
            (posPixelX - centerPixelX + halfW).toFloat(),
            (posPixelY - centerPixelY + halfH).toFloat()
        )
    }

    // To hide marker during zoom
    val lastZoomRef = remember { mutableStateOf(mapView.model.mapViewPosition.zoomLevel) }

    DisposableEffect(mapView, position) {
        val observer = Observer {
            val currentZoom = mapView.model.mapViewPosition.zoomLevel
            if (currentZoom != lastZoomRef.value) {
                lastZoomRef.value = currentZoom
                isZoomingState.value = true
            }
            recalculate()
        }

        mapView.model.mapViewPosition.addObserver(observer)
        recalculate()

        onDispose {
            mapView.model.mapViewPosition.removeObserver(observer)
        }
    }

    val pos = screenPos ?: return

    @SuppressLint("ClickableViewAccessibility")
    DisposableEffect(mapView) {
        val touchListener = View.OnTouchListener { view, event ->
            if ((event?.pointerCount ?: 0) >= 2) isZoomingState.value = true
            if (event?.action == MotionEvent.ACTION_UP) view.performClick()
            false
        }
        mapView.setOnTouchListener(touchListener)
        onDispose {
            mapView.setOnTouchListener(null)
        }
    }

    LaunchedEffect(lastZoomRef.value) {
        delay(300L)  // delay after zoom
        isZoomingState.value = false
    }

    if (!isZoomingState.value) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(color = Color(0x3D2196F3.toInt()), radius = 24f, center = pos)
            drawCircle(color = Color(0xFF2196F3.toInt()), radius = 12f, center = pos)
            drawCircle(color = Color.White, radius = 12f, center = pos, style = Stroke(width = 4f))
        }
    }
}