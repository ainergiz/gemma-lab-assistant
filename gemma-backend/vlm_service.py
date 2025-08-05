import logging
import asyncio
import time
import gc
from typing import Optional, Tuple, Dict, Any
from PIL import Image # type: ignore
import io
from prompts import prompt_registry

logger = logging.getLogger(__name__)

class VLMStatus:
    """Class to track VLM model status"""
    def __init__(self):
        self.is_loaded = False
        self.model_name: Optional[str] = None
        self.last_error: Optional[str] = None
        self.load_attempts = 0
        self.last_load_attempt = 0
        self.consecutive_failures = 0

class VLMService:
    """Service class for MLX-VLM integration"""
    
    def __init__(self):
        self.status = VLMStatus()
        self.model = None
        self.processor = None
        self.config_obj = None
        self.conversation_history = []
        self.max_history_length = 10
        self.max_retries = 3
        self.retry_delay = 5  # seconds
        self.load_timeout = 60  # seconds
        self._load_lock = asyncio.Lock()
    
    async def check_connection(self) -> bool:
        """Check if VLM model is loaded and available"""
        if not self.status.is_loaded:
            # Check if we should retry loading
            current_time = time.time()
            if (self.status.load_attempts > 0 and 
                current_time - self.status.last_load_attempt < self.retry_delay):
                return False
            return await self.load_model()
        return True
    
    async def load_model(self) -> bool:
        """Load the MLX-VLM model with retry logic and timeout"""
        async with self._load_lock:
            if self.status.is_loaded:
                return True
                
            self.status.load_attempts += 1
            self.status.last_load_attempt = time.time()
            
            for attempt in range(self.max_retries):
                try:
                    logger.info(f"🚀 Loading Gemma 3N model (attempt {attempt + 1}/{self.max_retries})...")
                    
                    # Load with timeout
                    model_name = "mlx-community/gemma-3n-E4B-it-bf16"
                    
                    # Run model loading in executor to avoid blocking
                    loop = asyncio.get_event_loop()
                    
                    def _load_model_sync():
                        try:
                            from mlx_vlm import load
                            from mlx_vlm.utils import load_config
                            return load(model_name), load_config(model_name)
                        except Exception as e:
                            logger.error(f"Model loading failed: {e}")
                            raise
                    
                    # Load with timeout
                    (model, processor), config_obj = await asyncio.wait_for(
                        loop.run_in_executor(None, _load_model_sync),
                        timeout=self.load_timeout
                    )
                    
                    self.model = model
                    self.processor = processor
                    self.config_obj = config_obj
                    
                    self.status.is_loaded = True
                    self.status.model_name = model_name
                    self.status.last_error = None
                    self.status.consecutive_failures = 0
                    
                    logger.info("✅ VLM Model loaded successfully!")
                    logger.info("💬 Ready for multimodal chat (text + images)")
                    return True
                    
                except asyncio.TimeoutError:
                    error_msg = f"Model loading timeout ({self.load_timeout}s)"
                    logger.error(f"❌ {error_msg}")
                    self.status.last_error = error_msg
                    
                except Exception as e:
                    error_msg = f"Model loading failed: {str(e)}"
                    logger.error(f"❌ {error_msg}")
                    self.status.last_error = error_msg
                    
                    # Wait before retry
                    if attempt < self.max_retries - 1:
                        logger.info(f"⏳ Retrying in {self.retry_delay} seconds...")
                        await asyncio.sleep(self.retry_delay)
                    
                    # Clear any partial state
                    self.model = None
                    self.processor = None
                    self.config_obj = None
                    gc.collect()  # Force garbage collection
            
            # All attempts failed
            self.status.is_loaded = False
            self.status.consecutive_failures += 1
            logger.error(f"❌ Failed to load VLM model after {self.max_retries} attempts")
            return False
    
    def add_text_message(self, text: str, role: str = "user", clear_media: bool = False):
        """Add text message to conversation history"""
        # Clear previous media if this is a new user message without media
        if clear_media and role == "user":
            self._clear_previous_images()
            self._clear_previous_audio()
            
        message = {
            "role": role,
            "content": [{"type": "text", "text": text}]
        }
        self.conversation_history.append(message)
        self._trim_history()
    
    def add_audio_message(self, audio_data: bytes, text: str, role: str = "user", clear_previous_media: bool = True) -> bool:
        """Add audio + text message to conversation history with format conversion"""
        try:
            # Clear previous media when adding new audio (unless part of multi-media message)
            if role == "user" and clear_previous_media:
                self._clear_previous_images()
                self._clear_previous_audio()
                
            # Validate audio data
            if not audio_data or len(audio_data) == 0:
                logger.error("Empty audio data provided")
                return False
                
            # Check audio size (limit to 10MB)
            if len(audio_data) > 10 * 1024 * 1024:
                logger.error(f"Audio too large: {len(audio_data)} bytes")
                return False
            
            # Convert audio to WAV format for MLX-VLM processing
            import tempfile
            import subprocess
            import os
            
            # Create temp file for incoming audio (with unknown format)
            temp_input_file = tempfile.NamedTemporaryFile(suffix='.audio', delete=False)
            temp_input_file.write(audio_data)
            temp_input_file.close()
            
            # Create temp file for WAV output
            temp_wav_file = tempfile.NamedTemporaryFile(suffix='.wav', delete=False)
            temp_wav_file.close()
            
            try:
                # Use ffmpeg to convert any audio format to WAV with lower quality
                result = subprocess.run([
                    'ffmpeg', '-y',  # -y to overwrite output file
                    '-i', temp_input_file.name,  # input file
                    '-ar', '8000',   # sample rate 8kHz (lower quality)
                    '-ac', '1',      # mono
                    '-c:a', 'pcm_s16le',  # 16-bit PCM
                    temp_wav_file.name   # output file
                ], capture_output=True, text=True, timeout=30)
                
                if result.returncode != 0:
                    logger.error(f"FFmpeg conversion failed: {result.stderr}")
                    return False
                
                # Verify the WAV file was created and has content
                if not os.path.exists(temp_wav_file.name) or os.path.getsize(temp_wav_file.name) == 0:
                    logger.error("Audio conversion produced empty file")
                    return False
                
                logger.info(f"✅ Audio converted to WAV: {os.path.getsize(temp_wav_file.name)} bytes")
                
                message = {
                    "role": role,
                    "content": [
                        {"type": "text", "text": text},
                        {"type": "audio", "audio_path": temp_wav_file.name}
                    ]
                }
                self.conversation_history.append(message)
                self._trim_history()
                logger.info("✅ Audio message added to conversation")
                return True
                
            except subprocess.TimeoutExpired:
                logger.error("Audio conversion timeout")
                return False
            except FileNotFoundError:
                logger.error("FFmpeg not found - please install ffmpeg for audio processing")
                return False
            finally:
                # Clean up input temp file
                try:
                    os.unlink(temp_input_file.name)
                except:
                    pass
            
        except Exception as e:
            logger.error(f"❌ Error processing audio: {e}")
            return False
    
    def add_image_message(self, image_data: bytes, text: str, role: str = "user", clear_previous_media: bool = True) -> bool:
        """Add image + text message to conversation history with proper error handling"""
        image = None
        try:
            # Validate image data
            if not image_data or len(image_data) == 0:
                logger.error("Empty image data provided")
                return False
                
            # Check image size (limit to 50MB)
            if len(image_data) > 50 * 1024 * 1024:
                logger.error(f"Image too large: {len(image_data)} bytes")
                return False
            
            # Clear previous images from conversation history before adding new one (unless part of multi-media message)
            if clear_previous_media:
                self._clear_previous_images()
            
            image = Image.open(io.BytesIO(image_data))
            
            # Convert to RGB and validate
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # Limit image dimensions - small size to prevent GPU memory exhaustion
            max_size = (512, 512)
            if image.size[0] > max_size[0] or image.size[1] > max_size[1]:
                image.thumbnail(max_size, Image.Resampling.LANCZOS)
                logger.info(f"Resized image to {image.size}")
            
            message = {
                "role": role,
                "content": [
                    {"type": "text", "text": text},
                    {"type": "image", "image": image}
                ]
            }
            self.conversation_history.append(message)
            self._trim_history()
            logger.info("✅ New image added to conversation, previous images cleared")
            return True
            
        except Exception as e:
            logger.error(f"❌ Error processing image: {e}")
            # Clean up if image was partially loaded
            if image:
                try:
                    image.close()
                except:
                    pass
            return False
    
    def _clear_previous_images(self):
        """Clear all messages containing images from conversation history"""
        messages_to_remove = []
        
        for i, message in enumerate(self.conversation_history):
            has_image = False
            for content in message.get("content", []):
                if content.get("type") == "image":
                    has_image = True
                    # Close the image to free memory
                    image = content.get("image")
                    if image and hasattr(image, "close"):
                        try:
                            image.close()
                        except Exception as e:
                            logger.warning(f"Error closing previous image: {e}")
                    break
            
            # Mark messages with images for removal
            if has_image:
                messages_to_remove.append(i)
        
        # Remove messages with images (in reverse order to maintain indices)
        for i in reversed(messages_to_remove):
            removed_msg = self.conversation_history.pop(i)
            logger.debug(f"Removed image message: {removed_msg.get('role', 'unknown')}")
        
        logger.info(f"🧹 Cleared {len(messages_to_remove)} image messages, kept {len(self.conversation_history)} text messages")
        # Force garbage collection after clearing images
        gc.collect()
    
    def _clear_previous_audio(self):
        """Clear all messages containing audio from conversation history"""
        messages_to_remove = []
        
        for i, message in enumerate(self.conversation_history):
            has_audio = False
            for content in message.get("content", []):
                if content.get("type") == "audio":
                    has_audio = True
                    # Clean up temporary audio files
                    audio_path = content.get("audio_path")
                    if audio_path:
                        try:
                            import os
                            if os.path.exists(audio_path):
                                os.unlink(audio_path)
                                logger.debug(f"Cleaned up audio file: {audio_path}")
                        except Exception as e:
                            logger.warning(f"Error removing audio file {audio_path}: {e}")
                    break
            
            # Mark messages with audio for removal
            if has_audio:
                messages_to_remove.append(i)
        
        # Remove messages with audio (in reverse order to maintain indices)
        for i in reversed(messages_to_remove):
            removed_msg = self.conversation_history.pop(i)
            logger.debug(f"Removed audio message: {removed_msg.get('role', 'unknown')}")
        
        logger.info(f"🧹 Cleared {len(messages_to_remove)} audio messages, kept {len(self.conversation_history)} text messages")
        # Force garbage collection after clearing audio
        gc.collect()
    
    def clear_conversation(self):
        """Clear conversation history and free image/audio resources"""
        for message in self.conversation_history:
            for content in message.get("content", []):
                if content.get("type") == "image":
                    image = content.get("image")
                    if image and hasattr(image, "close"):
                        try:
                            image.close()
                        except Exception as e:
                            logger.warning(f"Error closing image: {e}")
                elif content.get("type") == "audio":
                    # Clean up temporary audio files
                    audio_path = content.get("audio_path")
                    if audio_path:
                        try:
                            import os
                            if os.path.exists(audio_path):
                                os.unlink(audio_path)
                                logger.debug(f"Cleaned up audio file: {audio_path}")
                        except Exception as e:
                            logger.warning(f"Error removing audio file {audio_path}: {e}")
        self.conversation_history = []
        # Force garbage collection after clearing
        gc.collect()
    
    def format_conversation_for_gemma(self) -> str:
        """Convert conversation history to Gemma chat format - let apply_chat_template handle audio tokens"""
        formatted_parts = []
        
        for message in self.conversation_history:
            role = message["role"]
            gemma_role = "model" if role == "assistant" else "user"
            
            text_content = ""
            
            # Only extract text content - apply_chat_template will handle audio tokens
            for content in message["content"]:
                if content["type"] == "text":
                    text_content += content["text"]
                # Skip audio content - apply_chat_template handles audio token placement
            
            formatted_parts.append(f"<start_of_turn>{gemma_role}\n{text_content}<end_of_turn>\n")
        
        formatted_parts.append("<start_of_turn>model\n")
        return "".join(formatted_parts)
    
    def _trim_history(self):
        """Keep conversation history within memory limits"""
        if len(self.conversation_history) > self.max_history_length:
            excess = len(self.conversation_history) - self.max_history_length
            
            # Clean up images and audio in messages to be removed
            for i in range(excess):
                message = self.conversation_history[i]
                for content in message.get("content", []):
                    if content.get("type") == "image":
                        image = content.get("image")
                        if image and hasattr(image, "close"):
                            try:
                                image.close()
                            except Exception as e:
                                logger.warning(f"Error closing image during trim: {e}")
                    elif content.get("type") == "audio":
                        # Clean up temporary audio files
                        audio_path = content.get("audio_path")
                        if audio_path:
                            try:
                                import os
                                if os.path.exists(audio_path):
                                    os.unlink(audio_path)
                                    logger.debug(f"Cleaned up audio file during trim: {audio_path}")
                            except Exception as e:
                                logger.warning(f"Error removing audio file {audio_path} during trim: {e}")
            
            # Remove old messages
            self.conversation_history = self.conversation_history[excess:]
            
            # Force garbage collection after trimming
            gc.collect()
    
    def generate_text_streaming(self, prompt: str, prompt_name: str = "default", max_tokens: int = 512, 
                               temperature: float = 1.0, top_k: int = 64, 
                               top_p: float = 0.95, image_data: Optional[bytes] = None,
                               audio_data: Optional[bytes] = None):
        """
        Generate text using VLM model with streaming and robust error handling
        Yields: text tokens as they are generated
        """
        
        # Validate inputs
        if not prompt or not prompt.strip():
            yield "Error: Empty prompt provided"
            return
            
        if max_tokens <= 0 or max_tokens > 4096:
            yield "Error: Invalid max_tokens value (must be 1-4096)"
            return
        
        # Check if model is loaded
        if not self.status.is_loaded:
            yield f"Error: VLM model is not available. {self.status.last_error or 'Model not loaded'}"
            return
        
        generation_start_time = time.time()
        complete_response = ""
        
        try:
            from mlx_vlm import stream_generate #type: ignore
            from mlx_vlm.prompt_utils import apply_chat_template #type: ignore
            
            # Get system prompt
            system_prompt = prompt_registry.get_prompt(prompt_name)
            
            # Optimize parameters based on prompt type
            if prompt_name == "structured_data_extraction":
                # For JSON extraction, use deterministic settings
                temperature = min(temperature, 0.2)  # Much lower temperature
                top_p = min(top_p, 0.8)  # More focused sampling
                top_k = min(top_k, 40)   # Reduced top-k

            # Add message to conversation history
            if image_data or audio_data:
                # Clear all previous media when any new media is received
                self._clear_previous_images()
                self._clear_previous_audio()
                
                if image_data and audio_data:
                    # Both image and audio
                    if not self.add_image_message(image_data, prompt, clear_previous_media=False):
                        yield "Error: Failed to process image"
                        return
                    if not self.add_audio_message(audio_data, "", clear_previous_media=False):
                        yield "Error: Failed to process audio"
                        return
                elif image_data:
                    if not self.add_image_message(image_data, prompt, clear_previous_media=False):
                        yield "Error: Failed to process image"
                        return
                elif audio_data:
                    if not self.add_audio_message(audio_data, prompt, clear_previous_media=False):
                        yield "Error: Failed to process audio"
                        return
            else:
                # For text-only messages, clear previous media
                self.add_text_message(prompt, clear_media=True)
            
            # Get only the current image and audio (from the most recent messages)
            num_images = 0
            num_audios = 0
            images = []
            audio_paths = []
            
            # Look through messages in reverse order to find the most recent image and audio
            for message in reversed(self.conversation_history):
                for content in message["content"]:
                    if content["type"] == "image" and num_images == 0:
                        num_images = 1  # Only count the most recent image
                        images = [content["image"]]  # Only use the most recent image
                    elif content["type"] == "audio" and not audio_paths:
                        num_audios = 1  # Count the audio
                        audio_paths = [content["audio_path"]]  # Only use the most recent audio
            
            # Apply chat template with properly formatted conversation
            try:
                conversation_string = self.format_conversation_for_gemma()
                if system_prompt:
                    conversation_string = f"{system_prompt}\n\n{conversation_string}"
                logger.debug(f"Formatted prompt for model:\n{conversation_string}")
                
                formatted_prompt = apply_chat_template(
                    self.processor, 
                    self.config_obj, 
                    conversation_string,
                    num_images=num_images,
                    num_audios=num_audios
                )
            except Exception as e:
                logger.error(f"Template formatting error: {e}")
                yield f"Error: Failed to format prompt: {str(e)}"
                return
            
            # Stream generate response with timeout handling
            try:
                generator = stream_generate(
                    self.model,
                    self.processor,
                    formatted_prompt,
                    images if images else None,
                    audio=audio_paths if audio_paths else None,
                    max_tokens=max_tokens,
                    temperature=temperature,
                    top_k=top_k,
                    top_p=top_p
                )
                
                # Process tokens with timeout
                token_count = 0
                last_token_time = time.time()
                
                for result in generator:
                    current_time = time.time()
                    
                    # Check for generation timeout (60s per token for audio, 30s for others)
                    timeout_per_token = 60 if num_audios > 0 else 30
                    if current_time - last_token_time > timeout_per_token:
                        logger.error(f"Token generation timeout ({timeout_per_token}s)")
                        yield "Error: Generation timeout"
                        break
                    
                    # Check for total timeout (10 minutes for audio, 5 minutes for others)
                    total_timeout = 600 if num_audios > 0 else 300
                    if current_time - generation_start_time > total_timeout:
                        logger.error(f"Total generation timeout ({total_timeout}s)")
                        yield "Error: Total generation timeout"
                        break
                    
                    token_text = result.text if hasattr(result, 'text') else str(result)
                    complete_response += token_text
                    token_count += 1
                    last_token_time = current_time
                    
                    yield token_text
                    
                    # Yield control periodically to prevent blocking
                    if token_count % 10 == 0:
                        time.sleep(0.001)  # Small yield
                
                # Add assistant's response to history if we got any response
                if complete_response.strip():
                    self.add_text_message(complete_response.strip(), role="assistant")
                    
            except Exception as e:
                logger.error(f"Generation error: {e}")
                yield f"Error: Generation failed: {str(e)}"
                
        except ImportError as e:
            logger.error(f"MLX-VLM import error: {e}")
            yield "Error: MLX-VLM dependencies not available"
        except Exception as e:
            logger.error(f"VLM streaming generation error: {e}")
            yield f"Error: Generation failed: {str(e)}"
        finally:
            # Cleanup
            generation_time = time.time() - generation_start_time
            logger.info(f"Generation completed in {generation_time:.2f}s, response length: {len(complete_response)}")

    async def generate_text(self, prompt: str, prompt_name: str = "default", max_tokens: int = 512, 
                          temperature: float = 1.0, top_k: int = 64, 
                          top_p: float = 0.95, image_data: Optional[bytes] = None,
                          audio_data: Optional[bytes] = None) -> Tuple[bool, str, Optional[Dict[str, Any]]]:
        """
        Generate text using VLM model (non-streaming) with robust error handling
        Returns: (success: bool, result: str, metadata: Optional[Dict])
        """
        
        # Validate inputs
        if not prompt or not prompt.strip():
            return False, "Empty prompt provided", None
            
        if max_tokens <= 0 or max_tokens > 4096:
            return False, "Invalid max_tokens value (must be 1-4096)", None
        
        # Check if model is loaded
        if not self.status.is_loaded:
            if not await self.load_model():
                return False, f"VLM model is not available. Error: {self.status.last_error}", None
        
        generation_start_time = time.time()
        
        try:
            from mlx_vlm import generate #type: ignore
            from mlx_vlm.prompt_utils import apply_chat_template #type: ignore
            
            # Get system prompt
            system_prompt = prompt_registry.get_prompt(prompt_name)
            
            # Optimize parameters based on prompt type
            if prompt_name == "structured_data_extraction":
                # For JSON extraction, use deterministic settings
                temperature = min(temperature, 0.2)  # Much lower temperature
                top_p = min(top_p, 0.8)  # More focused sampling
                top_k = min(top_k, 40)   # Reduced top-k
            
            # Add message to conversation history
            if image_data or audio_data:
                # Clear all previous media when any new media is received
                self._clear_previous_images()
                self._clear_previous_audio()
                
                if image_data and audio_data:
                    # Both image and audio
                    if not self.add_image_message(image_data, prompt, clear_previous_media=False):
                        return False, "Failed to process image", None
                    if not self.add_audio_message(audio_data, "", clear_previous_media=False):
                        return False, "Failed to process audio", None
                elif image_data:
                    if not self.add_image_message(image_data, prompt, clear_previous_media=False):
                        return False, "Failed to process image", None
                elif audio_data:
                    if not self.add_audio_message(audio_data, prompt, clear_previous_media=False):
                        return False, "Failed to process audio", None
            else:
                # For text-only messages, clear previous media
                self.add_text_message(prompt, clear_media=True)
            
            # Get only the current image and audio (from the most recent messages)
            num_images = 0
            num_audios = 0
            images = []
            audio_paths = []
            
            # Look through messages in reverse order to find the most recent image and audio
            for message in reversed(self.conversation_history):
                for content in message["content"]:
                    if content["type"] == "image" and num_images == 0:
                        num_images = 1  # Only count the most recent image
                        images = [content["image"]]  # Only use the most recent image
                    elif content["type"] == "audio" and not audio_paths:
                        num_audios = 1  # Count the audio
                        audio_paths = [content["audio_path"]]  # Only use the most recent audio
            
            # Apply chat template with proper error handling
            try:
                conversation_string = self.format_conversation_for_gemma()
                if system_prompt:
                    conversation_string = f"{system_prompt}\n\n{conversation_string}"
                logger.debug(f"Formatted prompt for model:\n{conversation_string}")
                
                formatted_prompt = apply_chat_template(
                    self.processor, 
                    self.config_obj, 
                    conversation_string,
                    num_images=num_images,
                    num_audios=num_audios
                )
            except Exception as e:
                logger.error(f"Template formatting error: {e}")
                return False, f"Failed to format prompt: {str(e)}", None
            
            # Generate response with timeout
            try:
                # Run generation with timeout (5 minutes max)
                loop = asyncio.get_event_loop()
                
                def _generate_sync():
                    return generate(
                        self.model,
                        self.processor,
                        formatted_prompt,
                        images if images else None,
                        audio=audio_paths if audio_paths else None,
                        max_tokens=max_tokens,
                        verbose=False
                    )
                
                # Use longer timeout for audio processing
                timeout = 600 if num_audios > 0 else 300  # 10 minutes for audio, 5 for others
                response = await asyncio.wait_for(
                    loop.run_in_executor(None, _generate_sync),
                    timeout=timeout
                )
                
                response_text = response.text.strip() if hasattr(response, 'text') else str(response).strip()
                generation_time = time.time() - generation_start_time
                
                # Add assistant's response to history
                if response_text:
                    self.add_text_message(response_text, role="assistant")
                
                metadata = {
                    "model": self.status.model_name,
                    "num_images": num_images,
                    "conversation_length": len(self.conversation_history),
                    "generation_time_ms": int(generation_time * 1000),
                    "response_length": len(response_text)
                }
                
                logger.info(f"Generation completed in {generation_time:.2f}s, response length: {len(response_text)}")
                return True, response_text, metadata
                
            except asyncio.TimeoutError:
                timeout_msg = f"Generation timeout ({timeout // 60} minutes)"
                logger.error(timeout_msg)
                return False, timeout_msg, None
                
        except ImportError as e:
            logger.error(f"MLX-VLM import error: {e}")
            return False, "MLX-VLM dependencies not available", None
        except Exception as e:
            logger.error(f"VLM generation error: {e}")
            return False, f"Generation failed: {str(e)}", None

# Global service instance
vlm_service = VLMService()