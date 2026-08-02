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
        val sharedUris = extractSharedUris(intent)
        setContent {
            PrintReadyTheme {
                PrintReadyNavGraph(sharedUris = sharedUris)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Note: For singleTask/singleTop, we might need to handle onNewIntent
        // Currently we just handle onCreate. 
    }

    private fun extractSharedUris(intent: android.content.Intent?): List<android.net.Uri> {
        val uris = mutableListOf<android.net.Uri>()
        if (intent == null) return uris
        
        if (intent.action == android.content.Intent.ACTION_SEND) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            }
            uri?.let { uris.add(it) }
        } else if (intent.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
            val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM)
            }
            list?.let { uris.addAll(it) }
        }
        return uris
    }
}
