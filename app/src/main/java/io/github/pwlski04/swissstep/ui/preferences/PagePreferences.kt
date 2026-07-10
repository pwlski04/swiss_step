package io.github.pwlski04.swissstep.ui.preferences

import android.content.Context
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.AlertDialogDefaults
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pwlski04.swissstep.defaultColorMap
import io.github.pwlski04.swissstep.colorMap
import io.github.pwlski04.swissstep.hiddenMovementTypes
import io.github.pwlski04.swissstep.tracking.MovementType
import io.github.pwlski04.swissstep.ui.home.HomeViewModel
import io.github.pwlski04.swissstep.ui.home.InverseCornerBox
import io.github.pwlski04.swissstep.ui.theme.accentColor_main
import io.github.pwlski04.swissstep.ui.theme.accentColor_main_subtle
import io.github.pwlski04.swissstep.ui.theme.appColors
import io.github.pwlski04.swissstep.ui.theme.dialog_subtitle_dark
import io.github.pwlski04.swissstep.ui.theme.dialog_surface_dark
import io.github.pwlski04.swissstep.ui.theme.divider_dark
import io.github.pwlski04.swissstep.ui.theme.gray_light
import io.github.pwlski04.swissstep.ui.theme.text_contrast
import io.github.pwlski04.swissstep.ui.theme.text_main_dark

import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import io.github.pwlski04.swissstep.R
import io.github.pwlski04.swissstep.ui.theme.accentColor_blue
import io.github.pwlski04.swissstep.ui.theme.accentColor_green
import io.github.pwlski04.swissstep.ui.theme.accentColor_red
import io.github.pwlski04.swissstep.ui.theme.text_main
import io.github.pwlski04.swissstep.ui.home.DialogBox
import io.github.pwlski04.swissstep.ui.home.ExportResult
import io.github.pwlski04.swissstep.ui.home.ShadowedButton
import io.github.pwlski04.swissstep.ui.home.filterNameInput


fun handleColorSelect(movementType: MovementType, color: Int, viewModel: HomeViewModel) {
    colorMap[movementType] = color
    viewModel.saveColorMap()
}


@Composable
fun Page_Preferences(context: Context, viewModel: HomeViewModel) {
    /*
    Settings screen. The logo bar pinned to the top of the screen, while everything else scrolls
    underneath it.
    */

    val pixelDensity = LocalDensity.current
    var logoBarHeight by remember { mutableStateOf(0.dp) }

    val isDark = viewModel.darkMap
    val colors = appColors(isDark)
    val pageBg = colors.background
    val pageFg = colors.foreground
    val pageDivider = colors.divider
    val pageMuted = colors.muted
    val topBarAccent = colors.accentSubtle

    var showNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }

    // "Export" button
    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.saveAllRoutesToDevice(context, it) }
    }

    // "Import" button
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importRoute(context, it) }
    }



    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(logoBarHeight))
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = pageBg,
                    modifier = Modifier.clip(shape = RoundedCornerShape(28.dp))
                        .border(2.dp, pageDivider, RoundedCornerShape(28.dp))
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
                                tint = pageFg
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = viewModel.userName,
                            overflow = TextOverflow.Ellipsis,
                            color = pageFg,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }

            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp)){
                SettingsGroup(
                    "Preferences", titleColor = pageFg,
                    {
                        ToggleSetting(
                            name ="Dark mode", description = "Toggle dark mode", textColor = pageFg, offColor = pageMuted,
                            checked = viewModel.darkMap, onCheckedChange = { viewModel.darkMap = it }
                        )
                        Divider(thickness = 2.dp, color = pageDivider)

                        ToggleSetting(
                            name ="Location points", description ="Overlay the points for the displayed route", textColor = pageFg, offColor = pageMuted,
                            checked = viewModel.showLocationPoints, onCheckedChange = { viewModel.showLocationPoints = it }
                        )
                        Divider(thickness = 2.dp, color = pageDivider)

                        ToggleSetting(
                            name ="Customize path", description ="Tap movement type to select color, long press to show/hide", textColor = pageFg, offColor = pageMuted,
                            checked = viewModel.showPathColorChoice, onCheckedChange = { viewModel.showPathColorChoice = it }
                        )
                        if(viewModel.showPathColorChoice){
                            Column(modifier = Modifier.border(2.dp, pageDivider,
                                RoundedCornerShape(18.dp))){
                                for (movementType in MovementType.entries.filter { it != MovementType.STILL }) {
                                    MovementColorPicker(movementType = movementType, viewModel = viewModel, textColor = pageFg, mutedColor = pageMuted, isDarkMode = isDark)
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
                    "Routes", titleColor = pageFg,
                    {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            ShadowedButton(content = {
                                Row(modifier = Modifier.height(60.dp).width(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pageDivider)
                                    .clickable(onClick = { exportAllLauncher.launch("export_all_routes.json") }),     // for share instead: onClick = { viewModel.exportAllRoutes(context) }
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ){
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        tint = pageFg,
                                        contentDescription = ("Download routes"),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export", color = pageFg)
                                }
                            })
                            ShadowedButton(content = {
                                Row(modifier = Modifier.height(60.dp).width(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pageDivider)
                                    .clickable(onClick = { importLauncher.launch(arrayOf("application/json")) }),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ){
                                    Icon(
                                        imageVector = Icons.Outlined.FileUpload,
                                        tint = pageFg,
                                        contentDescription = ("Upload routes"),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import", color = pageFg)
                                }
                            })
                        }

                        when (val result = viewModel.importResult) {
                            is ExportResult.Success -> Text(
                                "Imported successfully: ${result.displayName}",
                                color = accentColor_green, fontSize = 14.sp
                            )
                            is ExportResult.Failure -> Text(
                                "Import failed",
                                color = accentColor_red, fontSize = 14.sp
                            )
                            null -> {}
                        }
                    }
                )
            }
        }

        // Logo bar — pinned to the top of the screen, content scrolls underneath it
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coordinates ->
                    logoBarHeight = with(pixelDensity) { coordinates.size.height.toDp() }
                }
        ) {
            // Solid backing so the translucent accent color doesn't show scrolled content through it
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(pageBg)
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                // Bar — fills status bar area only, notch hangs below it
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .background(topBarAccent)) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                topBarAccent,
                                shape = RoundedCornerShape(bottomEnd = 18.dp)
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp),
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
                        color = topBarAccent,
                        cornerRadius = 12.dp,
                        isLeft = false,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }

    if(showNameDialog){
        DialogBox(
            onDismiss = { showNameDialog = false },
            title = "Display name",
            subTitle = null,
            isDarkMode = isDark,
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
                    colors = if (isDark) OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor_main_subtle,
                        focusedTextColor = text_main_dark, unfocusedTextColor = text_main_dark,
                        unfocusedBorderColor = divider_dark, cursorColor = text_main_dark
                    ) else OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor_main_subtle)
                )

                Spacer(modifier = Modifier.height(24.dp))

                val canSaveName = inputName.trim().length >= 3 && inputName.length <= 15

                TextButton(
                    onClick = {
                        viewModel.userName = inputName
                        showNameDialog = false
                    },
                    enabled = canSaveName,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.width(100.dp).background(
                        color = if (canSaveName) accentColor_blue else accentColor_blue.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp))
                ) {
                    Text("Done", color = text_contrast)
                }
            }
        )
    }
}

@Composable
fun SettingsGroup(title: String, titleColor: ComposeColor = text_main, content: @Composable () -> Unit){
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)){
        Spacer(modifier = Modifier.height(40.dp))

        Text(text = title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()){
            content()
        }
    }
}

@Composable
fun ToggleSetting(
    name: String = "Example", description: String = "This is the default description for the example setting.",
    textColor: ComposeColor = text_main,
    offColor: ComposeColor = gray_light,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
){
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)){
            Text(name, color = textColor, fontSize = 16.sp)
            Text(description, color = textColor, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Toggle(checked, onCheckedChange, offColor = offColor)
    }
    Spacer(modifier = Modifier.height(8.dp))
}


@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    offColor: ComposeColor = gray_light,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) accentColor_green else offColor
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
fun MovementColorPicker(movementType: MovementType, viewModel: HomeViewModel, textColor: ComposeColor = AltColor(0,0,0), mutedColor: ComposeColor = gray_light, isDarkMode: Boolean = false){
    /*
    One row per movement type: tap opens the color-editing dialog, long-press instead toggles
    whether that movement type's paths are hidden on the map (hiddenMovementTypes).
    */
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
            color = if(hiddenMovementTypes.contains(movementType)) mutedColor else textColor,
            text = movementType.name.lowercase().replaceFirstChar { it.uppercase() },
            fontSize = 16.sp
        )

        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (hiddenMovementTypes.contains(movementType))
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    tint = mutedColor,
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
            containerColor = if (isDarkMode) dialog_surface_dark else AlertDialogDefaults.containerColor,
            titleContentColor = if (isDarkMode) text_main_dark else AlertDialogDefaults.titleContentColor,
            textContentColor = if (isDarkMode) text_main_dark else AlertDialogDefaults.textContentColor,
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
                        colors = if (isDarkMode) OutlinedTextFieldDefaults.colors(
                            focusedTextColor = text_main_dark, unfocusedTextColor = text_main_dark,
                            unfocusedBorderColor = divider_dark,
                            focusedLabelColor = dialog_subtitle_dark, unfocusedLabelColor = dialog_subtitle_dark
                        ) else OutlinedTextFieldDefaults.colors(),
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