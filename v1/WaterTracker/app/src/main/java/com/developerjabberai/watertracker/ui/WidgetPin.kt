package com.developerjabberai.watertracker.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.developerjabberai.watertracker.widget.WaterWidgetProvider

internal fun widgetCount(context: Context): Int {
    val manager = AppWidgetManager.getInstance(context)
    return manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java)).size
}

/** Some launchers can't add widgets on the app's behalf; callers fall back to written steps. */
internal fun canPin(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/** Asks the launcher to add the widget. The system shows its own confirmation. */
internal fun requestPin(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, WaterWidgetProvider::class.java), null, null)
    }
}
