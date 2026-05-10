package com.example.stepMap_v10.paths

import android.content.Context
import android.util.Log
import com.example.stepMap_v10.tracking.MovementType
import org.json.JSONObject
import java.io.File



// OLD

private const val WALKED_SEGMENTS_FILE = "walked_segments.json"


fun saveWalkedSegments(context: Context, walkedSegments: Map<String, MovementType>){
    try {
        val jsonObject = JSONObject()

        walkedSegments.forEach { (segmentId, movementType) ->
            jsonObject.put(segmentId, movementType.name)
        }

        val file = File(context.filesDir, WALKED_SEGMENTS_FILE)
        file.writeText(jsonObject.toString())
    }catch (e: Exception) {
        Log.e("StepByStep_v1.0_TAG", "Failed to save segment IDs", e)
    }
}


fun loadWalkedSegments(context: Context): MutableMap<String, MovementType>{
    return try {
        val file = File(context.filesDir, WALKED_SEGMENTS_FILE)

        if(!file.exists()){
            return mutableMapOf()
        }

        val jsonText = file.readText()
        if(jsonText.isBlank()){
            return mutableMapOf()
        }

        val jsonObject = JSONObject(jsonText)
        val result = mutableMapOf<String, MovementType>()

        val keys = jsonObject.keys()

        while (keys.hasNext()) {
            val segmentId = keys.next()
            val movementName = jsonObject.getString(segmentId)

            val movementType = runCatching {
                MovementType.valueOf(movementName)
            }.getOrNull()

            if (movementType != null) {
                result[segmentId] = movementType
            }
        }

        result
    } catch (e: Exception) {
        Log.e("StepByStep_v1.0_TAG", "Failed to load segment IDs", e)

        mutableMapOf()
    }
}


fun clearWalkedSegments(context: Context){
    try {
        val file = File(context.filesDir, WALKED_SEGMENTS_FILE)
        if (file.exists()){
            file.delete()
        }
    } catch (e: Exception) {
        Log.e("StepByStep_v1.0_TAG", "Failed to clear segment IDs", e)
    }
}