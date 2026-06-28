package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM repeat_alarms ORDER BY id DESC")
    fun getAllAlarmsFlow(): Flow<List<RepeatAlarm>>

    @Query("SELECT * FROM repeat_alarms WHERE isActive = 1")
    suspend fun getActiveAlarms(): List<RepeatAlarm>

    @Query("SELECT * FROM repeat_alarms")
    suspend fun getAllAlarms(): List<RepeatAlarm>

    @Query("SELECT * FROM repeat_alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): RepeatAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: RepeatAlarm): Long

    @Update
    suspend fun updateAlarm(alarm: RepeatAlarm)

    @Delete
    suspend fun deleteAlarm(alarm: RepeatAlarm)
}
