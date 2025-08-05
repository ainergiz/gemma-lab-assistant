# Gemma Lab Assistant

Android app for scientific laboratory assistance with dual AI inference: on-device MediaPipe LLM + desktop server multimodal streaming.

## Features

### Core Capabilities
- **Lab-Focused Home Screen** - Specialized workflows for protocol help, data structuring, and general chat
- **Dual Inference Modes** - Switch between Mobile (on-device) and Desktop (server)
- **On-Device AI** - Gemma 3N E2B model with MediaPipe (~2.9GB)
- **Desktop Streaming** - Full streaming support with conversation context
- **Material 3 UI** - Modern Jetpack Compose interface with custom lab branding

### Multimodal Support
- **Image Understanding** - Upload and analyze images with Desktop mode
- **Audio Recording** - Record and send audio messages with real-time analysis
- **Audio Playback** - Play back recorded audio messages with visual feedback

### Connectivity & Data
- **Offline Wi-Fi Pairing** - QR code scanning for instant hotspot connection setup
- **Chat History** - SQLite persistence with enhanced conversation management
- **Context Continuity** - Full conversation history shared between mobile and desktop models
- **Conversation Loading** - Resume specific conversations from chat history with full context

### Performance & Settings
- **Smart Settings** - Persistent preferences with auto-detection
- **Performance Metrics** - Response times, tokens/sec, memory usage with model identification
- **Specialized AI Workflows** - Context-aware system prompts for lab protocols, data analysis, and general assistance  

## Installation

### Prerequisites
- Android device or emulator with API 24+ (Android 7.0+)
- Android Studio or Android SDK with ADB
- Java 17 or higher

### Build and Install
1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd gemma-mobile
   ```

2. **Build the APK**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on device**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Alternative Installation Methods
- **Android Studio**: Open project and run using the play button
- **Direct APK**: Download from releases and install via file manager

## Quick Start

1. **Setup Offline Connection**
   - Create Android hotspot: Settings → Hotspot → Name: `LabPort`, Password: `gemma3n8080`
   - Connect Mac to hotspot and run: `uv run vlm_main.py` in `gemma-backend`
   - Scan QR code: App Settings → Wi-Fi Pairing → Scan QR from `http://localhost:8000/qr_display.html`
   - Automatic connection and Desktop mode activation

2. **Traditional Setup** (for regular use)
   - Run `gemma-backend` VLM server on same network
   - Configure server URL in app settings  
   - Switch to Desktop mode for multimodal capabilities

3. **Download Mobile Model** (optional for text-only)
   - Tap "Download Model" for on-device inference
   - Model: `gemma-3n-E2B-it-int4.task` (2.9GB)
   - Stored in app's external files directory

## Architecture

### Mobile Mode (On-Device)
- **Model**: Gemma 3N E2B INT4 quantized
- **Framework**: Google MediaPipe LLM Inference  
- **Backend**: CPU (stable) with optional GPU acceleration
- **Privacy**: 100% offline inference
- **Streaming**: Native token streaming via MediaPipe
- **AI Workflows**: Context-aware system prompts for specialized lab assistance
- **Limitations**: Text-only (no image or audio support)

### Desktop Mode (Server)
- **Model**: Gemma 3N 4B with MLX optimization
- **Backend**: FastAPI VLM server with MLX
- **Features**: Real-time token streaming, **multimodal image and audio analysis**, conversation context
- **Connection**: Auto-detection with health monitoring
- **Protocol**: HTTP with Server-Sent Events (SSE)
- **Performance**: Enhanced token/sec reporting and response metrics
- **AI Workflows**: Full multimodal support with specialized lab protocol and data analysis prompts

## Lab Assistant Workflows

### Home Screen Options
1. **Lab Protocol Help** - Get assistance with interpreting and following laboratory procedures
   - Specialized protocol analysis and equipment guidance
   - Step-by-step breakdowns with quality control checkpoints
   - Critical control points and technique optimization

2. **Data Structuring** - Organize and analyze experimental data efficiently  
   - Transform raw data into structured, analyzable formats
   - Genomic sequence analysis and variant interpretation
   - Pattern recognition and statistical analysis suggestions

3. **General Chat** - Ask general questions and get help with lab work
   - General laboratory procedures and scientific concepts
   - Research planning and methodology assistance
   - Technical troubleshooting and data interpretation

### Multimodal Analysis (Desktop Mode)
- **Image Upload** - Analyze lab equipment, experiments, or results
- **Audio Recording** - Record experimental notes with real-time analysis
- **Audio Playback** - Review recorded lab notes with visual feedback
- **Context-Aware** - AI responses tailored to selected workflow (protocol, data, or general)
- **Enhanced Chat History** - Access and resume previous conversations with full context
- **Seamless Navigation** - Switch between workflows while maintaining conversation continuity

### Audio Recording Features
- **Real-time Timer** - See recording duration as you speak
- **Format Support** - Records in M4A format, auto-converted to WAV on server
- **Size Optimization** - 32kbps bitrate for faster processing
- **Playback Controls** - Tap audio messages to play/stop
- **Persistence** - Audio messages saved and persist after responses
- **Smart Cleanup** - Each audio message processed independently
