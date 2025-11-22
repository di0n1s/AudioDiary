package com.example.audiodiary

import android.app.Application
import com.example.audiodiary.di.AppContainer

class AudioDiaryApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}