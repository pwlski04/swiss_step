package com.example.stepbystep_v10

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface

@Composable
fun Page_Home() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mapFilePath by remember { mutableStateOf<String?>(null) }
    var themeFilePath by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var isTracking by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    var currentSessionId by remember { mutableStateOf(System.currentTimeMillis()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        try {
            val paths = withContext(Dispatchers.IO) {
                val mapPath = copyAssetToInternalStorage(context, "switzerland.map")
                val themePath = copyAssetToInternalStorage(context, "minmap.xml")
                mapPath to themePath
            }

            mapFilePath = paths.first
            themeFilePath = paths.second
        } catch (e: Exception) {
            errorMessage = "${e::class.java.simpleName}: ${e.message}"
        }
    }

    val tracker = remember(currentSessionId) {
        LocationTracker(context) { location ->
            scope.launch {
                val point = PathPoint(
                    lat = location.latitude,
                    lon = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    sessionId = currentSessionId
                )

                PathFunctions.addPoint(point)

                val sessionPoints = PathFunctions.getAllPoints()
                    .filter { it.sessionId == currentSessionId }

                mapView?.let { mv ->
                    addLatestDotIfNeeded(context = context, mapView = mv, points = sessionPoints)
                    mv.setCenter(LatLong(location.latitude, location.longitude))
                    mv.layerManager.redrawLayers()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            errorMessage != null -> Text("Map load error: $errorMessage")

            mapFilePath == null || themeFilePath == null ->
                Text("Preparing offline map...")

            else -> Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)}
                else{
                    OfflineMapScreen(
                        modifier = Modifier.fillMaxSize(),
                        mapFilePath = mapFilePath!!,
                        themeFilePath = themeFilePath!!,
                        onMapReady = { readyMapView: MapView ->
                            mapView = readyMapView

                            val sessionPoints = PathFunctions.getAllPoints()
                                .filter { it.sessionId == currentSessionId }

                            addLatestDotIfNeeded(context, readyMapView, sessionPoints)
                            readyMapView.layerManager.redrawLayers()
                        }
                    )
                }

                Surface(
                    onClick = {
                        if (!isTracking) {
                            currentSessionId = System.currentTimeMillis()
                            mapView?.let { removeRouteLayers(it) }
                            tracker.start()
                            isTracking = true
                        } else {
                            tracker.stop()
                            isTracking = false
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isTracking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isTracking) "Stop tracking" else "Start tracking",
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (isTracking) {
            Text(
                text = "Tracking active",
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier,
    mapFilePath: String,
    themeFilePath: String,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current

    val mapView = remember(mapFilePath, themeFilePath) {
        createMapsforgeView(context, mapFilePath, themeFilePath)
    }

    LaunchedEffect(mapView) {
        onMapReady(mapView)
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.destroyAll()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView }
    )
}