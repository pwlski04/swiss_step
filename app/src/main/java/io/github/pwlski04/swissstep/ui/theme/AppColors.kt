package io.github.pwlski04.swissstep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Bundles the handful of colors that repeatedly get resolved with `if (darkMode) X else Y`
across Screen/Page_Home/Page_Preferences into one lookup, computed once per screen/dialog via
appColors(isDark) instead of re-deriving the same light/dark pairs at every call site. */
data class AppColors(
    val background: Color,
    val foreground: Color,
    val divider: Color,
    val muted: Color,
    val dialogSurface: Color,
    val dialogSubtitle: Color,
    val navbarBg: Color,
    val navbarContent: Color,
    val mapButtonBg: Color,
    val mapButtonIcon: Color,
    val accent: Color,
    val accentSubtle: Color,
)

@Composable
fun appColors(isDark: Boolean): AppColors = if (isDark) {
    AppColors(
        background = page_background_dark,
        foreground = text_main_dark,
        divider = divider_dark,
        muted = gray_light_dark,
        dialogSurface = dialog_surface_dark,
        dialogSubtitle = dialog_subtitle_dark,
        navbarBg = gray_pale_subtle_dark,
        navbarContent = text_contrast,
        mapButtonBg = map_button_gray_dark,
        mapButtonIcon = text_contrast,
        accent = accentColor_main_dark,
        accentSubtle = accentColor_main_subtle_dark,
    )
} else {
    AppColors(
        background = page_background,
        foreground = text_main,
        divider = gray_light_subtle,
        muted = gray_light,
        dialogSurface = MaterialTheme.colorScheme.surface,
        dialogSubtitle = gray_dark,
        navbarBg = Color(red = 240, green = 240, blue = 240, alpha = 144),
        navbarContent = Color.Black,
        mapButtonBg = gray_pale_subtle,
        mapButtonIcon = Color.Black,
        accent = accentColor_main,
        accentSubtle = accentColor_main_subtle,
    )
}
