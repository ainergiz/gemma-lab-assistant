package com.example.mediapipeapp

import android.content.Context
import android.util.Log
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.example.mediapipeapp.repository.ChatRepository
import com.example.mediapipeapp.data.entities.toChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "LlmChatViewModel"

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val responseTime: Long? = null,
    val tokensPerSecond: Double? = null,
    val modelLoadTime: Long? = null,
    val memoryUsed: Long? = null,
    val id: Long = timestamp,
    val modelMode: String? = null,
    val structuredData: Any? = null,
    val imageUri: String? = null,  // Local image URI for display
    val imageBase64: String? = null,  // Base64 image data for API
    val audioFile: String? = null,  // Local audio file path for display/playback
    val audioDurationMs: Long? = null  // Audio duration in milliseconds
)

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY
}

enum class DesktopConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class LlmChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val chatRepository: ChatRepository,
    private val promptName: String
) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    // Current conversation tracking
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()
    
    init {
        // Start checking desktop connection when inference mode changes
        // Delay initialization to avoid accessing flows before they're ready
        viewModelScope.launch {
            try {
                // Start monitoring after initial setup
                startDesktopConnectionChecking()
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization: ${e.message}")
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelStatus = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private val modelFileName = "gemma-3n-E2B-it-int4.task"
    private val modelUrl = "https://jobclerkmedia.blob.core.windows.net/models/gemma-3n-E2B-it-int4.task"
    private var currentMobileBackend: String = "CPU" // Track current backend
    
    // Expose preference flows
    val inferenceMode = preferencesRepository.inferenceMode
    val desktopServerUrl = preferencesRepository.desktopServerUrl
    val autoDetectDesktop = preferencesRepository.autoDetectDesktop
    val mobileGpuAcceleration = preferencesRepository.mobileGpuAcceleration
    
    // Desktop connection status
    private val _desktopConnectionStatus = MutableStateFlow(DesktopConnectionStatus.DISCONNECTED)
    val desktopConnectionStatus: StateFlow<DesktopConnectionStatus> = _desktopConnectionStatus.asStateFlow()
    
    // Streaming message state
    private val _streamingMessage = MutableStateFlow("")
    val streamingMessage: StateFlow<String> = _streamingMessage.asStateFlow()
    
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    
    // Desktop API service
    private var desktopApiService: DesktopApiService? = null
    
    // Preference setters
    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch {
            preferencesRepository.setInferenceMode(mode)
        }
    }
    
    fun setDesktopServerUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setDesktopServerUrl(url)
        }
    }
    
    fun setAutoDetectDesktop(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoDetectDesktop(enabled)
        }
    }
    
    fun setMobileGpuAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMobileGpuAcceleration(enabled)
            // Note: Model needs to be re-initialized for this change to take effect
        }
    }
    
    fun dismissMessage(messageId: Long) {
        // Implementation for dismissing messages if needed
    }
    
    // Conversation management methods
    fun loadConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
        viewModelScope.launch {
            chatRepository.getMessagesForConversation(conversationId).collect { dbMessages ->
                _messages.value = dbMessages.map { it.toChatMessage() }
            }
        }
    }
    
    fun startNewConversation() {
        viewModelScope.launch {
            // Immediately clear current messages to prevent cross-conversation contamination
            _messages.value = emptyList()
            _currentConversationId.value = null
            
            val conversationId = chatRepository.createNewConversation("New Chat")
            loadConversation(conversationId)
        }
    }
    
    fun updateConversationTitle(title: String) {
        viewModelScope.launch {
            _currentConversationId.value?.let { conversationId ->
                chatRepository.updateConversationTitle(conversationId, title)
            }
        }
    }
    
    private fun buildConversationContext(currentMessage: String): String {
        // Use messages from current conversation
        val messages = _messages.value
        val contextMessages = messages.takeLast(6) // Last 6 messages for context (3 exchanges)
        
        val contextBuilder = StringBuilder()
        
        // Always add system prompt to maintain consistent role
        val systemPrompt = SystemPrompts.getSystemPrompt(promptName)
        contextBuilder.append("$systemPrompt\n\n")
        
        // Add conversation history
        contextMessages.forEach { message ->
            if (message.text.isNotEmpty()) { // Skip empty placeholder messages
                val role = if (message.isUser) "User" else "Assistant"
                contextBuilder.append("$role: ${message.text}\n\n")
            }
        }
        
        // Add current message
        contextBuilder.append("User: $currentMessage\n\nAssistant:")
        
        return contextBuilder.toString()
    }
    
    // Manual trigger for desktop connection testing
    fun testDesktopConnection() {
        Log.d(TAG, "Manual desktop connection test triggered")
        viewModelScope.launch {
            try {
                updateDesktopApiService()
            } catch (e: Exception) {
                Log.e(TAG, "Error in manual connection test: ${e.message}")
            }
        }
    }
    
    // Desktop connection management
    private fun updateDesktopApiService() {
        viewModelScope.launch {
            try {
                val serverUrl = desktopServerUrl.first()
                desktopApiService = DesktopApiService(serverUrl)
                checkDesktopConnection()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating desktop API service: ${e.message}")
                _desktopConnectionStatus.value = DesktopConnectionStatus.ERROR
            }
        }
    }
    
    private fun checkDesktopConnection() {
        if (desktopApiService == null) {
            updateDesktopApiService()
            return
        }
        
        viewModelScope.launch {
            _desktopConnectionStatus.value = DesktopConnectionStatus.CONNECTING
            
            try {
                val isConnected = desktopApiService?.testConnection() ?: false
                _desktopConnectionStatus.value = if (isConnected) {
                    Log.d(TAG, "Desktop server connected successfully")
                    DesktopConnectionStatus.CONNECTED
                } else {
                    Log.w(TAG, "Desktop server connection failed")
                    DesktopConnectionStatus.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Desktop connection error: ${e.message}")
                _desktopConnectionStatus.value = DesktopConnectionStatus.ERROR
            }
        }
    }
    
    // Call this when server URL changes - override the existing function
    fun setDesktopServerUrlWithConnection(url: String) {
        viewModelScope.launch {
            preferencesRepository.setDesktopServerUrl(url)
            updateDesktopApiService()
        }
    }
    
    // Check desktop connection periodically when in desktop mode
    private suspend fun startDesktopConnectionChecking() {
        try {
            Log.d(TAG, "Starting desktop connection monitoring")
            inferenceMode.collect { mode ->
                if (mode == InferenceMode.DESKTOP) {
                    Log.d(TAG, "Inference mode switched to desktop, checking connection")
                    checkDesktopConnection()
                } else {
                    Log.d(TAG, "Inference mode is mobile, setting desktop status to disconnected")
                    try {
                        _desktopConnectionStatus.value = DesktopConnectionStatus.DISCONNECTED
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting desktop status: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in desktop connection checking: ${e.message}")
            try {
                _desktopConnectionStatus.value = DesktopConnectionStatus.ERROR
            } catch (ex: Exception) {
                Log.e(TAG, "Error setting error status: ${ex.message}")
            }
        }
    }

    fun downloadModel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _modelStatus.value = ModelStatus.DOWNLOADING
                // Store in external storage so it persists between app installs
                val modelDir = File(context.getExternalFilesDir(null), "models")
                modelDir.mkdirs()
                val modelFile = File(modelDir, modelFileName)
                
                // Always delete existing file to ensure fresh download
                if (modelFile.exists()) {
                    Log.d(TAG, "Deleting existing model file...")
                    modelFile.delete()
                }
                
                Log.d(TAG, "Downloading model from: $modelUrl")
                val url = URL(modelUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                
                Log.d(TAG, "Connection response code: ${connection.responseCode}")
                Log.d(TAG, "Final URL after redirects: ${connection.url}")
                val totalBytes = connection.contentLength
                Log.d(TAG, "Total bytes to download: $totalBytes")
                
                // Use known file size if Content-Length is not available
                val expectedFileSize = 2900000000L // ~2.9GB for E2B model
                val fileSizeForProgress = if (totalBytes > 0) totalBytes.toLong() else expectedFileSize
                
                val inputStream = connection.getInputStream()
                val outputStream = modelFile.outputStream()
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            // Calculate progress using known file size
                            val progress = ((downloadedBytes * 100) / fileSizeForProgress).toInt()
                            _downloadProgress.value = progress.coerceIn(0, 100)
                            
                            // Log progress every 100MB for debugging
                            if (downloadedBytes % (100 * 1024 * 1024) == 0L) {
                                Log.d(TAG, "Downloaded: ${downloadedBytes / (1024 * 1024)}MB (${progress}%)")
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Model downloaded successfully")
                Log.d(TAG, "Downloaded file size: ${modelFile.length()} bytes")
                
                // Check if file starts with typical ZIP/model header
                val firstBytes = ByteArray(100)
                modelFile.inputStream().use { it.read(firstBytes) }
                Log.d(TAG, "File header: ${firstBytes.joinToString(" ") { "%02x".format(it) }}")
                Log.d(TAG, "File content preview: ${String(firstBytes).take(100)}")
                
                initializeModel(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model", e)
                _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                withContext(Dispatchers.Main) {
                    addMessage("Failed to download model: ${e.message}", false)
                }
            }
        }
    }

    private suspend fun initializeModel(context: Context) {
        try {
            val modelDir = File(context.getExternalFilesDir(null), "models")
            val modelFile = File(modelDir, modelFileName)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file does not exist")
                _modelStatus.value = ModelStatus.NOT_DOWNLOADED
                return
            }
            
            // Get GPU acceleration preference
            val useGpuAcceleration = preferencesRepository.mobileGpuAcceleration.first()
            
            val backend = if (useGpuAcceleration) {
                Log.d(TAG, "Using GPU acceleration")
                LlmInference.Backend.GPU
            } else {
                Log.d(TAG, "Using CPU for stability")
                LlmInference.Backend.CPU
            }
            
            Log.d(TAG, "Initializing LLM with model: ${modelFile.absolutePath}")
            Log.d(TAG, "Backend: $backend")
            
            // Step 1: Create LlmInferenceOptions with backend based on preference
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setPreferredBackend(backend)
                .setMaxNumImages(0) // No image support in this implementation
                .build()

            // Step 2: Create LlmInference instance
            llmInference = LlmInference.createFromOptions(context, options)
            
            // Step 3: Create session with proper GraphOptions
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(64)
                .setTopP(0.95f)
                .setTemperature(1.0f)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(false) // Disable vision for text-only model
                        .build()
                )
                .build()
                
            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            _modelStatus.value = ModelStatus.READY
            
            
            Log.d(TAG, "LLM initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            _modelStatus.value = ModelStatus.NOT_DOWNLOADED
            withContext(Dispatchers.Main) {
                addMessage("Failed to initialize model: ${e.message}", false)
            }
        }
    }

    fun sendMessage(text: String, imageUri: Uri? = null, audioFile: File? = null, context: Context? = null) {
        viewModelScope.launch {
            val currentMode = inferenceMode.first()
            Log.d(TAG, "Sending message in $currentMode mode: $text, hasImage: ${imageUri != null}, hasAudio: ${audioFile != null}")
            
            if (currentMode == InferenceMode.DESKTOP) {
                sendMessageToDesktopStreaming(text, imageUri, audioFile, context, promptName)
            } else {
                sendMessageToMobile(text, imageUri, audioFile)
            }
        }
    }
    
    private suspend fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to base64: ${e.message}")
            null
        }
    }
    
    private suspend fun fileToBase64(file: File): String? {
        return try {
            withContext(Dispatchers.IO) {
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting file to base64: ${e.message}")
            null
        }
    }
    
    private fun sendMessageToMobile(text: String, imageUri: Uri? = null, audioFile: File? = null) {
        if (_modelStatus.value != ModelStatus.READY) {
            addMessage("Please download the model first.", false)
            return
        }

        // Note: Mobile inference doesn't support images or audio yet with MediaPipe in this implementation
        // Show message that multimedia is only supported in desktop mode
        if (imageUri != null || audioFile != null) {
            val mediaType = when {
                imageUri != null && audioFile != null -> "Image and audio analysis"
                imageUri != null -> "Image analysis"
                audioFile != null -> "Audio analysis"
                else -> "Multimedia analysis"
            }
            addMessage("$mediaType is only available in Desktop mode. Please switch to Desktop mode to analyze multimedia content.", false)
            return
        }

        addMessage(text, true)
        _isLoading.value = true
        addMessage("", false, saveToDatabase = false) // Placeholder for AI response

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var queryTime = 0L
            var inferenceTime = 0L
            
            try {
                llmSession?.let { session ->
                    // Build conversation context from recent messages
                    val conversationContext = buildConversationContext(text)
                    Log.d(TAG, "Starting inference with context length: ${conversationContext.length}")
                    Log.d(TAG, "Context preview: ${conversationContext.take(200)}...")
                    
                    val runtime = Runtime.getRuntime()
                    
                    val queryStart = System.currentTimeMillis()
                    session.addQueryChunk(conversationContext)
                    queryTime = System.currentTimeMillis() - queryStart
                    
                    val inferenceStart = System.currentTimeMillis()
                    var currentResponse = ""
                    
                    session.generateResponseAsync { partialResult, done ->
                        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
                        currentResponse += partialResult
                        val totalTime = System.currentTimeMillis() - startTime
                        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
                        val memoryUsed = memoryAfter - memoryBefore
                        
                        val estimatedTokens = currentResponse.split("\\s+".toRegex()).size
                        val currentInferenceTime = System.currentTimeMillis() - inferenceStart
                        val tokensPerSecond = if (currentInferenceTime > 0) (estimatedTokens * 1000.0) / currentInferenceTime else 0.0

                        viewModelScope.launch(Dispatchers.Main) {
                            val updatedMessage = ChatMessage(
                                text = currentResponse,
                                isUser = false,
                                responseTime = totalTime,
                                tokensPerSecond = tokensPerSecond,
                                memoryUsed = memoryUsed,
                                modelMode = "Mobile • Gemma 3n-2B • CPU"
                            )
                            _messages.value = _messages.value.dropLast(1) + updatedMessage
                        }

                        if (done) {
                            inferenceTime = System.currentTimeMillis() - inferenceStart
                            Log.d(TAG, "Response generated!")
                            Log.d(TAG, "Response length: ${currentResponse.length} chars, ~$estimatedTokens tokens")
                            Log.d(TAG, "Query time: ${queryTime}ms")
                            Log.d(TAG, "Inference time: ${inferenceTime}ms")
                            Log.d(TAG, "Total time: ${totalTime}ms")
                            Log.d(TAG, "Tokens/sec: ${String.format("%.2f", tokensPerSecond)}")
                            Log.d(TAG, "Memory used: ${memoryUsed / (1024 * 1024)}MB")
                            
                            viewModelScope.launch(Dispatchers.Main) {
                                _isLoading.value = false
                            }
                            
                            // Save final response to database
                            viewModelScope.launch {
                                addMessageToDatabase(
                                    text = currentResponse,
                                    isUser = false,
                                    responseTime = totalTime,
                                    tokensPerSecond = tokensPerSecond,
                                    modelMode = "Mobile • Gemma 3n-2B • CPU"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "Failed to generate response in ${responseTime}ms", e)
                withContext(Dispatchers.Main) {
                    addMessage("Error generating response: ${e.message}", false)
                    _isLoading.value = false
                }
            }
        }
    }
    
    private fun sendMessageToDesktop(text: String) {
        if (_desktopConnectionStatus.value != DesktopConnectionStatus.CONNECTED) {
            addMessage("Desktop server not connected. Please check connection.", false)
            return
        }
        
        addMessage(text, true)
        _isLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Build conversation context for desktop
                val conversationContext = buildConversationContext(text)
                Log.d(TAG, "Sending request to desktop server with context length: ${conversationContext.length}")
                val result = desktopApiService?.generateText(conversationContext)
                
                result?.onSuccess { response ->
                    val responseTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Desktop response received in ${responseTime}ms")
                    
                    withContext(Dispatchers.Main) {
                        val desktopMessage = ChatMessage(
                            text = response.generated_text,
                            isUser = false,
                            responseTime = response.response_time_ms ?: responseTime,
                            tokensPerSecond = response.tokens_per_second,
                            modelMode = response.model_info ?: "Desktop • Gemma 3n-4B • MLX"
                        )
                        _messages.value = _messages.value + desktopMessage
                        _isLoading.value = false
                        
                        // Save to database
                        viewModelScope.launch {
                            addMessageToDatabase(
                                text = response.generated_text,
                                isUser = false,
                                responseTime = response.response_time_ms ?: responseTime,
                                tokensPerSecond = response.tokens_per_second,
                                modelMode = response.model_info ?: "Desktop • Gemma 3n-4B • MLX"
                            )
                        }
                    }
                }?.onFailure { error ->
                    val responseTime = System.currentTimeMillis() - startTime
                    Log.e(TAG, "Desktop request failed in ${responseTime}ms: ${error.message}")
                    
                    withContext(Dispatchers.Main) {
                        addMessage("Error from desktop server: ${error.message}", false)
                        _isLoading.value = false
                    }
                }
                
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "Desktop request exception in ${responseTime}ms", e)
                
                withContext(Dispatchers.Main) {
                    addMessage("Desktop connection error: ${e.message}", false)
                    _isLoading.value = false
                }
            }
        }
    }
    
    private fun sendMessageToDesktopStreaming(text: String, imageUri: Uri? = null, audioFile: File? = null, context: Context? = null, promptName: String) {
        if (_desktopConnectionStatus.value != DesktopConnectionStatus.CONNECTED) {
            addMessage("Desktop server not connected. Please check connection.", false)
            return
        }
        
        // Add user message to chat (with image and/or audio if present)
        addMessage(
            text = text, 
            isUser = true,
            imageUri = imageUri?.toString(),
            audioFile = audioFile?.absolutePath
        )
        _isLoading.value = true
        _isStreaming.value = true
        _streamingMessage.value = ""
        
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var completeResponse = ""
            
            try {
                // Convert image URI to base64 if provided
                var imageBase64: String? = null
                if (imageUri != null && context != null) {
                    imageBase64 = uriToBase64(context, imageUri)
                    if (imageBase64 == null) {
                        withContext(Dispatchers.Main) {
                            addMessage("Failed to process image. Please try again.", false)
                            _isLoading.value = false
                            _isStreaming.value = false
                            _streamingMessage.value = ""
                        }
                        return@launch
                    }
                    Log.d(TAG, "Image converted to base64, size: ${imageBase64.length} characters")
                }
                
                // Convert audio file to base64 if provided
                var audioBase64: String? = null
                if (audioFile != null) {
                    audioBase64 = fileToBase64(audioFile)
                    if (audioBase64 == null) {
                        withContext(Dispatchers.Main) {
                            addMessage("Failed to process audio. Please try again.", false)
                            _isLoading.value = false
                            _isStreaming.value = false
                            _streamingMessage.value = ""
                        }
                        return@launch
                    }
                    Log.d(TAG, "Audio converted to base64, size: ${audioBase64.length} characters")
                }
                
                // Build conversation context for desktop
                val conversationContext = buildConversationContext(text)
                Log.d(TAG, "Starting streaming request to desktop server with context length: ${conversationContext.length}, hasImage: ${imageBase64 != null}, hasAudio: ${audioBase64 != null}")
                
                desktopApiService?.generateTextStreaming(
                    prompt = conversationContext,
                    promptName = promptName,
                    imageBase64 = imageBase64,
                    audioBase64 = audioBase64
                )?.collect { streamingToken ->
                    Log.d(TAG, "Received streaming token: '${streamingToken.token}', isComplete: ${streamingToken.isComplete}, error: ${streamingToken.error}")
                    when {
                        streamingToken.error != null -> {
                            Log.e(TAG, "Streaming error: ${streamingToken.error}")
                            withContext(Dispatchers.Main) {
                                addMessage("Streaming error: ${streamingToken.error}", false)
                                _isLoading.value = false
                                _isStreaming.value = false
                                _streamingMessage.value = ""
                            }
                        }
                        streamingToken.isComplete -> {
                            val responseTime = System.currentTimeMillis() - startTime
                            Log.d(TAG, "Streaming completed in ${responseTime}ms")
                            
                            withContext(Dispatchers.Main) {
                                // Add final complete message with desktop model info
                                val desktopMessage = ChatMessage(
                                    text = completeResponse,
                                    isUser = false,
                                    responseTime = responseTime,
                                    modelMode = "Desktop • Gemma 3n-4B • MLX"
                                )
                                _messages.value = _messages.value + desktopMessage
                                _isLoading.value = false
                                _isStreaming.value = false
                                _streamingMessage.value = ""
                                
                                // Save to database
                                viewModelScope.launch {
                                    addMessageToDatabase(
                                        text = completeResponse,
                                        isUser = false,
                                        responseTime = responseTime,
                                        tokensPerSecond = null,
                                        modelMode = "Desktop • Gemma 3n-4B • MLX"
                                    )
                                }
                            }
                        }
                        else -> {
                            // Stream token received
                            completeResponse += streamingToken.token
                            withContext(Dispatchers.Main) {
                                _streamingMessage.value = completeResponse
                                Log.d(TAG, "Updated streaming message length: ${completeResponse.length}")
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "Streaming exception in ${responseTime}ms", e)
                
                withContext(Dispatchers.Main) {
                    addMessage("Streaming error: ${e.message}", false)
                    _isLoading.value = false
                    _isStreaming.value = false  
                    _streamingMessage.value = ""
                }
            }
        }
    }

    private fun addMessage(
        text: String, 
        isUser: Boolean, 
        responseTime: Long? = null,
        tokensPerSecond: Double? = null,
        memoryUsed: Long? = null,
        saveToDatabase: Boolean = true,
        imageUri: String? = null,
        imageBase64: String? = null,
        audioFile: String? = null,
        audioDurationMs: Long? = null
    ) {
        val newMessage = ChatMessage(
            text = text, 
            isUser = isUser, 
            responseTime = responseTime,
            tokensPerSecond = tokensPerSecond,
            memoryUsed = memoryUsed,
            imageUri = imageUri,
            imageBase64 = imageBase64,
            audioFile = audioFile,
            audioDurationMs = audioDurationMs
        )
        _messages.value = _messages.value + newMessage
        
        // Save to database only if requested and has content (text, image, or audio)
        if (saveToDatabase && (text.isNotEmpty() || imageUri != null || audioFile != null)) {
            viewModelScope.launch {
                addMessageToDatabase(text, isUser, responseTime, tokensPerSecond)
            }
        }
    }
    
    private suspend fun addMessageToDatabase(
        text: String,
        isUser: Boolean,
        responseTime: Long? = null,
        tokensPerSecond: Double? = null,
        modelMode: String? = null
    ) {
        val conversationId = _currentConversationId.value ?: run {
            // Create new conversation if none exists
            chatRepository.createNewConversation("New Chat")
        }
        
        val modelSource = when {
            isUser -> "user"
            inferenceMode.first() == InferenceMode.DESKTOP -> "desktop"
            else -> "mobile"
        }
        
        chatRepository.addMessage(
            conversationId = conversationId,
            text = text,
            isUser = isUser,
            modelSource = modelSource,
            responseTime = responseTime,
            tokensPerSecond = tokensPerSecond,
            modelMode = modelMode
        )
    }

    fun checkModelStatus(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val modelDir = File(context.getExternalFilesDir(null), "models")
            val modelFile = File(modelDir, modelFileName)
            if (modelFile.exists()) {
                initializeModel(context)
            } else {
                _modelStatus.value = ModelStatus.NOT_DOWNLOADED
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmSession?.close()
        llmInference?.close()
    }
}