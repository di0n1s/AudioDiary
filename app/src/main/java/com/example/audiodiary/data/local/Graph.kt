package com.example.audiodiary.data.local

import android.content.Context
import androidx.room.Room
import com.example.audiodiary.data.repository.AudioRepositoryImpl


//object Graph {
//    lateinit var database: AudioDatabase
//
//    val audioRepository by lazy {
//        AudioRepositoryImpl(database.recordDao)
//    }
//
//    fun provide(context: Context){
//        database = Room.databaseBuilder(
//            context = context,
//            klass = AudioDatabase::class.java,
//            name = "audio_db"
//        ).fallbackToDestructiveMigration().build()
//    }
//}