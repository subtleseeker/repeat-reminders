package com.example.data

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {
    val allAlarms: Flow<List<RepeatAlarm>> = alarmDao.getAllAlarmsFlow()

    suspend fun getActiveAlarms(): List<RepeatAlarm> = alarmDao.getActiveAlarms()

    suspend fun getAllAlarmsList(): List<RepeatAlarm> = alarmDao.getAllAlarms()

    suspend fun getAlarmById(id: Long): RepeatAlarm? = alarmDao.getAlarmById(id)

    suspend fun insert(alarm: RepeatAlarm): Long = alarmDao.insertAlarm(alarm)

    suspend fun update(alarm: RepeatAlarm) = alarmDao.updateAlarm(alarm)

    suspend fun delete(alarm: RepeatAlarm) = alarmDao.deleteAlarm(alarm)
}
