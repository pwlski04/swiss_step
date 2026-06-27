package com.example.stepmap_v10.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.stepmap_v10.chains.PathOverlayLayer
import com.example.stepmap_v10.tracking.LocationTrackingService
import com.example.stepmap_v10.tracking.TrackingLiveState
import org.mapsforge.map.android.view.MapView
import com.example.stepmap_v10.map.RawGpsPointsLayer
import com.example.stepmap_v10.map.centerMap
import com.example.stepmap_v10.paths.PathPoint
import com.example.stepmap_v10.tracking.loadTrackingSessionId
import org.mapsforge.core.model.LatLong


@Composable
fun HomeEffects(
    context: Context,
    lifecycleOwner: LifecycleOwner,

    mapView: MapView?,
    viewModel: HomeViewModel,

    pathOverlayLayer: PathOverlayLayer,

    isDrawing: Boolean,
    showLocationPoints: Boolean = viewModel.showLocationPoints,

    latestLivePoint: PathPoint?,
    isFollowingLocation: Boolean,

    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    hasLocationPermission: Boolean,
    onLocationPermissionChange: (Boolean) -> Unit,
    ){
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no action needed on result */ }
    var hasInitiallyCentered by remember { mutableStateOf(false) }

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
        if (!mv.layerManager.layers.contains(viewModel.replayOverlayLayer)) {
            mv.layerManager.layers.add(viewModel.replayOverlayLayer)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notificationGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /* UPDATE LOCATION MARKER WHEN CURRENT LOCATION MOVES */
    /* UPDATE PATH WITH LOCATION MARKER IF DRAWING IS ON */

    LaunchedEffect(isDrawing) {
        TrackingLiveState.isDrawing.value = isDrawing
    }

    LaunchedEffect(latestLivePoint, isFollowingLocation) {
        if (!isFollowingLocation) return@LaunchedEffect
        val point = latestLivePoint ?: return@LaunchedEffect
        viewModel.sharedMapView.centerMap(LatLong(point.lat, point.lon))
    }
    LaunchedEffect(latestLivePoint) {
        if (hasInitiallyCentered) return@LaunchedEffect
        val mv = viewModel.sharedMapView ?: return@LaunchedEffect
        val point = latestLivePoint ?: return@LaunchedEffect
        mv.model.mapViewPosition.setCenter(LatLong(point.lat, point.lon))
        hasInitiallyCentered = true
    }
    DisposableEffect(Unit) {
        onDispose { hasInitiallyCentered = false }
    }

    /* OTHER */
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if(hasLocationPermission){
                        LocationTrackingService.Companion.useForegroundUpdates(context)
                        Log.d("StepByStep_v1.0_TAG", "Using foreground location updates")
                    } else {
                        val sessionId = loadTrackingSessionId(context)
                        LocationTrackingService.restartForForeground(context, sessionId)
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if(TrackingLiveState.isDrawing.value){
                        LocationTrackingService.useBackgroundUpdates(context)
                        Log.d("StepByStep_v1.0_TAG", "Using background location updates")
                    } else {
                        LocationTrackingService.stop(context)
                    }
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