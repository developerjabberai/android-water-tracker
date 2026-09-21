package com.developerjabberai.watertracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.developerjabberai.watertracker.MainActivity
import com.developerjabberai.watertracker.R
import com.developerjabberai.watertracker.domain.NudgeController
import java.util.concurrent.Executors

/**
 * Keeps an unlock listener alive (Android won't deliver ACTION_USER_PRESENT to a manifest receiver).
 * Its notification is minimum priority: silent, no popup, tucked away in the shade.
 */
class UnlockService : Service() {
    private val executor = Executors.newSingleThreadExecutor()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            executor.execute { runCatching { NudgeController.onUnlock(applicationContext) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Widget reminders", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Lets the widget notice when you unlock your phone"
                setShowBadge(false)
            },
        )
        showForeground()
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        MidnightReceiver.schedule(this)
    }

    /** Also called on every start, so the notification appears as soon as the user allows notifications. */
    private fun showForeground() {
        // Tapping it goes to the home screen, where the widget is.
        val home = PendingIntent.getActivity(
            this, 0, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_water)
            .setContentTitle("Water Tracker")
            .setContentText("Tap your widget to log water")
            .setContentIntent(home)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "watching"
        private const val ID = 1

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, UnlockService::class.java)) }
        }
    }
}
