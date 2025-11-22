package com.example.audiodiary.presentation.addAudio

import android.annotation.SuppressLint
import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiodiary.domain.model.AudioRecord
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.example.audiodiary.data.repository.AudioRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

class AddAudioViewModel(
    application: Application,
    private val audioRepository: AudioRepositoryImpl
) : AndroidViewModel(application) {

    private var _permanentUriForAudio: String? = null
    val permanentUriForAudio: String? get() = _permanentUriForAudio

    private var _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState = _recordingState.asStateFlow()

    private var _amplitude = MutableStateFlow(0)
    val amplitude = _amplitude.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var progressBarJob: Job? = null
    private var currentFilePath: String? = null

    fun savePermanentUriForAudio(
        uri: String
    ){
        _permanentUriForAudio = uri
    }

    fun upsertAudioRecord(
        title: String,
        filePathString: String,
        timestamp: Long
    ){
        val uri = filePathString.toUri()

        viewModelScope.launch(Dispatchers.IO) {
            audioRepository.upsert(
                AudioRecord(
                    title = title,
                    filePath = filePathString,
                    timestamp = timestamp,
                    duration = getAudioDuration(uri)
                )
            )
        }
    }

    fun startRecording(){
        val context = getApplication<Application>().applicationContext
        val fileName = "audio_${System.currentTimeMillis()}.m4a"
        val file = File(context.filesDir, fileName)
        currentFilePath = file.absolutePath

        mediaRecorder = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            MediaRecorder(context)
        }else{
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)

                setOutputFile(currentFilePath)

                prepare()
                start()

                _recordingState.value = RecordingState.Recording

                startProgressUpdater()
            }catch (e: Exception){
                e.printStackTrace()
                cleanupPendingRecording()
            }
        }
    }

    fun stopRecording(){
        progressBarJob?.cancel()
        _amplitude.value = 0

        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null

        currentFilePath?.let {
            _recordingState.value = RecordingState.Finished(it)
        }
    }

    fun resetRecordingState(){
        _recordingState.value = RecordingState.Idle
        _permanentUriForAudio = null
        currentFilePath = null
    }

    fun cleanupPendingRecording(){
        currentFilePath?.let { path ->
            try {
                File(path).delete()
            }catch (e: Exception){
                e.printStackTrace()
            }
        }

        resetRecorder()
    }

    private fun resetRecorder() {
        progressBarJob?.cancel()
        _amplitude.value = 0

        try {
            mediaRecorder?.stop()
        }catch (e: Exception){
            e.printStackTrace()
        }finally {
            try {
                mediaRecorder?.release()
            }catch (e: Exception){
                e.printStackTrace()
            }
            mediaRecorder = null
        }

        currentFilePath = null
        _recordingState.value = RecordingState.Idle

    }

    private fun startProgressUpdater() {
        progressBarJob?.cancel()
        progressBarJob = viewModelScope.launch {
            while (isActive){
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                _amplitude.value = amplitude
                delay(150)
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private suspend fun getAudioDuration(uri: Uri): Long = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)

            val durationString = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )

            val durationLong = durationString?.toLongOrNull() ?: 0L

            return@withContext durationLong

        } catch (t: Throwable){
            t.printStackTrace()
            return@withContext 0L
        }finally {
            retriever.release()
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupPendingRecording()
    }
}