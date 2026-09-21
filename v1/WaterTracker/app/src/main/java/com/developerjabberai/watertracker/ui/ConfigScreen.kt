package com.developerjabberai.watertracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.developerjabberai.watertracker.widget.BottleRenderer
import com.developerjabberai.watertracker.widget.Frame
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
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
import com.developerjabberai.watertracker.data.WaterStore
import com.developerjabberai.watertracker.domain.Creeper
import com.developerjabberai.watertracker.widget.WaterWidgetProvider
import java.time.format.TextStyle
import java.util.Locale

internal val Blue = Color(0xFF2E9BF0)
internal val Ink = Color(0xFF12324A)

@Composable
fun ConfigScreen(resumeTick: Int = 0) {
    val context = LocalContext.current
    val store = remember { WaterStore(context) }
    var goal by remember { mutableIntStateOf(store.goalMl) }
    var tap by remember { mutableIntStateOf(store.tapMl) }
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

        Section("Home screen widget") {
            // Re-checked every time the app comes back to the front, so it stays right after adding or removing one.
            val widgets = remember(resumeTick) { widgetCount(context) }
            if (widgets > 0) {
                Text(
                    if (widgets == 1) "\u2713  Widget added to your home screen" else "\u2713  $widgets widgets on your home screen",
                    color = Blue, fontWeight = FontWeight.Bold,
                )
                Text("Tap it there to log water.", color = Ink.copy(alpha = 0.75f))
                if (canPin(context)) {
                    TextButton(onClick = { requestPin(context) }) { Text("Add another", color = Blue) }
                }
            } else {
                Text("The widget is where you log water. Add it to your home screen to start.", color = Ink.copy(alpha = 0.75f))
                if (canPin(context)) {
                    Button(
                        onClick = { requestPin(context) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    ) { Text("Add widget to home screen", fontWeight = FontWeight.Bold) }
                } else {
                    Hint("Long-press an empty spot on your home screen, tap Widgets, then drag out Water Tracker.")
                }
            }
        }

        Section("Your creeper") {
            val level = remember { store.creeperLevel() }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..Creeper.MAX) {
                    Box(
                        Modifier
                            .size(if (i == level) 22.dp else 16.dp)
                            .background(if (i <= level) Blue else Ink.copy(alpha = 0.12f), CircleShape),
                    )
                }
            }
            Text("Level $level of ${Creeper.MAX}: ${Creeper.name(level)}", fontWeight = FontWeight.SemiBold, color = Ink)
            Hint("It grows a level each day you reach your goal, and shrinks a level for each day you miss.")
        }

        Section("Last 7 days") { WeekChart(history, goal) }

        Section("Daily goal") {
            GoalPicker(listOf(2000, 3000, 4000), goal) { goal = it; store.goalMl = it; changed() }
        }

        Section("1 Tap on widget fills") {
            TapPicker(tap) { tap = it; store.tapMl = it }
            Hint("A glass is ${WaterStore.GLASS_ML} ml")
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

/** Goal tiles use the widget's own bottle drawing, so the picker previews exactly what the widget shows. */
@Composable
private fun GoalPicker(goals: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        goals.forEach { goal ->
            // Drawn a hair under full so the goal-reached badge stays off; the widget prints the amount as the label.
            val image = remember(goal) { BottleRenderer.render(context, goal, 0f, Frame(totalMl = goal * 0.999f, creeper = 0f)).asImageBitmap() }
            OptionTile(selected = goal == selected, modifier = Modifier.weight(1f), onClick = { onPick(goal) }) {
                Image(image, contentDescription = "${goal / 1000} litre goal", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Same glass, two fill levels: what one tap logs. */
@Composable
private fun TapPicker(selected: Int, onPick: (Int) -> Unit) {
    val options = listOf(
        Triple(WaterStore.HALF_GLASS_ML, 0.5f, "Half glass"),
        Triple(WaterStore.GLASS_ML, 1f, "One glass"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (ml, fill, label) ->
            OptionTile(selected = ml == selected, modifier = Modifier.weight(1f), onClick = { onPick(ml) }) {
                Box(Modifier.height(64.dp), contentAlignment = Alignment.BottomCenter) { Glass(fill) }
                Text(label, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("$ml ml", style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun OptionTile(selected: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(if (selected) Blue.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Blue else Ink.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** A 200 ml tumbler, filled to [fill] of its height. */
@Composable
private fun Glass(fill: Float) {
    Canvas(Modifier.width(30.dp).height(46.dp)) {
        val w = size.width
        val h = size.height
        val inset = w * 0.14f
        val glass = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w - inset, h); lineTo(inset, h); close()
        }
        clipPath(glass) {
            drawRect(Blue.copy(alpha = 0.85f), topLeft = Offset(0f, h * (1f - fill)), size = Size(w, h * fill))
        }
        drawPath(glass, Ink.copy(alpha = 0.35f), style = Stroke(width = 2.5.dp.toPx()))
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.6f))

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
