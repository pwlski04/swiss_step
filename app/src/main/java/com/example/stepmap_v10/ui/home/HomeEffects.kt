package com.example.stepmap_v10.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.stepmap_v10.chains.PathOverlayLayer
import com.example.stepmap_v10.map.applySmoothMapForceField
import com.example.stepmap_v10.tracking.LocationTrackingService
import com.example.stepmap_v10.tracking.TrackingLiveState
import kotlinx.coroutines.delay
import org.mapsforge.map.android.view.MapView
import com.example.stepmap_v10.chains.RawGpsPointsLayer


@Composable
fun HomeEffects(
    context: Context,
    lifecycleOwner: LifecycleOwner,

    mapView: MapView?,
    viewModel: HomeViewModel,

    pathOverlayLayer: PathOverlayLayer,

    isDrawing: Boolean,
    showLocationPoints: Boolean = viewModel.showLocationPoints,

    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    hasLocationPermission: Boolean,
    onLocationPermissionChange: (Boolean) -> Unit,

    ){

    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect

        // Apply persisted showLocationPoints state on map ready
        if (viewModel.showLocationPoints && viewModel.rawGpsPointsLayer == null) {
            val layer = RawGpsPointsLayer(viewModel.routeRecorder)
            viewModel.rawGpsPointsLayer = layer
            mv.layerManager.layers.add(layer)
            mv.layerManager.redrawLayers()
        }
    }

    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!mv.layerManager.layers.contains(pathOverlayLayer)) {
            mv.layerManager.layers.add(pathOverlayLayer)
        }

        // Pre-project
        viewModel.preProjectAllZoomLevels()
        mv.layerManager.redrawLayers()
    }

    LaunchedEffect(mapView, showLocationPoints) {
        val mv = mapView ?: return@LaunchedEffect

        if (showLocationPoints) {
            if (viewModel.rawGpsPointsLayer == null) {
                val rawGpsPointsLayer = RawGpsPointsLayer(viewModel.routeRecorder)
                viewModel.rawGpsPointsLayer = rawGpsPointsLayer
                mv.layerManager.layers.add(rawGpsPointsLayer)
            }
        } else {
            viewModel.rawGpsPointsLayer?.let { layer ->
                mv.layerManager.layers.remove(layer)
                viewModel.rawGpsPointsLayer = null
            }
        }
        mv.layerManager.redrawLayers()
    }

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
            LocationTrackingService.Companion.stop(context)
            Log.d("StepByStep_v1.0_TAG", "Tracking stopped")
        }
    }
    val currentIsDrawing by rememberUpdatedState(isDrawing)
    DisposableEffect(hasLocationPermission) {
        onDispose {
            if (!currentIsDrawing){
                LocationTrackingService.Companion.stop(context)
                Log.d("StepByStep_v1.0_TAG", "Tracking stopped")
            }
        }
    }

    // Permission check
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        onLocationPermissionChange(granted)
        if (!granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /* UPDATE LOCATION MARKER WHEN CURRENT LOCATION MOVES */
    /* UPDATE PATH WITH LOCATION MARKER IF DRAWING IS ON */

    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!mv.layerManager.layers.contains(pathOverlayLayer)) {
            mv.layerManager.layers.add(pathOverlayLayer)
        }
    }

    LaunchedEffect(isDrawing) {
        TrackingLiveState.isDrawing.value = isDrawing
    }


    /* OTHER */

    LaunchedEffect(mapView) {
        while (mapView != null) {
            mapView?.let {
                applySmoothMapForceField(it)
            }

            delay(16L)
        }
    }

    DisposableEffect(lifecycleOwner) {
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
                Lifecycle.Event.ON_RESUME -> {
                    // Invalidate projection cache so all chains re-project on next draw
                    viewModel.pathStorage.chains.values.flatten().forEach { it.dirty = true }
                    viewModel.sharedMapView?.layerManager?.redrawLayers()
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