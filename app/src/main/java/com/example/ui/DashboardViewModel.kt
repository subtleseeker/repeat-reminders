package com.example.ui

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmRepository
import com.example.data.RepeatAlarm
import com.example.receiver.RepeatAlarmScheduler
import com.example.service.AlarmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: AlarmRepository) : ViewModel() {

    val allAlarms: StateFlow<List<RepeatAlarm>> = repository.allAlarms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _permissionStates = MutableStateFlow(PermissionStatus())
    val permissionStates: StateFlow<PermissionStatus> = _permissionStates.asStateFlow()

    fun updatePermissionStates(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimizationsIgnored = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        val canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val canScheduleExact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        _permissionStates.value = PermissionStatus(
            isBatteryOptimizationsIgnored = isBatteryOptimizationsIgnored,
            canDrawOverlays = canDrawOverlays,
            canScheduleExact = canScheduleExact
        )
    }

    fun syncAlarmsWithSystem(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val alarms = repository.getAllAlarmsList()
            val active = alarms.filter { it.isActive }
            val sub60Count = active.count { it.intervalSeconds < 60 }

            // Cancel any old scheduled alarms
            alarms.forEach {
                RepeatAlarmScheduler.cancelAlarm(context, it.id)
            }

            // Schedule the ones > 60 seconds
            active.forEach { alarm ->
                if (alarm.intervalSeconds >= 60) {
                    RepeatAlarmScheduler.scheduleNextAlarm(context, alarm)
                }
            }

            // Sync Foreground service running state
            if (sub60Count > 0) {
                AlarmService.startService(context)
            } else {
                AlarmService.stopService(context)
            }
        }
    }

    fun toggleAlarmActive(context: Context, alarm: RepeatAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = alarm.copy(isActive = !alarm.isActive, lastTriggeredTime = 0L)
            repository.update(updated)
            syncAlarmsWithSystem(context)
        }
    }

    fun deleteAlarm(context: Context, alarm: RepeatAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(alarm)
            syncAlarmsWithSystem(context)
        }
    }

    fun saveAlarm(context: Context, alarm: RepeatAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            if (alarm.id == 0L) {
                repository.insert(alarm)
            } else {
                repository.update(alarm)
            }
            syncAlarmsWithSystem(context)
        }
    }

    fun quickUpdateAlarmInterval(context: Context, alarm: RepeatAlarm, newIntervalSeconds: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = alarm.copy(intervalSeconds = newIntervalSeconds, lastTriggeredTime = 0L)
            repository.update(updated)
            syncAlarmsWithSystem(context)
        }
    }
}

data class PermissionStatus(
    val isBatteryOptimizationsIgnored: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val canScheduleExact: Boolean = false
)

class DashboardViewModelFactory(private val repository: AlarmRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
