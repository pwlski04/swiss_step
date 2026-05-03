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
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.view.MapView

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.mapsforge.core.model.LatLong


/*
TODO:
- experience
    - fix paths to match perfectly over the gray template
    - save paths under a name when resetting to reuse later
    - improve map boundaries
    - more cities
- code
    - make object-oriented
    - clean up code (split up into files/directories)
    - add good documentation
 */

@Composable
fun Page_Home() {
    val lifecycleOwner = LocalLifecycleOwner.current

    val context = LocalContext.current

    var mapFilePath by remember { mutableStateOf<String?>(null) }
    var themeFilePath by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val walkedSegments: MutableMap<String, MovementType> = remember { loadWalkedSegments(context) }
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

    var isTracking by remember { mutableStateOf(loadIsTracking(context)) }      //var isTracking by remember { mutableStateOf(false) }

    val locationMarker = remember { LocationMarker() }

    val latestLivePoint = TrackingLiveState.latestPoint.value
    val liveMovementType = TrackingLiveState.movementType.value

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }


    LaunchedEffect(mapView) {
        while (mapView != null) {
            mapView?.let { mv ->
                applySmoothMapForceField(mv)
            }

            kotlinx.coroutines.delay(16L)
        }
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

    DisposableEffect(lifecycleOwner, isTracking) {
        val observer = LifecycleEventObserver { _, event ->
            if (!isTracking) return@LifecycleEventObserver

            when (event) {
                Lifecycle.Event.ON_START -> {
                    LocationTrackingService.useForegroundUpdates(context)
                    Log.d("StepByStep_v1.0_TAG", "Using foreground location updates")

                    val mv = mapView

                    if (mv != null) {
                        val allPoints = PathFunctions.getAllPoints()

                        val latestSessionId = allPoints.lastOrNull()?.sessionId

                        if (latestSessionId != null) {
                            val sessionPoints = allPoints
                                .filter { it.sessionId == latestSessionId }

                            drawSessionDotsAndPaths(context, mv, sessionPoints, walkedSegments, segmentIndex, TrackingLiveState.movementType.value)
                        }
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    LocationTrackingService.useBackgroundUpdates(context)
                    Log.d("StepByStep_v1.0_TAG", "Using background location updates")
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isTracking, mapView) {
        if (!isTracking) {
            mapView?.let { mv ->
                locationMarker.hide(mv)
            }
        }
    }

    LaunchedEffect(mapView, allPaths) {
        val mv = mapView ?: return@LaunchedEffect

        if (allPaths.isEmpty()) return@LaunchedEffect

        if (walkedSegments.isNotEmpty()) {
            drawWalkedSegments(
                mapView = mv,
                allPaths = allPaths,
                walkedSegments = walkedSegments
            )

            mv.layerManager.redrawLayers()

            Log.d(
                "StepByStep_v1.0_TAG",
                "Restored walked paths: ${walkedSegments.size}"
            )
        }

        var lastZoom = mv.model.mapViewPosition.zoomLevel

        while (true) {
            kotlinx.coroutines.delay(150L)

            val currentMapView = mapView ?: break
            val currentZoom = currentMapView.model.mapViewPosition.zoomLevel

            if (lastZoom != currentZoom) {
                lastZoom = currentZoom

                if (allPaths.isNotEmpty() && walkedSegments.isNotEmpty()) {
                    drawWalkedSegments(currentMapView, allPaths,walkedSegments)

                    currentMapView.layerManager.redrawLayers()

                    Log.d(
                        "StepByStep_v1.0_TAG",
                        "Redrew walked paths for zoom=$currentZoom"
                    )
                }
            }
        }
    }


    LaunchedEffect(latestLivePoint, mapView, segmentIndex, isTracking) {
        val point = latestLivePoint
        val mv = mapView

        if (mv == null) return@LaunchedEffect

        if (!isTracking) {
            locationMarker.hide(mv)
            return@LaunchedEffect
        }

        if (point != null) {
            locationMarker.update(mv, point.lat, point.lon, true)

            val sessionPoints = PathFunctions.getAllPoints()
                .filter { it.sessionId == point.sessionId }

            addLatestDotIfNeeded(context, mv, sessionPoints, walkedSegments, segmentIndex, liveMovementType)

            mv.layerManager.redrawLayers()
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
                                drawWalkedSegments(readyMapView, allPaths, walkedSegments)
                            }
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
                        .padding(top = 8.dp)
                ){
                    Text(text = liveMovementType.name,
                        modifier = Modifier
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

                                LocationTrackingService.start(context, newSessionId) //tracker.start()
                                isTracking = true
                                Log.d("StepByStep_v1.0_TAG", "Tracking started")
                            } else {
                                LocationTrackingService.stop(context) //tracker.stop()
                                isTracking = false
                                Log.d("StepByStep_v1.0_TAG", "Tracking stopped")
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(top = 8.dp, end = 12.dp)
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
                                walkedSegments.clear()
                                clearWalkedSegments(context)

                                PathFunctions.clear()

                                TrackingLiveState.latestPoint.value = null
                                TrackingLiveState.movementType.value = MovementType.STILL

                                mapView?.let { mv ->
                                    locationMarker.hide(mv)
                                    removeWalkedRoutes(mv)
                                    mv.layerManager.redrawLayers()
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .padding(top = 12.dp, end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Remove history",
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }


                Column (modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 12.dp)){
                    val point = latestLivePoint

                    Surface(
                        //BUTTON: RECENTER MAP
                        onClick = {
                            val mv = mapView
                            val p = point

                            if (mv != null && p != null) {
                                mv.setZoomLevel(18.toByte())
                                mv.setCenter(
                                    LatLong(
                                        p.lat,
                                        p.lon
                                    )
                                )

                                mv.layerManager.redrawLayers()
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(4.dp, bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Recenter map",
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    //BUTTON: ZOOM IN
                    Surface(
                        onClick = { mapView?.let { mv ->
                            val currentZoom = mv.model.mapViewPosition.zoomLevel
                            mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
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
                            mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
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