package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.RepeatAlarm

object RepeatAlarmScheduler {
    private const val TAG = "RepeatAlarmScheduler"

    fun scheduleNextAlarm(context: Context, alarm: RepeatAlarm) {
        if (!alarm.isActive || alarm.intervalSeconds < 60) {
            cancelAlarm(context, alarm.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = System.currentTimeMillis()
        val baseTime = if (alarm.lastTriggeredTime > 0) alarm.lastTriggeredTime else now
        var nextTime = baseTime + alarm.intervalSeconds * 1000
        
        if (nextTime <= now) {
            nextTime = now + alarm.intervalSeconds * 1000
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "TRIGGER_ALARM"
            putExtra("ALARM_ID", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Exact scheduled alarm ${alarm.id} for $nextTime")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Inexact idle-gated scheduled alarm ${alarm.id} for $nextTime")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
                Log.d(TAG, "Pre-S Exact scheduled alarm ${alarm.id} for $nextTime")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm ${alarm.id}", e)
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Double failure scheduling alarm", e2)
            }
        }
    }

    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "TRIGGER_ALARM"
            putExtra("ALARM_ID", alarmId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled System Alarm ID: $alarmId")
        }
    }
}
