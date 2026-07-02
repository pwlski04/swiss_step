package com.example.stepmap_v10.ui.preferences

import android.content.Context
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color as AltColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stepmap_v10.accentColor_main
import com.example.stepmap_v10.accentColor_main_subtle
import com.example.stepmap_v10.gray_light
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
import com.example.stepmap_v10.gray_light_subtle
import com.example.stepmap_v10.page_background
import com.example.stepmap_v10.text_main
import com.example.stepmap_v10.ui.home.DialogBox
import com.example.stepmap_v10.ui.home.ShadowedButton
import com.example.stepmap_v10.ui.home.filterNameInput


fun handleColorSelect(movementType: MovementType, color: Int, viewModel: HomeViewModel) {
    colorMap[movementType] = color
    viewModel.saveColorMap()
}


@Composable
fun Page_Preferences(context: Context, viewModel: HomeViewModel) {
    var inputName by remember { mutableStateOf("") }

    // "Save all to device": system document picker, so "device files" is always a destination
    // regardless of which share-target apps are installed (unlike viewModel.exportAllRoutes).
    val saveAllToDeviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.saveAllRoutesToDevice(context, it) }
    }
    var showNameDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(page_background)
        .verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bar — fills status bar area only, notch hangs below it
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(accentColor_main_subtle)) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                accentColor_main_subtle,
                                shape = RoundedCornerShape(bottomEnd = 18.dp)
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                   //.padding(16.dp)
                                ,
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "swiss",
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    color = accentColor_main
                                )
                                Icon(
                                    painter = painterResource(R.drawable.app_icon_outline),
                                    contentDescription = "SwissStep icon",
                                    modifier = Modifier.size(48.dp),
                                    tint = AltColor.Unspecified
                                )
                                Text(
                                    text = "step",
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
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
                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = page_background,
                        modifier = Modifier.clip(shape = RoundedCornerShape(28.dp))
                            .border(2.dp, gray_light_subtle, RoundedCornerShape(28.dp))
                            .combinedClickable(
                                onClick = {}, onLongClick = { showNameDialog = true }
                            )) {
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 200.dp)
                                .height(80.dp)
                                .padding(vertical = 28.dp, horizontal = 60.dp),
                            horizontalArrangement = Arrangement.Center,
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

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = viewModel.userName,
                                overflow = TextOverflow.Ellipsis,
                                color = text_main,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }


            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp)){
                SettingsGroup(
                    "Preferences",
                    {
                        SwitchSetting(
                            name ="Dark mode", description = "Toggle dark mode", checked = false, onCheckedChange = { }
                        )
                        Divider(thickness = 2.dp, color = gray_light_subtle)

                        SwitchSetting(
                            name ="Location points", description ="Overlay the points used for the displayed segments",
                            checked = viewModel.showLocationPoints, onCheckedChange = { viewModel.showLocationPoints = it }
                        )
                        Divider(thickness = 2.dp, color = gray_light_subtle)

                        SwitchSetting(
                            name ="Custom path colors", description ="Customize the colors of your walked paths",
                            checked = viewModel.showPathColorChoice, onCheckedChange = { viewModel.showPathColorChoice = it }
                        )
                        if(viewModel.showPathColorChoice){
                            Column(modifier = Modifier.border(2.dp, gray_light_subtle,
                                RoundedCornerShape(18.dp))){
                                for (movementType in MovementType.entries.filter { it != MovementType.STILL }) {
                                    MovementColorPicker(movementType = movementType, viewModel = viewModel)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                )
            }


            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp)) {
                SettingsGroup(
                    "Routes",
                    {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            ShadowedButton(content = {
                                Row(modifier = Modifier.height(60.dp).width(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(gray_light_subtle)
                                    .clickable(onClick = { saveAllToDeviceLauncher.launch("export_all_routes.json") }),     // for share instead: onClick = { viewModel.exportAllRoutes(context) }
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ){
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        tint = text_main,
                                        contentDescription = ("Download routes"),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export", color = text_main)
                                }
                            })
                            ShadowedButton(content = {
                                Row(modifier = Modifier.height(60.dp).width(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(gray_light_subtle)
                                    .clickable(onClick = { saveAllToDeviceLauncher.launch("export_all_routes.json") }),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ){
                                    Icon(
                                        imageVector = Icons.Outlined.FileUpload,
                                        tint = text_main,
                                        contentDescription = ("Upload routes"),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import", color = text_main)
                                }
                            })
                        }
                    }
                )
            }
        }
    }

    if(showNameDialog){
        DialogBox(
            onDismiss = { showNameDialog = false },
            title = "Display name",
            subTitle = null,
            buttonsWithSpacers = {
                OutlinedTextField(
                    value = inputName,
                    shape = RoundedCornerShape(18.dp),
                    onValueChange = { input ->
                        val filtered = filterNameInput(input)
                        if (filtered.length <= 24) inputName = filtered
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor_main_subtle)
                )

                Spacer(modifier = Modifier.height(24.dp))


                TextButton(
                    onClick = {
                        viewModel.userName = inputName
                        showNameDialog = false
                    },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.width(100.dp).background(color = accentColor_blue, shape = RoundedCornerShape(16.dp))
                ) {
                    Text("Done", color = text_contrast)
                }
            }
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit){
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)){
        Spacer(modifier = Modifier.height(40.dp))

        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()){
            content()
        }
    }
}

@Composable
fun SwitchSetting(
    name: String = "Example", description: String = "This is the default description for the example setting.",
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
){
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
    val trackColor = if (checked) accentColor_green else gray_light
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { showDialog = true },
                onLongClick = {
                    if (!hiddenMovementTypes.remove(movementType))
                        hiddenMovementTypes.add(movementType)
                }
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(modifier = Modifier.width(80.dp),
            color = if(hiddenMovementTypes.contains(movementType)) gray_light else AltColor(0,0,0),
            text = movementType.name.lowercase().replaceFirstChar { it.uppercase() },
            fontSize = 16.sp
        )

        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (hiddenMovementTypes.contains(movementType))
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    tint = gray_light,
                    contentDescription = ("Hide " + movementType),
                )
            else
                Box(
                    modifier = Modifier
                        .size(28.dp)
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