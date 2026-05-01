package com.example.stepbystep_v10

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "tracking_state"
private const val KEY_IS_TRACKING = "is_tracking"
private const val KEY_SESSION_ID = "session_id"

fun saveTrackingState(context: Context, isTracking: Boolean, sessionId: Long){
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putBoolean(KEY_IS_TRACKING, isTracking)
                .putLong(KEY_SESSION_ID, sessionId)
        }
}

fun loadIsTracking(context: Context): Boolean{
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_IS_TRACKING, false)
}

fun loadTrackingSessionId(context: Context): Long{
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(KEY_SESSION_ID, System.currentTimeMillis())
}