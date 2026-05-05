package com.example.stepMap_v10.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun Page_Preferences(pathWidth: Float, onPathWidthChange: (Float) -> Unit) {
    var text by remember(pathWidth){
        mutableStateOf(pathWidth.toString())
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Path width")

        OutlinedTextField(value = text, onValueChange = {text = it}, label = { "Enter path width" })

        Button(onClick = {
            val width = text.toFloatOrNull()
            if (width != null) {
                onPathWidthChange(width.coerceIn(1f, 500f))
            }
        }) {
            Text("Save")
        }
    }
}