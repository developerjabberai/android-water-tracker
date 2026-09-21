package com.example.water_tracker_v1.ui

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.water_tracker_v1.data.WaterStore
import com.example.water_tracker_v1.widget.WaterWidgetProvider
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private val Blue = Color(0xFF2E9BF0)
private val Ink = Color(0xFF12324A)

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val store = remember { WaterStore(context) }
    var goal by remember { mutableIntStateOf(store.goalMl) }
    var glass by remember { mutableIntStateOf(store.glassMl) }
    var nudge by remember { mutableIntStateOf(store.nudgeMl) }
    var wake by remember { mutableStateOf(store.wake) }
    var sleep by remember { mutableStateOf(store.sleep) }
    val history = remember { store.historyMl() }

    fun changed() = WaterWidgetProvider.refresh(context)

    Column(
        Modifier
            .background(Color(0xFFF4FAFD))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Water Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text("The widget is the app. Set it up here, then tap it on your home screen.", color = Ink.copy(alpha = 0.7f))

        Section("Last 7 days") { WeekChart(history, goal) }

        Section("Daily goal") {
            Chips(listOf(2000, 3000, 4000), goal, { "${it / 1000} L" }) { goal = it; store.goalMl = it; changed() }
            Hint(if (goal <= 2000) "Shown as two 1 L bottles" else "Shown as one big bottle")
        }

        Section("Glass size") {
            GlassPicker(listOf(200, 300, 400), glass) { glass = it; store.glassMl = it; changed() }
            Hint("One tap on the widget = half a glass = ${glass / 2} ml")
        }

        Section("Waking hours") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton("Wake", wake, Modifier.weight(1f)) { wake = it; store.wake = it; changed() }
                TimeButton("Sleep", sleep, Modifier.weight(1f)) { sleep = it; store.sleep = it; changed() }
            }
            Hint("Your target rises faster in the morning and flattens toward bedtime")
        }

        Section("Nudge me when I'm behind by") {
            Chips(listOf(100, 200, 300), nudge, { "$it ml" }) { nudge = it; store.nudgeMl = it }
            Hint("The widget pulses when you unlock your phone and you're this far behind")
        }

        Section("Add the widget") {
            Text("1. Long-press an empty spot on your home screen\n2. Tap Widgets\n3. Find Water Tracker and drag it out", color = Ink)
            Button(
                onClick = { requestPin(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) { Text("Add to home screen") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink)
            content()
        }
    }
}

/** Tumbler drawings scaled to real size, filled to the half a single tap adds. */
@Composable
private fun GlassPicker(sizes: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sizes.forEach { ml ->
            val on = ml == selected
            Column(
                Modifier
                    .weight(1f)
                    .background(if (on) Blue.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) Blue else Ink.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .clickable { onPick(ml) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.height(96.dp), contentAlignment = Alignment.BottomCenter) {
                    Glass(heightDp = 48 + (ml - 200) * 24 / 100)
                }
                Text("$ml ml", fontWeight = FontWeight.SemiBold, color = Ink)
                Text("1 tap = ${ml / 2} ml", style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun Glass(heightDp: Int) {
    Canvas(Modifier.width((heightDp * 0.72f).dp).height(heightDp.dp)) {
        val w = size.width
        val h = size.height
        val inset = w * 0.14f
        val glass = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w - inset, h); lineTo(inset, h); close()
        }
        // Water up to the half-way mark: exactly what one tap adds.
        clipPath(glass) {
            drawRect(Blue.copy(alpha = 0.85f), topLeft = Offset(0f, h / 2), size = Size(w, h / 2))
        }
        drawPath(glass, Ink.copy(alpha = 0.35f), style = Stroke(width = 3.dp.toPx()))
        drawLine(
            Ink.copy(alpha = 0.5f), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        )
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.6f))

@Composable
private fun Chips(options: List<Int>, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(
            selected = it == selected,
            onClick = { onPick(it) },
            label = { Text(label(it)) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue.copy(alpha = 0.18f), selectedLabelColor = Ink),
        ) }
    }
}

@Composable
private fun TimeButton(label: String, time: LocalTime, modifier: Modifier, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier,
        onClick = { TimePickerDialog(context, { _, h, m -> onPick(LocalTime.of(h, m)) }, time.hour, time.minute, false).show() },
    ) { Text("$label  %02d:%02d".format(time.hour, time.minute)) }
}

@Composable
private fun WeekChart(days: List<Pair<java.time.LocalDate, Int>>, goalMl: Int) {
    val top = maxOf(goalMl, days.maxOf { it.second }).toFloat()
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, (date, ml) ->
            val today = i == days.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(ml / 1000f), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((90 * ml / top).coerceAtLeast(3f).dp)
                        .background(if (ml >= goalMl) Blue else Blue.copy(alpha = if (today) 0.9f else 0.45f), RoundedCornerShape(6.dp)),
                )
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                    color = Ink,
                )
            }
        }
    }
    Hint("Litres per day. Solid bars hit the goal.")
}

private fun requestPin(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, WaterWidgetProvider::class.java), null, null)
    }
}
