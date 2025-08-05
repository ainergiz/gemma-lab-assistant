# Gemma Lab Assistant

Complete dual-platform laboratory AI assistant system with Android mobile app and Python backend server for scientific research workflows.

## Overview

Gemma Lab Assistant provides specialized AI assistance for laboratory work through:

- **Android Mobile App** (`gemma-mobile/`) - Native Android application with on-device AI and desktop connectivity
- **Python Backend Server** (`gemma-backend/`) - FastAPI VLM server with multimodal AI capabilities
- **Dual AI Modes** - Switch between on-device inference and powerful desktop server processing

## Quick Start

### 1. Backend Setup

**Prerequisites:**
- macOS with Apple Silicon (for MLX optimization)
- Python 3.11+
- UV package manager

**Install and run:**
```bash
# Install UV if needed
curl -LsSf https://astral.sh/uv/install.sh | sh

# Setup backend
cd gemma-backend
uv sync
uv run vlm_main.py
```

Server runs at `http://0.0.0.0:8000`

### 2. Mobile App Setup

**Prerequisites:**
- Android device/emulator (API 24+)
- Android Studio or Android SDK with ADB
- Java 17+

**Build and install:**
```bash
cd gemma-mobile
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Offline Wi-Fi Pairing 

For offline demonstrations without internet connectivity:

1. **Access QR code interface**
   ```
   http://localhost:8000/qr_display.html
   ```

2. **Setup mobile hotspot**
   - Create hotspot: Name `LabPort`, Password `gemma3n8080`
   - Follow platform-specific commands shown on QR page

3. **Connect mobile app**
   - Open Gemma app → Settings → Wi-Fi Pairing
   - Scan QR code for automatic connection
   - Switch to Desktop mode for full AI capabilities

## Architecture

### Mobile App (`gemma-mobile/`)

**Core Features:**
- Lab-specialized workflows (Protocol Help, Data Structuring, General Chat)
- Dual inference modes (Mobile/Desktop)
- Multimodal support (images, audio) in Desktop mode
- SQLite chat history with conversation continuity
- QR code Wi-Fi pairing for offline setup

**Mobile Mode (On-Device):**
- Model: Gemma 3N E2B INT4 (~2.9GB)
- Framework: Google MediaPipe LLM
- Privacy: 100% offline inference
- Limitations: Text-only

**Desktop Mode (Server):**
- Full multimodal capabilities
- Real-time streaming responses
- Image and audio analysis
- Enhanced performance metrics

### Backend Server (`gemma-backend/`)

**AI Capabilities:**
- MLX-VLM integration with Apple Silicon optimization
- Specialized lab prompts for protocol analysis
- Multimodal processing (text, images, audio)
- Real-time token streaming via Server-Sent Events

**Production Features:**
- Comprehensive error handling and retry logic
- Health monitoring and performance metrics
- Request logging and resource management
- CORS enabled for mobile connectivity

**Key Models:**
- Primary: `mlx-community/gemma-3n-E4B-it-bf16`
- Specialized prompts: Lab protocols, data extraction, general chat

## Lab Assistant Workflows

### Specialized AI Workflows

1. **Lab Protocol Help**
   - Equipment setup and procedure interpretation
   - Step-by-step protocol breakdowns
   - Quality control and safety guidance

2. **Data Structuring**
   - Transform raw experimental data into structured formats
   - Genomic sequence analysis and variant interpretation
   - Statistical analysis recommendations

3. **General Chat**
   - Laboratory procedures and scientific concepts
   - Research methodology assistance
   - Technical troubleshooting

### Multimodal Analysis (Desktop Mode)

- **Image Analysis** - Lab equipment, experimental results, microscopy images
- **Audio Processing** - Experimental notes, voice memos, meeting recordings
- **Context-Aware Responses** - AI tailored to selected workflow type
- **Conversation Continuity** - Full history shared between mobile and desktop modes

## API Reference

### Backend Endpoints

- `GET /vlm/health` - Model status and performance metrics
- `POST /vlm/generate/stream` - Streaming text generation with lab prompts
- `POST /vlm/generate/image` - Image upload and analysis
- `GET /vlm/conversation/history` - View conversation history

**Wi-Fi Pairing:**
- `GET /qr_display.html` - Visual QR code display page
- `GET /qrcode` - QR code data and Wi-Fi configuration
- `GET /health` - Server health monitoring

### Configuration

Backend supports extensive configuration via environment variables:

```bash
# Server settings
VLM_HOST=0.0.0.0
VLM_PORT=8000
LOG_LEVEL=INFO

# Model settings
VLM_MODEL=mlx-community/gemma-3n-E4B-it-bf16
VLM_MAX_TOKENS=1024
VLM_TEMPERATURE=1.0

# Performance settings
VLM_TIMEOUT=300
MAX_IMAGE_SIZE_MB=50
MAX_CONVERSATION_LENGTH=10
```

## System Requirements

### Backend Requirements
- **macOS** with Apple Silicon (for MLX-VLM)
- **Python 3.11+**
- **FFmpeg** (for audio format conversion)
- **Dependencies**: FastAPI, MLX, PIL, torchvision

### Mobile Requirements
- **Android 7.0+** (API 24+)
- **2.9GB storage** (for on-device model)
- **Network connectivity** (for Desktop mode)

## Development

### Project Structure
```
gemma/
├── gemma-mobile/          # Android application
│   ├── app/              # Main app module
│   └── README.md         # Mobile-specific documentation
├── gemma-backend/         # Python FastAPI server
│   ├── vlm_main.py       # Main server entry point
│   ├── vlm_service.py    # VLM business logic
│   ├── qr_display.html   # QR code interface
│   └── README.md         # Backend-specific documentation
└── README.md             # This file
```

### Key Features

**Offline Capabilities:**
- QR code-based Wi-Fi pairing for no-internet demos
- On-device AI inference for complete privacy
- Automatic IP detection and hotspot integration

**Production Ready:**
- Comprehensive error handling and retry logic
- Health monitoring and performance metrics
- Request logging and resource management
- Graceful degradation when services unavailable

**Multimodal AI:**
- Image understanding for lab equipment and results
- Audio processing for experimental notes
- Text analysis with specialized scientific contexts
- Real-time streaming for responsive interactions

## Example Use Cases

1. Setup offline hotspot using QR code system
2. Connect mobile app via QR scan
3. Demonstrate multimodal AI capabilities
4. Analyze lab images and audio recordings
5. Generate structured data from experimental results

### Research Laboratory
1. Install both mobile app and backend server
2. Connect via local network
3. Use specialized workflows for daily lab tasks
4. Maintain conversation history across sessions
5. Switch between on-device and server AI as needed

### Field Research
1. Use mobile-only mode for privacy-sensitive work
2. Download on-device model for offline operation
3. Collect and organize field data
4. Sync with desktop server when connectivity available

Built with Android Jetpack Compose, FastAPI, MLX-VLM, and MediaPipe for production-grade laboratory AI assistance.