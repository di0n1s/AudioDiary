package com.example.audiodiary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.audiodiary.domain.model.AudioRecord

@Database(
    entities = [AudioRecord::class],
    version = 1
)
abstract class AudioDatabase : RoomDatabase() {
    abstract val recordDao: RecordDao
}