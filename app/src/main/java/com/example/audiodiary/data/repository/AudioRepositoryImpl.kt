package com.example.audiodiary.data.repository

import com.example.audiodiary.data.local.RecordDao
import com.example.audiodiary.domain.model.AudioRecord
import com.example.audiodiary.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow

class AudioRepositoryImpl(
    val recordDao: RecordDao
) : AudioRepository {
    override suspend fun upsert(record: AudioRecord) {
        recordDao.upsert(record)
    }

    override suspend fun delete(record: AudioRecord) {
        recordDao.delete(record)
    }

    override fun getAllRecords(): Flow<List<AudioRecord>> {
        return recordDao.getAllRecords()
    }

    override fun getRecordById(id: Long): Flow<AudioRecord> {
        return recordDao.getRecordById(id)
    }
}