package com.example.monthlysms

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY dayOfMonth ASC")
    fun getAll(): LiveData<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules")
    suspend fun getAllOnce(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Int): ScheduleEntity?

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)
}
