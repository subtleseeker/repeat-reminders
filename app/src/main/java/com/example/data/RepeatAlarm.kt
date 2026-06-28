package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repeat_alarms")
data class RepeatAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val intervalSeconds: Long, // Interval in seconds
    val startHour: Int = 0,    // Specific hour to start (0-23)
    val startMinute: Int = 0,  // Specific minute to start (0-59)
    val endHour: Int = 23,     // Specific hour to end (0-23)
    val endMinute: Int = 59,   // Specific minute to end (0-59)
    val isActive: Boolean = true,
    val soundUri: String? = null,
    val soundName: String = "Default Alarm",
    val vibrationPattern: String = "Standard", // "None", "Standard", "Pulse", "Tick-Tock"
    val ringMode: String = "Alarm", // "Alarm" (loud overrides), "Ring" (respects ringer settings)
    val lastTriggeredTime: Long = 0L // Last time it was triggered
) {
    fun getIntervalString(): String {
        val days = intervalSeconds / 86400
        val remainderDays = intervalSeconds % 86400
        val hours = remainderDays / 3600
        val remainderHours = remainderDays % 3600
        val minutes = remainderHours / 60
        val seconds = remainderHours % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")
        return parts.joinToString(" ")
    }
}
