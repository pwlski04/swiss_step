package com.example.stepmap_v10.ui.home

import android.app.Application
import android.content.Context
import android.util.Log
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
//import com.example.stepMap_v10.map.LocationMarker
import com.example.stepmap_v10.map.copyAssetToInternalStorage
import com.example.stepmap_v10.paths.findNearestSegment
import com.example.stepmap_v10.paths.pointToSegmentDistance
import com.example.stepmap_v10.tracking.AppPathStorage
import com.example.stepmap_v10.tracking.AppSegmentIndex
import com.example.stepmap_v10.tracking.TrackingLiveState
import com.example.stepmap_v10.tracking.loadIsDrawing
import com.example.stepmap_v10.chains.AppRouteRecorder
import com.example.stepmap_v10.chains.DebugPointsLayer
import com.example.stepmap_v10.chains.RawGpsPointsLayer
import com.example.stepmap_v10.chains.RouteRecorder
import com.example.stepmap_v10.tracking.MovementType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mapsforge.core.model.LatLong

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    //TODO: REMOVE
    var debugLayer: DebugPointsLayer? = null

    /* ONE-TIME PATH LOADING */
    val pathStorage = PathStorage()
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }
    //val locationMarker = LocationMarker()
    var sharedMapView by mutableStateOf<MapView?>(null)

    var allPaths by mutableStateOf<List<Path>>(emptyList())
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var segmentIndex by mutableStateOf<SegmentIndex?>(null)

    private var saveJob: Job? = null
    private var wasDrawing: Boolean


    /* TAG REMOVE (RECORDER): this block */
    val routeRecorder = RouteRecorder()
    var isReplayingRoute by mutableStateOf(false)
    fun replayRoute(context: Context, fileName: String) {
        isReplayingRoute = true
        viewModelScope.launch(Dispatchers.Default) {
            pathStorage.clearSegments()
            val before = pathStorage.totalPointCount()
            Log.d("StepByStep_v1.0_TAG", "REPLAY: Points before replay: $before")

            var lastNonStillType: MovementType = MovementType.TRANSPORT
            var prevLat: Double? = null
            var prevLon: Double? = null
            var prevTimestamp: Long? = null

            /* START */
            // Add this to replayRoute before the loadAndReplay loop:
            val rawRoute = routeRecorder.loadAndReplay(context, fileName) { lat, lon, movementType, timestamp -> }
            Log.d("StepByStep_v1.0_TAG", "REPLAY: Raw points in file: $rawRoute")

// Also log the time gaps between consecutive points:
            var prevTimestamp2: Long? = null
            var maxGap = 0L
            var gapCount = 0

            routeRecorder.loadAndReplay(context, fileName) { lat, lon, movementType, timestamp ->
                val prev = prevTimestamp2
                if (prev != null) {
                    val gap = timestamp - prev
                    if (gap > maxGap) maxGap = gap
                    if (gap > 10_000L) gapCount++  // gaps > 10 seconds
                }
                prevTimestamp2 = timestamp
            }

            Log.d("StepByStep_v1.0_TAG", "REPLAY: Max time gap: ${maxGap/1000}s, Gaps > 10s: $gapCount")
            /* END */

            val filePoints = routeRecorder.loadAndReplay(context, fileName) { lat, lon, movementType, timestamp ->
                val index = AppSegmentIndex.instance ?: return@loadAndReplay
                val nearest = findNearestSegment(lat, lon, index) ?: return@loadAndReplay
                val dist = pointToSegmentDistance(LatLong(lat, lon), nearest)

                if (dist < 0.0003) {
                    val effectiveType = if (movementType != MovementType.STILL) {
                        lastNonStillType = movementType
                        movementType
                    } else {
                        // Only inherit last type if position is actually changing
                        val pLat = prevLat; val pLon = prevLon; val pTime = prevTimestamp
                        if (pLat != null && pLon != null && pTime != null && timestamp > pTime) {
                            val dLat = lat - pLat; val dLon = lon - pLon
                            val distMeters = Math.sqrt(dLat*dLat + dLon*dLon) * 111_000
                            val timeSec = (timestamp - pTime) / 1000.0
                            val speedKmh = if (timeSec > 0) (distMeters / timeSec) * 3.6 else 0.0
                            if (speedKmh > 2.0) {
                                // Moving but GPS says STILL — use last known type
                                lastNonStillType
                            } else {
                                // Genuinely stationary — drop
                                null
                            }
                        } else null
                    }

                    if (effectiveType != null) {
                        pathStorage.onGpsPoint(nearest, effectiveType, index)
                    }
                }

                prevLat = lat; prevLon = lon; prevTimestamp = timestamp
            }
            /*val filePoints = routeRecorder.loadAndReplay(context, fileName) { lat, lon, movementType ->
                val index = AppSegmentIndex.instance ?: return@loadAndReplay
                val nearest = findNearestSegment(lat, lon, index) ?: return@loadAndReplay
                val dist = pointToSegmentDistance(LatLong(lat, lon), nearest)
                if (dist < 0.0003) {
                    pathStorage.onGpsPoint(nearest, movementType, index)
                }
            }*/

            // TODO: remove this (is here to confirm the path points counts for saving) and filePoints (turn loadAndReplay back to returning Unit)
            val pointCount = pathStorage.totalPointCount()
            android.util.Log.d("StepByStep_v1.0_TAG", "REPLAY: Total points after replay: $pointCount")
            Log.d("StepByStep_v1.0_TAG", "REPLAY: Points in file: $filePoints, recorded into storage: ${pathStorage.totalPointCount()}")
            val chainDetails = pathStorage.chains.entries
                .filter { it.value.isNotEmpty() }
                .map { (type, chains) ->
                    "$type: ${chains.size} chains, sizes: ${chains.map { it.points.size }}"
                }
            Log.d("StepByStep_v1.0_TAG", "REPLAY: Chain details: $chainDetails")

            // TODO REMOVE
            withContext(Dispatchers.Default) {
                // Run gap merge with looser threshold for replay
                pathStorage.finalizeSession()
                /*for (movementType in MovementType.entries) {
                    pathStorage.runGapMerge(movementType, threshold = 0.003) // ~100m
                    pathStorage.runGapMerge(movementType, threshold = 0.003)
                }*/
            }

            // Single redraw after all points processed
            withContext(Dispatchers.Main) {
                val rawLayer = RawGpsPointsLayer(context, fileName)
                rawLayer.loadPoints()
                sharedMapView?.layerManager?.layers?.add(rawLayer)
                val chainDetails = pathStorage.chains.entries
                    .filter { it.value.isNotEmpty() }
                    .map { (type, chains) -> "$type: ${chains.size} chains, sizes: ${chains.map { it.points.size }}" }
                Log.d("REPLAY", "After finalize: $chainDetails")

                val chainCount = pathStorage.chains.values.sumOf { it.size }
                val pointCount = pathStorage.totalPointCount()
                Log.d(
                    "StepByStep_v1.0_TAG",
                    "REPLAY: Chains: $chainCount, Points: $pointCount, Avg per chain: ${
                        pointCount / chainCount.coerceAtLeast(1)
                    }"
                )


                // ADD BACK pathStorage.finalizeSession()
                saveChainsNow()
                sharedMapView?.layerManager?.redrawLayers()
                isReplayingRoute = false
            }
        }
    }


    init {
        AppPathStorage.instance = pathStorage
        AppSegmentIndex.instance = null

        /* TAG REMOVE (RECORDER): this line */
        AppRouteRecorder.instance = routeRecorder

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
        runBlocking(Dispatchers.IO) {
            pathStorage.finalizeSession()
            pathStorage.save(getApplication())
        }
        sharedMapView?.let { mv ->
            mv.destroyAll()
        }
        sharedMapView = null
    }


    /* Functions for stored chains */
    var hasChains by mutableStateOf(false)
        private set

    // Call this after any chain modification:
    fun refreshHasChains() {
        val newValue = pathStorage.chains.values.any { it.isNotEmpty() }
        // Switch to main thread for Compose state update
        viewModelScope.launch(Dispatchers.Main) {
            hasChains = newValue
        }
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