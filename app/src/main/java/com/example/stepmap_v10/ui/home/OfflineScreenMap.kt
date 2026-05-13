package com.example.stepMap_v10.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.stepMap_v10.map.createMapView
import org.mapsforge.map.android.view.MapView

@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier,
    mapFilePath: String,
    themeFilePath: String,
    existingMapView: MapView?,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current

    val mapView = remember(existingMapView) {
        existingMapView ?: createMapView(context, mapFilePath, themeFilePath)
    }

    LaunchedEffect(mapView) {
        onMapReady(mapView)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}