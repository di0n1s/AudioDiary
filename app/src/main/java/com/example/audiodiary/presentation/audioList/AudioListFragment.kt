package com.example.audiodiary.presentation.audioList

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.audiodiary.AudioDiaryApp
import com.example.audiodiary.R
import com.example.audiodiary.databinding.FragmentAudioListBinding
import com.example.audiodiary.presentation.common.AppViewModelFactory
import kotlinx.coroutines.launch

class AudioListFragment : Fragment() {

    private var _binding: FragmentAudioListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioListViewModel by viewModels{
        val application = requireActivity().application as AudioDiaryApp

        AppViewModelFactory(
            application = application,
            appContainer = application.appContainer
        )
    }

    private var playerService: AudioPlayerService? = null
    private lateinit var timelineAdapter: AudioTimelineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAudioListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolBar.title = "Your Audios"
        binding.toolBar.inflateMenu(R.menu.list_toolbar_menu)

        playerService = AudioPlayerService(requireContext(), viewLifecycleOwner.lifecycleScope)

        timelineAdapter = AudioTimelineAdapter(
            playerService = playerService!!,
            onDelete = {
                viewModel.deleteRecord(it)
            }
        )
        binding.rvTimelineItemList.adapter = timelineAdapter
        binding.rvTimelineItemList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTimelineItemList.clipToPadding = false

        val menuItem = binding.toolBar.menu.findItem(R.id.action_add_audio)
        val customButton = menuItem.actionView as AppCompatButton

        customButton.setOnClickListener {
            findNavController().navigate(R.id.action_audioListFragment_to_addAudioFragment)
        }

        binding.btnAddFirstAudio.setOnClickListener {
            findNavController().navigate(R.id.action_audioListFragment_to_addAudioFragment)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolBar) { v, insets ->
            val systemBarsTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val typedValue = TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
            val originalHeight = TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
            v.setPadding(v.paddingLeft, systemBarsTop, v.paddingRight, v.paddingBottom)
            v.layoutParams.height = originalHeight + systemBarsTop
            v.requestLayout()
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.rvTimelineItemList) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.timelineItems.collect {  timelineList ->
                    timelineAdapter.submitList(timelineList)

                    if(timelineList.isEmpty()){
                        with(binding){
                            flCircularMicro.visibility = View.VISIBLE
                            tvStartHint.visibility = View.VISIBLE
                            tvNoAudioHint.visibility = View.VISIBLE
                            btnAddFirstAudio.visibility = View.VISIBLE

                            rvTimelineItemList.visibility = View.INVISIBLE
                        }
                    }else{
                        with(binding){
                            flCircularMicro.visibility = View.INVISIBLE
                            tvStartHint.visibility = View.INVISIBLE
                            tvNoAudioHint.visibility = View.INVISIBLE
                            btnAddFirstAudio.visibility = View.INVISIBLE

                            rvTimelineItemList.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        playerService?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playerService?.release()
        playerService = null
        _binding = null
    }
}