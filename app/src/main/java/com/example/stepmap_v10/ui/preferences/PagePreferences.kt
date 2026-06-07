package com.example.stepmap_v10.ui.preferences

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stepmap_v10.colorMap
import com.example.stepmap_v10.tracking.MovementType

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
fun Page_Preferences() {
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

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = "Movement Colors")

        Spacer(modifier = Modifier.height(16.dp))

        for (movementType in MovementType.entries) {
            MovementColorDropdown(
                movementType = movementType,
                selections = movementColorSelections
            )

            Spacer(modifier = Modifier.height(12.dp))
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