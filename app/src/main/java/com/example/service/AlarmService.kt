package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AlarmDatabase
import com.example.receiver.AlarmReceiver
import kotlinx.coroutines.*
import java.time.LocalTime

class AlarmService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var timerJob: Job? = null
    
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "high_frequency_timers"
        private const val TAG = "AlarmService"
        
        fun startService(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RepeatAlarms::PrecisionTimerWakeLock").apply {
            acquire(30 * 60 * 1000L) // 30 minutes safe duration or acquire indefinitely
        }
        
        startTimerLoop()
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val database = AlarmDatabase.getDatabase(applicationContext)
            val dao = database.alarmDao()
            
            while (isActive) {
                val activeAlarms = dao.getActiveAlarms()
                val subMinuteAlarms = activeAlarms.filter { it.intervalSeconds < 60 }
                
                if (subMinuteAlarms.isEmpty()) {
                    Log.d(TAG, "No sub-minute active alarms, self-stopping service")
                    stopSelf()
                    break
                }
                
                val now = System.currentTimeMillis()
                val localTimeNow = LocalTime.now()
                
                for (alarm in subMinuteAlarms) {
                    val nextTrigger = if (alarm.lastTriggeredTime > 0) {
                        alarm.lastTriggeredTime + alarm.intervalSeconds * 1000
                    } else {
                        // If never run, run it immediately or start interval from now
                        now
                    }
                    
                    if (now >= nextTrigger) {
                        val start = LocalTime.of(alarm.startHour, alarm.startMinute)
                        val end = LocalTime.of(alarm.endHour, alarm.endMinute)
                        
                        val isInside = isTimeInRange(localTimeNow, start, end)
                        
                        // Update last triggered timestamp to trigger it correctly next round
                        val updatedAlarm = alarm.copy(lastTriggeredTime = now)
                        dao.updateAlarm(updatedAlarm)
                        
                        if (isInside) {
                            Log.d(TAG, "Triggering sub-minute alarm ${alarm.id}")
                            AlarmReceiver.triggerAlarmRing(applicationContext, alarm)
                        } else {
                            Log.d(TAG, "Skipping ring for alarm ${alarm.id} as current time $localTimeNow is outside active interval $start to $end")
                        }
                    }
                }
                
                delay(1000) // precise interval tick check
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

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Active Precision Alarms")
            .setContentText("High-frequency alarms scheduled under 60-seconds are running.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Precision Timers Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(30 * 60 * 1000L)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceJob.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock in onDestroy", e)
        }
        Log.d(TAG, "AlarmService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
