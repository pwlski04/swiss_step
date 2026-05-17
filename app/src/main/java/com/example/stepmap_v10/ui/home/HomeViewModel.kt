package com.example.stepMap_v10.ui.home

import android.app.Application
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
import com.example.stepMap_v10.map.copyAssetToInternalStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    /* ONE-TIME PATH LOADING */
    val pathStorage = PathStorage()
    val pathOverlayLayer = PathOverlayLayer(pathStorage).also {
        pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
    }
    var sharedMapView by mutableStateOf<MapView?>(null)

    var allPaths by mutableStateOf<List<Path>>(emptyList())
    var mapFilePath by mutableStateOf<String?>(null)
    var themeFilePath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var segmentIndex by mutableStateOf<SegmentIndex?>(null)

    private var saveJob: Job? = null

    init {
        pathStorage.onChainsChanged = { refreshHasChains() }
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.load(getApplication())
            refreshHasChains()
        }
        loadFilesAndPaths()
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
                    SegmentIndex(loadedPaths.toSegments())
                }
            } catch (e: Exception) {
                errorMessage = "${e::class.java.simpleName}: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            pathStorage.save(getApplication())
        }
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