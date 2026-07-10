package io.github.pwlski04.swissstep.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)


/* COLORS */
val page_background = Color(248, 243, 247)

val gray_pale_subtle = Color(red = 240, green = 240, blue = 240, 144)    // Color(red = 240, green = 240, blue = 240, 144)
val gray_pale = Color(240, 240, 240)
val gray_light_subtle = Color(red = 220, green = 220, blue = 220, 144)    // Color(red = 240, green = 240, blue = 240, 144)
val gray_light = Color(220, 220, 220)
val gray_medium = Color(180, 180, 180)
val gray_dark = Color(64, 64, 64)


val accentColor_main = Color(61, 90, 75)
val accentColor_main_subtle = accentColor_main.copy(alpha=(144f/255f))

val accentColor_red = Color(183, 28, 28)    // red
val accentColor_green = Color(102, 187, 106)
val accentColor_blue = Color(66, 165, 245)
val accentColor_purple = Color(171, 71, 188)

val accentColor_highLights = Color(255, 179, 0)  // orange


val text_main = Color(red = 0, green = 0, blue = 0)
val text_contrast = Color(red = 255, green = 255, blue = 255)


/* DARK MODE
Applied to page backgrounds and page-local content (preferences screen text/dividers/switches),
plus the navbar and the map's plain gray floating buttons (recenter/zoom). */
val page_background_dark = Color(18, 18, 18)
val text_main_dark = Color(230, 230, 230)
val divider_dark = Color(60, 60, 60)
val gray_light_dark = Color(90, 90, 90)

// Navbar: noticeably lighter than the dark map background (#1A1A1A) without being too light.
val gray_pale_subtle_dark = Color(red = 60, green = 60, blue = 60, alpha = 210)

// Map's plain gray floating buttons (recenter/zoom/route replay pills)
val map_button_gray_dark = Color(red = 110, green = 110, blue = 110, alpha = 215)

// Top bar accent
val accentColor_main_dark = Color(110, 160, 135)
val accentColor_main_subtle_dark = accentColor_main_dark.copy(alpha = 180f / 255f)

// Dialogs/popups
val dialog_surface_dark = Color(32, 32, 32)
val dialog_subtitle_dark = Color(150, 150, 150)