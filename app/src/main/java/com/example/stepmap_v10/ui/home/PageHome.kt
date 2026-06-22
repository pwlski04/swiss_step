package com.example.stepmap_v10.ui.home

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

            pathOverlayLayer = viewModel.pathOverlayLayer,

            isDrawing = isDrawing,

            permissionLauncher = permissionLauncher,
            hasLocationPermission = hasLocationPermission,
            onLocationPermissionChange = { granted -> hasLocationPermission = granted },
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
                        color = Color(red = 240, green = 240, blue = 240, 200),
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            Box(        // shadows
                                modifier = Modifier
                                    .matchParentSize()
                                    .offset(x = 1.dp, y = 2.dp)
                                    .blur(4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                    .background(
                                        Color.Black.copy(alpha = 0.08f),
                                        RoundedCornerShape(18.dp)
                                    )
                            )

                            Surface(
                                // BUTTON: START/STOP TRACKING
                                onClick = {
                                    if (!isDrawing) {
                                        viewModel.routeRecorder.startRecording()
                                        isDrawing = true
                                        Log.d("StepByStep_v1.0_TAG", "Drawing started")
                                    } else {
                                        isDrawing = false
                                        Log.d("StepByStep_v1.0_TAG", "Drawing stopped")
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = Color(red = 240, green = 240, blue = 240, 144),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDrawing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isDrawing) "Stop tracking" else "Start tracking",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (!isDrawing && viewModel.hasChainsToDisplay) { // Replay button — show when not drawing and delete when long pressing:
                            Box {
                                Box(        // shadows
                                    modifier = Modifier
                                        .matchParentSize()
                                        .offset(x = 1.dp, y = 2.dp)
                                        .blur(
                                            6.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                                        )
                                        .background(
                                            Color.Black.copy(alpha = 0.08f),
                                            RoundedCornerShape(18.dp)
                                        )
                                )

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
                                    color = Color(red = 240, green = 240, blue = 240, 144),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove history",
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.CenterStart).heightIn(max = 250.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 4.dp)){
                        val routes = remember { viewModel.routeRecorder.listSavedRoutes(context).toMutableStateList() }

                        routes.forEach { fileName ->
                            val scope = rememberCoroutineScope()
                            var holdJob by remember { mutableStateOf<Job?>(null) }

                            Box {
                                Box(        // shadows
                                    modifier = Modifier
                                        .matchParentSize()
                                        .offset(x = 1.dp, y = 1.dp)
                                        .blur(4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                        .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                                )

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(red = 240, green = 240, blue = 240, 144),
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    Text(
                                        text = fileName.removePrefix("route_")
                                            .removeSuffix(".json"),
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .padding(14.dp)
                                            .pointerInput(fileName) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        awaitFirstDown(requireUnconsumed = false)
                                                        holdJob = scope.launch {
                                                            delay(3000L)
                                                            File(
                                                                context.filesDir,
                                                                fileName
                                                            ).delete()
                                                            routes.remove(fileName)
                                                        }
                                                        do {
                                                            val event = awaitPointerEvent(
                                                                PointerEventPass.Final
                                                            )
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
                                                viewModel.routeRecorder.loadForDisplay(context, fileName)
                                                viewModel.replayRoute(context, fileName)
                                            }
                                    )
                                }
                            }
                        }
                    }


                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 12.dp)
                    ) {
                        val point = latestLivePoint

                        Box {
                            Box(        // shadows
                                modifier = Modifier
                                    .matchParentSize()
                                    .offset(x = 1.dp, y = 2.dp)
                                    .blur(4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                    .background(
                                        Color.Black.copy(alpha = 0.08f),
                                        RoundedCornerShape(18.dp)
                                    )
                            )

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
                                color = Color(red = 240, green = 240, blue = 240, 144),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = "Recenter map",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        //BUTTON: ZOOM IN
                        Box {
                            Box(        // shadows
                                modifier = Modifier
                                    .matchParentSize()
                                    .offset(x = 1.dp, y = 2.dp)
                                    .blur(4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                    .background(
                                        Color.Black.copy(alpha = 0.08f),
                                        RoundedCornerShape(18.dp)
                                    )
                            )

                            Surface(
                                onClick = {
                                    viewModel.sharedMapView?.let { mv ->
                                        val currentZoom = mv.model.mapViewPosition.zoomLevel
                                        mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = Color(red = 240, green = 240, blue = 240, 144),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Zoom in",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        //BUTTON: ZOOM OUT
                        Box {
                            Box(        // shadows
                                modifier = Modifier
                                    .matchParentSize()
                                    .offset(x = 1.dp, y = 2.dp)
                                    .blur(4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                    .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            )

                            Surface(
                                onClick = {
                                    viewModel.sharedMapView?.let { mv ->
                                        val currentZoom = mv.model.mapViewPosition.zoomLevel
                                        mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = Color(red = 240, green = 240, blue = 240, 144),
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
                viewModel.routeRecorder.displayPoints.clear()
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
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Delete history", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Do you want to save this route before deleting it?")
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onSaveAndDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Save and delete")
                }
                TextButton(onClick = onDeleteOnly, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete")
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}