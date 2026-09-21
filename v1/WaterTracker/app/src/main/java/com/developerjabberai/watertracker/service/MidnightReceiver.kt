package com.developerjabberai.watertracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.developerjabberai.watertracker.widget.WaterWidgetProvider
import java.time.LocalDate
import java.time.ZoneId

/** Redraws the widget just after midnight so it starts the new day empty. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterWidgetProvider.refresh(context)
        schedule(context)
    }

    companion object {
        /** Idempotent: replaces any earlier alarm. Inexact, so it needs no special permission. */
        fun schedule(context: Context) {
            val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(1)
            val pending = PendingIntent.getBroadcast(
                context, 0, Intent(context, MidnightReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC, next.toInstant().toEpochMilli(), pending)
        }
    }
}
