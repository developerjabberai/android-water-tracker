package com.developerjabberai.watertracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.developerjabberai.watertracker.data.WaterStore
import com.developerjabberai.watertracker.service.UnlockService
import com.developerjabberai.watertracker.ui.ConfigScreen
import com.developerjabberai.watertracker.ui.WelcomeScreen
import com.developerjabberai.watertracker.ui.theme.WaterTrackerTheme
import com.developerjabberai.watertracker.ui.widgetCount

class MainActivity : ComponentActivity() {
    // Once allowed, restart the service call so its quiet "Watching your pace" notification appears.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { UnlockService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UnlockService.start(this)
        setContent {
            WaterTrackerTheme(dynamicColor = false) {
                val store = remember { WaterStore(this) }
                // Anyone who already has a widget doesn't need the welcome.
                var welcomed by remember { mutableStateOf(store.onboarded || widgetCount(this) > 0) }
                LaunchedEffect(welcomed) { if (welcomed) askForNotificationsOnce(store) }
                if (welcomed) {
                    ConfigScreen()
                } else {
                    WelcomeScreen(onDone = { store.onboarded = true; welcomed = true })
                }
            }
        }
    }

    /** Android 13+ hides the service's notification unless the user allows notifications. Ask once, after the welcome. */
    private fun askForNotificationsOnce(store: WaterStore) {
        if (Build.VERSION.SDK_INT < 33 || store.notificationsAsked) return
        store.notificationsAsked = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
