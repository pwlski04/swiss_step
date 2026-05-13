package com.example.stepMap_v10.ui.home

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.stepMap_v10.chains.PathOverlayLayer
import com.example.stepMap_v10.chains.PathStorage
import com.example.stepMap_v10.map.LocationMarker
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.tracking.TrackingLiveState
import com.example.stepMap_v10.tracking.loadIsDrawing
import org.mapsforge.map.android.view.MapView

class HomeState(
    val lifecycleOwner: LifecycleOwner,
    val locationMarker: LocationMarker,
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    initialIsDrawing: Boolean
) {
    // DEFAULT (no values in state): by remember {x} => by x
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by  mutableStateOf<String?>(null)
    var hasLocationPermission by  mutableStateOf(false)
    var mapView by mutableStateOf<MapView?>(null)

    var allPaths by mutableStateOf<List<Path>>(emptyList())

    var isDrawing by mutableStateOf(initialIsDrawing)

    // GET (no state): x = y => x get() = y
    val latestLivePoint
        get() = TrackingLiveState.latestPoint.value
    val liveMovementType
        get() = TrackingLiveState.movementType.value

    // Chain rendering (create once)
    val pathStorage = PathStorage()
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }
}

@Composable
fun RememberHomeState(context: Context): HomeState{
    // state with values => move to state, pass values as arguments to HomeState
    val lifecycleOwner = LocalLifecycleOwner.current
    var rememberedHasLocationPermission by remember{ mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        rememberedHasLocationPermission = granted
    }

    val state = remember {
        HomeState(
            lifecycleOwner, LocationMarker(), permissionLauncher, loadIsDrawing(context)
        )
    }

    LaunchedEffect(rememberedHasLocationPermission) {
        state.hasLocationPermission = rememberedHasLocationPermission
    }

    return state
}