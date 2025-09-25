package org.godotengine.plugin.android.healthconnectplugin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

//        setContent {
//            SampleApp()
//        }
    }
}

@Composable
private fun AppAndroidPreview() {
//    SampleApp()
}