package com.example.audiodiary.presentation.addAudio

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    data class Finished(val filePath: String) : RecordingState()
}