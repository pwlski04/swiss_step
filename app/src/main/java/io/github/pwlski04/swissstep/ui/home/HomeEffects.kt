package io.github.pwlski04.swissstep.ui.home

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.pwlski04.swissstep.chains.PathOverlayLayer
import io.github.pwlski04.swissstep.tracking.LocationTrackingService
import io.github.pwlski04.swissstep.tracking.TrackingLiveState
import org.mapsforge.map.android.view.MapView
import io.github.pwlski04.swissstep.map.RawGpsPointsLayer
import io.github.pwlski04.swissstep.map.centerMap
import io.github.pwlski04.swissstep.paths.PathPoint
import io.github.pwlski04.swissstep.tracking.loadTrackingSessionId
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
    LaunchedEffect(mapView) {
        /* Once the MapView is ready, attaches the path/replay overlay layers and warms their projection cache for every zoom level, so the first real draw doesn't stall computing projections on demand. */
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
        /* Adds/removes the raw-GPS-points debug overlay layer to match the "show location points" preference. */
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
            Log.d("SwissStep_TAG", "Tracking started")
        } else {
            LocationTrackingService.Companion.stop(context)
            Log.d("SwissStep_TAG", "Tracking stopped")
        }
    }
    val currentIsDrawing by rememberUpdatedState(isDrawing)
    DisposableEffect(hasLocationPermission) {
        onDispose {
            if (!currentIsDrawing){
                LocationTrackingService.Companion.stop(context)
                Log.d("SwissStep_TAG", "Tracking stopped")
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
    LaunchedEffect(viewModel.sharedMapView, latestLivePoint) {
        /* One-time initial view centering */
        if (viewModel.hasInitiallyCentered) return@LaunchedEffect
        val mv = viewModel.sharedMapView ?: return@LaunchedEffect
        val point = latestLivePoint ?: return@LaunchedEffect
        mv.model.mapViewPosition.setCenter(LatLong(point.lat, point.lon))
        viewModel.hasInitiallyCentered = true
    }

    /* OTHER */
    DisposableEffect(lifecycleOwner) {
        /*
        Adjusts the tracking service's update rate to match the screen's lifecycle: full-rate
        foreground updates while visible, battery-friendlier background updates (or a full
        stop, if not recording) once backgrounded. ON_RESUME also marks every chain dirty so
        stale cached projections get recomputed on the next draw.
        */
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if(hasLocationPermission){
                        LocationTrackingService.Companion.useForegroundUpdates(context)
                        Log.d("SwissStep_TAG", "Using foreground location updates")
                    } else {
                        val sessionId = loadTrackingSessionId(context)
                        LocationTrackingService.restartForForeground(context, sessionId)
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if(TrackingLiveState.isDrawing.value){
                        LocationTrackingService.useBackgroundUpdates(context)
                        Log.d("SwissStep_TAG", "Using background location updates")
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