package com.example.util

object TimeUtils {

    fun getGreeting(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): String {
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    fun formatDate(timestamp: Long): String {
        return DateUtils.formatDate(timestamp)
    }

    fun formatTime(timestamp: Long): String {
        return DateUtils.formatTime(timestamp)
    }

    fun formatRelativeTime(timestamp: Long): String {
        return DateUtils.formatRelativeTime(timestamp)
    }

    fun formatFullDateTime(timestamp: Long): String {
        return DateUtils.formatDateTime(timestamp)
    }
}

