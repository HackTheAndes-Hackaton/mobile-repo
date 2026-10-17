package com.tiskz.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val spanish = Locale("es")

fun formatDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** "Ahora", "Hace 5 min", "Hoy 14:20", "Ayer 09:15", "12 oct". Siempre en español. */
fun formatWhen(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - timestamp) / 60_000
    if (minutes < 1) return "Ahora"
    if (minutes < 60) return "Hace $minutes min"

    val time = SimpleDateFormat("HH:mm", spanish).format(Date(timestamp))
    val day = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val dayDiff = today.get(Calendar.DAY_OF_YEAR) - day.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Hoy $time"
        sameYear && dayDiff == 1 -> "Ayer $time"
        else -> SimpleDateFormat("d MMM", spanish).format(Date(timestamp))
    }
}
