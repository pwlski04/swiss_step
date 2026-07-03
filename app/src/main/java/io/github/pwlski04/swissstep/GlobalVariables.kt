package io.github.pwlski04.swissstep

import io.github.pwlski04.swissstep.tracking.MovementType
import android.graphics.Color
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color as DirectColor
import androidx.compose.runtime.mutableStateMapOf

val defaultColorMap = mapOf(
    // pathColors:
    MovementType.STILL to Color.rgb(255, 180, 180),
    MovementType.WALKING to Color.rgb(102, 187, 106),   // green
    MovementType.RUNNING to Color.rgb(255, 112, 67),    // coral
    MovementType.BIKING to Color.rgb(66, 165, 245),     // blue
    MovementType.TRANSPORT to Color.rgb(171, 71, 188)   // purple
)
val colorMap = mutableStateMapOf<MovementType, Int>().apply {
    putAll(defaultColorMap)
}

var hiddenMovementTypes = mutableStateListOf<MovementType>(MovementType.STILL)



/* COLORS */
val page_background = DirectColor(248, 243, 247)

val gray_pale_subtle = DirectColor(red = 240, green = 240, blue = 240, 144)    // Color(red = 240, green = 240, blue = 240, 144)
val gray_pale = DirectColor(240, 240, 240)
val gray_light_subtle = DirectColor(red = 220, green = 220, blue = 220, 144)    // Color(red = 240, green = 240, blue = 240, 144)
val gray_light = DirectColor(220, 220, 220)
val gray_medium = DirectColor(180, 180, 180)
val gray_dark = DirectColor(64, 64, 64)


val accentColor_main = DirectColor(61, 90, 75)
val accentColor_main_subtle = accentColor_main.copy(alpha=(144f/255f))

val accentColor_red = DirectColor(183, 28, 28)    // red
val accentColor_green = DirectColor(102, 187, 106)
val accentColor_blue = DirectColor(66, 165, 245)

val accentColor_highLights = DirectColor(255, 179, 0)  // orange






val text_main = DirectColor(red = 0, green = 0, blue = 0)
val text_contrast = DirectColor(red = 255, green = 255, blue = 255)



//val accentColor1_alt = DirectColor(red = 200, green = 213, blue = 116, 144)
//val accentColor2_alt = DirectColor(red = 255, green = 137, blue = 137, 144)

// TEAL: val text_accentColor1 = DirectColor(red = 102, green = 168, blue = 145, 255)
// dark green: val text_accentColor1 = DirectColor(red = 30, green = 77, blue = 28, 255)
// tree brown: val text_accentColor1 = DirectColor(red = 127, green = 85, blue = 57, 255)
// sky color: val text_accentColor1 = DirectColor(red = 84, green = 182, blue = 202, 255)
// medium green: val text_accentColor1 = DirectColor(red = 15, green = 121, blue = 28, 255)
// peach: val text_accentColor1 = DirectColor(red = 188, green = 85, blue = 111, 255)

// navy (too formal): val text_accentColor1 = DirectColor(red = 20, green = 82, blue = 119, 255)