package com.example.stepmap_v10.ui.home

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mapsforge.map.android.view.MapView

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.example.stepmap_v10.map.LocationMarkerOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import org.mapsforge.core.model.LatLong
import java.io.File


/*
TODO:
- to fix:
    - path drawing
    - should not draw still paths
- experience
    - save paths under a name when resetting to reuse later
    - improve map boundaries
    - more cities
 */

@Composable
fun Page_Home(context: Context, viewModel: HomeViewModel) {
    val state = RememberHomeState(context, viewModel)

    var showDeleteDialog by remember { mutableStateOf(false) }

    with(state) { // To not have to write state.xyz everywhere
        HomeEffects(
            context = context,
            lifecycleOwner = state.lifecycleOwner,

            mapView = viewModel.sharedMapView,
            viewModel = viewModel,

            pathStorage = viewModel.pathStorage,
            pathOverlayLayer = viewModel.pathOverlayLayer,
            segmentIndex = viewModel.segmentIndex,

            isDrawing = isDrawing,
            latestLivePoint = latestLivePoint,
            liveMovementType = liveMovementType,

            permissionLauncher = permissionLauncher,
            hasLocationPermission = hasLocationPermission,
            onLocationPermissionChange = { granted -> hasLocationPermission = granted },

            onError = { message -> errorMessage = message },

            onMapViewReady = { readyMapView ->
                if (viewModel.sharedMapView == null) {
                    viewModel.sharedMapView = readyMapView
                }
            }
        )

        /* FRONTEND */
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.errorMessage != null -> Text("Map load error: ${viewModel.errorMessage}")
                viewModel.mapFilePath == null || viewModel.themeFilePath == null -> Text("Preparing offline map...")

                else -> Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!hasLocationPermission) {
                        Text(
                            text = "Location permission needed.",
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        OfflineMapScreen(
                            modifier = Modifier.fillMaxSize(),
                            mapFilePath = viewModel.mapFilePath!!,
                            themeFilePath = viewModel.themeFilePath!!,
                            existingMapView = viewModel.sharedMapView,
                            onMapReady = { readyMapView: MapView ->
                                /*mapView = readyMapView
                                readyMapView.layerManager.redrawLayers()*/
                                if (viewModel.sharedMapView == null) {
                                    viewModel.sharedMapView = readyMapView
                                }
                            }
                        )
                    }

                    LocationMarkerOverlay(
                        mapView = viewModel.sharedMapView,
                        position = state.latestLivePoint?.let {
                            LatLong(it.lat, it.lon)
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = liveMovementType.name + ", ZOOM: " + viewModel.sharedMapView?.model?.mapViewPosition?.zoomLevel?.toString(),
                            modifier = Modifier
                                .width(160.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }


                    Column(modifier = Modifier.align(Alignment.TopEnd)) {
                        Surface(
                            // BUTTON: START/STOP TRACKING
                            onClick = {
                                if (!isDrawing) {
                                    isDrawing = true
                                    Log.d("StepByStep_v1.0_TAG", "Drawing started")
                                } else {
                                    isDrawing = false
                                    Log.d("StepByStep_v1.0_TAG", "Drawing stopped")
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .padding(top = 8.dp, end = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isDrawing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isDrawing) "Stop tracking" else "Start tracking",
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        if (isDrawing) {
                            Surface(
                                onClick = {
                                    if (viewModel.routeRecorder.isRecording) {
                                        viewModel.routeRecorder.stopAndSave(context)
                                    } else {
                                        viewModel.routeRecorder.startRecording()
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier.padding(top = 12.dp, end = 12.dp)
                            ) {
                                Icon(
                                    imageVector = if (viewModel.routeRecorder.isRecording)
                                        Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                    contentDescription = "Record route",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        } else { // Replay button — show when not drawing and delete when long pressing:
                            if(viewModel.hasChainsToDisplay) {
                                Surface(
                                    //BUTTON: REMOVE HISTORY
                                    onClick = {
                                        /*
                                        state.pathStorage.clearSegments()
                                        viewModel.sharedMapView?.layerManager?.redrawLayers()
                                        viewModel.deleteSavedChains()
                                        */
                                        showDeleteDialog = true
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    tonalElevation = 6.dp,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.padding(top = 12.dp, end = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove history",
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.CenterStart).heightIn(max = 250.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 4.dp)){
                        val routes = remember { viewModel.routeRecorder.listSavedRoutes(context).toMutableStateList() }

                        routes.forEach { fileName ->
                            val scope = rememberCoroutineScope()
                            var holdJob by remember { mutableStateOf<Job?>(null) }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(
                                    text = fileName.removePrefix("route_").removeSuffix(".json"),
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .pointerInput(fileName) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    holdJob = scope.launch {
                                                        delay(3000L)
                                                        File(context.filesDir, fileName).delete()
                                                        routes.remove(fileName)
                                                    }
                                                    do {
                                                        val event = awaitPointerEvent(
                                                            PointerEventPass.Final)
                                                        if (event.changes.all { !it.pressed }) {
                                                            holdJob?.cancel()
                                                            holdJob = null
                                                            break
                                                        }
                                                    } while (true)
                                                }
                                            }
                                        }
                                        .clickable {
                                            viewModel.replayRoute(context, fileName)
                                        }
                                )
                            }
                        }
                    }


                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 12.dp)
                    ) {
                        val point = latestLivePoint

                        Surface(
                            //BUTTON: RECENTER MAP
                            onClick = {
                                val mv = viewModel.sharedMapView
                                val p = point

                                if (mv != null && p != null) {
                                    // ACTION_DOWN cancels fling in any scrollable view
                                    val downEvent = MotionEvent.obtain(
                                        System.currentTimeMillis(),
                                        System.currentTimeMillis(),
                                        MotionEvent.ACTION_DOWN,
                                        mv.width / 2f,
                                        mv.height / 2f,
                                        0
                                    )
                                    val cancelEvent = MotionEvent.obtain(
                                        System.currentTimeMillis(),
                                        System.currentTimeMillis(),
                                        MotionEvent.ACTION_CANCEL,
                                        mv.width / 2f,
                                        mv.height / 2f,
                                        0
                                    )
                                    mv.dispatchTouchEvent(downEvent)
                                    mv.dispatchTouchEvent(cancelEvent)
                                    downEvent.recycle()
                                    cancelEvent.recycle()

                                    // MAIN
                                    mv.model.mapViewPosition.setCenter(LatLong(p.lat, p.lon))
                                    mv.model.mapViewPosition.zoomLevel = 18.toByte()
                                    mv.layerManager.redrawLayers()
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(4.dp, bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = "Recenter map",
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        //BUTTON: ZOOM IN
                        Surface(
                            onClick = {
                                viewModel.sharedMapView?.let { mv ->
                                    val currentZoom = mv.model.mapViewPosition.zoomLevel
                                    mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Zoom in",
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        //BUTTON: ZOOM OUT
                        Surface(
                            onClick = {
                                viewModel.sharedMapView?.let { mv ->
                                    val currentZoom = mv.model.mapViewPosition.zoomLevel
                                    mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Zoom out",
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            if (isDrawing) {
                Text(
                    text = "Drawing active",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteHistoryDialog(
            onSaveAndDelete = {
                viewModel.routeRecorder.stopAndSave(context)
                state.pathStorage.clearSegments()
                viewModel.sharedMapView?.layerManager?.redrawLayers()
                viewModel.deleteSavedChains()
                showDeleteDialog = false
            },
            onDeleteOnly = {
                state.pathStorage.clearSegments()
                viewModel.sharedMapView?.layerManager?.redrawLayers()
                viewModel.deleteSavedChains()
                showDeleteDialog = false
            },
            onCancel = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DeleteHistoryDialog(
    onSaveAndDelete: () -> Unit,
    onDeleteOnly: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete history") },
        text = { Text("Do you want to save this route before deleting it?") },
        confirmButton = {
            TextButton(onClick = onSaveAndDelete) {
                Text("Save and delete")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleteOnly) { Text("Delete") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}