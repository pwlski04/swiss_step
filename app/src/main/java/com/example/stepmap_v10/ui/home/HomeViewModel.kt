package com.example.stepmap_v10.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
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
import com.example.stepmap_v10.chains.LocationPointsLayer
import com.example.stepmap_v10.chains.RouteRecorder
import com.example.stepmap_v10.tracking.LocationTrackingService
import kotlinx.coroutines.runBlocking
import org.mapsforge.core.model.LatLong


class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val routeRecorder = RouteRecorder()
    var isReplayingRoute by mutableStateOf(false)
    
    val pathStorage = PathStorage()
    var sharedMapView by mutableStateOf<MapView?>(null)
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }
    var locationPointsLayer: LocationPointsLayer? = null
    var showLocationPoints by mutableStateOf(false)
    var showPathColorChoice by mutableStateOf(false)

    var allPaths by mutableStateOf<List<Path>>(emptyList())
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var segmentIndex by mutableStateOf<SegmentIndex?>(null)
    private var wasDrawing: Boolean

    var hasChainsToDisplay by mutableStateOf(false)
        private set


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
        TODO: when current path is not saved ask whether user wants it saved before displaying, or
         make display temporary with current path as default
         */
        isReplayingRoute = true
        viewModelScope.launch(Dispatchers.Default) {
            pathStorage.clearSegments()
            routeRecorder.loadAndReplay(context, fileName) { lat, lon, movementType, timestamp ->
                val index = AppSegmentIndex.instance ?: return@loadAndReplay
                pathStorage.onGpsPoint(LatLong(lat, lon), movementType, index)
            }
            pathStorage.finalizeSession()
            pathStorage.mergeChainsByType()

            preProjectAllZoomLevels()

            withContext(Dispatchers.Main) {
                sharedMapView?.layerManager?.redrawLayers()
                isReplayingRoute = false
            }

            saveChainsNow()
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
            for (zoom in 12..18) {
                pathOverlayLayer.preProjectAll(pathStorage.chains, zoom.toByte())
            }
        }
    }
}