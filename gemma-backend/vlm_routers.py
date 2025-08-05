from fastapi import APIRouter, HTTPException, UploadFile, File, Request #type: ignore
from fastapi.responses import JSONResponse, StreamingResponse #type: ignore
import base64
import time
import logging
import psutil #type: ignore
from vlm_models import VLMGenerateRequest, VLMGenerateResponse, VLMHealthResponse, VLMRootResponse
from vlm_service import vlm_service

logger = logging.getLogger(__name__)

# Create router instance
vlm_router = APIRouter(prefix="/vlm", tags=["VLM"])

@vlm_router.get("/", response_model=VLMRootResponse)
async def vlm_root():
    """VLM root endpoint"""
    return VLMRootResponse(message="Gemma 3N VLM API Server is running")

@vlm_router.get("/health", response_model=VLMHealthResponse)
async def vlm_health_check():
    """Health check endpoint for VLM model"""
    vlm_loaded = await vlm_service.check_connection()
    
    # Get memory usage
    memory_info = psutil.virtual_memory()
    
    return VLMHealthResponse(
        status="healthy" if vlm_loaded else "degraded",
        timestamp=time.time(),
        services={
            "vlm_service": {
                "status": "online" if vlm_loaded else "offline",
                "model": vlm_service.status.model_name,
                "last_error": vlm_service.status.last_error
            }
        },
        memory={
            "total_gb": round(memory_info.total / (1024**3), 2),
            "available_gb": round(memory_info.available / (1024**3), 2),
            "used_percent": memory_info.percent
        }
    )

@vlm_router.post("/generate/stream")
async def vlm_generate_text_streaming(request: VLMGenerateRequest, http_request: Request):
    """Generate text using VLM model with streaming output and robust error handling"""
    
    # Log request details
    client_ip = http_request.client.host if http_request.client else "unknown"
    logger.info(f"Streaming request from {client_ip}, prompt length: {len(request.prompt)}")
    
    # Validate request
    if not request.prompt or not request.prompt.strip():
        raise HTTPException(status_code=400, detail="Empty prompt provided")
    
    if len(request.prompt) > 50000:  # 50k character limit
        raise HTTPException(status_code=400, detail="Prompt too long (max 50,000 characters)")
    
    # Decode image if provided
    image_data = None
    if request.image_base64:
        try:
            image_data = base64.b64decode(request.image_base64)
            # Validate image size (50MB limit)
            if len(image_data) > 50 * 1024 * 1024:
                raise HTTPException(status_code=400, detail="Image too large (max 50MB)")
        except Exception as e:
            logger.error(f"Base64 decode error: {e}")
            raise HTTPException(status_code=400, detail=f"Invalid base64 image data: {str(e)}")
    
    # Decode audio if provided
    audio_data = None
    if request.audio_base64:
        try:
            audio_data = base64.b64decode(request.audio_base64)
            # Validate audio size (10MB limit)
            if len(audio_data) > 10 * 1024 * 1024:
                raise HTTPException(status_code=400, detail="Audio too large (max 10MB)")
        except Exception as e:
            logger.error(f"Audio base64 decode error: {e}")
            raise HTTPException(status_code=400, detail=f"Invalid base64 audio data: {str(e)}")
    
    # Check if VLM service is available
    if not await vlm_service.check_connection():
        raise HTTPException(
            status_code=503, 
            detail=f"VLM service unavailable: {vlm_service.status.last_error}"
        )
    
    def generate_stream():
        """Generator function for streaming tokens with error handling"""
        request_start = time.time()
        token_count = 0
        
        try:
            # Use the sync generator directly
            for token in vlm_service.generate_text_streaming(
                prompt=request.prompt,
                prompt_name=request.prompt_name,
                max_tokens=request.max_tokens,
                temperature=request.temperature,
                top_k=request.top_k,
                top_p=request.top_p,
                image_data=image_data,
                audio_data=audio_data
            ):
                # Check if token contains error
                if token.startswith("Error:"):
                    logger.error(f"Generation error: {token}")
                    yield f"data: {token}\n\n"
                    yield "data: [DONE]\n\n"
                    return
                
                # Format as Server-Sent Events
                yield f"data: {token}\n\n"
                token_count += 1
            
            # Log completion stats
            request_time = time.time() - request_start
            logger.info(f"Streaming completed: {token_count} tokens in {request_time:.2f}s")
            
            # Send completion signal
            yield "data: [DONE]\n\n"
            
        except Exception as e:
            logger.error(f"Streaming generation error: {e}")
            yield f"data: Error: {str(e)}\n\n"
            yield "data: [DONE]\n\n"
    
    return StreamingResponse(
        generate_stream(),
        media_type="text/plain",
        headers={
            "Cache-Control": "no-cache", 
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"  # Disable nginx buffering
        }
    )

@vlm_router.post("/generate", response_model=VLMGenerateResponse)
async def vlm_generate_text(request: VLMGenerateRequest, http_request: Request):
    """Generate text using VLM model with optional image input (non-streaming)"""
    
    # Log request details
    client_ip = http_request.client.host if http_request.client else "unknown"
    logger.info(f"Generation request from {client_ip}, prompt length: {len(request.prompt)}")
    
    # Validate request
    if not request.prompt or not request.prompt.strip():
        raise HTTPException(status_code=400, detail="Empty prompt provided")
    
    if len(request.prompt) > 50000:  # 50k character limit
        raise HTTPException(status_code=400, detail="Prompt too long (max 50,000 characters)")
    
    # Decode image if provided
    image_data = None
    if request.image_base64:
        try:
            image_data = base64.b64decode(request.image_base64)
            # Validate image size (50MB limit)
            if len(image_data) > 50 * 1024 * 1024:
                raise HTTPException(status_code=400, detail="Image too large (max 50MB)")
        except Exception as e:
            logger.error(f"Base64 decode error: {e}")
            raise HTTPException(status_code=400, detail=f"Invalid base64 image data: {str(e)}")
    
    # Decode audio if provided
    audio_data = None
    if request.audio_base64:
        try:
            audio_data = base64.b64decode(request.audio_base64)
            # Validate audio size (10MB limit)
            if len(audio_data) > 10 * 1024 * 1024:
                raise HTTPException(status_code=400, detail="Audio too large (max 10MB)")
        except Exception as e:
            logger.error(f"Audio base64 decode error: {e}")
            raise HTTPException(status_code=400, detail=f"Invalid base64 audio data: {str(e)}")
    
    # Check if VLM service is available
    if not await vlm_service.check_connection():
        raise HTTPException(
            status_code=503, 
            detail=f"VLM service unavailable: {vlm_service.status.last_error}"
        )
    
    request_start = time.time()
    
    try:
        success, result, metadata = await vlm_service.generate_text(
            prompt=request.prompt,
            prompt_name=request.prompt_name,
            max_tokens=request.max_tokens,
            temperature=request.temperature,
            top_k=request.top_k,
            top_p=request.top_p,
            image_data=image_data,
            audio_data=audio_data
        )
        
        request_time = time.time() - request_start
        
        if not success:
            logger.error(f"Generation failed in {request_time:.2f}s: {result}")
            # Determine appropriate HTTP status code based on error type
            if any(phrase in result.lower() for phrase in ["not available", "not loaded", "timeout"]):
                raise HTTPException(status_code=503, detail=result)
            elif "invalid" in result.lower() or "failed to process" in result.lower():
                raise HTTPException(status_code=400, detail=result)
            else:
                raise HTTPException(status_code=500, detail=result)
        
        logger.info(f"Generation completed in {request_time:.2f}s, response length: {len(result)}")
        
        # Add request timing to metadata
        if metadata:
            metadata["request_time_ms"] = int(request_time * 1000)
        
        return VLMGenerateResponse(
            generated_text=result,
            prompt=request.prompt,
            metadata=metadata
        )
        
    except HTTPException:
        raise  # Re-raise HTTP exceptions as-is
    except Exception as e:
        request_time = time.time() - request_start
        logger.error(f"Unexpected error after {request_time:.2f}s: {e}")
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")

@vlm_router.post("/generate/image", response_model=VLMGenerateResponse)
async def vlm_generate_with_image_upload(
    prompt: str,
    image: UploadFile = File(...),
    max_tokens: int = 512,
    temperature: float = 1.0,
    top_k: int = 64,
    top_p: float = 0.95,
    http_request: Request = None
):
    """Generate text using VLM model with image file upload"""
    
    # Log request details
    client_ip = http_request.client.host if http_request and http_request.client else "unknown"
    logger.info(f"Image upload request from {client_ip}, prompt length: {len(prompt)}")
    
    # Validate prompt
    if not prompt or not prompt.strip():
        raise HTTPException(status_code=400, detail="Empty prompt provided")
    
    if len(prompt) > 50000:
        raise HTTPException(status_code=400, detail="Prompt too long (max 50,000 characters)")
    
    # Validate image file
    if not image.content_type or not image.content_type.startswith('image/'):
        raise HTTPException(status_code=400, detail="File must be an image")
    
    # Check file size before reading
    if hasattr(image, 'size') and image.size > 50 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image file too large (max 50MB)")
    
    # Check if VLM service is available
    if not await vlm_service.check_connection():
        raise HTTPException(
            status_code=503, 
            detail=f"VLM service unavailable: {vlm_service.status.last_error}"
        )
    
    request_start = time.time()
    
    try:
        # Read image data with size limit
        image_data = await image.read()
        
        if len(image_data) > 50 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="Image data too large (max 50MB)")
        
        logger.info(f"Processing image: {len(image_data)} bytes, type: {image.content_type}")
        
        success, result, metadata = await vlm_service.generate_text(
            prompt=prompt,
            max_tokens=max_tokens,
            temperature=temperature,
            top_k=top_k,
            top_p=top_p,
            image_data=image_data
        )
        
        request_time = time.time() - request_start
        
        if not success:
            logger.error(f"Image generation failed in {request_time:.2f}s: {result}")
            if any(phrase in result.lower() for phrase in ["not available", "not loaded", "timeout"]):
                raise HTTPException(status_code=503, detail=result)
            elif "invalid" in result.lower() or "failed to process" in result.lower():
                raise HTTPException(status_code=400, detail=result)
            else:
                raise HTTPException(status_code=500, detail=result)
        
        logger.info(f"Image generation completed in {request_time:.2f}s, response length: {len(result)}")
        
        # Add request timing to metadata
        if metadata:
            metadata["request_time_ms"] = int(request_time * 1000)
            metadata["image_size_bytes"] = len(image_data)
        
        return VLMGenerateResponse(
            generated_text=result,
            prompt=prompt,
            metadata=metadata
        )
        
    except HTTPException:
        raise  # Re-raise HTTP exceptions as-is
    except Exception as e:
        request_time = time.time() - request_start
        logger.error(f"Image processing error after {request_time:.2f}s: {e}")
        raise HTTPException(status_code=500, detail=f"Error processing image: {str(e)}")

@vlm_router.post("/clear")
async def clear_conversation(http_request: Request):
    """Clear conversation history"""
    client_ip = http_request.client.host if http_request.client else "unknown"
    logger.info(f"Clearing conversation history for {client_ip}")
    
    try:
        vlm_service.clear_conversation()
        return JSONResponse(content={"message": "Conversation history cleared"})
    except Exception as e:
        logger.error(f"Error clearing conversation: {e}")
        raise HTTPException(status_code=500, detail="Failed to clear conversation history")

@vlm_router.get("/conversation/history")
async def get_conversation_history():
    """Get current conversation history (without image data)"""
    history = []
    for message in vlm_service.conversation_history:
        history_entry = {
            "role": message["role"],
            "content": []
        }
        for content in message["content"]:
            if content["type"] == "text":
                history_entry["content"].append({
                    "type": "text",
                    "text": content["text"]
                })
            elif content["type"] == "image":
                history_entry["content"].append({
                    "type": "image",
                    "size": content["image"].size if hasattr(content["image"], "size") else "unknown"
                })
        history.append(history_entry)
    
    return JSONResponse(content={
        "conversation_history": history,
        "length": len(history)
    })