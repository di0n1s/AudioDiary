package com.example.audiodiary.presentation.audioList

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiodiary.data.repository.AudioRepositoryImpl
import com.example.audiodiary.domain.model.AudioRecord
import com.example.audiodiary.domain.model.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AudioListViewModel(
    private val audioRepository: AudioRepositoryImpl
) : ViewModel() {
    val timelineItems: StateFlow<List<TimelineItem>> = audioRepository.getAllRecords().map { records ->
        val items = mutableListOf<TimelineItem>()

        val groupByDate = records.groupBy {
            dateFormatter(it.timestamp)
        }

        groupByDate.forEach { (dateString, recordsInGroup) ->
            items.add(TimelineItem.Header(dateString))

            recordsInGroup.forEach { record ->
                items.add(TimelineItem.Audio(record))
            }
        }

        items
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun deleteRecord(record: AudioRecord){
        viewModelScope.launch(Dispatchers.IO) {
            audioRepository.delete(record)

            if(record.filePath.startsWith("file://")){
                try {
                    val file = File(record.filePath.toUri().path!!)
                    if(file.exists()){
                        file.delete()
                    }
                }catch (e: Exception){
                    e.printStackTrace()
                }
            }
        }
    }

    private fun dateFormatter(timestamp: Long): String{
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
            .toLocalDate()

        return date.format(
            DateTimeFormatter.ofPattern(
                "EEEE, MMMM d, yyyy",
                Locale.getDefault()
            )
        )
    }
}