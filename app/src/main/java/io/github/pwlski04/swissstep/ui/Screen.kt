package io.github.pwlski04.swissstep.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.pwlski04.swissstep.ui.home.HomeViewModel
import io.github.pwlski04.swissstep.ui.preferences.Page_Preferences
import io.github.pwlski04.swissstep.ui.home.Page_Home
import io.github.pwlski04.swissstep.ui.theme.AppColors
import io.github.pwlski04.swissstep.ui.theme.appColors


@Preview(showBackground = true)
@Composable
fun Screen() {
    /* This function handles what is displayed on the screen (page, navigation bar) */
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    var page by rememberSaveable { mutableIntStateOf(0) }

    val colors = appColors(viewModel.darkMap)

    LaunchedEffect(colors.background) {
        (context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
    }

    val pixelDensity = LocalDensity.current
    var navBarHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)){
        Box(modifier = Modifier.fillMaxSize().padding(bottom = navBarHeight)) {
            Page_Home(context, viewModel)
        }

        if (page == 1) {
            // Preferences page renders over home page
            Box(modifier = Modifier.fillMaxSize().background(colors.background))

            Box(modifier = Modifier.fillMaxSize().padding(bottom = navBarHeight)) {
                Page_Preferences(context, viewModel)
            }
        }

        NavBar(
            currentPage = page, onPageChange = { newPage -> page = newPage },
            colors = colors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    navBarHeight = with(pixelDensity) { coordinates.size.height.toDp() }
                }
        )
    }
}


/*COMPONENT: NAVIGATION BAR*/

@Composable
fun NavBar(modifier: Modifier = Modifier, currentPage: Int, onPageChange: (Int) -> Unit, colors: AppColors) {
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.navbarBg, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        NavBarIsland(
            modifier  = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
            icon = if (currentPage == 1) Icons.Outlined.Home else Icons.Filled.Home,
            description = "Home",
            contentColor = colors.navbarContent,
            onClick = { onPageChange(0) })
        NavBarIsland(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
            icon = if (currentPage == 0) Icons.Outlined.Person else Icons.Filled.Person,
            description = "Profile",
            contentColor = colors.navbarContent,
            onClick = { onPageChange(1) })
    }
}

@Composable
fun NavBarIsland(modifier: Modifier, icon: ImageVector?, description: String, contentColor: Color = Color.Black, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = description, tint = contentColor)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, color = contentColor)
    }
}