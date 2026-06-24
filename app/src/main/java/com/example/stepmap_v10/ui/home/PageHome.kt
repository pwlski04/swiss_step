package com.example.stepmap_v10.ui.home

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mapsforge.map.android.view.MapView

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.stepmap_v10.accentColor1
import com.example.stepmap_v10.accentColor2
import com.example.stepmap_v10.map.LocationMarkerOverlay
import com.example.stepmap_v10.map.centerMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.mapsforge.core.model.LatLong
import java.io.File


/*
TODO:
- experience
    - save paths under a name when resetting to reuse later
    - improve map boundaries
    - more cities
 */

@Composable
fun Page_Home(context: Context, viewModel: HomeViewModel) {
    val state = RememberHomeState(context, viewModel)

    val scope = rememberCoroutineScope()
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var zoomJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val interactionSource_zoomIn = remember { MutableInteractionSource() }
    val interactionSource_zoomOut = remember { MutableInteractionSource() }
    val isPressed by interactionSource_zoomIn.collectIsPressedAsState()

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
                        color = accentColor1,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    ) {
                        Text(
                            text = "ZOOM: " + viewModel.sharedMapView?.model?.mapViewPosition?.zoomLevel?.toString(),
                            modifier = Modifier.width(160.dp).padding(top = 16.dp, bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }


                    Column(modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 12.dp)) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // BUTTON: START/STOP TRACKING
                        ShadowedButton(content = {
                            Surface(
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
                                color = accentColor2,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDrawing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isDrawing) "Stop tracking" else "Start tracking",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        })

                        Spacer(modifier = Modifier.height(4.dp))

                        if (!isDrawing && viewModel.hasChainsToDisplay) {

                            //BUTTON: REMOVE HISTORY
                            ShadowedButton(content = {
                                Surface(
                                    onClick = { showDeleteDialog = true },
                                    shape = RoundedCornerShape(18.dp),
                                    color = accentColor2,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove history",
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp, horizontal = 8.dp)){
                        viewModel.savedRoutes.forEach { fileName ->
                            Surface(
                                color = accentColor2,
                                modifier = Modifier.padding(6.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if(viewModel.selectedRecording == fileName) Color.DarkGray else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {
                                            currentJob?.cancel()
                                            currentJob = scope.launch {
                                                delay(150) // Ignore quick taps
                                                if (!isActive) return@launch

                                                withContext(Dispatchers.Main) {
                                                    // Clear previous route
                                                    state.pathStorage.clearSegments()
                                                    viewModel.routeRecorder.displayPoints.clear()
                                                    viewModel.deleteSavedChains()
                                                    viewModel.selectedRecording = null
                                                    viewModel.sharedMapView?.layerManager?.redrawLayers()

                                                    // Load new route
                                                    viewModel.routeRecorder.loadForDisplay(context, fileName)
                                                    viewModel.replayRoute(context, fileName)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.longPressedRecording = fileName
                                        }
                                    )
                            ) {
                                Text(
                                    text = fileName.removePrefix("route_").removeSuffix(".json"),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }


                    Column(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 64.dp, start = 12.dp, end = 12.dp)) {
                        val point = latestLivePoint

                        // BUTTON: RECENTER MAP
                        ShadowedButton(content = {
                            Surface(
                                color = if (isPressed) accentColor2.copy(alpha = 0.7f) else accentColor2,
                                modifier = Modifier.padding(4.dp).clip(shape=RoundedCornerShape(18.dp)).combinedClickable(
                                    onClick = {
                                        viewModel.sharedMapView.centerMap(point?.let { LatLong(it.lat, it.lon) } as LatLong)
                                    },
                                    onLongClick = {
                                        viewModel.sharedMapView.centerMap(point?.let { LatLong(it.lat, it.lon) } as LatLong)

                                        viewModel.sharedMapView?.let { mv ->
                                            mv.model.mapViewPosition.zoomLevel = 18.toByte()
                                            mv.layerManager.redrawLayers()
                                        }
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = "Recenter map",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        })
                        Spacer(modifier = Modifier.height(8.dp))

                        // BUTTON: ZOOM IN
                        ShadowedButton( content = {
                            Surface(
                                color = if (isPressed) accentColor2.copy(alpha = 0.7f) else accentColor2,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .combinedClickable(
                                        interactionSource = interactionSource_zoomIn,
                                        indication = ripple(),
                                        onClick = {
                                            viewModel.sharedMapView?.let { mv ->
                                                val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                            }
                                        },
                                        onLongClick = {
                                            zoomJob = scope.launch {
                                                while (true) {
                                                    viewModel.sharedMapView?.let { mv ->
                                                        val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                        mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                                    }
                                                    delay(300L)
                                                }
                                            }
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Zoom in",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        })

                        // BUTTON: ZOOM OUT
                        ShadowedButton( content = {
                            Surface(
                                color = accentColor2,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .combinedClickable(
                                        interactionSource = interactionSource_zoomOut,
                                        indication = ripple(),
                                        onClick = {
                                            viewModel.sharedMapView?.let { mv ->
                                                val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                            }
                                        },
                                        onLongClick = {
                                            zoomJob = scope.launch {
                                                while (true) {
                                                    viewModel.sharedMapView?.let { mv ->
                                                        val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                        mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                                    }
                                                    delay(300L)
                                                }
                                            }
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Zoom out",
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        })
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
                viewModel.refreshSavedRoutes()

                state.pathStorage.clearSegments()
                viewModel.routeRecorder.displayPoints.clear()
                viewModel.sharedMapView?.layerManager?.redrawLayers()
                viewModel.deleteSavedChains()
                viewModel.selectedRecording = null
                showDeleteDialog = false
            },
            onDeleteOnly = {
                state.pathStorage.clearSegments()
                viewModel.routeRecorder.displayPoints.clear()
                viewModel.sharedMapView?.layerManager?.redrawLayers()
                viewModel.deleteSavedChains()
                viewModel.selectedRecording = null
                showDeleteDialog = false
            },
            onCancel = { showDeleteDialog = false }
        )
    }

    if(viewModel.longPressedRecording != null){        // pass the long-pressed route!
        var showRenameDialog by remember { mutableStateOf(false) }

        if (showRenameDialog) {
            RouteRenameDialog(
                initialName = viewModel.longPressedRecording!!
                    .removePrefix("route_").removeSuffix(".json"),
                onRename = { newName ->
                    val oldFile = File(context.filesDir, viewModel.longPressedRecording!!)
                    val newFileName = "route_${newName}.json"
                    oldFile.renameTo(File(context.filesDir, newFileName))
                    viewModel.longPressedRecording = null
                    viewModel.refreshSavedRoutes()
                },
                onBack = { showRenameDialog = false }
            )
        } else {
            RouteDialog(
                routeName = viewModel.selectedRecording,
                onRename = { showRenameDialog = true },
                onDelete = {
                    File(context.filesDir, viewModel.longPressedRecording!!).delete()
                    //viewModel.longPressedRecording!!.first.remove(viewModel.longPressedRecording!!.second)
                    viewModel.longPressedRecording = null
                    viewModel.refreshSavedRoutes()
                },
                onCancel = { viewModel.longPressedRecording = null }
            )
        }
    }
}

@Composable
private fun ShadowedButton(content: @Composable ()->Unit){
    Box {       //BUTTON
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

        content()
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


@Composable
private fun RouteDialog(
    routeName: String?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Options for route "+routeName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select an option:")
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                    Text("Rename route")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete route")
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}


@Composable
private fun RouteRenameDialog(
    initialName: String,
    onRename: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputName by remember { mutableStateOf(initialName) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(onDismissRequest = onBack) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rename route", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("New name:")
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isLetterOrDigit() }
                        if (filtered.length <= 15) inputName = filtered
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (inputName.length in 3..15) {
                                onRename(inputName)
                                keyboardController?.hide()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                    TextButton(
                        onClick = {
                            if (inputName.length in 3..15) {
                                onRename(inputName)
                                keyboardController?.hide()
                            }
                        },
                        enabled = inputName.length in 3..15
                    ) {
                        Text("Rename")
                    }
                }
            }
        }
    }
}