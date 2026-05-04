package com.example.stepbystep_v10.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.stepbystep_v10.map.LastMatchedPosition
import com.example.stepbystep_v10.map.LocationMarker
import com.example.stepbystep_v10.map.SegmentGridIndex
import com.example.stepbystep_v10.map.SegmentProgress
import com.example.stepbystep_v10.map.applySmoothMapForceField
import com.example.stepbystep_v10.map.copyAssetToInternalStorage
import com.example.stepbystep_v10.map.drawWalkedSegments
import com.example.stepbystep_v10.map.paths.Path
import com.example.stepbystep_v10.map.paths.PathPoint
import com.example.stepbystep_v10.map.paths.loadPathsFromGeoJson
import com.example.stepbystep_v10.map.updateWalkedPathFromCurrentLocation
import com.example.stepbystep_v10.tracking.LocationTrackingService
import com.example.stepbystep_v10.tracking.MovementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.view.MapView


@Composable
fun HomeEffects(
    context: Context,
    lifecycleOwner: LifecycleOwner,

    mapView: MapView?,
    allPaths: List<Path>,
    walkedSegments: MutableMap<String, MovementType>,
    segmentIndex: SegmentGridIndex?,
    pathWidth: Float,

    isTracking: Boolean,
    latestLivePoint: PathPoint?,
    liveMovementType: MovementType,
    locationMarker: LocationMarker,

    partialProgress: MutableMap<String, SegmentProgress>,
    lastMatchedPosition: LastMatchedPosition?,
    onLastMatchedPositionChange: (LastMatchedPosition?) -> Unit,

    onLocationPermissionChange: (Boolean) -> Unit,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,

    onMapFilesLoaded: (mapPath: String, themePath: String) -> Unit,
    onPathsLoaded: (List<Path>) -> Unit,
    onError: (String) -> Unit,

){
    LaunchedEffect(mapView) {
        while (mapView != null) {
            mapView?.let { mv ->
                applySmoothMapForceField(mv)
            }

            delay(16L)
        }
    }

    LaunchedEffect(mapView) {
        while (mapView != null) {
            mapView?.let { mv ->
                applySmoothMapForceField(mv)
            }

            delay(16L)
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        onLocationPermissionChange(granted)

        if (!granted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        onLocationPermissionChange(ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED)

        try {
            val paths = withContext(Dispatchers.IO) {
                val mapPath = copyAssetToInternalStorage(context, "zurich.map")
                val themePath = copyAssetToInternalStorage(context, "minmap.xml")
                mapPath to themePath
            }

            onMapFilesLoaded(paths.first, paths.second)

            val loadedPaths = withContext(Dispatchers.IO) {
                loadPathsFromGeoJson(context)
            }

            onPathsLoaded(loadedPaths)

        } catch (e: Exception) {
            onError("${e::class.java.simpleName}: ${e.message}")
        }
    }

    DisposableEffect(lifecycleOwner, isTracking) {
        val observer = LifecycleEventObserver { _, event ->
            if (!isTracking) return@LifecycleEventObserver

            when (event) {
                Lifecycle.Event.ON_START -> {
                    LocationTrackingService.Companion.useForegroundUpdates(context)
                    Log.d("StepByStep_v1.0_TAG", "Using foreground location updates")
                }

                Lifecycle.Event.ON_STOP -> {
                    LocationTrackingService.Companion.useBackgroundUpdates(context)
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
                mv,
                allPaths,
                walkedSegments,
                pathWidth
            )

            mv.layerManager.redrawLayers()

            Log.d(
                "StepByStep_v1.0_TAG",
                "Restored walked paths: ${walkedSegments.size}"
            )
        }

        var lastZoom = mv.model.mapViewPosition.zoomLevel

        while (true) {
            delay(150L)

            val currentMapView = mapView ?: break
            val currentZoom = currentMapView.model.mapViewPosition.zoomLevel

            if (lastZoom != currentZoom) {
                lastZoom = currentZoom

                if (allPaths.isNotEmpty() && walkedSegments.isNotEmpty()) {
                    drawWalkedSegments(currentMapView, allPaths, walkedSegments, pathWidth)

                    currentMapView.layerManager.redrawLayers()

                    Log.d(
                        "StepByStep_v1.0_TAG",
                        "Redrew walked paths for zoom=$currentZoom"
                    )
                }
            }
        }
    }


    LaunchedEffect(latestLivePoint, mapView, segmentIndex, isTracking, pathWidth) {
        val point = latestLivePoint
        val mv = mapView

        if (mv == null) return@LaunchedEffect

        if (!isTracking) {
            locationMarker.hide(mv)
            return@LaunchedEffect
        }

        if (point != null) {
            locationMarker.update(mv, point.lat, point.lon, true)

            val newLastMatchedPosition = updateWalkedPathFromCurrentLocation(context, mv, point, walkedSegments, partialProgress, segmentIndex, liveMovementType, pathWidth, lastMatchedPosition)
            onLastMatchedPositionChange(newLastMatchedPosition)
        }
    }
}