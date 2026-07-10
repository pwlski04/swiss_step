package io.github.pwlski04.swissstep

import io.github.pwlski04.swissstep.tracking.MovementType
import android.graphics.Color
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

val defaultColorMap = mapOf(
    // pathColors:
    MovementType.STILL to Color.rgb(255, 180, 180),
    MovementType.WALKING to Color.rgb(251, 180, 236),   // pink
    MovementType.RUNNING to Color.rgb(253, 165, 164),    // coral
    MovementType.BIKING to Color.rgb(148, 187, 254),     // blue
    MovementType.TRANSPORT to Color.rgb(179, 146, 253)   // purple
)
val colorMap = mutableStateMapOf<MovementType, Int>().apply {
    putAll(defaultColorMap)
}

var hiddenMovementTypes = mutableStateListOf<MovementType>(MovementType.STILL)