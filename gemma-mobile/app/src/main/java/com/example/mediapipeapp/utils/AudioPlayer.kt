package com.example.mediapipeapp.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var playingState = false
    private var currentAudioFile: File? = null
    
    companion object {
        private const val TAG = "AudioPlayer"
    }
    
    fun play(audioFile: File, onCompletion: () -> Unit = {}, onError: (String) -> Unit = {}) {
        try {
            // Stop any current playback
            stop()
            
            if (!audioFile.exists()) {
                onError("Audio file not found")
                return
            }
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener { player ->
                    player.start()
                    playingState = true
                    currentAudioFile = audioFile
                    Log.d(TAG, "Started playing: ${audioFile.name}")
                }
                setOnCompletionListener {
                    stop()
                    onCompletion()
                    Log.d(TAG, "Completed playing: ${audioFile.name}")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    onError("Playback error: $what")
                    true
                }
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            onError("Failed to play audio: ${e.message}")
        }
    }
    
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
        
        mediaPlayer = null
        playingState = false
        currentAudioFile = null
    }
    
    fun pause() {
        try {
            mediaPlayer?.pause()
            playingState = false
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }
    
    fun resume() {
        try {
            mediaPlayer?.start()
            playingState = true
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }
    
    fun isPlaying(): Boolean = playingState && mediaPlayer?.isPlaying == true
    
    fun getCurrentFile(): File? = currentAudioFile
    
    fun cleanup() {
        stop()
    }
}