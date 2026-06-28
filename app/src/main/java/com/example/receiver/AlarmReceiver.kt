package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AlarmDatabase
import com.example.data.RepeatAlarm
import com.example.ui.RingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val RING_CHANNEL_ID = "repeat_alarm_ringing"

        fun triggerAlarmRing(context: Context, alarm: RepeatAlarm) {
            Log.d(TAG, "triggerAlarmRing called for Alarm ID: ${alarm.id}")
            val intent = Intent(context, RingActivity::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            // Always show the persistent ringing notification on top
            showRingNotification(context, alarm, intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start RingActivity directly", e)
                }
            }
        }

        private fun showRingNotification(context: Context, alarm: RepeatAlarm, ringActivityIntent: Intent) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    RING_CHANNEL_ID,
                    "Active Ringing Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Channel for active ringing alarms"
                    setBypassDnd(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                alarm.id.toInt(),
                ringActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "DISMISS_ALARM"
                putExtra("ALARM_ID", alarm.id)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id.toInt() + 20000,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, RING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (alarm.name.isNotEmpty()) alarm.name else "Repeat Alarm")
                .setContentText("Interval: ${alarm.getIntervalString()} - Running...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
                .build()

            notificationManager.notify(alarm.id.toInt(), notification)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val alarmId = intent.getLongExtra("ALARM_ID", -1L)
        Log.d(TAG, "onReceive: action=$action, alarmId=$alarmId")

        if (alarmId == -1L) return

        val database = AlarmDatabase.getDatabase(context.applicationContext)
        val dao = database.alarmDao()

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = dao.getAlarmById(alarmId) ?: return@launch

            if (action == "TRIGGER_ALARM") {
                if (alarm.isActive) {
                    val localTimeNow = LocalTime.now()
                    val start = LocalTime.of(alarm.startHour, alarm.startMinute)
                    val end = LocalTime.of(alarm.endHour, alarm.endMinute)

                    if (isTimeInRange(localTimeNow, start, end)) {
                        triggerAlarmRing(context, alarm)
                    } else {
                        Log.d(TAG, "Skipping Alarm $alarmId: $localTimeNow is outside active interval $start to $end")
                    }

                    // Update last triggered time to now
                    val updatedAlarm = alarm.copy(lastTriggeredTime = System.currentTimeMillis())
                    dao.updateAlarm(updatedAlarm)

                    // Schedule next run
                    RepeatAlarmScheduler.scheduleNextAlarm(context, updatedAlarm)
                }
            } else if (action == "DISMISS_ALARM") {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(alarmId.toInt())
                Log.d(TAG, "Dismissed alarm notification for ID: $alarmId")
            }
        }
    }

    private fun isTimeInRange(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start <= end) {
            now >= start && now <= end
        } else {
            now >= start || now <= end
        }
    }
}
