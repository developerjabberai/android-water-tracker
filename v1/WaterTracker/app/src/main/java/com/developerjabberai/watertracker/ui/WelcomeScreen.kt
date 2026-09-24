package com.developerjabberai.watertracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.developerjabberai.watertracker.data.WaterStore
import com.developerjabberai.watertracker.domain.Creeper
import com.developerjabberai.watertracker.widget.BottleRenderer
import com.developerjabberai.watertracker.widget.Frame
import kotlinx.coroutines.delay

/**
 * First launch: one job, get the widget onto the home screen. Moves on to settings as soon as a
 * widget exists (or the user skips). The widget starts with default settings, which the next screen edits.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WaterStore(context) }
    val preview = remember { BottleRenderer.render(context, 2000, 0.7f, Frame(totalMl = 1100f, creeper = Creeper.START.toFloat())).asImageBitmap() }
    var waiting by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(false) }

    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        var tries = 0
        while (tries++ < 90 && widgetCount(context) == 0) delay(1000)
        if (widgetCount(context) > 0) {
            added = true
            delay(900)
            onDone()
        }
        waiting = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF4FAFD))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(preview, contentDescription = "Widget preview", modifier = Modifier.size(220.dp))
        Text(
            "Water Tracker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "The widget is the whole app. Tap it on your home screen to log water.",
            color = Ink.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        if (added) {
            Text("\u2713  Widget added", color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        } else if (canPin(context)) {
            Button(
                onClick = {
                    // Written immediately, before handing off to the launcher's own confirmation UI:
                    // on some OEMs that hand-off backgrounds (and can even kill) this activity, which
                    // would otherwise strand the user on this screen forever since the widget-detection
                    // loop below never gets to finish and mark onboarding done itself.
                    store.onboarded = true
                    requestPin(context)
                    waiting = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Add widget", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            Text(
                "You can change your goal and settings next.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                "Long-press an empty spot on your home screen, tap Widgets, then drag out Water Tracker.",
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Continue") }
        }
        if (!added) TextButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
            Text("Skip for now", color = Ink.copy(alpha = 0.6f))
        }
    }
}
