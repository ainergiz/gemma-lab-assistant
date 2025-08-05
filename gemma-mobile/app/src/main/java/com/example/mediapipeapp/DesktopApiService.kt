package com.example.mediapipeapp

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GenerateRequest(
    val prompt: String,
    val prompt_name: String? = "default",
    val max_tokens: Int = 512,
    val temperature: Double = 1.0,
    val top_k: Int = 64,
    val top_p: Double = 0.95,
    val image_base64: String? = null,
    val audio_base64: String? = null
)

data class GenerateResponse(
    val generated_text: String,
    val prompt: String,
    val response_time_ms: Long? = null,
    val tokens_per_second: Double? = null,
    val model_info: String? = null
)

data class HealthResponse(
    val status: String,
    val vlm_loaded: Boolean,
    val model_name: String?,
    val last_error: String?
)

data class StreamingToken(
    val token: String,
    val isComplete: Boolean = false,
    val error: String? = null
)

class DesktopApiService(private val baseUrl: String = "http://192.168.0.33:8000") {
    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    suspend fun checkHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/vlm/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val healthResponse = gson.fromJson(responseBody, HealthResponse::class.java)
                Result.success(healthResponse)
            } else {
                Result.failure(IOException("Health check failed: ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateText(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Double = 1.0,
        topK: Int = 64,
        topP: Double = 0.95
    ): Result<GenerateResponse> = withContext(Dispatchers.IO) {
        try {
            val requestData = GenerateRequest(
                prompt = prompt,
                max_tokens = maxTokens,
                temperature = temperature,
                top_k = topK,
                top_p = topP
            )

            val requestBody = gson.toJson(requestData).toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/vlm/generate")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val generateResponse = gson.fromJson(responseBody, GenerateResponse::class.java)
                Result.success(generateResponse)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(IOException("Generation failed: ${response.code} $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/vlm/")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    fun generateTextStreaming(
        prompt: String,
        promptName: String = "default",
        maxTokens: Int = 512,
        temperature: Double = 1.0,
        topK: Int = 64,
        topP: Double = 0.95,
        imageBase64: String? = null,
        audioBase64: String? = null
    ): Flow<StreamingToken> = callbackFlow {
        try {
            val requestData = GenerateRequest(
                prompt = prompt,
                prompt_name = promptName,
                max_tokens = maxTokens,
                temperature = temperature,
                top_k = topK,
                top_p = topP,
                image_base64 = imageBase64,
                audio_base64 = audioBase64
            )

            val requestBody = gson.toJson(requestData).toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/vlm/generate/stream")
                .post(requestBody)
                .build()

            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    trySendBlocking(StreamingToken("", isComplete = true, error = e.message))
                    close()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        trySendBlocking(StreamingToken("", isComplete = true, error = "HTTP ${response.code}"))
                        close()
                        return
                    }

                    response.body?.source()?.let { source ->
                        try {
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                
                                if (line.startsWith("data: ")) {
                                    val data = line.substring(6)
                                    
                                    when {
                                        data == "[DONE]" -> {
                                            trySendBlocking(StreamingToken("", isComplete = true))
                                            break
                                        }
                                        data.isNotEmpty() -> {
                                            trySendBlocking(StreamingToken(data))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            trySendBlocking(StreamingToken("", isComplete = true, error = e.message))
                        } finally {
                            response.close()
                            close()
                        }
                    }
                }
            })
            
            awaitClose { call.cancel() }
            
        } catch (e: Exception) {
            trySendBlocking(StreamingToken("", isComplete = true, error = e.message))
            close()
        }
    }
}