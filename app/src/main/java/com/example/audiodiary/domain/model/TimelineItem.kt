package com.example.audiodiary.domain.model

sealed class TimelineItem {
    data class Audio(val record: AudioRecord): TimelineItem(){
        val id: Long = record.id
    }

    data class Header(val title: String): TimelineItem(){
        val id: String = title
    }
}