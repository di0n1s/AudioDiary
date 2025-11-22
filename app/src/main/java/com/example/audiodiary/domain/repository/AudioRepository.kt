package com.example.audiodiary.domain.repository

import com.example.audiodiary.data.local.RecordDao
import com.example.audiodiary.domain.model.AudioRecord
import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    suspend fun upsert(record: AudioRecord)
    suspend fun delete(record: AudioRecord)
    fun getAllRecords(): Flow<List<AudioRecord>>
    fun getRecordById(id: Long): Flow<AudioRecord>
}