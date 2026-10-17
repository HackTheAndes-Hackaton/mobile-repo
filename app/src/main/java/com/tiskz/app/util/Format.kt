package com.tiskz.app.util

import android.text.format.DateUtils

fun formatDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun formatWhen(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS).toString()
