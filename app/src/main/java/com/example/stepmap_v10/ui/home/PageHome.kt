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
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import com.example.stepMap_v10.chains.PathOverlayLayer
import com.example.stepMap_v10.chains.PathStorage
import com.example.stepMap_v10.map.LocationMarkerOverlay

import org.mapsforge.core.model.LatLong


/*
TODO:
- to fix:
    - pressing locator while scrolling map should stop movement
    - location marker is huge while zooming in and
    - should not draw still paths
- experience
    - save paths under a name when resetting to reuse later
    - improve map boundaries
    - more cities
 */

@Composable
fun Page_Home(context: Context, viewModel: HomeViewModel) {
    val state = RememberHomeState(context, viewModel)

    with(state) { // To not have to write state.xyz everywhere
        HomeEffects(
            context = context,
            lifecycleOwner = state.lifecycleOwner,

            mapView = viewModel.sharedMapView,
            viewModel = viewModel,

            pathStorage = viewModel.pathStorage,
            pathOverlayLayer = viewModel.pathOverlayLayer,
            segmentIndex = viewModel.segmentIndex,

            isDrawing = isDrawing,
            latestLivePoint = latestLivePoint,
            liveMovementType = liveMovementType,
            //locationMarker = viewModel.locationMarker,

            permissionLauncher = permissionLauncher,
            hasLocationPermission = hasLocationPermission,
            onLocationPermissionChange = { granted -> hasLocationPermission = granted },

            onError = { message -> errorMessage = message },

            onMapViewReady = { readyMapView ->
                if (viewModel.sharedMapView == null) {
                    viewModel.sharedMapView = readyMapView
                }
            }
        )

        /* FRONTEND */
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.errorMessage != null -> Text("Map load error: ${viewModel.errorMessage}")
                viewModel.mapFilePath == null || viewModel.themeFilePath == null -> Text("Preparing offline map...")

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
                            mapFilePath = viewModel.mapFilePath!!,
                            themeFilePath = viewModel.themeFilePath!!,
                            existingMapView = viewModel.sharedMapView,
                            onMapReady = { readyMapView: MapView ->
                                /*mapView = readyMapView
                                readyMapView.layerManager.redrawLayers()*/
                                if (viewModel.sharedMapView == null) {
                                    viewModel.sharedMapView = readyMapView
                                }
                            }
                        )
                    }

                    LocationMarkerOverlay(
                        mapView = viewModel.sharedMapView,
                        position = state.latestLivePoint?.let {
                            LatLong(it.lat, it.lon)
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = liveMovementType.name + ", ZOOM: " + viewModel.sharedMapView?.model?.mapViewPosition?.zoomLevel?.toString(),
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


                        if (!isDrawing && viewModel.hasChains) {
                            Surface(
                                //BUTTON: REMOVE HISTORY
                                onClick = {
                                    state.pathStorage.clearSegments()
                                    viewModel.sharedMapView?.layerManager?.redrawLayers()
                                    viewModel.deleteSavedChains()
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
                                val mv = viewModel.sharedMapView
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
                                viewModel.sharedMapView?.let { mv ->
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
                                viewModel.sharedMapView?.let { mv ->
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