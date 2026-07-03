package io.github.pwlski04.swissstep.ui.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.pwlski04.swissstep.chains.PathOverlayLayer
import io.github.pwlski04.swissstep.chains.PathStorage
import io.github.pwlski04.swissstep.paths.Path
import io.github.pwlski04.swissstep.paths.SegmentIndex
import io.github.pwlski04.swissstep.paths.loadPathsFromGeoJson
import io.github.pwlski04.swissstep.paths.toSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.view.MapView
import io.github.pwlski04.swissstep.map.copyAssetToInternalStorage
import io.github.pwlski04.swissstep.tracking.AppPathStorage
import io.github.pwlski04.swissstep.tracking.AppSegmentIndex
import io.github.pwlski04.swissstep.tracking.TrackingLiveState
import io.github.pwlski04.swissstep.tracking.loadIsDrawing
import io.github.pwlski04.swissstep.chains.AppRouteRecorder
import io.github.pwlski04.swissstep.chains.RecordedRoute
import io.github.pwlski04.swissstep.chains.RouteBundle
import io.github.pwlski04.swissstep.chains.uniqueRouteFileName
import io.github.pwlski04.swissstep.map.RawGpsPointsLayer
import io.github.pwlski04.swissstep.chains.RouteRecorder
import io.github.pwlski04.swissstep.colorMap
import io.github.pwlski04.swissstep.tracking.LocationTrackingService
import io.github.pwlski04.swissstep.tracking.MovementType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.mapsforge.core.model.LatLong
import java.io.File


class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // HOME PAGE
    val routeRecorder = RouteRecorder()
    var isReplayingRoute by mutableStateOf(false)

    var sharedMapView by mutableStateOf<MapView?>(null)
    var rawGpsPointsLayer: RawGpsPointsLayer? = null
    var hasInitiallyCentered = false

    val pathStorage = PathStorage()
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }

    val replayStorage = PathStorage()
    val replayOverlayLayer = PathOverlayLayer(replayStorage).also {
        replayStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }

    var allPaths by mutableStateOf<List<Path>>(emptyList())
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var segmentIndex by mutableStateOf<SegmentIndex?>(null)
    private var wasDrawing: Boolean

    var hasChainsToDisplay by mutableStateOf(false)
        private set

    var longPressedRecording: String? by mutableStateOf(null)
    var selectedRecording: String? by mutableStateOf(null)
    val replayProgress = MutableStateFlow(0f)

    // Requests are processed one at a time; a new request always fully cancels
    // and awaits the in-flight one before starting, however fast they arrive.
    private val replayRequests = MutableSharedFlow<ReplayRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private sealed class ReplayRequest {
        data class Play(val context: Context, val fileName: String) : ReplayRequest()
        object Stop : ReplayRequest()
    }

    var exportResult by mutableStateOf<ExportResult?>(null)


    var savedRoutes = mutableStateListOf<String>()

    fun refreshSavedRoutes() {
        viewModelScope.launch(Dispatchers.Main){
            val newRoutes = withContext(Dispatchers.IO){
                routeRecorder.listSavedRoutes(getApplication())
            }
            savedRoutes.clear()
            savedRoutes.addAll(newRoutes)
        }
    }

    /* PREFERENCES PAGE */
    private val prefs = getApplication<Application>().getSharedPreferences("swissstep_prefs", Context.MODE_PRIVATE)

    private var _userName by mutableStateOf(prefs.getString("userName", "[name]") ?: "[name]")
    var userName : String
        get() = prefs.getString("userName", "[name]") ?: "[name]"
        set(value){
            prefs.edit().putString("userName", value).apply()
        }

    private var _showLocationPoints by mutableStateOf(prefs.getBoolean("showLocationPoints", false))
    var showLocationPoints: Boolean
        get() = _showLocationPoints
        set(value) {
            _showLocationPoints = value
            prefs.edit().putBoolean("showLocationPoints", value).apply()
        }

    private var _showPathColorChoice by mutableStateOf(prefs.getBoolean("showPathColorChoice", false))
    var showPathColorChoice: Boolean
        get() = _showPathColorChoice
        set(value) {
            _showPathColorChoice = value
            prefs.edit().putBoolean("showPathColorChoice", value).apply()

            pathOverlayLayer.useCustomColors = value
            pathOverlayLayer.clearPaintCache()

            replayOverlayLayer.useCustomColors = value
            replayOverlayLayer.clearPaintCache()
            sharedMapView?.layerManager?.redrawLayers()
        }

    fun loadColorMap() {
        MovementType.entries.forEach { movementType ->
            val default = colorMap[movementType] ?: return@forEach
            colorMap[movementType] = prefs.getInt("color_${movementType.name}", default)
        }
    }

    fun saveColorMap() {
        val editor = prefs.edit()
        colorMap.forEach { (movementType, color) ->
            editor.putInt("color_${movementType.name}", color)
        }
        editor.apply()
        pathOverlayLayer.clearPaintCache()
        replayOverlayLayer.clearPaintCache()
        sharedMapView?.layerManager?.redrawLayers()
    }


    /* INIT */
    init {
        val previousRecorder = AppRouteRecorder.instance
        val previousStorage = AppPathStorage.instance

        /* Initial instances */
        AppPathStorage.instance = null
        AppSegmentIndex.instance = null
        AppRouteRecorder.instance = null        // held null until resume/start finishes below

        /* Are we drawing the chains? (e.g. for start/stop buttons) */
        val restoredIsDrawing = loadIsDrawing(getApplication()) && LocationTrackingService.isRunning
        TrackingLiveState.isDrawing.value = restoredIsDrawing
        wasDrawing = restoredIsDrawing

        /* Chain existence check (link to chain operations, for current application) */
        pathStorage.onChainsChanged = { refreshHasChains() }
        viewModelScope.launch(Dispatchers.IO) {
            // Flush any data the service accumulated in the old instances while the app was closed
            previousStorage?.save(getApplication())
            if (restoredIsDrawing) previousRecorder?.saveInProgress(getApplication())

            pathStorage.load(getApplication())
            AppPathStorage.instance = pathStorage

            if (restoredIsDrawing) {
                if (!routeRecorder.resumeFromInProgress(getApplication())) {
                    routeRecorder.startRecording()
                }
            }
            AppRouteRecorder.instance = routeRecorder

            refreshHasChains()
        }

        /* Displayed on map (initialize and continuously update) */
        loadFilesAndPaths()
        startRedrawLoop()

        viewModelScope.launch(Dispatchers.IO) {
            routeRecorder.cleanStillPointsFromSavedRoutes(getApplication())
        }

        refreshSavedRoutes()

        /* PREFERENCES PAGE */
        loadColorMap()
        pathOverlayLayer.useCustomColors = showPathColorChoice
        replayOverlayLayer.useCustomColors = showPathColorChoice

        /* Replay requests: collectLatest guarantees the previous request is fully
           cancelled and torn down before the next one starts touching replayStorage,
           no matter how quickly the user switches between routes. */
        viewModelScope.launch(Dispatchers.Default) {
            replayRequests.collectLatest { request ->
                when (request) {
                    is ReplayRequest.Play -> performReplay(request.context, request.fileName)
                    is ReplayRequest.Stop -> performStopReplay()
                }
            }
        }
    }


    /* GENERAL MAP/PATH FUNCTIONS */

    private fun loadFilesAndPaths() {
        /*
        This function 1. loads the map and 2. loads all raw paths, then turns them into path
        segments. Is called during app initialization.
        */
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val (mapPath, themePath) = withContext(Dispatchers.IO) {
                    copyAssetToInternalStorage(context, "zurich.map") to
                            copyAssetToInternalStorage(context, "minmap.xml")
                }
                mapFilePath = mapPath
                themeFilePath = themePath

                val loadedPaths = withContext(Dispatchers.IO) {
                    loadPathsFromGeoJson(context)
                }
                allPaths = loadedPaths
                segmentIndex = withContext(Dispatchers.Default) {
                    SegmentIndex(loadedPaths.toSegments()).also{
                        AppSegmentIndex.instance = it
                    }
                }
            } catch (e: Exception) {
                errorMessage = "${e::class.java.simpleName}: ${e.message}"
            }
        }
    }

    private fun startRedrawLoop() {
        /*
        This function:
            1. If drawing is enabled, it redraws the map layers for each new point. Checks for each
                new location point in TrackingLiveState.
            2. If drawing was just now stopped, it finalizes the path drawing and saving. Checks
                every time isDrawing in TrackingLiveState is modified.
        */
        viewModelScope.launch {
            TrackingLiveState.latestPoint.collect {
                if (TrackingLiveState.isDrawing.value) sharedMapView?.layerManager?.redrawLayers()
            }
        }

        viewModelScope.launch {
            var wasDrawing = false
            TrackingLiveState.isDrawing.collect { drawing ->
                if (wasDrawing && !drawing) {
                    withContext(Dispatchers.Default) {
                        pathStorage.finalizeSession()
                        pathStorage.mergeChainsByType()
                    }
                    preProjectAllZoomLevels()
                    saveChainsNow()
                    sharedMapView?.layerManager?.redrawLayers()
                }
                wasDrawing = drawing
            }
        }
    }


    /* CHAIN DISPLAY AND STORAGE FUNCTIONS */

    fun refreshHasChains() {
        /*
        This function checks if PathStorage has any saved chains and updates hasChainsToDisplay
        accordingly. Should be called after any chain modification (load, add, clear, merge).
        */
        val newValue = pathStorage.chains.values.any { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.Main) {       // Update on the main thread
            hasChainsToDisplay = newValue
        }
    }

    fun saveChainsNow() {
        /* This stores walked paths to storage. */
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.save(getApplication())
        }
    }

    fun deleteSavedChains() {
        /* This function deletes deletes saved paths from storage. */
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.deleteSaved(getApplication())
            withContext(Dispatchers.Main) {
                refreshHasChains()
            }
        }
    }

    fun stopReplay() {
        /* Requests replay to stop via the same debounced request queue as replayRoute(), so a stop racing a just-started play is handled in order rather than corrupting replayStorage. */
        isReplayingRoute = false
        selectedRecording = null
        replayProgress.value = 0f
        pathOverlayLayer.isDisplayed = true
        replayRequests.tryEmit(ReplayRequest.Stop)
    }

    fun replayRoute(context: Context, fileName: String) {
        /*
        If the user selects a specific path, this function loads and displays previously saved
        walked paths. Is not called for the current path.
        */
        isReplayingRoute = true
        selectedRecording = fileName
        replayProgress.value = 0f
        replayRequests.tryEmit(ReplayRequest.Play(context, fileName))
    }

    private suspend fun performStopReplay() {
        replayStorage.clearSegments()
        withContext(Dispatchers.Main) {
            sharedMapView?.layerManager?.redrawLayers()
        }
    }

    private suspend fun performReplay(context: Context, fileName: String) {
        /*
        Loads a saved route into replayStorage and re-walks it through onGpsPoint() as if it
        were live, point by point. Skips points that are too close in distance/time.
        */

        withContext(Dispatchers.Main) {
            pathOverlayLayer.isDisplayed = false
            sharedMapView?.layerManager?.redrawLayers()
        }

        replayStorage.clearSegments()
        routeRecorder.loadForDisplay(context, fileName)

        val allPoints = synchronized(routeRecorder.displayPoints) { routeRecorder.displayPoints.toList() }
        val totalPoints = allPoints.size.coerceAtLeast(1)
        var processed = 0
        var lastReportedPercent = -1

        var lastLat: Double? = null
        var lastLon: Double? = null
        var lastTimestamp: Long? = null
        var lastMovementType: MovementType? = null
        var lastBearing: Double? = null

        for (pt in allPoints) {
            currentCoroutineContext().ensureActive()

            processed++
            val percent = processed * 100 / totalPoints
            if (percent != lastReportedPercent) {
                lastReportedPercent = percent
                replayProgress.value = processed.toFloat() / totalPoints
            }

            val lat = pt.lat; val lon = pt.lon
            val movementType = pt.movementType; val timestamp = pt.timestamp

            val pLat = lastLat; val pLon = lastLon; val pTime = lastTimestamp

            var shouldSkip = false
            if (pLat != null && pLon != null && pTime != null) {
                val dLat = lat - pLat; val dLon = lon - pLon
                val distMeters = Math.sqrt(dLat*dLat + dLon*dLon) * 111_000
                val timeDiff = timestamp - pTime

                val typeChanged = movementType != lastMovementType

                val bearing = Math.atan2(dLon, dLat) * 180.0 / Math.PI
                val bearingChanged = lastBearing != null &&
                        Math.abs(((bearing - lastBearing!! + 540) % 360) - 180) > 25.0

                shouldSkip = distMeters < 4.0 && timeDiff < 4000 && !typeChanged && !bearingChanged
            }

            if (!shouldSkip) {
                if (pLat != null && pLon != null) {
                    val dLat = lat - pLat; val dLon = lon - pLon
                    val distMeters = Math.sqrt(dLat*dLat + dLon*dLon) * 111_000
                    if (distMeters > 2.0) lastBearing = Math.atan2(dLon, dLat) * 180.0 / Math.PI
                }
                lastLat = lat; lastLon = lon; lastTimestamp = timestamp
                lastMovementType = movementType

                val index = AppSegmentIndex.instance ?: continue

                val effectiveType = if (movementType != MovementType.STILL) {
                    movementType
                } else {
                    replayStorage.lastActiveMovementType
                }
                replayStorage.onGpsPoint(LatLong(lat, lon), effectiveType, index)
            }
        }

        currentCoroutineContext().ensureActive()
        replayStorage.finalizeSession()
        replayStorage.mergeChainsByType()
        preProjectAllZoomLevels()
        withContext(Dispatchers.Main) {
            sharedMapView?.layerManager?.redrawLayers()
            isReplayingRoute = false
        }
    }

    override fun onCleared() {
        /*
        If the user selects to clear the paths, this function blocks actions on the walked chains,
        and visually clears the currently displayed layers. No changes to storage.
        */
        super.onCleared()
        runBlocking(Dispatchers.IO) {
            pathStorage.finalizeSession()
            pathStorage.mergeChainsByType()
            pathStorage.save(getApplication())
        }
        sharedMapView?.let { mv ->
            mv.destroyAll()
        }
        sharedMapView = null
    }


    /* HELPERS */

    suspend fun preProjectAllZoomLevels() {
        /*
        This function loads how the path would look with each zoom level (without displaying it).
        Is called in the background to speed up zooming in and out of replayed paths.
        */
        withContext(Dispatchers.Default) {
            val zoomLevel = sharedMapView?.model?.mapViewPosition?.zoomLevel ?: 15
            pathOverlayLayer.preProjectAll(pathStorage.chains, zoomLevel)
            replayOverlayLayer.preProjectAll(replayStorage.chains, zoomLevel)
        }
    }

    fun exportRoute(context: Context, fileName: String){
        /* Shares one saved route file via the system share sheet, exposed through FileProvider. */
        val displayName = fileName.removePrefix("route_").removeSuffix(".json")

        viewModelScope.launch(Dispatchers.IO){
            try {
                val file = File(context.filesDir, fileName)
                if(!file.exists()) throw java.io.FileNotFoundException(fileName)

                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Export route"))
                    exportResult = ExportResult.Success(displayName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exportResult = ExportResult.Failure(displayName)
                }
            }
        }
    }

    fun exportAllRoutes(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (bundleText, count) = buildAllRoutesBundleText(context)

                // Written to cacheDir (not filesDir) so the bundle never shows up as a saved route itself.
                val bundleFile = File(context.cacheDir, "export_all_routes.json")
                bundleFile.writeText(bundleText)

                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", bundleFile)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Export all routes"))
                    exportResult = ExportResult.Success("all routes ($count)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exportResult = ExportResult.Failure("all routes")
                }
            }
        }
    }

    fun saveRouteToDevice(context: Context, fileName: String, targetUri: Uri) {
        /*
        Saves directly to a location the user picks via the system "Save to device" (Storage
        Access Framework) picker.
        */
        val displayName = fileName.removePrefix("route_").removeSuffix(".json")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                if (!file.exists()) throw java.io.FileNotFoundException(fileName)

                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(file.readBytes())
                } ?: throw java.io.IOException("Could not open destination for writing")

                withContext(Dispatchers.Main) {
                    exportResult = ExportResult.Success(displayName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exportResult = ExportResult.Failure(displayName)
                }
            }
        }
    }

    fun saveAllRoutesToDevice(context: Context, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (bundleText, count) = buildAllRoutesBundleText(context)

                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(bundleText.toByteArray())
                } ?: throw java.io.IOException("Could not open destination for writing")

                withContext(Dispatchers.Main) {
                    exportResult = ExportResult.Success("all routes ($count)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exportResult = ExportResult.Failure("all routes")
                }
            }
        }
    }

    private fun buildAllRoutesBundleText(context: Context): Pair<String, Int> {
        /*
        Shared by exportAllRoutes() and saveAllRoutesToDevice(): bundles every saved route into
        one JSON blob (a RouteBundle) plus how many routes it contains.
        */
        val fileNames = routeRecorder.listSavedRoutes(context)
        if (fileNames.isEmpty()) throw java.io.FileNotFoundException("No saved routes")

        val json = Json { ignoreUnknownKeys = true }
        val routes = fileNames.associateWith { fileName ->
            json.decodeFromString<RecordedRoute>(File(context.filesDir, fileName).readText())
        }
        return json.encodeToString(RouteBundle.serializer(), RouteBundle(routes)) to fileNames.size
    }

    fun importRoute(context: Context, uri: Uri) {
        /*
        Accepts either a single exported route or a bundle of many (from
        exportAllRoutes()/saveAllRoutesToDevice()), auto-detecting which by trying to decode
        as a bundle first and falling back to a single route. Each imported route gets a
        collision-safe filename.
        */
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val text = inputStream.bufferedReader().readText()
                inputStream.close()

                val json = Json { ignoreUnknownKeys = true }

                val bundle = try {
                    json.decodeFromString<RouteBundle>(text)
                } catch (e: Exception) {
                    null
                }

                if (bundle != null) {
                    for ((originalFileName, route) in bundle.routes) {
                        val newFileName = uniqueRouteFileName(context, originalFileName)
                        File(context.filesDir, newFileName)
                            .writeText(json.encodeToString(RecordedRoute.serializer(), route))
                    }
                } else {
                    json.decodeFromString<RecordedRoute>(text)  // throws if invalid

                    val newFileName = uniqueRouteFileName(context, "route_${System.currentTimeMillis()}.json")
                    File(context.filesDir, newFileName).writeText(text) // saving
                }

                withContext(Dispatchers.Main) {
                    refreshSavedRoutes()
                }
            } catch (e: Exception) {
                Log.e("SwissStep_TAG", "Failed to import route", e)
            }
        }
    }
}

sealed class ExportResult {
    data class Success(val displayName: String) : ExportResult()
    data class Failure(val displayName: String) : ExportResult()
}