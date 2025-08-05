# Gemma Lab Assistant Backend

FastAPI server providing specialized laboratory AI assistance with VLM (Vision Language Model) APIs for multimodal interactions.

## Features

### AI Capabilities
- **Lab-Specialized Prompts** - Context-aware AI for protocol analysis and data structuring
- **MLX-VLM Integration** - Native streaming multimodal AI with Apple Silicon optimization
- **Image Understanding** - Analyze lab equipment, experiments, and results
- **Audio Processing** - Process lab notes and experimental recordings
- **Multimodal Support** - Handle text, images, and audio in combination

### Performance & Connectivity
- **Real-time Streaming** - Token-by-token response streaming with SSE
- **Offline Wi-Fi Pairing** - QR code generation for hotspot-based mobile connection
- **Conversation Context** - Handles full conversation history from mobile app
- **Performance Metrics** - Enhanced response tracking and token/sec reporting
- **CORS Enabled** - Mobile app connectivity

### Production Features
- **Robust Error Handling** - Comprehensive retry logic, timeouts, and graceful degradation
- **Production Ready** - Request logging, health monitoring, and proper resource management  

## Installation

### Prerequisites
- **macOS** with Apple Silicon (for MLX-VLM optimization)
- **Python 3.11+**
- **UV** - Modern Python package manager

### Install UV
If you don't have UV installed:

```bash
# macOS/Linux
curl -LsSf https://astral.sh/uv/install.sh | sh

# Or via pip
pip install uv

# Or via Homebrew
brew install uv
```

### Setup and Run
1. **Install dependencies**
   ```bash
   uv sync
   ```

2. **Run the VLM server**
   ```bash
   uv run vlm_main.py
   ```

Server runs at `http://0.0.0.0:8000`

### Additional Requirements
- **FFmpeg** (for audio format conversion)
- **MLX-VLM** (`mlx-community/gemma-3n-E4B-it-bf16`)

## Offline Setup

### QR Code Wi-Fi Pairing

The backend provides automatic QR code generation for instant mobile app pairing:

1. **Start the server**
   ```bash
   uv run vlm_main.py
   ```

2. **Access QR code interface**
   Open your browser and navigate to:
   ```
   http://localhost:8000/qr_display.html
   ```

3. **Setup hotspot** (follow instructions on the page)
   - **macOS**: Create hotspot named `LabPort` with password `gemma3n8080`
   - **Windows/Linux**: Use provided commands from the QR display page

4. **Mobile app connection**
   - Open Gemma mobile app
   - Go to Settings → Wi-Fi Pairing
   - Scan the QR code
   - App automatically connects and switches to Desktop mode

### QR Code Features

- **Auto IP Detection**: Automatically detects the correct server IP address
- **Platform-Specific Instructions**: Shows appropriate hotspot setup commands for your OS
- **Health Monitoring**: Displays server and model status
- **Auto-Refresh**: QR code updates every 30 seconds
- **Manual Refresh**: Click refresh button to update QR code instantly

### API Endpoints for QR Pairing

- `GET /qr_display.html` - Visual QR code display page
- `GET /qrcode` - JSON response with QR code data and Wi-Fi configuration
- `GET /health` - Server health status for monitoring

## Lab Assistant Prompts

The backend includes specialized prompt contexts:

- **`lab_protocol_guide`** - Lab assistant for analyzing equipment, experiments, and procedures
- **`structured_data_extraction`** - GenomeStruct AI for pathogen genomic data extraction  
- **`default`** - General conversation mode

## API Endpoints

### VLM (Vision Language Model)
- `GET /vlm/health` - VLM model status and performance metrics
- `POST /vlm/generate` - Text generation with `prompt_name` parameter for context-aware responses
- `POST /vlm/generate/stream` - Streaming text generation with specialized lab prompts
- `POST /vlm/generate/image` - Image file upload + text analysis
- `POST /vlm/clear` - Clear conversation history
- `GET /vlm/conversation/history` - View conversation history

### Wi-Fi Pairing
- `GET /qrcode` - Generate QR code with Wi-Fi credentials and server IP
- `GET /qr_display.html` - Visual QR code display page for mobile scanning

### Health & Monitoring
- `GET /health` - Comprehensive server health check

## Configuration

VLM server supports extensive configuration via environment variables:

```bash
# Server settings
VLM_HOST=0.0.0.0          # Server host
VLM_PORT=8000             # Server port  
LOG_LEVEL=INFO            # Logging level

# Model settings
VLM_MODEL=mlx-community/gemma-3n-E4B-it-bf16  # VLM model
VLM_MAX_TOKENS=1024       # Default max tokens
VLM_TEMPERATURE=1.0       # Default temperature

# Performance settings
VLM_TIMEOUT=300           # Generation timeout (5 minutes)
VLM_RETRY_ATTEMPTS=3      # Model loading retry attempts
VLM_LOAD_TIMEOUT=120      # Model loading timeout (2 minutes)

# Content limits
MAX_IMAGE_SIZE_MB=50      # Max image size
MAX_PROMPT_LENGTH=50000   # Max prompt length
MAX_CONVERSATION_LENGTH=10 # Max conversation history

# Features
ENABLE_REQUEST_LOGGING=true
ENABLE_PERFORMANCE_METRICS=true
```

## System Requirements

- **macOS** with Apple Silicon (for MLX-VLM)
- **Python 3.11+**
- **MLX-VLM** (`mlx-community/gemma-3n-E4B-it-bf16`)
- **FFmpeg** (for audio format conversion)
- **Dependencies**: FastAPI, MLX, PIL, torchvision

## Audio Processing Features

- **Format Support** - Automatic conversion of M4A, MP3, WAV to 16kHz WAV
- **Smart Timeouts** - Extended processing time for audio content (10 min vs 5 min)
- **Context Isolation** - Each audio message processed independently
- **Automatic Cleanup** - Temporary audio files cleaned after processing
- **Size Limits** - 10MB max audio file size with validation
- **Streaming Compatible** - Audio works with real-time token streaming

## Example Usage

### Lab Protocol Analysis
```bash
curl -X POST "http://localhost:8000/vlm/generate/stream" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Analyze this lab equipment setup",
    "prompt_name": "lab_protocol_guide",
    "max_tokens": 500
  }'
```

### Structured Data Extraction
```bash
curl -X POST "http://localhost:8000/vlm/generate/stream" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Extract genomic sequence data from this lab report",
    "prompt_name": "structured_data_extraction",
    "max_tokens": 500
  }'
```

### Image Analysis
```bash
curl -X POST "http://localhost:8000/vlm/generate" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "What do you see in this image?",
    "image_base64": "iVBORw0KGgoAAAANSUhEUgAA..."
  }'
```

### Image Upload
```bash
curl -X POST "http://localhost:8000/vlm/generate/image" \
  -F "prompt=Describe this image in detail" \
  -F "image=@photo.jpg"
```

### Audio Analysis
```bash
curl -X POST "http://localhost:8000/vlm/generate/stream" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "What do you hear in this audio?",
    "audio_base64": "UklGRnoGAABXQVZFZm10IBAAAAABAAEA..."
  }'
```

### Multimodal (Image + Audio)
```bash
curl -X POST "http://localhost:8000/vlm/generate/stream" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Describe both the image and audio content",
    "image_base64": "iVBORw0KGgoAAAANSUhEUgAA...",
    "audio_base64": "UklGRnoGAABXQVZFZm10IBAAAAABAAEA..."
  }'
```

## Mobile App Integration

The mobile app sends context-aware requests with specialized prompts:

```json
{
  "prompt": "User: What's in this lab equipment photo?\n\nAssistant:",
  "prompt_name": "lab_protocol_guide",
  "image_base64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "audio_base64": "UklGRnoGAABXQVZFZm10IBAAAAABAAEA...",
  "max_tokens": 512,
  "temperature": 1.0
}
```

### Enhanced Response Format

The server returns structured responses with performance metrics:

```json
{
  "generated_text": "I can see a beautiful sunset over mountains with orange and pink clouds, and I hear the sound of ocean waves...",
  "prompt": "User context...",
  "metadata": {
    "model": "mlx-community/gemma-3n-E4B-it-bf16",
    "generation_time_ms": 1250,
    "response_length": 156,
    "num_images": 1,
    "num_audios": 1,
    "request_time_ms": 1340
  }
}
```

## Architecture

### Separation of Concerns
- **VLM Server** (`vlm_main.py`) - Pure VLM functionality
- **Hybrid Server** (`ollama/main.py`) - VLM + Ollama integration
- **Modular Design** - Separate configs, models, and services

### Robustness Features
- **Retry Logic** - Automatic model loading retries with exponential backoff
- **Timeout Protection** - Generation and loading timeouts
- **Memory Management** - Automatic image cleanup and garbage collection
- **Error Recovery** - Graceful degradation and detailed error reporting
- **Resource Limits** - Image size, prompt length, and conversation limits

### Production Features
- **Health Monitoring** - Detailed service status and metrics
- **Request Logging** - Comprehensive request/response tracking
- **Performance Metrics** - Response times and throughput monitoring
- **Graceful Shutdown** - Proper resource cleanup

## Development

### Project Structure
```
gemma-backend/
├── vlm_main.py          # VLM-only server
├── vlm_config.py        # VLM configuration
├── vlm_models.py        # VLM Pydantic models
├── vlm_service.py       # VLM business logic
├── vlm_routers.py       # VLM API endpoints
└── ollama/              # Hybrid server files
    ├── main.py
    ├── config.py
    ├── models.py
    └── ...
```

### Key Improvements
- **FastAPI Lifespan** - Modern startup/shutdown handling
- **Comprehensive Logging** - Request tracing and error tracking
- **Input Validation** - Robust request validation and sanitization
- **Image Processing** - PIL integration with size limits and format validation
- **Streaming Optimization** - Enhanced Server-Sent Events implementation

Built with FastAPI, MLX-VLM, and Apple Silicon optimization for production-grade multimodal AI applications.