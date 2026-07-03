package com.example.stepmap_v10.chains

import android.content.Context
import com.example.stepmap_v10.tracking.MovementType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File


/* TAG REMOVE (RECORDER): entire file */
object AppRouteRecorder {
    var instance: RouteRecorder? = null
}

fun uniqueRouteFileName(context: Context, baseFileName: String): String {
    /*
    Never let a route write overwrite an existing file of the same name - if the
    requested name is taken, fall back to a "_1", "_2", ... suffix instead. Shared by regular saves
    and bulk import, both of which take a user/bundle-supplied name that may collide.
    */
    var candidate = baseFileName
    var counter = 1
    while (File(context.filesDir, candidate).exists()) {
        val base = candidate.removePrefix("route_").removeSuffix(".json")
        candidate = "route_${base}_$counter.json"
        counter++
    }
    return candidate
}

class RouteRecorder {
    private val recordedPoints = mutableListOf<RecordedPoint>()
    private val IN_PROGRESS_FILE = "route_inprogress.json"
    val points: List<RecordedPoint> get() = recordedPoints.toList()
    val displayPoints = mutableListOf<RecordedPoint>()
    var isRecording = false
        private set

    fun startRecording() {
        recordedPoints.clear()
        synchronized(displayPoints) { displayPoints.clear() }
        isRecording = true
    }

    fun recordPoint(lat: Double, lon: Double, movementType: MovementType) {
        if (!isRecording || movementType == MovementType.STILL) return
        val point = RecordedPoint(lat, lon, System.currentTimeMillis(), movementType)
        recordedPoints.add(point)
        synchronized(displayPoints) { displayPoints.add(point) }
    }

    fun stopAndSave(context: Context, newName: String = ""): String {
        isRecording = false
        synchronized(displayPoints) {
            displayPoints.clear()
            displayPoints.addAll(recordedPoints)
        }
        val baseFileName = if (newName.length < 3) "route_${System.currentTimeMillis()}.json" else "route_${newName}.json"
        val fileName = uniqueRouteFileName(context, baseFileName)
        val json = Json { prettyPrint = false }
        val text = json.encodeToString(RecordedRoute.serializer(), RecordedRoute(recordedPoints.toList()))
        File(context.filesDir, fileName).writeText(text)
        recordedPoints.clear()
        File(context.filesDir, IN_PROGRESS_FILE).delete()
        return fileName
    }

    fun saveInProgress(context: Context) {
        val json = Json { prettyPrint = false }
        val text = json.encodeToString(RecordedRoute.serializer(), RecordedRoute(recordedPoints.toList()))
        File(context.filesDir, IN_PROGRESS_FILE).writeText(text)
    }

    fun resumeFromInProgress(context: Context): Boolean {
        val file = File(context.filesDir, IN_PROGRESS_FILE)
        if (!file.exists()) return false
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val route = json.decodeFromString<RecordedRoute>(file.readText())
            recordedPoints.clear()
            recordedPoints.addAll(route.points)
            synchronized(displayPoints) {
                displayPoints.clear()
                displayPoints.addAll(route.points)
            }
            isRecording = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearInProgress(context: Context) {
        File(context.filesDir, IN_PROGRESS_FILE).delete()
    }

    fun listSavedRoutes(context: Context): List<String> {
        return context.filesDir.listFiles()
            ?.filter { it.name.startsWith("route_") && it.name.endsWith(".json") && it.name != IN_PROGRESS_FILE }
            ?.map { it.name }
            ?: emptyList()
    }

    fun loadAndReplay(
        context: Context,
        fileName: String,
        onPoint: (lat: Double, lon: Double, movementType: MovementType, timestamp: Long) -> Unit
    ): Int {
        val text = File(context.filesDir, fileName).readText()
        val json = Json { ignoreUnknownKeys = true }
        val route = json.decodeFromString<RecordedRoute>(text)
        route.points.forEach { onPoint(it.lat, it.lon, it.movementType, it.timestamp) }
        return route.points.size
    }

    fun syncDisplayPoints() {
        synchronized(displayPoints) {
            displayPoints.clear()
            displayPoints.addAll(recordedPoints)
        }
    }

    fun loadForDisplay(context: Context, fileName: String) {
        val json = Json { ignoreUnknownKeys = true }
        val route = json.decodeFromString<RecordedRoute>(File(context.filesDir, fileName).readText())
        synchronized(displayPoints) {
            displayPoints.clear()
            displayPoints.addAll(route.points)
        }
    }

    fun cleanStillPointsFromSavedRoutes(context: Context) {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("route_") && it.name.endsWith(".json") && it.name != IN_PROGRESS_FILE }
            ?.forEach { file ->
                try {
                    val route = json.decodeFromString<RecordedRoute>(file.readText())
                    val filtered = route.points.filter { it.movementType != MovementType.STILL }
                    file.writeText(json.encodeToString(RecordedRoute.serializer(), RecordedRoute(filtered)))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }
}

@Serializable
data class RecordedPoint(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val movementType: MovementType
)

@Serializable
data class RecordedRoute(
    val points: List<RecordedPoint>
)

@Serializable
data class RouteBundle(
    val routes: Map<String, RecordedRoute>   // fileName -> route, for "export all" / bulk import
)