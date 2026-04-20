package com.example.stepbystep_v10

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Page_Home() {
    val context = LocalContext.current

    var mapFilePath by remember { mutableStateOf<String?>(null) }
    var themeFilePath by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val paths = withContext(Dispatchers.IO) {
                val mapPath = copyAssetToInternalStorage(context, "switzerland.map")
                val themePath = copyAssetToInternalStorage(context, "minmap.xml")
                mapPath to themePath
            }

            mapFilePath = paths.first
            themeFilePath = paths.second
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            errorMessage != null -> Text("Map load error: $errorMessage")
            mapFilePath == null || themeFilePath == null -> Text("Preparing offline map...")
            else -> OfflineMapScreen(
                modifier = Modifier.fillMaxSize(),
                mapFilePath = mapFilePath!!,
                themeFilePath = themeFilePath!!
            )
        }
    }
}


@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier, mapFilePath: String, themeFilePath: String
) {
    val context = LocalContext.current

    val mapView = remember(mapFilePath, themeFilePath) {
        createMapsforgeView(context, mapFilePath, themeFilePath)
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.destroyAll()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(), factory = { mapView })

}