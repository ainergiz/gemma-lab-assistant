package com.example.mediapipeapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class InferenceMode {
    MOBILE, DESKTOP
}

class PreferencesRepository(private val context: Context) {
    private object PreferencesKeys {
        val INFERENCE_MODE = stringPreferencesKey("inference_mode")
        val DESKTOP_SERVER_URL = stringPreferencesKey("desktop_server_url")
        val AUTO_DETECT_DESKTOP = booleanPreferencesKey("auto_detect_desktop")
        val MOBILE_GPU_ACCELERATION = booleanPreferencesKey("mobile_gpu_acceleration")
    }

    val inferenceMode: Flow<InferenceMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[PreferencesKeys.INFERENCE_MODE] ?: InferenceMode.MOBILE.name
        try {
            InferenceMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            InferenceMode.MOBILE
        }
    }

    val desktopServerUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DESKTOP_SERVER_URL] ?: "http://192.168.0.33:8000"
    }

    val autoDetectDesktop: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_DETECT_DESKTOP] ?: false
    }

    val mobileGpuAcceleration: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MOBILE_GPU_ACCELERATION] ?: false // Default to CPU (false)
    }

    suspend fun setInferenceMode(mode: InferenceMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INFERENCE_MODE] = mode.name
        }
    }

    suspend fun setDesktopServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DESKTOP_SERVER_URL] = url
        }
    }

    suspend fun setAutoDetectDesktop(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_DETECT_DESKTOP] = enabled
        }
    }

    suspend fun setMobileGpuAcceleration(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOBILE_GPU_ACCELERATION] = enabled
        }
    }
}