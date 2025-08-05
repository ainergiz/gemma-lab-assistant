from pydantic import BaseModel #type: ignore
from typing import Optional, Dict, Any
from enum import Enum

class VLMGenerateRequest(BaseModel):
    """Request model for VLM text/image/audio generation"""
    prompt: str
    prompt_name: Optional[str] = "default"  # "default", "lab_protocol_guide", or "structured_data_extraction"
    image_base64: Optional[str] = None  # Base64 encoded image
    audio_base64: Optional[str] = None  # Base64 encoded audio
    max_tokens: Optional[int] = 512
    temperature: Optional[float] = 1.0
    top_k: Optional[int] = 64
    top_p: Optional[float] = 0.95

class VLMGenerateResponse(BaseModel):
    """Response model for VLM generation"""
    generated_text: str
    prompt: str
    metadata: Optional[Dict[str, Any]] = None

class VLMModelStatus(str, Enum):
    """Enum for VLM model status"""
    LOADED = "loaded"
    LOADING = "loading"
    FAILED = "failed"
    NOT_LOADED = "not_loaded"

class VLMHealthResponse(BaseModel):
    """Response model for VLM health check"""
    status: str  # "healthy", "degraded", "unhealthy"
    timestamp: float
    services: Dict[str, Any]
    memory: Dict[str, Any]

class VLMRootResponse(BaseModel):
    """Response model for VLM root endpoint"""
    message: str
    version: str = "1.0.0"
    service: str = "VLM"

class VLMStreamingResponse(BaseModel):
    """Response model for streaming data"""
    token: str
    is_complete: bool = False
    error: Optional[str] = None

class VLMConversationMessage(BaseModel):
    """Model for conversation history entries"""
    role: str  # "user" or "assistant"
    content: list  # List of content items (text/image)
    timestamp: Optional[float] = None

class VLMConversationHistory(BaseModel):
    """Model for conversation history response"""
    conversation_history: list
    length: int
    max_length: int