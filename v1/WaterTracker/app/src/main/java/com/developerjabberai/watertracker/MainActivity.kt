package com.developerjabberai.watertracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private var resumeTick by mutableIntStateOf(0)

    // Once allowed, start the service again so its quiet "Watching your pace" notification appears.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { UnlockService.start(this) }

    override fun onResume() {
        super.onResume()
        resumeTick++ // lets the settings screen re-check whether the widget is on the home screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UnlockService.start(this)
        setContent {
            WaterTrackerTheme(dynamicColor = false) {
                val store = remember { WaterStore(this) }
                // Anyone who already has a widget doesn't need the welcome.
                var welcomed by remember { mutableStateOf(store.onboarded || widgetCount(this) > 0) }
                var explainNotifications by remember { mutableStateOf(false) }
                LaunchedEffect(welcomed) {
                    if (welcomed && needsNotificationPermission() && !store.notificationsAsked) explainNotifications = true
                }
                if (welcomed) {
                    ConfigScreen(resumeTick)
                } else {
                    WelcomeScreen(onDone = { store.onboarded = true; welcomed = true })
                }
                if (explainNotifications) {
                    AlertDialog(
                        onDismissRequest = { store.notificationsAsked = true; explainNotifications = false },
                        title = { Text("Allow one quiet notification?") },
                        text = {
                            Text(
                                "To remind you when you unlock your phone, Water Tracker keeps a small service running. " +
                                    "Android shows it as one silent notification, \"Watching your pace\", which you can hide any time. " +
                                    "The reminder works either way.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                store.notificationsAsked = true; explainNotifications = false
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text("Continue") }
                        },
                        dismissButton = {
                            TextButton(onClick = { store.notificationsAsked = true; explainNotifications = false }) { Text("Not now") }
                        },
                    )
                }
            }
        }
    }

    /** Android 13+ hides the service's notification unless the user allows notifications. */
    private fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
