package com.example.water_tracker_v1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.water_tracker_v1.data.WaterStore
import com.example.water_tracker_v1.service.UnlockService
import com.example.water_tracker_v1.ui.ConfigScreen
import com.example.water_tracker_v1.ui.WelcomeScreen
import com.example.water_tracker_v1.ui.theme.Watertracker_v1Theme
import com.example.water_tracker_v1.ui.widgetCount

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UnlockService.start(this)
        setContent {
            Watertracker_v1Theme(dynamicColor = false) {
                val store = remember { WaterStore(this) }
                // Anyone who already has a widget doesn't need the welcome.
                var welcomed by remember { mutableStateOf(store.onboarded || widgetCount(this) > 0) }
                if (welcomed) {
                    ConfigScreen()
                } else {
                    WelcomeScreen(onDone = { store.onboarded = true; welcomed = true })
                }
            }
        }
    }
}
