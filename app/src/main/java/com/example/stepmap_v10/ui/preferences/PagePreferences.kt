package com.example.stepmap_v10.ui.preferences

import android.graphics.Color
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color as AltColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stepmap_v10.defaultColorMap
import com.example.stepmap_v10.colorMap
import com.example.stepmap_v10.tracking.MovementType
import com.example.stepmap_v10.ui.home.HomeViewModel

import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.rememberColorPickerController



fun handleColorSelect(movementType: MovementType, color: Int, viewModel: HomeViewModel) {
    colorMap[movementType] = color
    viewModel.saveColorMap()
}

val colorNamesToValues = mapOf(
    "Gray" to Color.rgb(220, 220, 220),
    "Pink" to Color.rgb(244, 178, 193),
    "Red" to Color.rgb(220, 120, 120),
    "Orange" to Color.rgb(232, 160, 105),
    "Yellow" to Color.rgb(232, 202, 120),
    "Green" to Color.rgb(142, 190, 150),
    "Blue" to Color.rgb(130, 170, 210),
    "Purple" to Color.rgb(178, 150, 205),
)

val colorValuesToNames = colorNamesToValues.entries.associate { (name, value) ->
    value to name
}

@Preview
@Composable
fun Page_Preferences(viewModel: HomeViewModel = viewModel()) {

    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth().background(AltColor(0xFFF0F0F0), shape = RoundedCornerShape(20.dp))){
            Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally){
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Icon(imageVector = Icons.Rounded.AccountCircle, contentDescription = "Profile image", modifier = Modifier.size(80.dp), tint = AltColor.DarkGray)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Quandale Dingle", color = AltColor.Black, fontSize = 18.sp)

                Spacer(modifier = Modifier.height(40.dp))
                Row(modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text(text = "400m explored", color = AltColor.Black, fontSize = 14.sp)
                    Text(text = "prefers walking", color = AltColor.Black, fontSize = 14.sp)
                }
            }
        }

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

                if(viewModel.showPathColorChoice){
                    for (movementType in MovementType.entries) {
                        MovementColorPicker(
                            movementType = movementType,
                            viewModel = viewModel
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit){
    Spacer(modifier = Modifier.height(40.dp))

    Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(8.dp))

    Spacer(modifier = Modifier.height(4.dp))

    content()
}

@Composable
fun SingularSetting(
    name: String = "Example", description: String = "This is the default description for the example setting.",
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
){
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable{showDialog = true},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = movementType.name.lowercase().replaceFirstChar { it.uppercase() },
            fontSize = 16.sp
        )
        Box(
            modifier = Modifier.size(32.dp)
                .background(ComposeColor(currentColor), RoundedCornerShape(8.dp))
                .clickable { showDialog = true }
        )
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
                    val argb = android.graphics.Color.argb(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementColorDropdown(movementType: MovementType, selections: SnapshotStateMap<MovementType, String>, modifier: Modifier = Modifier, viewModel: HomeViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedColorName = selections[movementType] ?: colorNamesToValues.keys.first()
    val usedColorsByOtherMovementTypes = selections.filterKeys { it != movementType }.values.toSet()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = colorValuesToNames[colorMap[movementType]] ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(movementType.name.lowercase().replaceFirstChar { it.uppercase() }) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            ComposeColor(colorMap[movementType] ?: Color.GRAY),
                            CircleShape
                        )
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = if(expanded) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) else RoundedCornerShape(20.dp)
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            colorNamesToValues.forEach { (colorName, colorValue) ->
                val alreadyUsedByOther = colorName in usedColorsByOtherMovementTypes

                DropdownMenuItem(
                    enabled = !alreadyUsedByOther,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(16.dp).background( ComposeColor(colorValue), CircleShape))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = colorName)
                        }
                    },
                    onClick = {
                        selections[movementType] = colorName
                        handleColorSelect(movementType, colorValue, viewModel)
                        expanded = false
                    }
                )
            }
        }
    }
}