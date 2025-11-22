package com.example.audiodiary.presentation.audioList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.audiodiary.R
import com.example.audiodiary.databinding.ItemAudioRecordBinding
import com.example.audiodiary.databinding.ItemDateHeaderBinding
import com.example.audiodiary.domain.model.AudioRecord
import com.example.audiodiary.domain.model.TimelineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class AudioTimelineAdapter(
    val playerService: AudioPlayerService,
    val onDelete: (AudioRecord) -> Unit
) :
ListAdapter<TimelineItem, RecyclerView.ViewHolder>(TimelineDiffCallback()){

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_AUDIO = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when(getItem(position)){
            is TimelineItem.Header -> VIEW_TYPE_HEADER
            is TimelineItem.Audio -> VIEW_TYPE_AUDIO
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when(viewType){
            VIEW_TYPE_HEADER -> {
                val binding = ItemDateHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_AUDIO -> {
                val binding = ItemAudioRecordBinding.inflate(inflater, parent, false)
                AudioViewHolder(binding, playerService)
            }
            else -> throw IllegalArgumentException("Unknown viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        when(holder){
            is HeaderViewHolder -> holder.bind(item as TimelineItem.Header)
            is AudioViewHolder -> holder.bind(item as TimelineItem.Audio)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemDateHeaderBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(item: TimelineItem.Header){
            binding.tvDateHeader.text = item.title
        }
    }

    inner class AudioViewHolder(
        private val binding: ItemAudioRecordBinding,
        private val playerService: AudioPlayerService
    ) : RecyclerView.ViewHolder(binding.root){

        private var currentAudioItem: TimelineItem.Audio? = null
        private var stateCollectorJob: Job? = null

        init {
            binding.btnPlayPause.setOnClickListener {
                currentAudioItem?.let {
                    playerService.onPlayPause(it.record)
                }
            }

            binding.btnDelete.setOnClickListener {
                currentAudioItem?.let {
                    //TODO first check if it is in our files or in external storage(so it is uri content or file) and if it is a file
                    //TODO delete the file and in roomDB (check AI is it needed)
                    onDelete(it.record)
                    playerService.release()
                }
            }

            binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if(fromUser){
                        binding.tvCurrentTime.text = formatDuration(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    val item = currentAudioItem ?: return
                    val state = playerService.playbackState.value
                    if(state.currentRecordId == item.id){
                        playerService.pause()
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val item = currentAudioItem ?: return
                    val state = playerService.playbackState.value
                    if(state.currentRecordId == item.record.id){
                        seekBar?.let {
                            playerService.seekTo(it.progress)
                        }
                        playerService.onPlayPause(item.record)
                    }
                }

            })
        }

        fun bind(item: TimelineItem.Audio){
            currentAudioItem = item

            binding.tvTitle.text = item.record.title
            binding.tvAudioDuration.text = formatDuration(item.record.duration)
            binding.tvTime.text = timestampFormatter(item.record.timestamp)

            stateCollectorJob?.cancel()

            // Initialize UI from current state immediately
            applyState(playerService.playbackState.value, item)

            val lifecycleOwner = itemView.findViewTreeLifecycleOwner()
            if(lifecycleOwner != null){
                startCollecting(lifecycleOwner.lifecycleScope, item)
            } else {
                itemView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener{
                    override fun onViewAttachedToWindow(v: View) {
                        val owner = itemView.findViewTreeLifecycleOwner()
                        if(owner != null){
                            startCollecting(owner.lifecycleScope, item)
                            itemView.removeOnAttachStateChangeListener(this)
                        }
                    }

                    override fun onViewDetachedFromWindow(v: View) { }
                })
            }
        }

        private fun startCollecting(scope: CoroutineScope, item: TimelineItem.Audio){
            stateCollectorJob?.cancel()
            stateCollectorJob = scope.launch {
                playerService.playbackState.collectLatest { state ->
                    applyState(state, item)
                }
            }
        }

        private fun applyState(state: PlaybackState, item: TimelineItem.Audio){
            val isCurrentTrack = (state.currentRecordId == item.record.id)

            if(isCurrentTrack){
                binding.tvCurrentTime.text = formatDuration(state.currentPosition.toLong())
                binding.seekBar.max = state.totalDuration
                binding.seekBar.progress = state.currentPosition

                if(state.isPlaying){
                    binding.btnPlayPause.setImageResource(R.drawable.pause)
                }else{
                    binding.btnPlayPause.setImageResource(R.drawable.play_button)
                }
            }else{
                binding.tvCurrentTime.text = "00:00"
                binding.seekBar.max = item.record.duration.toInt()
                binding.seekBar.progress = 0
                binding.btnPlayPause.setImageResource(R.drawable.play_button)
            }
        }

        fun timestampFormatter(timestamp: Long): String{
            val date = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())

            return date.format(
                DateTimeFormatter.ofPattern(
                    "HH:mm",
                    Locale.getDefault()
                )
            )
        }

        fun formatDuration(duration: Long): String{
            val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes)

            return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
            )
        }
    }
}

class TimelineDiffCallback : DiffUtil.ItemCallback<TimelineItem>(){
    override fun areItemsTheSame(
        oldItem: TimelineItem,
        newItem: TimelineItem
    ): Boolean {
        return (oldItem is TimelineItem.Header && newItem is TimelineItem.Header && oldItem.id == newItem.id) ||
                (oldItem is TimelineItem.Audio && newItem is TimelineItem.Audio && oldItem.id == newItem.id)
    }

    override fun areContentsTheSame(
        oldItem: TimelineItem,
        newItem: TimelineItem
    ): Boolean {
        return oldItem == newItem
    }

}