package com.example.audiodiary.di

import android.content.Context
import androidx.room.Room
import com.example.audiodiary.data.local.AudioDatabase
import com.example.audiodiary.data.repository.AudioRepositoryImpl
import com.example.audiodiary.domain.repository.AudioRepository

class AppContainer(
    private val context: Context
) {
    private val dataBase = Room.databaseBuilder(
        context = context,
        klass = AudioDatabase::class.java,
        name = "audio_db"
    ).fallbackToDestructiveMigration().build()

    val audioRepository = AudioRepositoryImpl(dataBase.recordDao)
}