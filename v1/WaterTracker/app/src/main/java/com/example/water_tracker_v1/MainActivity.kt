package com.example.water_tracker_v1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.water_tracker_v1.service.UnlockService
import com.example.water_tracker_v1.ui.ConfigScreen
import com.example.water_tracker_v1.ui.theme.Watertracker_v1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UnlockService.start(this)
        setContent {
            Watertracker_v1Theme(dynamicColor = false) {
                ConfigScreen()
            }
        }
    }
}
