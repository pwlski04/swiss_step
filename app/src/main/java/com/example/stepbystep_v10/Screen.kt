package com.example.stepbystep_v10

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun Screen() {
    var page by rememberSaveable { mutableIntStateOf(0) }

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
                .statusBarsPadding()
        ) {
            when (page) {
                0 -> Page_Home()
                1 -> Page_Preferences()
                else -> Page_Home()
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
            .background(Color.LightGray)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
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
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center, modifier = Modifier
                .size(25.dp)
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