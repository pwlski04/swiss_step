package com.example.stepMap_v10.paths

import android.content.Context
import androidx.core.content.edit

private const val PATH_WIDTH_PREFS = "path_width_prefs"
private const val KEY_PATH_WIDTH = "path_width"

fun loadPathWidth(context: Context): Float {
    return context
        .getSharedPreferences(PATH_WIDTH_PREFS, Context.MODE_PRIVATE)
        .getFloat(KEY_PATH_WIDTH, 5f)
}

fun savePathWidth(context: Context, width: Float) {
    context
        .getSharedPreferences(PATH_WIDTH_PREFS, Context.MODE_PRIVATE)
        .edit {
            putFloat(KEY_PATH_WIDTH, width.coerceIn(1f, 500f))
        }
}