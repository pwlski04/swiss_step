package com.example.stepmap_v10.ui.home

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.stepmap_v10.accentColor_blue
import com.example.stepmap_v10.accentColor_green
import com.example.stepmap_v10.accentColor_main
import com.example.stepmap_v10.gray_pale_subtle
import com.example.stepmap_v10.accentColor_red
import com.example.stepmap_v10.gray_dark
import com.example.stepmap_v10.gray_light_subtle
import com.example.stepmap_v10.gray_medium
import com.example.stepmap_v10.map.LocationMarkerOverlay
import com.example.stepmap_v10.map.centerMap
import com.example.stepmap_v10.text_contrast
import com.example.stepmap_v10.text_main
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.mapsforge.core.model.LatLong
import java.io.File


/*
TODO:
    - more cities
 */

@Composable
fun Page_Home(context: Context, viewModel: HomeViewModel) {
    val state = RememberHomeState(context, viewModel)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteVerificationDialogFromDelete by remember { mutableStateOf(false) }
    var showDeleteVerificationDialogFromOptions by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val accentColorMainReplayBased by remember {
        derivedStateOf {
            if (viewModel.selectedRecording == null) accentColor_main
            else Color(188, 85, 111)        // peach
        }
    }
    val accentColorMainReplayBasedSubtle by remember {
        derivedStateOf { accentColorMainReplayBased.copy(144f/255f) }
    }

    with(state) { // To not have to write state.xyz everywhere
        val isPressed by interactionSource_zoomIn.collectIsPressedAsState()

        HomeEffects(
            context = context,
            lifecycleOwner = state.lifecycleOwner,

            mapView = viewModel.sharedMapView,
            viewModel = viewModel,

            pathOverlayLayer = viewModel.pathOverlayLayer,
            isDrawing = isDrawing,

            latestLivePoint = latestLivePoint,
            isFollowingLocation = isFollowingLocation,

            permissionLauncher = permissionLauncher,
            hasLocationPermission = hasLocationPermission,
            onLocationPermissionChange = { granted -> hasLocationPermission = granted }
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        // Bar — fills status bar area only, notch hangs below it
                        Box(modifier = Modifier.fillMaxWidth().background(accentColorMainReplayBasedSubtle)) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()){
                            // Row with notch and button, sitting below the bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(shape = RoundedCornerShape(bottomEnd = 18.dp), color = accentColorMainReplayBasedSubtle, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.heightIn(min = 60.dp), contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(text="[swst]", fontWeight = FontWeight.ExtraLight, fontFamily = FontFamily.SansSerif, fontSize = 20.sp,
                                            color= text_contrast, modifier = Modifier.padding(start = 20.dp))
                                    }
                                }

                                InverseCornerBox(color = accentColorMainReplayBasedSubtle, cornerRadius = 12.dp, isLeft = false, modifier = Modifier.size(12.dp))

                                Column{
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // BUTTON: START/STOP TRACKING
                                    ShadowedButton(content = {
                                        if(isDrawing){
                                            Surface(
                                                onClick = { isDrawing = false },
                                                shape = RoundedCornerShape(18.dp), color = gray_pale_subtle,
                                                modifier = Modifier.padding(4.dp).border(width = 1.dp, color = accentColor_main, shape = RoundedCornerShape(18.dp))
                                            ){
                                                Icon(
                                                    imageVector = Icons.Filled.Pause,
                                                    contentDescription = null, modifier = Modifier.padding(14.dp), tint = accentColor_main
                                                )
                                            }
                                        } else {
                                            Surface(
                                                onClick = { viewModel.routeRecorder.clearInProgress(context); viewModel.routeRecorder.startRecording(); isDrawing = true },
                                                shape = RoundedCornerShape(18.dp), color = accentColor_main, modifier = Modifier.padding(4.dp)
                                            ){
                                                Icon(
                                                    imageVector =  Icons.Filled.PlayArrow,
                                                    contentDescription = null, modifier = Modifier.padding(14.dp), tint = text_contrast
                                                )
                                            }
                                        }
                                    })

                                    //Spacer(modifier = Modifier.height(4.dp))

                                    if (!isDrawing && viewModel.hasChainsToDisplay) {

                                        //BUTTON: REMOVE HISTORY
                                        ShadowedButton(content = {
                                            Surface(onClick = { showDeleteDialog = true }, shape = RoundedCornerShape(18.dp), color = gray_pale_subtle,
                                                modifier = Modifier.padding(4.dp).border(width = 1.dp, color = accentColor_main, shape = RoundedCornerShape(18.dp))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline, contentDescription = "Remove history",
                                                    modifier = Modifier.padding(14.dp), tint = accentColor_main
                                                )
                                            }
                                        })
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Text(
                                text = if(viewModel.selectedRecording != null) "Zürich (replay)" else "Zürich",
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                color = text_contrast,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )
                        }
                    }

                    // BUTTONS: Available routes
                    Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp, horizontal = 8.dp)){
                        viewModel.savedRoutes.forEach { fileName ->
                            Surface(
                                color = if(viewModel.selectedRecording == fileName) accentColorMainReplayBased else gray_pale_subtle,
                                modifier = Modifier.padding(6.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if(viewModel.selectedRecording == fileName) gray_light_subtle else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {
                                            currentJob?.cancel()
                                            currentJob = scope.launch {
                                                delay(150) // Ignore quick taps
                                                if (!isActive) return@launch

                                                withContext(Dispatchers.Main) {
                                                    val wasSelected = viewModel.selectedRecording == fileName

                                                    viewModel.replayStorage.clearSegments()
                                                    if(wasSelected){
                                                        viewModel.routeRecorder.syncDisplayPoints()
                                                        viewModel.selectedRecording = null
                                                        viewModel.sharedMapView?.layerManager?.redrawLayers()
                                                        pathOverlayLayer.isDisplayed = true
                                                    } else {
                                                        viewModel.routeRecorder.displayPoints.clear()
                                                        viewModel.selectedRecording = null
                                                        viewModel.sharedMapView?.layerManager?.redrawLayers()
                                                        pathOverlayLayer.isDisplayed = true
                                                        viewModel.routeRecorder.loadForDisplay(context, fileName)
                                                        viewModel.replayRoute(context, fileName)
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.longPressedRecording = fileName
                                        }
                                    )
                                    .border(width = 1.dp, color = accentColorMainReplayBased, shape = RoundedCornerShape(18.dp))
                            ) {
                                Text(
                                    text = fileName.removePrefix("route_").removeSuffix(".json"),
                                    fontSize = 12.sp,
                                    color = if(viewModel.selectedRecording == fileName) text_contrast else accentColorMainReplayBased,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp)
                                )
                            }
                        }
                    }


                    Column(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 64.dp, start = 12.dp, end = 12.dp)) {
                        val point = latestLivePoint

                        // BUTTON: RECENTER MAP
                        ShadowedButton(content = {
                            Surface(
                                color = if (isPressed) gray_pale_subtle.copy(alpha = 0.7f) else if (isFollowingLocation) gray_light_subtle else gray_pale_subtle,
                                modifier = Modifier.padding(4.dp).clip(shape=RoundedCornerShape(18.dp)).combinedClickable(
                                    onClick = {
                                        zoomInJob?.cancel()
                                        zoomOutJob?.cancel()

                                        val p = point ?: return@combinedClickable
                                        viewModel.sharedMapView.centerMap(LatLong(p.lat, p.lon))
                                    },
                                    onDoubleClick = {
                                        isFollowingLocation = !isFollowingLocation
                                    },
                                    onLongClick = {
                                        zoomInJob?.cancel()
                                        zoomOutJob?.cancel()

                                        val p = point ?: return@combinedClickable
                                        viewModel.sharedMapView.centerMap(LatLong(p.lat, p.lon))

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
                                color = if (isPressed) gray_pale_subtle.copy(alpha = 0.7f) else gray_pale_subtle,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .indication(interactionSource_zoomIn, ripple())
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                zoomInJob?.cancel()
                                                zoomOutJob?.cancel()
                                                viewModel.sharedMapView?.let { mv ->
                                                    val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                    mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                                }
                                            },
                                            onLongPress = {
                                                zoomOutJob?.cancel()
                                                zoomInJob = scope.launch {
                                                    while (true) {
                                                        viewModel.sharedMapView?.let { mv ->
                                                            val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                            mv.setZoomLevel((currentZoom + 1).coerceIn(13, 20).toByte())
                                                        }
                                                        delay(150L)
                                                    }
                                                }
                                            },
                                            onPress = {
                                                val press = PressInteraction.Press(it)
                                                interactionSource_zoomIn.emit(press)

                                                tryAwaitRelease()
                                                interactionSource_zoomIn.emit(PressInteraction.Release(press))

                                                zoomInJob?.cancel()
                                                zoomInJob = null
                                            }
                                        )
                                    }
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
                                color = gray_pale_subtle,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .indication(interactionSource_zoomOut, ripple())
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                zoomInJob?.cancel()
                                                zoomOutJob?.cancel()
                                                viewModel.sharedMapView?.let { mv ->
                                                    val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                    mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                                }
                                            },
                                            onLongPress = {
                                                zoomInJob?.cancel()
                                                zoomOutJob = scope.launch {
                                                    while (true) {
                                                        viewModel.sharedMapView?.let { mv ->
                                                            val currentZoom = mv.model.mapViewPosition.zoomLevel
                                                            mv.setZoomLevel((currentZoom - 1).coerceIn(13, 20).toByte())
                                                        }
                                                        delay(150L)
                                                    }
                                                }
                                            },
                                            onPress = {
                                                val press = PressInteraction.Press(it)
                                                interactionSource_zoomOut.emit(press)

                                                tryAwaitRelease()
                                                interactionSource_zoomOut.emit(PressInteraction.Release(press))

                                                zoomOutJob?.cancel()
                                                zoomOutJob = null
                                            }
                                        )
                                    }
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
        RouteDeleteDialog(
            viewModel,
            onSaveAndDelete = {
                showDeleteDialog = false
                showSaveDialog = true
            },
            onDeleteOnly = {
                showDeleteDialog = false
                showDeleteVerificationDialogFromDelete = true
            },
            onCancel = { showDeleteDialog = false }
        )
    }

    if(showDeleteVerificationDialogFromDelete){
        RouteDeleteVerificationDialog(
            onDeleteOnly = {
                state.pathStorage.clearSegments()
                viewModel.routeRecorder.displayPoints.clear()
                viewModel.sharedMapView?.layerManager?.redrawLayers()
                viewModel.deleteSavedChains()
                viewModel.selectedRecording = null

                showDeleteVerificationDialogFromDelete = false
           },
            onCancel = {
                showDeleteVerificationDialogFromDelete = false
            }
        )
    }
    if(showDeleteVerificationDialogFromOptions){
        RouteDeleteVerificationDialog(
            onDeleteOnly = {
                File(context.filesDir, viewModel.longPressedRecording!!).delete()
                //viewModel.longPressedRecording!!.first.remove(viewModel.longPressedRecording!!.second)
                viewModel.longPressedRecording = null
                viewModel.refreshSavedRoutes()

                showDeleteVerificationDialogFromOptions = false
            },
            onCancel = {
                showDeleteVerificationDialogFromOptions = false
            }
        )
    }

    if(showSaveDialog){
        RouteSaveDialog(
            onSaveAndDelete = {inputName ->
                viewModel.routeRecorder.stopAndSave(context, inputName)
                viewModel.refreshSavedRoutes()

                state.pathStorage.clearSegments()
                viewModel.routeRecorder.displayPoints.clear()
                viewModel.sharedMapView?.layerManager?.redrawLayers()
                viewModel.deleteSavedChains()
                viewModel.selectedRecording = null

                showSaveDialog = false
            }, onBack = {
                showSaveDialog = false
                showDeleteDialog = true
            }
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
            RouteOptionsDialog(
                routeName = viewModel.selectedRecording,
                onRename = { showRenameDialog = true },
                onDelete = { showDeleteVerificationDialogFromOptions = true },
                onCancel = { viewModel.longPressedRecording = null }
            )
        }
    }
}



/* DIALOG BOXES */
@Composable
private fun DialogBox(onDismiss: () -> Unit, title: String, subTitle: String, buttonsWithSpacers: @Composable () -> Unit){
    Dialog(onDismissRequest = onDismiss){
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = text_main
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(subTitle, fontSize = 16.sp, color = gray_dark)
                Spacer(modifier = Modifier.height(12.dp))

                buttonsWithSpacers()

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(onClick = onDismiss, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", textDecoration = TextDecoration.Underline, color = gray_medium)
                }
            }
        }
    }
}

@Composable
private fun RouteOptionsDialog(
    routeName: String?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    DialogBox(onDismiss = onCancel, title = "Route $routeName", subTitle = "Select an option:", buttonsWithSpacers = {
        OutlinedButton(onClick = onRename, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_blue)) {
            Text("Rename", color = text_main)
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(width = 2.dp, color = accentColor_red)) {
            Text("Delete", color = text_main)
        }
    })
}


@Composable
private fun RouteDeleteDialog(
    viewModel: HomeViewModel,
    onSaveAndDelete: () -> Unit,
    onDeleteOnly: () -> Unit,
    onCancel: () -> Unit
) {
    DialogBox(onDismiss = onCancel, title = "Delete history", subTitle = "Do you want to save this route before deleting it?", buttonsWithSpacers = {
        if (viewModel.selectedRecording == null) {
            OutlinedButton(onClick = onSaveAndDelete, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_blue)) {
                Text("Save and delete", color = text_main)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(onClick = onDeleteOnly, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(width = 2.dp, color = accentColor_red)) {
            Text("Delete", color = text_main)
        }
    })
}


@Composable
private fun RouteDeleteVerificationDialog(
    onDeleteOnly: () -> Unit,
    onCancel: () -> Unit
) {
    DialogBox(onDismiss = onCancel, title = "", subTitle = "Are you sure you want to delete this route?", buttonsWithSpacers = {
        OutlinedButton(onClick = onDeleteOnly, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(width = 2.dp, color = accentColor_red)) {
            Text("Delete", color = text_main)
        }
    })
}


@Composable
private fun RouteSaveDialog(
    onSaveAndDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputName by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    DialogBox(onDismiss = onBack, title = "Rename route", subTitle = "Enter new name:", buttonsWithSpacers = {
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
                        onSaveAndDelete(inputName)
                        keyboardController?.hide()
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = {
                    if (inputName.length in 3..15) {
                        onSaveAndDelete(inputName)
                        keyboardController?.hide()
                    }
                }, enabled = inputName.length in 3..15, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_green)) {
                Text("Save and rename", color = text_main)
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = { onSaveAndDelete("") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(width = 2.dp, color = accentColor_blue)) {
                Text("Save default", color = text_main)
            }
        }
    })
}


@Composable
private fun RouteRenameDialog(
    initialName: String,
    onRename: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputName by remember { mutableStateOf(initialName) }
    val keyboardController = LocalSoftwareKeyboardController.current

    DialogBox(onDismiss = onBack, title = "Rename route", subTitle = "Enter new name:", buttonsWithSpacers = {
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

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = {
                    if (inputName.length in 3..15) {
                        onRename(inputName)
                        keyboardController?.hide()
                    }
                }, enabled = inputName.length in 3..15, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_green)) {
                Text("Rename", color = text_main)
            }
        }
    })
}



/* AESTHETIC COMPONENTS */

@Composable
private fun ShadowedButton(modifier: Modifier = Modifier, content: @Composable ()->Unit){
    Box(modifier) {       //BUTTON
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
fun InverseCornerBox(
    color: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    isLeft: Boolean = true
) {
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    Canvas(modifier = modifier) {
        val path = Path().apply {
            if (isLeft) {
                // Original flipped both vertically and horizontally
                moveTo(size.width, size.height)
                lineTo(0f, size.height)
                lineTo(0f, radiusPx)
                arcTo(
                    rect = Rect(-radiusPx, 0f, radiusPx, radiusPx * 2),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(size.width, 0f)
            } else {
                // Original flipped vertically only
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width, radiusPx)
                arcTo(
                    rect = Rect(size.width - radiusPx, 0f, size.width + radiusPx, radiusPx * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(0f, 0f)
            }
            close()
        }
        drawPath(path, color = color)
    }
}