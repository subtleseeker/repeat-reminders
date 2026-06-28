package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlarmDatabase
import com.example.data.RepeatAlarm
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*

class RingActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private var alarmItem: RepeatAlarm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure system flags to display over lockscreen and force turn screen on in Pixel 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        val alarmId = intent.getLongExtra("ALARM_ID", -1L)

        // Initialize vibration service
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        activityScope.launch {
            if (alarmId != -1L) {
                val db = AlarmDatabase.getDatabase(applicationContext)
                alarmItem = db.alarmDao().getAlarmById(alarmId)
            }
            
            // Generate fallback if not found
            val alarm = alarmItem ?: RepeatAlarm(name = "Repeat Alarm", intervalSeconds = 10)

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val ringerMode = audioManager.ringerMode

            // Alarm mode: rings even if silent/vibrate. Ring mode: respects system settings.
            val shouldPlaySound = if (alarm.ringMode == "Ring") {
                ringerMode == AudioManager.RINGER_MODE_NORMAL
            } else {
                true // Alarm mode always plays sound
            }

            val shouldVibrate = if (alarm.ringMode == "Ring") {
                ringerMode == AudioManager.RINGER_MODE_NORMAL || ringerMode == AudioManager.RINGER_MODE_VIBRATE
            } else {
                true // Alarm mode always vibrates if configured
            }

            // Play music & activate vibration based on conditions
            if (shouldPlaySound) {
                startPlayingSound(alarm.soundUri, alarm.ringMode)
            }
            if (shouldVibrate) {
                startVibrating(alarm.vibrationPattern)
            }

            setContent {
                MyApplicationTheme {
                    RingScreen(
                        alarmName = alarm.name.ifEmpty { "Repeat Alarm" },
                        intervalString = alarm.getIntervalString(),
                        soundName = alarm.soundName,
                        vibrationPattern = alarm.vibrationPattern,
                        ringMode = alarm.ringMode,
                        onDismiss = {
                            dismissAlarm()
                        }
                    )
                }
            }

            // Enforce screen timeout after 5 seconds irrespective of device sleep configuration
            delay(5000)
            dismissAlarm()
        }
    }

    private fun startPlayingSound(soundUriString: String?, ringMode: String) {
        try {
            val uri = if (!soundUriString.isNullOrEmpty()) {
                Uri.parse(soundUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RingActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (ringMode == "Ring") AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("RingActivity", "Failed to start sound, using fallback default alarm", e)
            try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@RingActivity, defaultUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(if (ringMode == "Ring") AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                Log.e("RingActivity", "Double tone error fallback", ex)
            }
        }
    }

    private fun startVibrating(vibrationPattern: String) {
        if (vibrationPattern == "None") return
        val vibratorRef = vibrator ?: return

        val pattern = when (vibrationPattern) {
            "Standard" -> longArrayOf(0, 500, 500)
            "Pulse" -> longArrayOf(0, 200, 200)
            "Tick-Tock" -> longArrayOf(0, 100, 900)
            else -> longArrayOf(0, 500, 500)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibratorRef.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibratorRef.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("RingActivity", "Vibration starting failed", e)
        }
    }

    private fun dismissAlarm() {
        stopPlayingAndVibrating()
        // Cancel persistent ringing notification too on dismiss
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val alarmId = intent.getLongExtra("ALARM_ID", -1L)
            if (alarmId != -1L) {
                notificationManager.cancel(alarmId.toInt())
            }
        } catch (e: Exception) {
            Log.e("RingActivity", "Failed to cancel notification", e)
        }
        finish()
    }

    private fun stopPlayingAndVibrating() {
        activityScope.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w("RingActivity", "MediaPlayer stop/release failure")
        }

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w("RingActivity", "ToneGenerator release failure")
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w("RingActivity", "Vibrator cancel failure")
        }
    }

    override fun onDestroy() {
        stopPlayingAndVibrating()
        super.onDestroy()
    }
}

@Composable
fun RingScreen(
    alarmName: String,
    intervalString: String,
    soundName: String,
    vibrationPattern: String,
    ringMode: String,
    onDismiss: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF1C1B1F)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1C1B1F)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF313033),
                    border = BorderStroke(1.5.dp, Color(0xFF49454F)),
                    modifier = Modifier.size(110.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Active Ringing Icon",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = alarmName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Recurring every $intervalString",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD0BCFF)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2B2930)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF49454F)),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Mode: $ringMode | Sound: $soundName | Vibe: $vibrationPattern",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCAC4D0),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(54.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEADDFF),
                        contentColor = Color(0xFF21005D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(72.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "DISMISS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Timeout limit: 5 seconds...",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFCAC4D0).copy(alpha = 0.6f)
                )
            }
        }
    }
}
