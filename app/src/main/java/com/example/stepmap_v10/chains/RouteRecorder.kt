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

class RouteRecorder {
    private val recordedPoints = mutableListOf<RecordedPoint>()
    var isRecording = false
        private set

    fun startRecording() {
        recordedPoints.clear()
        isRecording = true
    }

    fun recordPoint(lat: Double, lon: Double, movementType: MovementType) {
        if (!isRecording) return
        recordedPoints.add(RecordedPoint(lat, lon, System.currentTimeMillis(), movementType))
    }

    fun stopAndSave(context: Context): String {
        isRecording = false
        val fileName = "route_${System.currentTimeMillis()}.json"
        val json = Json { prettyPrint = false }
        val text = json.encodeToString(RecordedRoute.serializer(), RecordedRoute(recordedPoints.toList()))
        File(context.filesDir, fileName).writeText(text)
        recordedPoints.clear()
        return fileName
    }

    fun listSavedRoutes(context: Context): List<String> {
        return context.filesDir.listFiles()
            ?.filter { it.name.startsWith("route_") && it.name.endsWith(".json") }
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

    /*fun loadAndReplay(
        context: Context,
        fileName: String,
        onPoint: (lat: Double, lon: Double, movementType: MovementType) -> Unit
    ): Int {
        val text = File(context.filesDir, fileName).readText()
        val json = Json { ignoreUnknownKeys = true }
        val route = json.decodeFromString<RecordedRoute>(text)
        route.points.forEach { onPoint(it.lat, it.lon, it.movementType) }
        return route.points.size
    }*/
}

@kotlinx.serialization.Serializable
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