package io.github.pwlski04.swissstep.ui.home

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.pwlski04.swissstep.accentColor_blue
import io.github.pwlski04.swissstep.accentColor_green
import io.github.pwlski04.swissstep.accentColor_main
import io.github.pwlski04.swissstep.gray_pale_subtle
import io.github.pwlski04.swissstep.accentColor_red
import io.github.pwlski04.swissstep.gray_dark
import io.github.pwlski04.swissstep.gray_light_subtle
import io.github.pwlski04.swissstep.map.LocationMarkerOverlay
import io.github.pwlski04.swissstep.map.centerMap
import io.github.pwlski04.swissstep.text_contrast
import io.github.pwlski04.swissstep.text_main
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.mapsforge.core.model.LatLong
import java.io.File
import io.github.pwlski04.swissstep.R


/*
TODO:
    - more cities
 */

@Composable
fun Page_Home(context: Context, viewModel: HomeViewModel) {
    /*
    The main map screen: hosts the offline MapView, the location marker, the path overlays, map
    controls, the saved-routes buttons, and every dialog reachable from this screen (delete/save/
    rename/route-options). HomeEffects() wires up the actual side effects (permissions,
    tracking service, camera following); instead, this function is mostly layout plus local dialog
    visibility state.
    */

    val state = RememberHomeState(context, viewModel)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteVerificationDialogFromDelete by remember { mutableStateOf(false) }
    var showDeleteVerificationDialogFromOptions by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // "Save to device": lets the user pick any on-device/cloud location via the system
    // document picker, unlike the app-to-app share sheet used by viewModel.exportRoute.
    var pendingSaveFileName by remember { mutableStateOf<String?>(null) }
    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val fileName = pendingSaveFileName
        pendingSaveFileName = null
        if (uri != null && fileName != null) {
            viewModel.saveRouteToDevice(context, fileName, uri)
        }
    }

    val accentColorMainReplayBased by remember {
        derivedStateOf {
            if (viewModel.selectedRecording == null) accentColor_main
            else Color(188, 85, 111)        // peach
        }
    }
    val accentColorMainReplayBasedSubtle by remember {
        derivedStateOf { accentColorMainReplayBased.copy(144f/255f) }
    }
    val routeProgress by viewModel.replayProgress.collectAsState()

    // Temporary green/red feedback shown over the top bar after an export attempt
    val topBarColor by remember {
        derivedStateOf {
            when (viewModel.exportResult) {
                is ExportResult.Success -> accentColor_green.copy(144f / 255f)
                is ExportResult.Failure -> accentColor_red.copy(144f / 255f)
                null -> accentColorMainReplayBasedSubtle
            }
        }
    }

    LaunchedEffect(viewModel.exportResult) {
        if (viewModel.exportResult != null) {
            delay(2500)
            viewModel.exportResult = null
        }
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
                viewModel.mapFilePath == null || viewModel.themeFilePath == null -> { }

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
                        Box(modifier = Modifier.fillMaxWidth().background(topBarColor)) {
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
                                Surface(shape = RoundedCornerShape(bottomEnd = 18.dp), color = topBarColor, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.heightIn(min = 60.dp), contentAlignment = Alignment.CenterStart
                                    ) {
                                        Icon(painter = painterResource(R.drawable.app_icon_outline), contentDescription = "SwissStep icon",
                                            Modifier.padding(start = 28.dp).size(40.dp), tint = Color.Unspecified)
                                    }
                                }

                                InverseCornerBox(color = topBarColor, cornerRadius = 12.dp, isLeft = false, modifier = Modifier.size(12.dp))

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
                                text = when (val result = viewModel.exportResult) {
                                    is ExportResult.Success -> "Exported successfully as ${result.displayName}"
                                    is ExportResult.Failure -> "Export failed"
                                    null -> if (viewModel.selectedRecording != null) "Zürich (replay)" else "Zürich"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                color = text_contrast,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }

                    // BUTTONS: Available routes
                    Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp, horizontal = 8.dp)){
                        viewModel.savedRoutes.forEach { fileName ->
                            val isSelectedRoute = viewModel.selectedRecording == fileName
                            val isLoadingThisRoute = viewModel.isReplayingRoute && isSelectedRoute

                            Box(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .drawBehind {
                                        drawRect(gray_pale_subtle)
                                        when {
                                            isLoadingThisRoute -> drawRect(
                                                color = accentColorMainReplayBased,
                                                size = Size(size.width * routeProgress, size.height)
                                            )
                                            isSelectedRoute -> drawRect(accentColorMainReplayBased)
                                        }
                                    }
                                    .combinedClickable(
                                        onClick = {
                                            currentJob?.cancel()
                                            currentJob = scope.launch {
                                                delay(150)
                                                if (!isActive) return@launch
                                                withContext(Dispatchers.Main) {
                                                    if (viewModel.selectedRecording == fileName) {
                                                        viewModel.stopReplay()
                                                        viewModel.routeRecorder.syncDisplayPoints()
                                                    } else {
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
                                    color = if (isSelectedRoute && !isLoadingThisRoute) text_contrast else accentColorMainReplayBased,
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
            isNameTaken = { candidateName -> viewModel.savedRoutes.contains("route_${candidateName}.json") },
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
            val currentFileName = viewModel.longPressedRecording!!
            RouteRenameDialog(
                initialName = currentFileName
                    .removePrefix("route_").removeSuffix(".json"),
                isNameTaken = { candidateName ->
                    val candidateFileName = "route_${candidateName}.json"
                    viewModel.savedRoutes.any { it != currentFileName && it == candidateFileName }
                },
                onRename = { newName ->
                    val oldFile = File(context.filesDir, currentFileName)
                    val newFileName = "route_${newName}.json"
                    val newFile = File(context.filesDir, newFileName)
                    // Re-check right before the actual file op: shouldn't be reachable with a
                    // taken name (the dialog already blocks it), but never silently overwrite.
                    if (newFileName == currentFileName || !newFile.exists()) {
                        oldFile.renameTo(newFile)
                    }
                    viewModel.longPressedRecording = null
                    viewModel.refreshSavedRoutes()
                },
                onBack = { showRenameDialog = false }
            )
        } else {
            RouteOptionsDialog(
                routeName = viewModel.selectedRecording,
                onRename = { showRenameDialog = true },
                onExport = {
                    viewModel.exportRoute(context, viewModel.longPressedRecording!!)
                    viewModel.longPressedRecording = null
               },
                onSaveToDevice = {
                    val fileName = viewModel.longPressedRecording!!
                    pendingSaveFileName = fileName
                    saveToDeviceLauncher.launch(fileName)
                    viewModel.longPressedRecording = null
                },
                onDelete = { showDeleteVerificationDialogFromOptions = true },
                onCancel = { viewModel.longPressedRecording = null }
            )
        }
    }
}



/* DIALOG BOXES */
@Composable
fun DialogBox(onDismiss: () -> Unit, title: String, subTitle: String?, buttonsWithSpacers: @Composable () -> Unit){
    Dialog(onDismissRequest = onDismiss){
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = text_main
                )
                if(subTitle != null){
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(subTitle, fontSize = 16.sp, color = gray_dark)
                }
                Spacer(modifier = Modifier.height(12.dp))

                buttonsWithSpacers()

                /*Spacer(modifier = Modifier.height(4.dp))

                TextButton(onClick = onDismiss, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", textDecoration = TextDecoration.Underline, color = gray_medium)
                }*/
            }
        }
    }
}

@Composable
private fun RouteOptionsDialog(
    routeName: String?,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onSaveToDevice: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    DialogBox(onDismiss = onCancel, title = "$routeName", subTitle = "Select an option:", buttonsWithSpacers = {
        OutlinedButton(onClick = onRename, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_blue)) {
            Text("Rename", color = text_main)
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(onClick = onExport, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(width = 2.dp, color = accentColor_blue)) {
            Text("Share", color = text_main)
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(onClick = onSaveToDevice, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(width = 2.dp, color = accentColor_blue)) {
            Text("Save to device", color = text_main)
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


/* ROUTE NAMING
Route names become part of a file name ("route_<name>.json"), so the allowed character set has
to stay safe for the filesystem (no path separators, no "." to rule out ".."/extension games,
no other filesystem-special characters) - but within that constraint, normal punctuation
(spaces, hyphens, underscores, apostrophes) is fine and much less strict than letters/digits-only. */
private const val ROUTE_NAME_MIN_LENGTH = 3
private const val ROUTE_NAME_MAX_LENGTH = 15

fun filterNameInput(input: String): String =
    input.filter { it.isLetterOrDigit() || it in " -_'" }

private fun isValidRouteName(name: String): Boolean =
    name.trim().length in ROUTE_NAME_MIN_LENGTH..ROUTE_NAME_MAX_LENGTH

@Composable
private fun RouteSaveDialog(
    isNameTaken: (String) -> Boolean,
    onSaveAndDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputName by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val trimmedName = inputName.trim()
    val nameTaken = trimmedName.isNotEmpty() && isNameTaken(trimmedName)
    val canSave = isValidRouteName(inputName) && !nameTaken

    fun submit() {
        if (canSave) {
            onSaveAndDelete(trimmedName)
            keyboardController?.hide()
        }
    }

    DialogBox(onDismiss = onBack, title = "Rename route", subTitle = "Enter new name:", buttonsWithSpacers = {
        OutlinedTextField(
            value = inputName,
            onValueChange = { input ->
                val filtered = filterNameInput(input)
                if (filtered.length <= ROUTE_NAME_MAX_LENGTH) inputName = filtered
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        if (nameTaken) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "A route with this name already exists",
                color = accentColor_red,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { submit() },
                enabled = canSave, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_green)) {
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
    isNameTaken: (String) -> Boolean,
    onRename: (String) -> Unit,
    onBack: () -> Unit
) {
    var inputName by remember { mutableStateOf(initialName) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val trimmedName = inputName.trim()
    val nameTaken = trimmedName != initialName.trim() && isNameTaken(trimmedName)
    val canRename = isValidRouteName(inputName) && !nameTaken

    fun submit() {
        if (canRename) {
            onRename(trimmedName)
            keyboardController?.hide()
        }
    }

    DialogBox(onDismiss = onBack, title = "Rename route", subTitle = "Enter new name:", buttonsWithSpacers = {
        OutlinedTextField(
            value = inputName,
            onValueChange = { input ->
                val filtered = filterNameInput(input)
                if (filtered.length <= ROUTE_NAME_MAX_LENGTH) inputName = filtered
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        if (nameTaken) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "A route with this name already exists",
                color = accentColor_red,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { submit() },
                enabled = canRename, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), border= BorderStroke(width = 2.dp, color = accentColor_green)) {
                Text("Rename", color = text_main)
            }
        }
    })
}



/* AESTHETIC COMPONENTS */

@Composable
fun ShadowedButton(modifier: Modifier = Modifier, content: @Composable ()->Unit){
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
    /* Draws a rounded-corner "notch" shape (the visual inverse of a normal rounded corner) used to stitch a rounded panel edge smoothly into the straight edge next to it. */
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