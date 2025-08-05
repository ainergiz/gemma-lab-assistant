package com.example.mediapipeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AccountBox
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.mediapipeapp.utils.AudioRecorder
import com.example.mediapipeapp.utils.AudioPlayer
import java.io.File
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.mikepenz.markdown.m3.Markdown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mediapipeapp.ChatMessage
import com.example.mediapipeapp.LlmChatViewModel
import com.example.mediapipeapp.LlmChatViewModelFactory
import com.example.mediapipeapp.ModelStatus
import com.example.mediapipeapp.DesktopConnectionStatus
import com.example.mediapipeapp.InferenceMode
import com.example.mediapipeapp.R
import com.example.mediapipeapp.ui.theme.LoadingAnimation
import kotlinx.coroutines.launch

@Composable
fun LlmChatRoute(
    promptName: String,
    modifier: Modifier = Modifier,
    conversationId: Long? = null,
    onShowHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: LlmChatViewModel = viewModel(
        factory = LlmChatViewModelFactory(context, promptName)
    )
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val desktopConnectionStatus by viewModel.desktopConnectionStatus.collectAsState()
    val inferenceMode by viewModel.inferenceMode.collectAsState(initial = InferenceMode.MOBILE)
    val desktopServerUrl by viewModel.desktopServerUrl.collectAsState(initial = "http://192.168.0.33:8000")
    val autoDetectDesktop by viewModel.autoDetectDesktop.collectAsState(initial = false)
    val mobileGpuAcceleration by viewModel.mobileGpuAcceleration.collectAsState(initial = false)

    // Check model status on startup
    LaunchedEffect(Unit) {
        viewModel.checkModelStatus(context)
    }
    
    // Handle conversation initialization
    LaunchedEffect(conversationId) {
        if (conversationId != null) {
            // Load specific conversation from history
            viewModel.loadConversation(conversationId)
        } else {
            // Start new conversation if none specified
            viewModel.startNewConversation()
        }
    }

    LlmChatScreen(
        modifier = modifier,
        messages = messages,
        isLoading = isLoading,
        modelStatus = modelStatus,
        downloadProgress = downloadProgress,
        desktopConnectionStatus = desktopConnectionStatus,
        inferenceMode = inferenceMode,
        desktopServerUrl = desktopServerUrl,
        autoDetectDesktop = autoDetectDesktop,
        mobileGpuAcceleration = mobileGpuAcceleration,
        onSendMessage = { message, imageUri, audioFile ->
            viewModel.sendMessage(message, imageUri, audioFile, context)
        },
        onDownloadModel = {
            viewModel.downloadModel(context)
        },
        onToggleInferenceMode = {
            val newMode = if (inferenceMode == InferenceMode.MOBILE) InferenceMode.DESKTOP else InferenceMode.MOBILE
            viewModel.setInferenceMode(newMode)
            // Trigger desktop connection test when switching to desktop mode
            if (newMode == InferenceMode.DESKTOP) {
                viewModel.testDesktopConnection()
            }
        },
        onDismissMessage = { messageId ->
            viewModel.dismissMessage(messageId)
        },
        onSetDesktopServerUrl = { url ->
            viewModel.setDesktopServerUrl(url)
            // Test connection after URL change
            viewModel.testDesktopConnection()
        },
        onSetAutoDetectDesktop = { enabled ->
            viewModel.setAutoDetectDesktop(enabled)
        },
        onSetMobileGpuAcceleration = { enabled ->
            viewModel.setMobileGpuAcceleration(enabled)
        },
        onShowHistory = onShowHistory
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmChatScreen(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    modelStatus: ModelStatus,
    downloadProgress: Int,
    desktopConnectionStatus: DesktopConnectionStatus,
    inferenceMode: InferenceMode,
    desktopServerUrl: String,
    autoDetectDesktop: Boolean,
    mobileGpuAcceleration: Boolean,
    onSendMessage: (String, Uri?, File?) -> Unit,
    onDownloadModel: () -> Unit,
    onToggleInferenceMode: () -> Unit,
    onDismissMessage: (Long) -> Unit,
    onSetDesktopServerUrl: (String) -> Unit,
    onSetAutoDetectDesktop: (Boolean) -> Unit,
    onSetMobileGpuAcceleration: (Boolean) -> Unit,
    onShowHistory: () -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioFile by remember { mutableStateOf<File?>(null) }
    var showMediaSelector by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    val audioPlayer = remember { AudioPlayer(context) }
    var playingAudioFile by remember { mutableStateOf<File?>(null) }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        showMediaSelector = false
    }
    
    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording(audioRecorder) { file ->
                selectedAudioFile = file
                isRecording = false
                showMediaSelector = false
            }
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    // Recording timer effect
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingTime = (System.currentTimeMillis() - startTime) / 1000
                kotlinx.coroutines.delay(1000)
            }
        } else {
            recordingTime = 0
        }
    }
    
    // Cleanup audio player when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.cleanup()
        }
    }

    // Show snackbar for success feedback
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            snackbarMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // Top Row with Inference Mode Toggle and Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    onClick = { if (inferenceMode != InferenceMode.MOBILE) onToggleInferenceMode() },
                    label = { Text("📱 Mobile") },
                    selected = inferenceMode == InferenceMode.MOBILE
                )
                FilterChip(
                    onClick = { if (inferenceMode != InferenceMode.DESKTOP) onToggleInferenceMode() },
                    label = { Text("🖥️ Desktop") },
                    selected = inferenceMode == InferenceMode.DESKTOP
                )
            }
            
            Row {
                IconButton(onClick = onShowHistory) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Chat History"
                    )
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Status cards based on inference mode
        when (inferenceMode) {
            InferenceMode.MOBILE -> {
                when (modelStatus) {
                    ModelStatus.READY -> {
                        MobileModelStatusCard()
                    }
                    else -> {
                        ModelStatusCard(
                            modelStatus = modelStatus,
                            downloadProgress = downloadProgress,
                            onDownloadModel = onDownloadModel
                        )
                    }
                }
            }
            InferenceMode.DESKTOP -> {
                DesktopStatusCard(
                    connectionStatus = desktopConnectionStatus
                )
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageItem(
                    message = message,
                    onDismiss = { onDismissMessage(message.id) },
                    audioPlayer = audioPlayer,
                    playingAudioFile = playingAudioFile,
                    onAudioPlay = { audioFile ->
                        if (playingAudioFile == audioFile) {
                            audioPlayer.stop()
                            playingAudioFile = null
                        } else {
                            audioPlayer.play(
                                audioFile = audioFile,
                                onCompletion = { playingAudioFile = null },
                                onError = { playingAudioFile = null }
                            )
                            playingAudioFile = audioFile
                        }
                    }
                )
            }

            if (isLoading && messages.lastOrNull()?.isUser == true) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingAnimation()
                    }
                }
            }
        }

        // Image preview (if selected)
        selectedImageUri?.let { uri ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    
                    // Remove image button
                    IconButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }

        // Audio preview (if selected)
        selectedAudioFile?.let { audioFile ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Audio message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "🎵 Audio recorded",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${audioFile.length() / 1024}KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    IconButton(onClick = { selectedAudioFile = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove audio",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media picker button
            IconButton(
                onClick = { showMediaSelector = true },
                enabled = ((inferenceMode == InferenceMode.MOBILE && modelStatus == ModelStatus.READY) || 
                          (inferenceMode == InferenceMode.DESKTOP && desktopConnectionStatus == DesktopConnectionStatus.CONNECTED)) && !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add media",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text(stringResource(R.string.type_message)) },
                modifier = Modifier.weight(1f),
                enabled = ((inferenceMode == InferenceMode.MOBILE && modelStatus == ModelStatus.READY) || 
                          (inferenceMode == InferenceMode.DESKTOP && desktopConnectionStatus == DesktopConnectionStatus.CONNECTED)) && !isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                trailingIcon = {
                    if (messageText.isNotEmpty()) {
                        IconButton(onClick = { messageText = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear_message)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = {
                    if (messageText.isNotBlank() || selectedImageUri != null || selectedAudioFile != null) {
                        // If audio-only message, provide default text
                        val finalText = when {
                            messageText.isNotBlank() -> messageText
                            selectedAudioFile != null && selectedImageUri != null -> "Image and audio message"
                            selectedAudioFile != null -> "Audio message"
                            selectedImageUri != null -> "Image message"
                            else -> messageText
                        }
                        onSendMessage(finalText, selectedImageUri, selectedAudioFile)
                        messageText = ""
                        selectedImageUri = null
                        selectedAudioFile = null
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send)
                )
            }
        }

        // Media Selector Dialog
        if (showMediaSelector) {
            AlertDialog(
                onDismissRequest = { showMediaSelector = false },
                title = { Text("Add Media") },
                text = {
                    Column {
                        TextButton(
                            onClick = { 
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = "Add Image",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("📷 Add Image")
                            }
                        }
                        
                        TextButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                                    == PackageManager.PERMISSION_GRANTED) {
                                    showMediaSelector = false
                                    startRecording(audioRecorder, isRecording) { file, recording ->
                                        selectedAudioFile = file
                                        isRecording = recording
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Record Audio",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("🎤 Record Audio")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMediaSelector = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Audio Permission Dialog
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Audio Permission Required") },
                text = { Text("This app needs microphone permission to record audio messages.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    ) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Recording Dialog
        if (isRecording) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Recording Audio") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Recording",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Recording in progress...")
                        Text(
                            text = "${recordingTime}s",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            stopRecording(audioRecorder) { file ->
                                selectedAudioFile = file
                                isRecording = false
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            audioRecorder.cancelRecording()
                            isRecording = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Settings Dialog
        SettingsDialog(
            isVisible = showSettingsDialog,
            onDismiss = { showSettingsDialog = false },
            inferenceMode = inferenceMode,
            onInferenceModeChange = { mode ->
                if (mode != inferenceMode) {
                    onToggleInferenceMode()
                }
            },
            desktopServerUrl = desktopServerUrl,
            onDesktopServerUrlChange = onSetDesktopServerUrl,
            autoDetectDesktop = autoDetectDesktop,
            onAutoDetectDesktopChange = onSetAutoDetectDesktop,
            mobileGpuAcceleration = mobileGpuAcceleration,
            onMobileGpuAccelerationChange = onSetMobileGpuAcceleration,
            onWifiQrScanned = { qrCode ->
                // Parse QR code and extract server URL
                val wifiManager = com.example.mediapipeapp.wifi.WifiPairingManager(context)
                wifiManager.parseWifiQr(qrCode)?.let { payload ->
                    val serverUrl = "http://${payload.hostIp}:${payload.port}"
                    
                    // Update server URL
                    onSetDesktopServerUrl(serverUrl)
                    
                    // Auto-switch to Desktop mode for demo effect
                    if (inferenceMode == InferenceMode.MOBILE) {
                        onToggleInferenceMode()
                    }
                    
                    // Close settings dialog
                    showSettingsDialog = false
                    
                    // Show success feedback
                    snackbarMessage = "✅ Connected to offline server: ${payload.hostIp}"
                }
            }
        )
        }
        
        // Snackbar host for success messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun ModelStatusCard(
    modelStatus: ModelStatus,
    downloadProgress: Int,
    onDownloadModel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (modelStatus) {
                ModelStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.errorContainer
                ModelStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiaryContainer
                ModelStatus.READY -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (modelStatus) {
                        ModelStatus.NOT_DOWNLOADED -> "Model not downloaded"
                        ModelStatus.DOWNLOADING -> "Downloading model... $downloadProgress%"
                        ModelStatus.READY -> "Gemma 3n model ready"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                if (modelStatus == ModelStatus.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = downloadProgress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }

            if (modelStatus == ModelStatus.NOT_DOWNLOADED) {
                Button(onClick = onDownloadModel) {
                    Text("Download")
                }
            }

            if (modelStatus == ModelStatus.DOWNLOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MessageItem(
    message: ChatMessage, 
    onDismiss: () -> Unit,
    audioPlayer: AudioPlayer? = null,
    playingAudioFile: File? = null,
    onAudioPlay: (File) -> Unit = {}
) {
    // If the message contains structured data, show the special card
    if (message.structuredData != null && message.structuredData is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        StructuredResponseCard(
            data = message.structuredData as Map<String, Any>,
            onDismiss = onDismiss
        )
        return // Stop here for this type of message
    }

    // Otherwise, show the regular chat bubble
    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    } else {
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    }

    val horizontalAlignment = if (message.isUser) {
        Alignment.End
    } else {
        Alignment.Start
    }

    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = if (message.isUser) "User" else "Model",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        BoxWithConstraints {
            Card(
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = bubbleShape,
                modifier = Modifier.widthIn(0.dp, maxWidth * 0.9f)
            ) {
                if (message.text.isEmpty() && !message.isUser) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Show image if present (for user messages)
                        message.imageUri?.let { imageUriString ->
                            val imageUri = remember(imageUriString) {
                                try {
                                    Uri.parse(imageUriString)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            
                            if (imageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(imageUri),
                                    contentDescription = "User uploaded image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp)
                                )
                            } else {
                                // If image can't be loaded, show placeholder
                                Text(
                                    text = "📷 Image attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp)
                                )
                            }
                        }
                        
                        // Show audio if present (for user messages)
                        message.audioFile?.let { audioFilePath ->
                            val audioFile = File(audioFilePath)
                            val isCurrentlyPlaying = playingAudioFile == audioFile
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp)
                                    .clickable { onAudioPlay(audioFile) }
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = if (isCurrentlyPlaying) "Stop audio" else "Play audio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCurrentlyPlaying) "🔊 Playing..." else "🎵 Audio message",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                message.audioDurationMs?.let { duration ->
                                    Text(
                                        text = " (${duration / 1000}s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        
                        // Show text if present
                        if (message.text.isNotEmpty()) {
                            if (message.isUser) {
                                // User messages - plain text
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                // LLM responses - render as markdown
                                Markdown(
                                    content = message.text
                                )
                            }
                        }

                        // Show performance metrics for AI messages
                        if (!message.isUser && message.responseTime != null) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = "⚡ ${message.responseTime}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )

                                message.tokensPerSecond?.let { tps ->
                                    Text(
                                        text = "🚀 ${"%.1f".format(tps)} tokens/sec",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }

                                message.modelMode?.let { mode ->
                                    Text(
                                        text = "🤖 $mode mode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileModelStatusCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📱 Mobile Gemma 3n available",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DesktopStatusCard(
    connectionStatus: DesktopConnectionStatus
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionStatus) {
                DesktopConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.tertiaryContainer
                DesktopConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                DesktopConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                DesktopConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (connectionStatus) {
                    DesktopConnectionStatus.CONNECTED -> "🖥️ Desktop server connected"
                    DesktopConnectionStatus.CONNECTING -> "🔄 Connecting to desktop..."
                    DesktopConnectionStatus.ERROR -> "❌ Desktop connection failed"
                    DesktopConnectionStatus.DISCONNECTED -> "🖥️ Desktop disconnected"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Helper functions for audio recording
private fun startRecording(
    audioRecorder: AudioRecorder,
    callback: (File?) -> Unit
) {
    val file = audioRecorder.startRecording()
    callback(file)
}

private fun startRecording(
    audioRecorder: AudioRecorder,
    isRecording: Boolean,
    callback: (File?, Boolean) -> Unit
) {
    val file = audioRecorder.startRecording()
    callback(file, true)
}

private fun stopRecording(
    audioRecorder: AudioRecorder,
    callback: (File?) -> Unit
) {
    val file = audioRecorder.stopRecording()
    callback(file)
}
