package com.printready.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.printready.app.navigation.PrintReadyNavGraph
import com.printready.app.ui.theme.PrintReadyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrintReadyTheme {
                PrintReadyNavGraph()
            }
        }
    }
}
