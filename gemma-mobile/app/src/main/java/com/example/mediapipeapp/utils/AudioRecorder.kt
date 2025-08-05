package com.example.mediapipeapp.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null
    
    companion object {
        private const val TAG = "AudioRecorder"
    }
    
    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return null
        }
        
        try {
            // Create output file
            outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(outputFile?.absolutePath)
                
                prepare()
                start()
                
                isRecording = true
                Log.d(TAG, "Recording started: ${outputFile?.absolutePath}")
            }
            
            return outputFile
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            cleanup()
            return null
        }
    }
    
    fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }
        
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            val file = outputFile
            Log.d(TAG, "Recording stopped: ${file?.absolutePath}, size: ${file?.length()} bytes")
            file
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            cleanup()
            null
        }
    }
    
    fun cancelRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling recording", e)
        }
        
        cleanup()
        
        // Delete the file if it exists
        outputFile?.let { file ->
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted canceled recording file")
            }
        }
    }
    
    private fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        outputFile = null
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun getCurrentFile(): File? = outputFile
}