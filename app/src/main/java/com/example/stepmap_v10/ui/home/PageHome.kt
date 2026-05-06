package com.example.stepMap_v10.ui.home

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mapsforge.map.android.view.MapView

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign

import com.example.stepMap_v10.tracking.LocationTrackingService
import com.example.stepMap_v10.tracking.MovementType
import com.example.stepMap_v10.paths.PathFunctions
import com.example.stepMap_v10.map.SegmentGridIndex
import com.example.stepMap_v10.tracking.TrackingLiveState
import com.example.stepMap_v10.paths.clearWalkedSegments
import com.example.stepMap_v10.map.drawWalkedSegments
import com.example.stepMap_v10.paths.PathPoint
import com.example.stepMap_v10.map.removeWalkedRoutes
import org.mapsforge.core.model.LatLong

import com.example.stepMap_v10.map.updateWalkedPathFromCurrentLocation


/*
TODO:
- to fix:
    - background tracking doesnt draw paths
    - instead of closest segment -> all segments in a radius
- experience
    - save paths under a name when resetting to reuse later
    - improve map boundaries
    - more cities
- code
    - clean up code (oop, split up into files/directories)
    - add good documentation
 */

@Composable
fun Page_Home(context: Context, pathWidth: Float) {
    //TEST
    var fakeStep by remember { mutableIntStateOf(0) }
    val state = RememberHomeState(context)

    with(state) { // To not have to write state.xyz everywhere
        HomeEffects(
            context = context,
            lifecycleOwner = state.lifecycleOwner,

            mapView = mapView,
            allPaths = allPaths,
            walkedSegments = walkedSegments,
            segmentIndex = segmentIndex,
            pathWidth = pathWidth,

            isDrawing = isDrawing,
            latestLivePoint = latestLivePoint,
            liveMovementType = liveMovementType,
            locationMarker = locationMarker,

            partialProgress = partialProgress,
            lastMatchedPosition = lastMatchedPosition,
            onLastMatchedPositionChange = { newValue ->
                lastMatchedPosition = newValue
            },

            permissionLauncher = permissionLauncher,
            hasLocationPermission = hasLocationPermission,
            onLocationPermissionChange = { granted ->
                hasLocationPermission = granted
            },

            onMapFilesLoaded = { mapPath, themePath ->
                mapFilePath = mapPath
                themeFilePath = themePath
            },
            onPathsLoaded = { loadedPaths ->
                allPaths = loadedPaths

                segmentIndex =
                    if (loadedPaths.isEmpty()) {
                        null
                    } else {
                        SegmentGridIndex(
                            paths = loadedPaths,
                            projector = projector,
                            cellSizeMeters = 20.0
                        )
                    }
            },
            onError = { message ->
                errorMessage = message
            }
        )

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
                                    drawWalkedSegments(
                                        readyMapView,
                                        allPaths,
                                        walkedSegments,
                                        pathWidth
                                    )
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
                    ) {
                        Text(
                            text = liveMovementType.name + ", ZOOM: " + mapView?.model?.mapViewPosition?.zoomLevel?.toString(),
                            modifier = Modifier
                                .width(160.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }


                    Column(modifier = Modifier.align(Alignment.TopEnd)) {
                        Surface(
                            // BUTTON: START/STOP TRACKING
                            onClick = {
                                if (!isDrawing) {
                                    isDrawing = true
                                    Log.d("StepByStep_v1.0_TAG", "Drawing started")
                                } else {
                                    isDrawing = false
                                    Log.d("StepByStep_v1.0_TAG", "Drawing stopped")
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .padding(top = 8.dp, end = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isDrawing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isDrawing) "Stop tracking" else "Start tracking",
                                modifier = Modifier.padding(14.dp)
                            )
                        }


                        if (!isDrawing) {
                            Surface(
                                //BUTTON: REMOVE HISTORY
                                onClick = {
                                    walkedSegments.clear()
                                    clearWalkedSegments(context)

                                    PathFunctions.clear()

                                    TrackingLiveState.latestPoint.value = null
                                    TrackingLiveState.movementType.value = MovementType.STILL

                                    mapView?.let { mv ->
                                        //locationMarker.hide(mv)
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


                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 12.dp)
                    ) {
                        val point = latestLivePoint

                        Surface(
                            //BUTTON: RECENTER MAP
                            onClick = {
                                val mv = mapView
                                val p = point

                                if (mv != null && p != null) {
                                    mv.setZoomLevel(18.toByte())
                                    mv.setCenter(LatLong(p.lat, p.lon))
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
                            onClick = {
                                mapView?.let { mv ->
                                    val currentZoom = mv.model.mapViewPosition.zoomLevel
                                    mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                }
                            },
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
                            onClick = {
                                mapView?.let { mv ->
                                    val currentZoom = mv.model.mapViewPosition.zoomLevel
                                    mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                }
                            },
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

                    Button(
                        onClick = {
                            val mv = mapView ?: return@Button
                            val path = allPaths.firstOrNull { it.walkable && it.points.size >= 2 }
                                ?: return@Button

                            val start = path.points[0]
                            val end = path.points[1]

                            val t = (fakeStep.coerceAtMost(10)) / 10.0

                            val lat = start.latitude + (end.latitude - start.latitude) * t
                            val lon = start.longitude + (end.longitude - start.longitude) * t

                            val point = PathPoint(
                                lat,
                                lon,
                                System.currentTimeMillis(),
                                999L,
                                MovementType.WALKING
                            )

                            locationMarker.update(mv, point.lat, point.lon, true)

                            lastMatchedPosition = updateWalkedPathFromCurrentLocation(
                                context,
                                mv,
                                point,
                                walkedSegments,
                                partialProgress,
                                segmentIndex,
                                MovementType.WALKING,
                                pathWidth,
                                lastMatchedPosition
                            )

                            fakeStep++

                            if (fakeStep > 10) {
                                fakeStep = 0
                            }
                        }
                    ) {
                        Text("Fake path progress")
                    }
                }
            }

            if (isDrawing) {
                Text(
                    text = "Drawing active",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}