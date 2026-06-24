package com.example.stepmap_v10.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stepmap_v10.chains.PathOverlayLayer
import com.example.stepmap_v10.chains.PathStorage
import com.example.stepmap_v10.paths.Path
import com.example.stepmap_v10.paths.SegmentIndex
import com.example.stepmap_v10.paths.loadPathsFromGeoJson
import com.example.stepmap_v10.paths.toSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.view.MapView
import com.example.stepmap_v10.map.copyAssetToInternalStorage
import com.example.stepmap_v10.tracking.AppPathStorage
import com.example.stepmap_v10.tracking.AppSegmentIndex
import com.example.stepmap_v10.tracking.TrackingLiveState
import com.example.stepmap_v10.tracking.loadIsDrawing
import com.example.stepmap_v10.chains.AppRouteRecorder
import com.example.stepmap_v10.chains.RawGpsPointsLayer
import com.example.stepmap_v10.chains.RouteRecorder
import com.example.stepmap_v10.colorMap
import com.example.stepmap_v10.tracking.LocationTrackingService
import com.example.stepmap_v10.tracking.MovementType
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.mapsforge.core.model.LatLong


class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // HOME PAGE
    val routeRecorder = RouteRecorder()
    var isReplayingRoute by mutableStateOf(false)
    
    val pathStorage = PathStorage()
    var sharedMapView by mutableStateOf<MapView?>(null)
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }
    var rawGpsPointsLayer: RawGpsPointsLayer? = null

    var allPaths by mutableStateOf<List<Path>>(emptyList())
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var segmentIndex by mutableStateOf<SegmentIndex?>(null)
    private var wasDrawing: Boolean

    var hasChainsToDisplay by mutableStateOf(false)
        private set

    var longPressedRecording: String? by mutableStateOf(null)
    private var replayJob: Job? = null
    var selectedRecording: String? by mutableStateOf(null);

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
    private val prefs = getApplication<Application>().getSharedPreferences("stepbystep_prefs", Context.MODE_PRIVATE)

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
        sharedMapView?.layerManager?.redrawLayers()
    }

    init {
        /* Initial instances */
        AppPathStorage.instance = pathStorage           // Storage
        AppSegmentIndex.instance = null                 // Segments
        AppRouteRecorder.instance = routeRecorder       // Path recording

        /* Are we drawing the chains? (e.g. for start/stop buttons) */
        val restoredIsDrawing = loadIsDrawing(getApplication()) && LocationTrackingService.isRunning
        TrackingLiveState.isDrawing.value = restoredIsDrawing
        wasDrawing = restoredIsDrawing

        /* Chain existence check (link to chain operations, for current application) */
        pathStorage.onChainsChanged = { refreshHasChains() }
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.load(getApplication())
            refreshHasChains()
        }

        /* Displayed on map (initialize and continuously update) */
        loadFilesAndPaths()
        startRedrawLoop()

        viewModelScope.launch(Dispatchers.IO) {
            routeRecorder.cleanStillPointsFromSavedRoutes(getApplication())
        }

        refreshSavedRoutes()

        /*PREFERENCES PAGE */
        loadColorMap()
        pathOverlayLayer.useCustomColors = showPathColorChoice
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
                    //AppRouteRecorder.instance?.stopAndSave(getApplication())

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

    fun replayRoute(context: Context, fileName: String) {
        /*
        If the user selects a specific path, this function loads and displays previously saved
        walked paths. Is not called for the current path.
        */
        replayJob?.cancel()
        isReplayingRoute = true
        selectedRecording = fileName

        replayJob = viewModelScope.launch(Dispatchers.Default) {
            pathStorage.isReplaying = true
            pathStorage.clearSegments()
            routeRecorder.loadForDisplay(context, fileName)

            var lastLat: Double? = null
            var lastLon: Double? = null
            var lastTimestamp: Long? = null
            var lastMovementType: MovementType? = null
            var lastBearing: Double? = null

            routeRecorder.loadAndReplay(context, fileName) { lat, lon, movementType, timestamp ->
                ensureActive()

                val pLat = lastLat; val pLon = lastLon; val pTime = lastTimestamp

                var shouldSkip = false
                if (pLat != null && pLon != null && pTime != null) {
                    val dLat = lat - pLat; val dLon = lon - pLon
                    val distMeters = Math.sqrt(dLat*dLat + dLon*dLon) * 111_000
                    val timeDiff = timestamp - pTime

                    // Never skip if movement type changed — transitions matter
                    val typeChanged = movementType != lastMovementType

                    // Never skip if direction changed meaningfully — turns matter
                    val bearing = Math.atan2(dLon, dLat) * 180.0 / Math.PI
                    val bearingChanged = lastBearing != null &&
                            Math.abs(((bearing - lastBearing!! + 540) % 360) - 180) > 25.0

                    shouldSkip = distMeters < 4.0 && timeDiff < 4000 && !typeChanged && !bearingChanged
                }

                if (shouldSkip) return@loadAndReplay

                // Update bearing only when we actually moved a meaningful amount
                if (pLat != null && pLon != null) {
                    val dLat = lat - pLat; val dLon = lon - pLon
                    val distMeters = Math.sqrt(dLat*dLat + dLon*dLon) * 111_000
                    if (distMeters > 2.0) lastBearing = Math.atan2(dLon, dLat) * 180.0 / Math.PI
                }

                lastLat = lat; lastLon = lon; lastTimestamp = timestamp
                lastMovementType = movementType

                val index = AppSegmentIndex.instance ?: return@loadAndReplay
                pathStorage.onGpsPoint(LatLong(lat, lon), movementType, index, isReplayPoint = true)
            }

            ensureActive()
            pathStorage.finalizeSession()
            pathStorage.mergeChainsByType()
            preProjectAllZoomLevels()
            withContext(Dispatchers.Main) {
                sharedMapView?.layerManager?.redrawLayers()
                isReplayingRoute = false
            }

            //pathStorage.restoreSnapshot(snapshot)
            pathStorage.isReplaying = false
            withContext(Dispatchers.IO) {
                pathStorage.load(getApplication())
            }
            AppSegmentIndex.instance?.let { index ->
                pathStorage.flushBufferedPoints(index)
            }
            preProjectAllZoomLevels()
            withContext(Dispatchers.Main) {
                sharedMapView?.layerManager?.redrawLayers()
            }
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
        }
    }
}