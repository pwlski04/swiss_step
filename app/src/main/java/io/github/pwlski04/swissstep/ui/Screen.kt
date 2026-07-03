package io.github.pwlski04.swissstep.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.pwlski04.swissstep.ui.home.HomeViewModel
import io.github.pwlski04.swissstep.ui.preferences.Page_Preferences
import io.github.pwlski04.swissstep.ui.home.Page_Home


@Preview(showBackground = true)
@Composable
fun Screen() {
    /*
    App root: a plain `when(page)` switch between Home/Preferences, not Compose Navigation.
    This means switching tabs fully removes the non-visible screen from composition (and
    recreates it on return) rather than keeping it alive in a backstack — screen-local
    `remember` state does not survive a tab switch, only state that lives on the shared
    HomeViewModel does.
    */
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    var page by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
            NavBar(
                currentPage = page, onPageChange = { newPage -> page = newPage },
                modifier = Modifier
            )
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (page) {
                0 -> Page_Home(context, viewModel)
                1 -> Page_Preferences(context, viewModel)
                else -> Page_Home(context, viewModel)
            }
        }
    }
}


/*COMPONENT: NAVIGATION BAR*/

@Composable
fun NavBar(modifier: Modifier = Modifier, currentPage: Int, onPageChange: (Int) -> Unit) {
    val selectedColor = Color(red = 0, green = 0, blue = 0, 0); // used to be alpha 32

    Row(
        modifier
            .fillMaxWidth()
            .background(Color(red = 240, green = 240, blue = 240, 144), shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        NavBarIsland(
            modifier  = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
            icon = if (currentPage == 1) Icons.Outlined.Home else Icons.Filled.Home,
            description = "Home",
            onClick = { onPageChange(0) })
        NavBarIsland(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
            icon = if (currentPage == 0) Icons.Outlined.Person else Icons.Filled.Person,
            description = "Profile",
            onClick = { onPageChange(1) })
    }
}

@Composable
fun NavBarIsland(modifier: Modifier, icon: ImageVector?, description: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = description, tint = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, color = Color.Black)
    }
}