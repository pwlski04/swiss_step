package com.example.stepmap_v10.ui.preferences

import android.graphics.Color
import androidx.compose.ui.graphics.Color as AltColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.stepmap_v10.colorMap
import com.example.stepmap_v10.tracking.MovementType
import com.example.stepmap_v10.ui.home.HomeViewModel

fun handleSelect(movementType: MovementType, color: Int) {
    colorMap[movementType] = color
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
    val movementColorSelections = remember {
        mutableStateMapOf<MovementType, String>().apply {
            MovementType.entries.forEachIndexed { index, movementType ->
                val currentColor = colorMap[movementType]

                val currentColorName = colorNamesToValues.entries
                    .firstOrNull { it.value == currentColor }
                    ?.key

                this[movementType] = currentColorName
                    ?: colorNamesToValues.keys.elementAt(index % colorNamesToValues.size)
            }
        }
    }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth().height(250.dp).background(AltColor(0xFFEEEEEE), shape = RoundedCornerShape(20.dp)), horizontalAlignment = Alignment.CenterHorizontally){
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Icon(imageVector = Icons.Outlined.AccountCircle, contentDescription = "Profile image", tint = AltColor.Black)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "Quandale Dingle", color = AltColor.Black, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(60.dp))

        Text(text = "Preferences", modifier = Modifier.align(Alignment.CenterHorizontally), fontWeight = FontWeight.Medium, fontSize = 18.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Show location points", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = viewModel.showLocationPoints,
                onCheckedChange = { viewModel.showLocationPoints = it }
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Custom path colors", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = viewModel.showPathColorChoice,
                onCheckedChange = { viewModel.showPathColorChoice = it }
            )
        }

        if(viewModel.showPathColorChoice){
            Spacer(modifier = Modifier.height(12.dp))

            for (movementType in MovementType.entries) {
                MovementColorDropdown(
                    movementType = movementType,
                    selections = movementColorSelections
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementColorDropdown(movementType: MovementType, selections: SnapshotStateMap<MovementType, String>, modifier: Modifier = Modifier) {
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
                        handleSelect(movementType, colorValue)
                        expanded = false
                    }
                )
            }
        }
    }
}