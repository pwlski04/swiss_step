package io.github.pwlski04.swissstep.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.pwlski04.swissstep.chains.PathStorage
import io.github.pwlski04.swissstep.paths.PathPoint
import io.github.pwlski04.swissstep.tracking.MovementType
import io.github.pwlski04.swissstep.tracking.TrackingLiveState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class HomeState(
    val lifecycleOwner: LifecycleOwner,
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    val pathStorage: PathStorage,
    initialIsDrawing: Boolean,
    initialHasLocationPermission: Boolean,
    val scope: CoroutineScope,
    val interactionSource_zoomIn: MutableInteractionSource,
    val interactionSource_zoomOut: MutableInteractionSource,
    val interactionSource_recenter: MutableInteractionSource,
) {
    var hasLocationPermission by mutableStateOf(initialHasLocationPermission)
    var isDrawing by mutableStateOf(initialIsDrawing)
    var latestLivePoint by mutableStateOf<PathPoint?>(null)
    var liveMovementType by mutableStateOf(MovementType.STILL)
    var currentJob: Job? = null
    var zoomInJob: Job? = null
    var zoomOutJob: Job? = null
    var isFollowingLocation by mutableStateOf(false)
}

@Composable
fun rememberHomeState(context: Context, viewModel: HomeViewModel): HomeState{
    /*
    Builds (once, via remember) the Home screen's UI-only state holder, then keeps its
    fields that mirror external sources (location permission, live GPS point/movement
    type from TrackingLiveState) up to date on every recomposition.
    */
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val initialHasLocationPermission = remember {       // Checked synchronously on first frame
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    var rememberedHasLocationPermission by remember { mutableStateOf(initialHasLocationPermission) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> rememberedHasLocationPermission = granted }

    val interactionSourceZoomIn = remember { MutableInteractionSource() }
    val interactionSourceZoomOut = remember { MutableInteractionSource() }
    val interactionSourceRecenter = remember { MutableInteractionSource() }


    val state = remember {
        HomeState(
            lifecycleOwner, permissionLauncher,
            viewModel.pathStorage,
            TrackingLiveState.isDrawing.value,
            initialHasLocationPermission,
            scope, interactionSourceZoomIn, interactionSourceZoomOut, interactionSourceRecenter
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