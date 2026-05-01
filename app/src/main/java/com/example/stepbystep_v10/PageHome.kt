package com.example.stepbystep_v10

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface


/* TODO:
- only track location when button was pressed
- background tracking
- track whether someone is walking or running vs using transportation (biking vs faster) in different colors/filterable

EXTRA TODO:
- save paths under a name when resetting to reuse later

 */
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

    val walkedSegmentIds = remember { loadWalkedSegmentIds(context) }
    var allPaths by remember { mutableStateOf<List<Path>>(emptyList()) }
    val projector = remember { LocalProjector(originLat = 47.3769) }

    val segmentIndex = remember(allPaths) {
        if (allPaths.isEmpty()) null
        else SegmentGridIndex(
            paths = allPaths,
            projector = projector,
            cellSizeMeters = 20.0
        )
    }

    var currentSessionId by remember { mutableStateOf(System.currentTimeMillis()) }

    /* For the tracker */
    val currentSessionIdState by rememberUpdatedState(currentSessionId)
    val segmentIndexState by rememberUpdatedState(segmentIndex)
    val mapViewState by rememberUpdatedState(mapView)



    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = granted

        if (!granted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        try {
            val paths = withContext(Dispatchers.IO) {
                val mapPath = copyAssetToInternalStorage(context, "zurich.map")
                val themePath = copyAssetToInternalStorage(context, "minmap.xml")
                mapPath to themePath
            }

            mapFilePath = paths.first
            themeFilePath = paths.second

            allPaths = withContext(Dispatchers.IO) {
                loadPathsFromGeoJson(context)
            }

        } catch (e: Exception) {
            errorMessage = "${e::class.java.simpleName}: ${e.message}"
        }
    }

    LaunchedEffect(mapView, allPaths) {
        val mv = mapView

        if (mv != null && allPaths.isNotEmpty() && walkedSegmentIds.isNotEmpty()) {
            drawWalkedSegments(
                mapView = mv,
                allPaths = allPaths,
                walkedSegmentIds = walkedSegmentIds
            )
        }
    }


    /* Location tracker */
    val tracker = remember {
        LocationTracker(context) { location ->
            scope.launch {
                val sessionId = currentSessionIdState

                val point = PathPoint(lat = location.latitude, lon = location.longitude, timestamp = System.currentTimeMillis(), sessionId = currentSessionId)

                PathFunctions.addPoint(point)

                val sessionPoints = PathFunctions.getAllPoints()
                    .filter { it.sessionId == sessionId }

                mapViewState?.let { mv ->
                    addLatestDotIfNeeded(context, mv, sessionPoints, walkedSegmentIds, segmentIndexState)

                    mv.setCenter(LatLong(location.latitude, location.longitude))
                    mv.layerManager.redrawLayers()
                }
            }
        }
    }

    DisposableEffect(tracker) {
        onDispose {
            tracker.stop()
            Log.d("StepByStep_v1.0_TAG", "Tracker stopped on dispose")
        }
    }


    /* FRONTEND */
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            errorMessage != null -> Text("Map load error: $errorMessage")

            mapFilePath == null || themeFilePath == null ->
                Text("Preparing offline map...")

            else -> Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!hasLocationPermission) {
                    Text(
                        text = "Location permission needed.",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    OfflineMapScreen(
                        modifier = Modifier.fillMaxSize(),
                        mapFilePath = mapFilePath!!,
                        themeFilePath = themeFilePath!!,
                        onMapReady = { readyMapView: MapView ->
                            mapView = readyMapView
                            if (allPaths.isNotEmpty()) {
                                drawWalkedSegments(readyMapView, allPaths, walkedSegmentIds)
                            }

                            val sessionPoints = PathFunctions.getAllPoints()
                                .filter { it.sessionId == currentSessionId }

                            val index = segmentIndex
                            addLatestDotIfNeeded(context, readyMapView, sessionPoints, walkedSegmentIds, index)
                            readyMapView.layerManager.redrawLayers()
                        }
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ){
                    Text("Hello World", modifier = Modifier
                        .width(160.dp)
                        .padding(top = 16.dp, bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }


                Column(modifier = Modifier.align(Alignment.TopEnd)){
                    Surface(
                        // BUTTON: START/STOP TRACKING
                        onClick = {
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                return@Surface
                            }

                            if (!isTracking) {
                                val newSessionId = System.currentTimeMillis()
                                currentSessionId = newSessionId

                                tracker.start()
                                isTracking = true
                                Log.d("StepByStep_v1.0_TAG", "Tracking started")
                            } else {
                                tracker.stop()
                                isTracking = false
                                Log.d("StepByStep_v1.0_TAG", "Tracking stopped")
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(top = 16.dp, end = 16.dp)
                    ) {
                        Icon(
                            imageVector = if (isTracking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isTracking) "Stop tracking" else "Start tracking",
                            modifier = Modifier.padding(14.dp)
                        )
                    }


                    if(!isTracking) {
                        Surface(
                            //BUTTON: REMOVE HISTORY
                            onClick = {
                                mapView?.let {
                                    removeWalkedRoutes(it) }
                                    walkedSegmentIds.clear()
                                    clearWalkedSegmentIds(context)
                                },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .padding(top = 16.dp, end = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Remove history",
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }


                Column (modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp, start = 12.dp)){
                    //BUTTON: ZOOM IN
                    Surface(
                        onClick = { mapView?.let { mv ->
                            val currentZoom = mv.model.mapViewPosition.zoomLevel
                            mv.setZoomLevel((currentZoom + 1).toByte())
                        }},
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Zoom in",
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    //BUTTON: ZOOM OUT
                    Surface(
                        onClick = { mapView?.let { mv ->
                            val currentZoom = mv.model.mapViewPosition.zoomLevel
                            mv.setZoomLevel((currentZoom - 1).toByte())
                        }},
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Zoom out",
                            modifier = Modifier.padding(14.dp)
                        )
                    }
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
        createMapView(context, mapFilePath, themeFilePath)
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