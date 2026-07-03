package io.github.pwlski04.swissstep.ui.home

import android.content.Context
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.pwlski04.swissstep.chains.PathOverlayLayer
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
    val pathOverlayLayer: PathOverlayLayer,
    initialIsDrawing: Boolean,
    val scope: CoroutineScope,
    val interactionSource_zoomIn: MutableInteractionSource,
    val interactionSource_zoomOut: MutableInteractionSource,
) {
    var hasLocationPermission by mutableStateOf(false)
    var isDrawing by mutableStateOf(initialIsDrawing)
    var latestLivePoint by mutableStateOf<PathPoint?>(null)
    var liveMovementType by mutableStateOf(MovementType.STILL)
    var currentJob: Job? = null
    var zoomInJob: Job? = null
    var zoomOutJob: Job? = null
    var isFollowingLocation by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
}

@Composable
fun RememberHomeState(context: Context, viewModel: HomeViewModel): HomeState{
    /*
    Builds (once, via remember) the Home screen's UI-only state holder, then keeps its
    fields that mirror external sources (location permission, live GPS point/movement
    type from TrackingLiveState) up to date on every recomposition.
    */

    val lifecycleOwner = LocalLifecycleOwner.current
    var rememberedHasLocationPermission by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()  // ← composable context

    val interactionSource_zoomIn = remember { MutableInteractionSource() }  // ← composable context
    val interactionSource_zoomOut = remember { MutableInteractionSource() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> rememberedHasLocationPermission = granted }

    val state = remember {
        HomeState(
            lifecycleOwner, permissionLauncher,
            viewModel.pathStorage, viewModel.pathOverlayLayer,
            TrackingLiveState.isDrawing.value,
            scope, interactionSource_zoomIn, interactionSource_zoomOut
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