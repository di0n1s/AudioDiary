package com.example.audiodiary.presentation.audioList

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.example.audiodiary.domain.model.AudioRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.Boolean

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentRecordId: Long? = null,
    val currentPosition: Int = 0,
    val totalDuration: Int = 0
)

class AudioPlayerService(
    private val context: Context,
    private val scope: CoroutineScope
){
    private var mediaPlayer: MediaPlayer? = null
    private val tag = "AudioPlayerService"

    private var progressUpdateJob: Job? = null

    private var _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    fun onPlayPause(record: AudioRecord){
        Log.d(tag, "onPlayPause recordId=${record.id}")
        val currentState = _playbackState.value

        if(currentState.currentRecordId == record.id){
            // If player was released after completion, start the track again
            if (mediaPlayer == null) {
                startNewTrack(record)
                return
            }
            if(currentState.isPlaying){
                pause()
            }else{
                play()
            }
        }else{
            stop()
            startNewTrack(record)
        }
    }

    private fun startNewTrack(record: AudioRecord) {
        try {
            val uri = record.filePath.toUri()
            val cr = context.contentResolver
            Log.d(tag, "startNewTrack uri=$uri")

            mediaPlayer = MediaPlayer().apply {
                setOnErrorListener { _,  what, extra ->
                    Log.e(tag, "MediaPlayer error what=$what extra=$extra")
                    Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show()
                    stop()
                    true
                }
                setOnPreparedListener {
                    Log.d(tag, "onPrepared duration=$duration")
                    _playbackState.value = PlaybackState(
                        isPlaying = true,
                        currentRecordId = record.id,
                        currentPosition = 0,
                        totalDuration = duration
                    )
                    start()
                    startProgressUpdater()
                }
                setOnCompletionListener {
                    Log.d(tag, "onCompletion")
                    complete()
                }

                val afd = try { cr.openAssetFileDescriptor(uri, "r") } catch (t: Throwable) { null }
                if (afd != null) {
                    Log.d(tag, "Using AssetFileDescriptor for data source")
                    afd.use {
                        setDataSource(it.fileDescriptor, it.startOffset, it.length)
                    }
                    prepareAsync()
                } else {
                    Log.w(tag, "AFD is null, copying to internal file as fallback")
                    val localFile = copyToInternalFile(uri)
                    Log.d(tag, "Fallback local path=${localFile.absolutePath}")
                    setDataSource(localFile.absolutePath)
                    prepareAsync()
                }
            }
        }catch (e: Exception){
            Log.e(tag, "startNewTrack exception", e)
            stop()
        }
    }

    private fun copyToInternalFile(uri: Uri): File {
        val dst = File(context.filesDir, "audio_${System.currentTimeMillis()}.m4a")
        context.contentResolver.openInputStream(uri).use { inS ->
            dst.outputStream().use { outS ->
                inS?.copyTo(outS)
            }
        }
        return dst
    }

    private fun stop() {
        progressUpdateJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        _playbackState.value = PlaybackState()
    }

    private fun complete(){
        // Capture current record id before releasing
        progressUpdateJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // Reset to default state so UI immediately resets and next tap starts fresh
        _playbackState.value = PlaybackState()
    }

    fun release(){
        stop()
    }

    fun seekTo(position: Int){
        Log.d(tag, "seekTo position=$position")
        mediaPlayer?.seekTo(position)

        _playbackState.update { it.copy(currentPosition = position) }
    }

    private fun play() {
        mediaPlayer?.start()
        _playbackState.update { it.copy(isPlaying = true) }
        startProgressUpdater()
    }

    private fun startProgressUpdater() {
        progressUpdateJob?.cancel()

        progressUpdateJob = scope.launch(Dispatchers.Main) {
            while (mediaPlayer?.isPlaying == true){
                val currentPosition = mediaPlayer?.currentPosition ?: 0
                //Log.d(tag, "progress=$currentPosition")

                _playbackState.update { it.copy(currentPosition = currentPosition) }

                delay(500)
            }
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _playbackState.update { it.copy(isPlaying = false) }
        progressUpdateJob?.cancel()
    }
}