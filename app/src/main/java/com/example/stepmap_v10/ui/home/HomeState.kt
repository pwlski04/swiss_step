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
import com.example.stepMap_v10.map.LastMatchedPosition
import com.example.stepMap_v10.map.LocalProjector
import com.example.stepMap_v10.map.LocationMarker
import com.example.stepMap_v10.map.SegmentGridIndex
import com.example.stepMap_v10.map.SegmentProgress
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.paths.loadWalkedSegments
import com.example.stepMap_v10.tracking.MovementType
import com.example.stepMap_v10.tracking.TrackingLiveState
import com.example.stepMap_v10.tracking.loadIsTracking
import org.mapsforge.map.android.view.MapView

class HomeState(
    val lifecycleOwner: LifecycleOwner,
    val walkedSegments: MutableMap<String, MovementType>,
    val projector: LocalProjector,
    val locationMarker: LocationMarker,
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    initialIsTracking: Boolean
) {
    // DEFAULT (no values in state): by remember {x} => by x
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by  mutableStateOf<String?>(null)
    var hasLocationPermission by  mutableStateOf(false)
    var mapView by mutableStateOf<MapView?>(null)

    var allPaths by mutableStateOf<List<Path>>(emptyList())

    var isTracking by mutableStateOf(initialIsTracking)     //var isTracking by remember { mutableStateOf(false) }

    val partialProgress = mutableMapOf<String, SegmentProgress>()
    var lastMatchedPosition by mutableStateOf<LastMatchedPosition?>(null)

    // GET (no state): x = y => x get() = y
    val latestLivePoint
        get() = TrackingLiveState.latestPoint.value
    val liveMovementType
        get() = TrackingLiveState.movementType.value

    var segmentIndex by mutableStateOf<SegmentGridIndex?>(null)
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
            lifecycleOwner, loadWalkedSegments(context), LocalProjector(originLat = 47.3769), LocationMarker(), permissionLauncher, loadIsTracking(context)
        )
    }

    LaunchedEffect(rememberedHasLocationPermission) {
        state.hasLocationPermission = rememberedHasLocationPermission
    }

    return state
}