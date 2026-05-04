package com.example.stepbystep_v10.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.stepbystep_v10.map.paths.loadPathWidth
import com.example.stepbystep_v10.map.paths.savePathWidth
import com.example.stepbystep_v10.ui.preferences.Page_Preferences
import com.example.stepbystep_v10.ui.home.Page_Home

@Composable
fun Screen() {
    val context = LocalContext.current

    var page by rememberSaveable { mutableIntStateOf(0) }
    var pathWidth by remember { mutableStateOf(loadPathWidth(context)) }

    Scaffold(
        modifier = Modifier.fillMaxSize(), bottomBar = {
            NavBar(
                currentPage = page,
                onPageChange = { newPage -> page = newPage },
                modifier = Modifier
            )
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (page) {
                0 -> Page_Home(context, pathWidth)
                1 -> Page_Preferences(pathWidth = pathWidth,
                    onPathWidthChange = { newWidth ->
                        pathWidth = newWidth
                        savePathWidth(context, newWidth)
                    })
                else -> Page_Home(context, pathWidth)
            }
        }
    }
}


/*COMPONENT: NAVIGATION BAR*/

@Composable
fun NavBar(modifier: Modifier = Modifier, currentPage: Int, onPageChange: (Int) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(red = 232, green = 232, blue = 232, 255))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        NavBarIsland(
            color = Color.Black,
            icon = Icons.Outlined.Home,
            description = "Home",
            onClick = { onPageChange(0) })
        NavBarIsland(
            color = Color.Black,
            icon = Icons.Outlined.Person,
            description = "Preferences",
            onClick = { onPageChange(1) })
    }
}

@Composable
fun NavBarIsland(color: Color, icon: ImageVector?, description: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center, modifier = Modifier
                .size(24.dp)
                .background(
                    if (icon == null) color else Color.Transparent
                )
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = description, tint = color)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, color = color)
    }
}