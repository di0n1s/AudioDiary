package com.example.audiodiary.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AudioRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val timestamp: Long,
    val duration: Long
)
