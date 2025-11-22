package com.example.audiodiary.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.audiodiary.domain.model.AudioRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Upsert
    suspend fun upsert(record: AudioRecord)

    @Delete
    suspend fun delete(record: AudioRecord)

    @Query("SELECT * FROM `AudioRecord` ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AudioRecord>>

    @Query("SELECT * FROM `AudioRecord` WHERE id=:id")
    fun getRecordById(id: Long): Flow<AudioRecord>
}