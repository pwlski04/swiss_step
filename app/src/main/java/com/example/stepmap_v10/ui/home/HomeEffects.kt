package com.example.stepMap_v10.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.stepMap_v10.chains.PathOverlayLayer
import com.example.stepMap_v10.map.LocationMarker
import com.example.stepMap_v10.map.applySmoothMapForceField
import com.example.stepMap_v10.map.copyAssetToInternalStorage
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.paths.PathPoint
import com.example.stepMap_v10.paths.loadPathsFromGeoJson
import com.example.stepMap_v10.paths.SegmentIndex
import com.example.stepMap_v10.paths.findNearestSegment
import com.example.stepMap_v10.paths.pointToSegmentDistance
import com.example.stepMap_v10.tracking.LocationTrackingService
import com.example.stepMap_v10.tracking.MovementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.view.MapView

import com.example.stepMap_v10.paths.toSegments
import org.mapsforge.core.model.LatLong

import com.example.stepMap_v10.chains.PathStorage


@Composable
fun HomeEffects(
    context: Context,
    lifecycleOwner: LifecycleOwner,

    mapView: MapView?,
    allPaths: List<Path>,

    pathStorage: PathStorage,
    pathOverlayLayer: PathOverlayLayer,

    isDrawing: Boolean,
    latestLivePoint: PathPoint?,
    liveMovementType: MovementType,
    locationMarker: LocationMarker,

    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    hasLocationPermission: Boolean,
    onLocationPermissionChange: (Boolean) -> Unit,

    onMapFilesLoaded: (mapPath: String, themePath: String) -> Unit,
    onPathsLoaded: (List<Path>) -> Unit,
    onError: (String) -> Unit,
    ){
    var segmentIndex by remember { mutableStateOf<SegmentIndex?>(null) }

    /* START TRACKING WHEN LOCATION PERMISSION IS GRANTED */
    /* STOP TRACKING WHEN LOCATION PERMISSION IS REVOKED, OR ON DISPOSE && NOT DRAWING */
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission){
            val newSessionId = System.currentTimeMillis()
            LocationTrackingService.Companion.start(
                context,
                newSessionId
            )
            Log.d("StepByStep_v1.0_TAG", "Tracking started")
        } else {
            LocationTrackingService.Companion.stop(context) //tracker.stop()
            Log.d("StepByStep_v1.0_TAG", "Tracking stopped")
        }
    }
    DisposableEffect(hasLocationPermission, isDrawing) {
        onDispose {
            if (!isDrawing){
                LocationTrackingService.Companion.stop(context) //tracker.stop()
                Log.d("StepByStep_v1.0_TAG", "Tracking stopped")
            }
        }
    }

    /* UPDATE LOCATION MARKER WHEN CURRENT LOCATION MOVES */
    /* UPDATE PATH WITH LOCATION MARKER IF DRAWING IS ON */
    LaunchedEffect(allPaths) {
        if (allPaths.isNotEmpty()) {
            segmentIndex = withContext(Dispatchers.Default) {
                SegmentIndex(allPaths.toSegments())
            }
        }
    }

    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect
        // Add overlay above tile layer but only once
        if (!mv.layerManager.layers.contains(pathOverlayLayer)) {
            mv.layerManager.layers.add(pathOverlayLayer)
        }
    }

    LaunchedEffect(latestLivePoint, mapView, isDrawing) {
        val mv = mapView ?: return@LaunchedEffect
        val point = latestLivePoint ?: return@LaunchedEffect

        locationMarker.update(mv, latestLivePoint.lat, latestLivePoint.lon, true)

        if(isDrawing){
            val index = segmentIndex ?: return@LaunchedEffect
            val nearest = findNearestSegment(point.lat, point.lon, index) ?: return@LaunchedEffect
            val dist = pointToSegmentDistance(LatLong(point.lat, point.lon), nearest)

            if (dist < 0.0003) {
                pathStorage.onGpsPoint(nearest, liveMovementType, index)
                mv.layerManager.redrawLayers()
            }
        }
    }

    var wasDrawing by remember { mutableStateOf(false) }
    LaunchedEffect(isDrawing) {
        if (wasDrawing && !isDrawing) {
            pathStorage.finalizeSession()
            mapView?.layerManager?.redrawLayers()
        }
        wasDrawing = isDrawing
    }


    /* OTHER */

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
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        onLocationPermissionChange(granted)
        if (!granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        try {
            val (mapPath, themePath) = withContext(Dispatchers.IO) {
                copyAssetToInternalStorage(context, "zurich.map") to
                        copyAssetToInternalStorage(context, "minmap.xml")
            }
            onMapFilesLoaded(mapPath, themePath)
            val loadedPaths = withContext(Dispatchers.IO) { loadPathsFromGeoJson(context) }
            onPathsLoaded(loadedPaths)
        } catch (e: Exception) {
            onError("${e::class.java.simpleName}: ${e.message}")
        }
    }

    DisposableEffect(lifecycleOwner, isDrawing) {
        val observer = LifecycleEventObserver { _, event ->
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
}