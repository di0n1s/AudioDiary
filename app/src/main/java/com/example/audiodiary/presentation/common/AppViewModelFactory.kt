package com.example.audiodiary.presentation.common

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audiodiary.di.AppContainer
import com.example.audiodiary.presentation.addAudio.AddAudioViewModel
import com.example.audiodiary.presentation.audioList.AudioListViewModel

class AppViewModelFactory(
    private val application: Application,
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when{
            modelClass.isAssignableFrom(AddAudioViewModel::class.java) ->
                AddAudioViewModel(application, appContainer.audioRepository) as T

            modelClass.isAssignableFrom(AudioListViewModel::class.java) ->
                AudioListViewModel(appContainer.audioRepository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel")
        }
    }
}