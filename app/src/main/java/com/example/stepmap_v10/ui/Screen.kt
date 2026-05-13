package com.example.stepMap_v10.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stepMap_v10.chains.PathOverlayLayer
import com.example.stepMap_v10.chains.PathStorage
import com.example.stepMap_v10.ui.preferences.Page_Preferences
import com.example.stepMap_v10.ui.home.Page_Home
import org.mapsforge.map.android.view.MapView


@Preview(showBackground = true)
@Composable
fun Screen() {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }

    // Created once, survive page switches
    val pathStorage = remember { PathStorage() }
    val pathOverlayLayer = remember {
        PathOverlayLayer(pathStorage).also {
            pathStorage.onChainRemoved = { id -> it.evictFromCache(id) }
        }
    }
    val sharedMapView = remember { mutableStateOf<MapView?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(), bottomBar = {
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
                0 -> Page_Home(context, pathStorage, pathOverlayLayer, sharedMapView)
                1 -> Page_Preferences(
                    /*pathWidth = pathWidth,
                    onPathWidthChange = { newWidth ->
                        pathWidth = newWidth
                        savePathWidth(context, width = newWidth)
                    }*/)
                else -> Page_Home(context, pathStorage, pathOverlayLayer, sharedMapView)
            }
        }
    }
}


/*COMPONENT: NAVIGATION BAR*/

@Composable
fun NavBar(modifier: Modifier = Modifier, currentPage: Int, onPageChange: (Int) -> Unit) {
    val selectedColor = Color(red = 0, green = 0, blue = 0, 32);

    Row(
        modifier
            .fillMaxWidth()
            .background(Color(red = 232, green = 232, blue = 232, 255), shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        NavBarIsland(
            modifier  = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).then(
                if (currentPage == 0){
                    Modifier.background(selectedColor, shape = RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
            icon = Icons.Outlined.Home,
            description = "Home",
            onClick = { onPageChange(0) })
        NavBarIsland(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).then(
                if (currentPage == 1){
                    Modifier.background(selectedColor, shape = RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
            icon = Icons.Outlined.Person,
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
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center, modifier = Modifier
                .size(24.dp)
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = description, tint = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, color = Color.Black)
    }
}