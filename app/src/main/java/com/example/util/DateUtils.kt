package com.example.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {

    fun toMillis(rawTimestamp: Any?): Long {
        if (rawTimestamp == null) return System.currentTimeMillis()
        return when (rawTimestamp) {
            is Timestamp -> rawTimestamp.toDate().time
            is Long -> rawTimestamp
            is Double -> rawTimestamp.toLong()
            is Int -> rawTimestamp.toLong()
            is Date -> rawTimestamp.time
            is String -> {
                rawTimestamp.toLongOrNull() ?: run {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                        sdf.parse(rawTimestamp)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                }
            }
            else -> System.currentTimeMillis()
        }
    }

    /**
     * Standard format: 'dd MMM yyyy hh:mm a' e.g. "27 Jul 2026 08:45 PM"
     */
    fun formatDateTime(timestamp: Any?): String {
        val millis = toMillis(timestamp)
        if (millis <= 0) return "N/A"
        val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US)
        return sdf.format(Date(millis))
    }

    /**
     * Format: 'dd MMM yyyy' e.g. "27 Jul 2026"
     */
    fun formatDate(timestamp: Any?): String {
        val millis = toMillis(timestamp)
        if (millis <= 0) return "N/A"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return sdf.format(Date(millis))
    }

    /**
     * Format: 'hh:mm a' e.g. "08:45 PM"
     */
    fun formatTime(timestamp: Any?): String {
        val millis = toMillis(timestamp)
        if (millis <= 0) return "N/A"
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatRelativeTime(timestamp: Any?): String {
        val millis = toMillis(timestamp)
        if (millis <= 0) return "Just now"
        val now = System.currentTimeMillis()
        val diff = now - millis

        if (diff < 0) return "Just now"

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days == 1L -> "Yesterday"
            days < 30 -> "$days days ago"
            else -> formatDate(millis)
        }
    }
}
