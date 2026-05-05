package com.example.stepMap_v10.paths

import android.content.Context
import org.json.JSONArray
import org.mapsforge.core.model.LatLong


fun loadPathsFromGeoJson(context: Context): List<Path> {
    val jsonText = context.assets
        .open("utilized_paths_0.json")
        .bufferedReader()
        .use { it.readText() }

    val jsonArray = JSONArray(jsonText)
    val paths = mutableListOf<Path>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)

        val id = obj.getLong("id")
        val pointsArray = obj.getJSONArray("points")

        val points = mutableListOf<LatLong>()

        for (j in 0 until pointsArray.length()) {
            val pair = pointsArray.getJSONArray(j)

            val lon = pair.getDouble(0)
            val lat = pair.getDouble(1)

            points.add(
                LatLong(lat, lon)
            )
        }

        val highway = obj.optString("highway", "")
        val walkable = obj.optBoolean("walkable", true)
        val drivable = obj.optBoolean("drivable", false)

        paths.add(
            Path(
                id = id,
                points = points,
                highway = highway,
                walkable = walkable,
                drivable = drivable
            )
        )
    }

    return paths
}