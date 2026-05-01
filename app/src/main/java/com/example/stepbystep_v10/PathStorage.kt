package com.example.stepbystep_v10

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File



private const val WALKED_SEGMENTS_FILE = "walked_segments.json"


fun saveWalkedSegmentIds(context: Context, walkedSegmentIds: Set<String>){
    try {
        val jsonArray = JSONArray()

        walkedSegmentIds.forEach { id ->
            jsonArray.put(id)
        }

        val file = File(context.filesDir, WALKED_SEGMENTS_FILE)
        file.writeText(jsonArray.toString())
    }catch (e: Exception) {
        Log.e("StepByStep_v1.0_TAG", "Failed to save segment IDs", e)
    }
}


fun loadWalkedSegmentIds(context: Context): MutableSet<String>{
    return try {
        val file = File(context.filesDir, WALKED_SEGMENTS_FILE)

        if(!file.exists()){
            return mutableSetOf()
        }

        val jsonText = file.readText()
        if(jsonText.isBlank()){
            return mutableSetOf()
        }

        val jsonArray = JSONArray(jsonText)
        val ids = mutableSetOf<String>()

        for(i in 0 until jsonArray.length()){
            ids.add(jsonArray.getString(i))
        }

        ids
    } catch (e: Exception) {
        Log.e("StepByStep_v1.0_TAG", "Failed to load segment IDs", e)

        mutableSetOf()
    }
}


fun clearWalkedSegmentIds(context: Context){
    try {
        val file = File(context.filesDir, WALKED_SEGMENTS_FILE)
        if (file.exists()){
            file.delete()
        }
    } catch (e: Exception) {
        Log.e("StepByStep_v1.0_TAG", "Failed to clear segment IDs", e)
    }
}