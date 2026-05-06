package com.example.stepMap_v10.tracking

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "tracking_state"
private const val KEY_IS_DRAWING = "is_drawing"
private const val KEY_SESSION_ID = "session_id"

fun saveTrackingState(context: Context, isDrawing: Boolean, sessionId: Long){
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putBoolean(KEY_IS_DRAWING, isDrawing)
                .putLong(KEY_SESSION_ID, sessionId)
        }
}

fun loadIsDrawing(context: Context): Boolean{
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_IS_DRAWING, false)
}

fun loadTrackingSessionId(context: Context): Long{
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(KEY_SESSION_ID, System.currentTimeMillis())
}