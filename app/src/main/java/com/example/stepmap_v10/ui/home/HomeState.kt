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
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.paths.PathPoint
import com.example.stepMap_v10.tracking.MovementType
import com.example.stepMap_v10.tracking.TrackingLiveState
import com.example.stepMap_v10.tracking.loadIsDrawing
import org.mapsforge.map.android.view.MapView
import androidx.compose.runtime.collectAsState

class HomeState(
    val lifecycleOwner: LifecycleOwner,
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    val pathStorage: PathStorage,
    val pathOverlayLayer: PathOverlayLayer,
    initialIsDrawing: Boolean
) {
    var errorMessage by  mutableStateOf<String?>(null)
    var hasLocationPermission by  mutableStateOf(false)
    var mapView by mutableStateOf<MapView?>(null)

    var isDrawing by mutableStateOf(initialIsDrawing)

    var latestLivePoint by mutableStateOf<PathPoint?>(null)
    var liveMovementType by mutableStateOf(MovementType.STILL)
}

@Composable
fun RememberHomeState(context: Context, viewModel: HomeViewModel): HomeState{
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
            lifecycleOwner, permissionLauncher, viewModel.pathStorage, viewModel.pathOverlayLayer, loadIsDrawing(context)
        )
    }

    val latestPoint by TrackingLiveState.latestPoint.collectAsState()
    val movementType by TrackingLiveState.movementType.collectAsState()
    state.latestLivePoint = latestPoint
    state.liveMovementType = movementType

    LaunchedEffect(rememberedHasLocationPermission) {
        state.hasLocationPermission = rememberedHasLocationPermission
    }

    return state
}