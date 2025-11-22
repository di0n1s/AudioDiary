package com.example.audiodiary.presentation.addAudio

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.findNavController
import com.example.audiodiary.R
import com.example.audiodiary.databinding.FragmentAddAudioBinding
import android.util.Log
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.audiodiary.AudioDiaryApp
import com.example.audiodiary.databinding.DialogRecordAudioBinding
import com.example.audiodiary.presentation.common.AppViewModelFactory
import kotlinx.coroutines.launch
import java.io.File

class AddAudioFragment : Fragment() {

    private var _binding: FragmentAddAudioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddAudioViewModel by viewModels {
        val application = requireActivity().application as AudioDiaryApp

        AppViewModelFactory(
            application = application,
            appContainer = application.appContainer
        )
    }

    private var flashingAnimation: Animation? = null

    private val pickAudioLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()){uri ->
            if(uri != null){
                takePermanentPermission(uri)
            }else{
                binding.llButtons.visibility = View.VISIBLE
                binding.clFileUploaded.visibility = View.INVISIBLE
                Toast.makeText(requireContext(), "No file selected", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted ->
            if(isGranted){
                showRecordingDialog()
            }else{
                Toast.makeText(requireContext(), "Permission denied to record audio", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddAudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        flashingAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.flashing_red_dot_anim)

        binding.toolBar.title = "Add Audio"

        binding.toolBar.setNavigationIcon(R.drawable.arrow_back)

        binding.toolBar.setNavigationOnClickListener {
            viewModel.cleanupPendingRecording()
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cleanupPendingRecording()
            findNavController().navigate(R.id.action_addAudioFragment_to_audioListFragment)
        }

        binding.btnAddEntry.setOnClickListener {
            if(viewModel.permanentUriForAudio == null){
                binding.tvAudioHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                return@setOnClickListener
            }else{
                binding.tvAudioHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark))
            }

            if(binding.etTitle.text.isNullOrEmpty()){
                binding.tilTitleInput.error = "Title should not be empty"
                return@setOnClickListener
            }else{
                binding.tilTitleInput.error = null
            }
            viewModel.upsertAudioRecord(
                title = binding.etTitle.text?.toString()?.trim()!!,
                filePathString = viewModel.permanentUriForAudio!!,
                timestamp = System.currentTimeMillis()
            )

            viewModel.resetRecordingState()

            findNavController().navigate(R.id.action_addAudioFragment_to_audioListFragment)
        }

        binding.btnUploadFile.setOnClickListener {
            openFilePicker()
        }

        binding.btnRecordAudio.setOnClickListener {
            checkRequestPermission()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                launch {
                    viewModel.recordingState.collect { state ->
                        updateUiFroRecordingState(state)
                    }
                }
            }
        }

    }

    private fun updateUiFroRecordingState(state: RecordingState){
        when(state){
            is RecordingState.Finished -> {
                val fileUri = Uri.fromFile(File(state.filePath)).toString()
                viewModel.savePermanentUriForAudio(fileUri)

                binding.llButtons.visibility = View.INVISIBLE
                binding.clFileUploaded.visibility = View.VISIBLE
                binding.tvAudioUploaded.text = "Record uploaded: $fileUri"
            }
            RecordingState.Idle -> {
                binding.llButtons.visibility = View.VISIBLE
                binding.clFileUploaded.visibility = View.INVISIBLE
            }
            RecordingState.Recording -> {

                binding.llButtons.visibility = View.VISIBLE
                binding.clFileUploaded.visibility = View.INVISIBLE
            }
        }
    }

    private fun openFilePicker(){
        pickAudioLauncher.launch(
            arrayOf(
                "audio/mpeg",
                "audio/wav",
                "audio/x-m4a",
                "audio/*"
            )
        )
    }

    private fun takePermanentPermission(uri: Uri){
        val contentResolver = requireActivity().contentResolver
        val takeFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION

        try {
            Log.d("AddAudioFragment", "takePermanentPermission uri=$uri")
            contentResolver.takePersistableUriPermission(
                uri, takeFlag)

            requireContext().contentResolver.openAssetFileDescriptor(uri, "r")?.close()

            val uriString = uri.toString()

            viewModel.savePermanentUriForAudio(uriString)

            requireActivity().runOnUiThread {
                binding.llButtons.visibility = View.INVISIBLE
                binding.clFileUploaded.visibility = View.VISIBLE
                binding.tvAudioUploaded.text = "Audio uploaded: $uriString"
            }

            Log.d("AddAudioFragment", "Permission granted successfully, UI updated")
        }catch (e: SecurityException){
            e.printStackTrace()
            Toast.makeText(requireContext(), "Could not get permanent permission for this file", Toast.LENGTH_SHORT).show()
            requireActivity().runOnUiThread {
                binding.llButtons.visibility = View.VISIBLE
                binding.clFileUploaded.visibility = View.INVISIBLE
            }
        }catch (e: Exception){
            e.printStackTrace()
            Toast.makeText(requireContext(), "Don't have access to audio", Toast.LENGTH_SHORT).show()
            requireActivity().runOnUiThread{
                binding.llButtons.visibility = View.VISIBLE
                binding.clFileUploaded.visibility = View.INVISIBLE
            }
        }
    }

    private fun checkRequestPermission(){
        if(ContextCompat.checkSelfPermission(
            requireContext(),
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        ){
            showRecordingDialog()
        }else{
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showRecordingDialog(){
        val binding = DialogRecordAudioBinding.inflate(LayoutInflater.from(requireContext()))

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)

        val dialog = builder.create()

        val amplitudeJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.amplitude.collect { amp ->
                binding.pbAmplitude.progress = amp
            }
        }

        val stateJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recordingState.collect { state ->
                when(state){
                    is RecordingState.Finished, RecordingState.Idle -> {
                        binding.btnStartStopRecording.text = "Start"
                        binding.ivRedDot.visibility = View.INVISIBLE
                        binding.ivRedDot.clearAnimation()
                    }
                    RecordingState.Recording -> {
                        binding.btnStartStopRecording.text = "Stop"
                        binding.ivRedDot.visibility = View.VISIBLE
                        binding.ivRedDot.startAnimation(flashingAnimation)
                    }
                }
            }
        }

        binding.btnStartStopRecording.setOnClickListener {
            if(viewModel.recordingState.value is RecordingState.Recording){
                viewModel.stopRecording()
                dialog.dismiss()
            }else{
                viewModel.startRecording()
            }
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cleanupPendingRecording()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            amplitudeJob.cancel()
            stateJob.cancel()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}