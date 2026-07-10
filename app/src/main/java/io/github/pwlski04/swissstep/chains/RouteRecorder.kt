package io.github.pwlski04.swissstep.chains

import android.content.Context
import io.github.pwlski04.swissstep.tracking.MovementType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File


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
        /* Ends the current recording, writes it to a permanent uniquely-named file, and deletes the in-progress crash-recovery file since it's no longer needed. Returns the saved file's name. */
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
        /* Snapshots the current recording to disk so it can be recovered via resumeFromInProgress() if the app/service is killed mid-recording. */
        val json = Json { prettyPrint = false }
        val text = json.encodeToString(RecordedRoute.serializer(), RecordedRoute(recordedPoints.toList()))
        File(context.filesDir, IN_PROGRESS_FILE).writeText(text)
    }

    fun resumeFromInProgress(context: Context): Boolean {
        /* Restores a recording that was in progress when the app/service last stopped (see saveInProgress()). Returns false if there was nothing to resume or the file was unreadable. */
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
        /* Loads a saved route and feeds every point through `onPoint` synchronously (caller drives the actual replay pacing); returns the point count. */
        val text = File(context.filesDir, fileName).readText()
        val json = Json { ignoreUnknownKeys = true }
        val route = json.decodeFromString<RecordedRoute>(text)
        route.points.forEach { onPoint(it.lat, it.lon, it.movementType, it.timestamp) }
        return route.points.size
    }

    fun syncDisplayPoints() {
        /* Copies the live recordedPoints buffer into displayPoints (the map-facing list) so the two stay in sync while actively recording. */
        synchronized(displayPoints) {
            displayPoints.clear()
            displayPoints.addAll(recordedPoints)
        }
    }

    fun loadForDisplay(context: Context, fileName: String) {
        /* Loads a saved route into displayPoints only, for viewing/replay — does not touch recordedPoints or the live recording state. */
        val json = Json { ignoreUnknownKeys = true }
        val route = json.decodeFromString<RecordedRoute>(File(context.filesDir, fileName).readText())
        synchronized(displayPoints) {
            displayPoints.clear()
            displayPoints.addAll(route.points)
        }
    }

    fun cleanStillPointsFromSavedRoutes(context: Context) {
        /* One-off migration/cleanup: strips STILL-classified points out of every saved route file on disk (e.g. to shrink older routes recorded before STILL points were filtered at record time). */
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