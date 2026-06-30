package com.example.stepmap_v10.ui.preferences

import android.graphics.Color
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color as AltColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stepmap_v10.accentColor_main
import com.example.stepmap_v10.accentColor_main_subtle
import com.example.stepmap_v10.gray_light_subtle
import com.example.stepmap_v10.defaultColorMap
import com.example.stepmap_v10.colorMap
import com.example.stepmap_v10.hiddenMovementTypes
import com.example.stepmap_v10.text_contrast
import com.example.stepmap_v10.tracking.MovementType
import com.example.stepmap_v10.ui.home.HomeViewModel
import com.example.stepmap_v10.ui.home.InverseCornerBox

import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.example.stepmap_v10.R
import com.example.stepmap_v10.accentColor_blue
import com.example.stepmap_v10.accentColor_green
import com.example.stepmap_v10.accentColor_highLights
import com.example.stepmap_v10.gray_medium
import com.example.stepmap_v10.text_main
import com.example.stepmap_v10.ui.home.ShadowedButton


fun handleColorSelect(movementType: MovementType, color: Int, viewModel: HomeViewModel) {
    colorMap[movementType] = color
    viewModel.saveColorMap()
}

@Preview
@Composable
fun PreviewPage_Preferences() {
    Column(modifier = Modifier.fillMaxSize().background(AltColor(255, 255, 255)).verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bar — fills status bar area only, notch hangs below it
            Box(modifier = Modifier.fillMaxWidth().background(accentColor_main_subtle)) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                )
            }
            Column(modifier = Modifier.fillMaxWidth()){
                Row(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).background(
                            accentColor_main_subtle,
                            shape = RoundedCornerShape(bottomEnd = 20.dp)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Swiss",
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    color = accentColor_main
                                )
                                Icon(painter = painterResource(R.drawable.app_icon_outline), contentDescription = "SwissStep icon",
                                    modifier = Modifier.size(48.dp), tint = AltColor.Unspecified)
                                Text(
                                    text = "Step",
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    color = text_contrast
                                )
                            }
                        }
                    }
                    InverseCornerBox(
                        color = accentColor_main_subtle,
                        cornerRadius = 12.dp,
                        isLeft = false,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.background(gray_light_subtle, RoundedCornerShape(28.dp)).border(2.dp, text_main, RoundedCornerShape(28.dp))){
                        Row(
                            modifier = Modifier.width(280.dp).height(80.dp).padding(28.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = "Profile image",
                                    modifier = Modifier.size(40.dp),
                                    tint = text_main
                                )
                            }

                            Spacer(modifier = Modifier.width(28.dp))
                            Text(
                                text = "Quandale Dingle",
                                fontWeight = FontWeight.Medium,
                                color = text_main,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}



    @Composable
fun Page_Preferences(viewModel: HomeViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bar — fills status bar area only, notch hangs below it
            Box(modifier = Modifier.fillMaxWidth().background(accentColor_main_subtle)) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(end = 40.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).background(
                            accentColor_main_subtle,
                            shape = RoundedCornerShape(bottomEnd = 20.dp)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Swiss",
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    color = accentColor_main
                                )
                                Icon(
                                    painter = painterResource(R.drawable.app_icon_outline),
                                    contentDescription = "SwissStep icon",
                                    modifier = Modifier.size(48.dp),
                                    tint = AltColor.Unspecified
                                )
                                Text(
                                    text = "Step",
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    color = text_contrast
                                )
                            }
                        }
                    }
                    InverseCornerBox(
                        color = accentColor_main_subtle,
                        cornerRadius = 12.dp,
                        isLeft = false,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.background(accentColor_main, RoundedCornerShape(28.dp))){
                        Row(
                            modifier = Modifier.width(240.dp).height(80.dp).padding(28.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = "Profile image",
                                    modifier = Modifier.size(32.dp),
                                    tint = text_contrast
                                )
                            }

                            Spacer(modifier = Modifier.width(28.dp))
                            Text(
                                text = "Quandale Dingle",
                                fontWeight = FontWeight.Medium,
                                color = text_contrast,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp)){
            SettingsGroup(
                "Preferences",
                {
                    SingularSetting(
                        name ="Location points", description ="Overlay the points used for the displayed segments",
                        checked = viewModel.showLocationPoints, onCheckedChange = { viewModel.showLocationPoints = it }
                    )

                    SingularSetting(
                        name ="Custom path colors", description ="Customize the colors of your walked paths",
                        checked = viewModel.showPathColorChoice, onCheckedChange = { viewModel.showPathColorChoice = it }
                    )

                    for (movementType in MovementType.entries.filter { it != MovementType.STILL }) {
                        MovementColorPicker(movementType = movementType, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp)) {
            SettingsGroup(
                "Data",
                {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShadowedButton(content = {
                            TextButton(
                                onClick = {},
                                content = { Text("Export", color = text_contrast) },
                                modifier = Modifier.width(200.dp).height(40.dp).background(
                                    accentColor_blue, RoundedCornerShape(16.dp)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        })
                        Spacer(modifier = Modifier.height(20.dp))
                        ShadowedButton(content = {
                            TextButton(
                                onClick = {},
                                content = { Text("Import", color = text_contrast) },
                                modifier = Modifier.width(200.dp).height(40.dp).background(
                                    gray_medium, RoundedCornerShape(16.dp)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        })
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit){
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)){
        Spacer(modifier = Modifier.height(40.dp))

        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

        Spacer(modifier = Modifier.height(4.dp))

        Column(modifier = Modifier.fillMaxWidth()){
            content()
        }
    }
}

@Composable
fun SingularSetting(
    name: String = "Example", description: String = "This is the default description for the example setting.",
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
){
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)){
            Text(name, fontSize = 16.sp)
            Text(description, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        SquareSwitch(checked, onCheckedChange)
    }
    Spacer(modifier = Modifier.height(8.dp))
}


@Composable
fun SquareSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) AltColor(0xFF4CAF50) else AltColor(0xFFBDBDBD)
    val thumbOffset by animateDpAsState(targetValue = if (checked) 18.dp else 0.dp)

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .background(trackColor, shape = RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .offset(x = thumbOffset)
                .size(20.dp)
                .background(AltColor.White, shape = RoundedCornerShape(17.dp))
        )
    }
}

@Composable
fun MovementColorPicker(movementType: MovementType, viewModel: HomeViewModel){
    var showDialog by remember {mutableStateOf(false)}
    val currentColor = colorMap[movementType] ?: Color.GRAY
    val hexCode = remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape = RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { showDialog = true },
                onLongClick = {
                    if (!hiddenMovementTypes.remove(movementType))
                        hiddenMovementTypes.add(movementType)
                }
            ).padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(modifier = Modifier.width(80.dp),
            color = if(hiddenMovementTypes.contains(movementType)) gray_light_subtle else AltColor(0,0,0),
            text = movementType.name.lowercase().replaceFirstChar { it.uppercase() },
            fontSize = 16.sp
        )

        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (hiddenMovementTypes.contains(movementType))
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    tint = gray_light_subtle,
                    contentDescription = ("Hide " + movementType),
                )
            else
                Box(
                    modifier = Modifier.size(28.dp)
                        .background(ComposeColor(currentColor), RoundedCornerShape(18.dp))
                )
        }
    }

    if(showDialog){
        val controller = rememberColorPickerController()

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(movementType.name.lowercase().replaceFirstChar { it.uppercase() }) },
            text = {
                Column {
                    HsvColorPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        controller = controller,
                        initialColor = ComposeColor(currentColor)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AlphaSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        controller = controller
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BrightnessSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        controller = controller
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "#${hexCode.value}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hex") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val color = controller.selectedColor.value
                    val argb = Color.argb(
                        (color.alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )
                    handleColorSelect(movementType, argb, viewModel)
                    showDialog = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val defaultColor = defaultColorMap[movementType]
                        if (defaultColor != null) {
                            handleColorSelect(movementType, defaultColor, viewModel)
                        }
                        showDialog = false
                    }) {
                        Text("Reset")
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}