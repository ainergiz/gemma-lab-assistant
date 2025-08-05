from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect #type: ignore
from fastapi.middleware.cors import CORSMiddleware #type: ignore
from fastapi.responses import JSONResponse, FileResponse #type: ignore
import uvicorn #type: ignore
import time
import warnings
import numpy as np #type: ignore
import qrcode #type: ignore
import io
import base64
import socket
import psutil #type: ignore
from pathlib import Path
from contextlib import asynccontextmanager
from vlm_config import vlm_config
from vlm_service import vlm_service
from vlm_routers import vlm_router

# Suppress audio processing warnings from transformers
warnings.filterwarnings("ignore", category=RuntimeWarning, module="transformers.models.gemma3n.feature_extraction_gemma3n")
np.seterr(divide='ignore', over='ignore', invalid='ignore')

# Setup logging
logger = vlm_config.setup_logging()

# --- Patched Code: Robust file paths and Wi-Fi configuration ---
STATIC_DIR = Path(__file__).parent.resolve()
WIFI_SSID = "LabPort"
WIFI_PASSWORD = "gemma3n8080"

def get_local_ip():
    """
    Get the local IP address of the server, prioritizing hotspot-typical addresses.
    This version works offline by scanning network interfaces.
    """
    # Common hotspot IP prefixes
    hotspot_prefixes = ('192.168.137.', '10.42.0.', '192.168.2.')
    
    try:
        for interface, addrs in psutil.net_if_addrs().items():
            for addr in addrs:
                if addr.family == socket.AF_INET:
                    # Prioritize known hotspot IPs
                    if any(addr.address.startswith(p) for p in hotspot_prefixes):
                        logger.info(f"Found hotspot IP {addr.address} on interface {interface}")
                        return addr.address
        
        # Fallback: look for any non-localhost IP
        for interface, addrs in psutil.net_if_addrs().items():
            for addr in addrs:
                if addr.family == socket.AF_INET and not addr.address.startswith('127.'):
                    logger.warning(f"No standard hotspot IP found. Falling back to {addr.address} on {interface}")
                    return addr.address

    except Exception as e:
        logger.error(f"Could not determine local IP using psutil: {e}")

    logger.error("Failed to find any suitable local IP address. Defaulting to 127.0.0.1")
    return "127.0.0.1"

def generate_wifi_qr(ssid: str, password: str, host_ip: str) -> str:
    """Generate QR code for Wi-Fi credentials in WIFI: format"""
    # The 'I' key for IP address is non-standard but helps the client app
    wifi_string = f"WIFI:T:WPA;S:{ssid};P:{password};I:{host_ip};;"
    
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=10,
        border=4,
    )
    qr.add_data(wifi_string)
    qr.make(fit=True)
    
    img = qr.make_image(fill_color="black", back_color="white")
    
    # Convert to base64
    buffer = io.BytesIO()
    img.save(buffer, format='PNG')
    buffer.seek(0)
    img_str = base64.b64encode(buffer.getvalue()).decode()
    
    return img_str

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Starting Gemma 3N VLM API Server...")
    logger.info("Loading VLM model...")
    
    try:
        if not await vlm_service.check_connection():
            logger.warning("⚠️  VLM model loading failed - server will start in degraded mode")
            logger.warning("Make sure MLX-VLM dependencies are installed and working properly")
            logger.info(f"Expected model: {vlm_service.status.model_name or vlm_config.VLM_MODEL}")
            if vlm_service.status.last_error:
                logger.error(f"Error: {vlm_service.status.last_error}")
        else:
            logger.info(f"✅ VLM Server ready! Using model: {vlm_service.status.model_name}")
    except Exception as e:
        logger.error(f"Startup error: {e}")
    
    yield
    
    # Shutdown
    logger.info("Shutting down VLM server...")
    try:
        vlm_service.clear_conversation()
        logger.info("✅ Cleanup completed")
    except Exception as e:
        logger.error(f"Error during shutdown: {e}")

app = FastAPI(
    title="Gemma 3N VLM API Server", 
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=vlm_config.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Add request logging middleware (only if enabled)
if vlm_config.ENABLE_REQUEST_LOGGING:
    @app.middleware("http")
    async def log_requests(request, call_next):
        start_time = time.time()
        client_ip = request.client.host if request.client else "unknown"
        
        try:
            response = await call_next(request)
            process_time = time.time() - start_time
            
            logger.info(
                f"{request.method} {request.url.path} - "
                f"{client_ip} - {response.status_code} - {process_time:.3f}s"
            )
            
            return response
        except Exception as e:
            process_time = time.time() - start_time
            logger.error(
                f"{request.method} {request.url.path} - "
                f"{client_ip} - ERROR - {process_time:.3f}s - {str(e)}"
            )
            raise

# Include VLM routes
app.include_router(vlm_router)

# --- Patched Code: Ingest WebSocket endpoint ---
@app.websocket("/ws")
async def websocket_ingest_endpoint(websocket: WebSocket):
    """WebSocket endpoint for real-time data ingestion from the mobile app."""
    await websocket.accept()
    logger.info(f"WebSocket connection accepted from {websocket.client.host}")
    try:
        while True:
            # The endpoint can receive text or bytes
            data = await websocket.receive_text()
            logger.info(f"Received message from client: {data}")
            
            # Simple echo response for testing
            response = f"Server received your message: '{data}'"
            await websocket.send_text(response)
            logger.info(f"Sent response to client: {response}")

    except WebSocketDisconnect:
        logger.info(f"WebSocket connection closed for client {websocket.client.host}")
    except Exception as e:
        logger.error(f"WebSocket error for client {websocket.client.host}: {e}")
        await websocket.close(code=1011)


# Wi-Fi pairing endpoints
@app.get("/qrcode")
async def get_wifi_qr():
    """Generate QR code for Wi-Fi pairing with mobile app"""
    try:
        host_ip = get_local_ip()
        qr_base64 = generate_wifi_qr(WIFI_SSID, WIFI_PASSWORD, host_ip)
        
        response_data = {
            "qr_code": qr_base64,
            "wifi_config": {
                "ssid": WIFI_SSID,
                "password": WIFI_PASSWORD,
                "host_ip": host_ip,
                "server_port": vlm_config.PORT
            },
            "instructions": {
                "windows": f"netsh wlan set hostednetwork mode=allow ssid={WIFI_SSID} key={WIFI_PASSWORD} && netsh wlan start hostednetwork",
                "linux": f"nmcli dev wifi hotspot ifname wlan0 ssid {WIFI_SSID} password {WIFI_PASSWORD}",
                "macos": f"sudo networksetup -setairportpower en0 on && networksetup -setairportnetwork en0 {WIFI_SSID} {WIFI_PASSWORD}"
            },
            "wifi_string": f"WIFI:T:WPA;S:{WIFI_SSID};P:{WIFI_PASSWORD};I:{host_ip};;"
        }
        return JSONResponse(content=response_data)
    except Exception as e:
        logger.error(f"QR code generation error: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to generate QR code: {str(e)}")

@app.get("/qr_display.html")
async def serve_qr_display():
    """Serve the QR code display HTML page"""
    file_path = STATIC_DIR / "qr_display.html"
    if not file_path.is_file():
        logger.error(f"QR display HTML not found at {file_path}")
        raise HTTPException(status_code=404, detail="qr_display.html not found")
    return FileResponse(str(file_path), media_type="text/html")

# Health check endpoint
@app.get("/health")
async def health_check():
    """Comprehensive health check endpoint"""
    health_status = {
        "status": "healthy",
        "timestamp": time.time(),
        "services": {
            "vlm": {
                "loaded": vlm_service.status.is_loaded,
                "model_name": vlm_service.status.model_name,
                "last_error": vlm_service.status.last_error,
                "load_attempts": vlm_service.status.load_attempts,
                "consecutive_failures": vlm_service.status.consecutive_failures
            }
        },
        "memory": {
            "conversation_length": len(vlm_service.conversation_history)
        },
        "config": {
            "model": vlm_config.VLM_MODEL,
            "max_tokens": vlm_config.VLM_MAX_TOKENS,
            "temperature": vlm_config.VLM_TEMPERATURE,
            "max_conversation_length": vlm_config.MAX_CONVERSATION_LENGTH
        }
    }
    
    # Determine overall health
    if not vlm_service.status.is_loaded:
        health_status["status"] = "degraded"
        if vlm_service.status.consecutive_failures > 3:
            health_status["status"] = "unhealthy"
            # Return 200 OK for health checks but indicate status in body
            # raise HTTPException(status_code=503, detail=health_status)
    
    return JSONResponse(content=health_status)

if __name__ == "__main__":
    logger.info(f"Starting VLM server on {vlm_config.HOST}:{vlm_config.PORT}")
    
    # Configure uvicorn for better stability
    uvicorn_config = {
        "host": vlm_config.HOST,
        "port": vlm_config.PORT,
        "log_level": "info",
        "access_log": True,
        "timeout_keep_alive": 5,
        "timeout_graceful_shutdown": 30
    }
    
    try:
        uvicorn.run(app, **uvicorn_config)
    except KeyboardInterrupt:
        logger.info("Server stopped by user")
    except Exception as e:
        logger.error(f"Server error: {e}")
