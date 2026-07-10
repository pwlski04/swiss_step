package io.github.pwlski04.swissstep.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pwlski04.swissstep.map.createMapView
import org.mapsforge.map.android.view.MapView

@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier,
    mapFilePath: String,
    themeFilePath: String,
    isDarkMode: Boolean,
    existingMapView: MapView?,
    onMapReady: (MapView) -> Unit
) {
    /*
    Bridges the legacy Mapsforge MapView into Compose via AndroidView. Reuses `existingMapView` if
    passed in, so the same map instance survives recomposition.
    */
    val context = LocalContext.current

    val mapView = remember(existingMapView) {
        existingMapView ?: createMapView(context, mapFilePath, themeFilePath, isDarkMode)
    }

    LaunchedEffect(mapView) {
        onMapReady(mapView)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}
