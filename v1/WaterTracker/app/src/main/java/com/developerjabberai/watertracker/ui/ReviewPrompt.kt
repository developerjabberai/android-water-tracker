package com.developerjabberai.watertracker.ui

import android.app.Activity
import android.content.Context
import com.developerjabberai.watertracker.data.WaterStore
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

private const val MIN_DAYS_INSTALLED = 2
private const val MIN_DAYS_BETWEEN_ASKS = 30
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Asks for a rating with Google's own in-app review card, once the app has been installed for a couple of
 * days and never more than once a month. Google decides whether the card actually appears (it rate-limits
 * apps), and it only works on copies installed from Play, so this is a quiet no-op everywhere else.
 */
suspend fun maybeAskForReview(activity: Activity, store: WaterStore) {
    val now = System.currentTimeMillis()
    val installedFor = now - firstInstallMs(activity)
    if (installedFor < MIN_DAYS_INSTALLED * DAY_MS) return
    val last = store.lastReviewAskMs
    if (last != 0L && now - last < MIN_DAYS_BETWEEN_ASKS * DAY_MS) return

    val manager = ReviewManagerFactory.create(activity)
    runCatching {
        val info = manager.requestReview()
        store.lastReviewAskMs = now
        manager.launchReview(activity, info)
    }
}

private fun firstInstallMs(context: Context): Long =
    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
