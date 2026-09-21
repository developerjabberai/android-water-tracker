package com.developerjabberai.watertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.developerjabberai.watertracker.data.WaterStore
import com.developerjabberai.watertracker.service.UnlockService
import com.developerjabberai.watertracker.ui.ConfigScreen
import com.developerjabberai.watertracker.ui.WelcomeScreen
import com.developerjabberai.watertracker.ui.theme.WaterTrackerTheme
import com.developerjabberai.watertracker.ui.widgetCount

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UnlockService.start(this)
        setContent {
            WaterTrackerTheme(dynamicColor = false) {
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
