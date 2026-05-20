package com.example.stepMap_v10.ui.home

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stepMap_v10.chains.PathOverlayLayer
import com.example.stepMap_v10.chains.PathStorage
import com.example.stepMap_v10.paths.Path
import com.example.stepMap_v10.paths.SegmentIndex
import com.example.stepMap_v10.paths.loadPathsFromGeoJson
import com.example.stepMap_v10.paths.toSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.view.MapView
import com.example.stepMap_v10.map.LocationMarker
import com.example.stepMap_v10.map.copyAssetToInternalStorage
import com.example.stepMap_v10.paths.findNearestSegment
import com.example.stepMap_v10.paths.pointToSegmentDistance
import com.example.stepMap_v10.tracking.AppPathStorage
import com.example.stepMap_v10.tracking.AppSegmentIndex
import com.example.stepMap_v10.tracking.TrackingLiveState
import com.example.stepMap_v10.tracking.loadIsDrawing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mapsforge.core.model.LatLong

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    /* ONE-TIME PATH LOADING */
    val pathStorage = PathStorage()
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }
    val locationMarker = LocationMarker()
    var sharedMapView by mutableStateOf<MapView?>(null)

    var allPaths by mutableStateOf<List<Path>>(emptyList())
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var segmentIndex by mutableStateOf<SegmentIndex?>(null)

    private var saveJob: Job? = null
    private var wasDrawing: Boolean

    init {
        AppPathStorage.instance = pathStorage
        AppSegmentIndex.instance = null

        val restoredIsDrawing = loadIsDrawing(getApplication())
        TrackingLiveState.isDrawing.value = restoredIsDrawing
        wasDrawing = restoredIsDrawing

        pathStorage.onChainsChanged = { refreshHasChains() }
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.load(getApplication())
            refreshHasChains()
        }
        loadFilesAndPaths()
        //startBackgroundRecording()
        startRedrawLoop()
    }
    /*
    private fun startBackgroundRecording() {
        viewModelScope.launch {
            TrackingLiveState.latestPoint.collect { point ->
                if (point == null || !TrackingLiveState.isDrawing.value) return@collect
                val index = segmentIndex ?: return@collect
                val nearest = findNearestSegment(point.lat, point.lon, index) ?: return@collect
                val dist = pointToSegmentDistance(LatLong(point.lat, point.lon), nearest)
                if (dist < 0.0003) {
                    try {
                        val movementType = TrackingLiveState.movementType.value
                        pathStorage.onGpsPoint(nearest, movementType, index)
                        sharedMapView?.layerManager?.redrawLayers()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        viewModelScope.launch {
            TrackingLiveState.isDrawing.collect { drawing ->
                if (!drawing && wasDrawing) {
                    withContext(Dispatchers.Default) {
                        pathStorage.finalizeSession()
                    }
                    saveChainsNow()
                    sharedMapView?.layerManager?.redrawLayers()
                }
                wasDrawing = drawing
            }
        }
    }*/

    private fun startRedrawLoop() {
        viewModelScope.launch {
            TrackingLiveState.latestPoint.collect {
                if (TrackingLiveState.isDrawing.value) {
                    sharedMapView?.layerManager?.redrawLayers()
                }
            }
        }

        // Finalize when drawing stops
        viewModelScope.launch {
            var wasDrawing = false
            TrackingLiveState.isDrawing.collect { drawing ->
                if (wasDrawing && !drawing) {
                    withContext(Dispatchers.Default) {
                        pathStorage.finalizeSession()
                    }
                    saveChainsNow()
                    sharedMapView?.layerManager?.redrawLayers()
                }
                wasDrawing = drawing
            }
        }
    }

    /* Functions for displayed paths */
    private fun loadFilesAndPaths() {
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

    override fun onCleared() {
        super.onCleared()
        /* replaced
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.save(getApplication())
        }
        with */
        runBlocking(Dispatchers.IO) {
            pathStorage.finalizeSession()
            pathStorage.save(getApplication())
        }
        locationMarker.hide()
        sharedMapView?.destroyAll()
        sharedMapView = null
    }


    /* Functions for stored chains */
    var hasChains by mutableStateOf(false)
        private set

    // Call this after any chain modification:
    fun refreshHasChains() {
        hasChains = pathStorage.chains.values.any { it.isNotEmpty() }
    }

    private fun loadSavedChains() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            pathStorage.load(context)
        }
    }

    fun saveChainsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.save(getApplication())
        }
    }

    // Debounced version for frequent updates:
    fun scheduleSaveChains() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(2000L)  // wait 2s after last update
            withContext(Dispatchers.IO) {
                pathStorage.save(getApplication())
            }
        }
    }

    fun deleteSavedChains() {
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.deleteSaved(getApplication())
            withContext(Dispatchers.Main) {
                refreshHasChains()
            }
        }
    }

}