package com.example.stepbystep_v10

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.stepbystep_v10.ui.theme.StepByStep_v10Theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Screen()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}


/* PAGE:LOADING */

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StepByStep_v10Theme {
        Greeting("Android")
    }
}


/* PREFERENCE PAGE */

@Composable
fun Page_Preferences() {
    Text(text = "Preferences page")
}