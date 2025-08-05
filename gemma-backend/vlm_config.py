import os
import logging
from typing import List
from dotenv import load_dotenv #type: ignore

# Load environment variables from .env file
load_dotenv()

class VLMConfig:
    """VLM-only server configuration settings"""
    
    # Server settings
    HOST: str = os.getenv("VLM_HOST", os.getenv("HOST", "0.0.0.0"))
    PORT: int = int(os.getenv("VLM_PORT", os.getenv("PORT", "8000")))
    
    # Logging
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO").upper()
    
    # VLM Model settings
    VLM_MODEL: str = os.getenv("VLM_MODEL", "mlx-community/gemma-3n-E4B-it-bf16")
    VLM_MAX_TOKENS: int = int(os.getenv("VLM_MAX_TOKENS", "2048"))
    VLM_TEMPERATURE: float = float(os.getenv("VLM_TEMPERATURE", "0.5"))
    VLM_TOP_K: int = int(os.getenv("VLM_TOP_K", "64"))
    VLM_TOP_P: float = float(os.getenv("VLM_TOP_P", "0.95"))
    
    # VLM Service settings
    VLM_TIMEOUT: int = int(os.getenv("VLM_TIMEOUT", "300"))  # 5 minutes
    VLM_RETRY_ATTEMPTS: int = int(os.getenv("VLM_RETRY_ATTEMPTS", "3"))
    VLM_RETRY_DELAY: int = int(os.getenv("VLM_RETRY_DELAY", "5"))  # seconds
    VLM_LOAD_TIMEOUT: int = int(os.getenv("VLM_LOAD_TIMEOUT", "120"))  # 2 minutes
    
    # Conversation settings
    MAX_CONVERSATION_LENGTH: int = int(os.getenv("MAX_CONVERSATION_LENGTH", "10"))
    MAX_IMAGE_SIZE_MB: int = int(os.getenv("MAX_IMAGE_SIZE_MB", "50"))
    MAX_PROMPT_LENGTH: int = int(os.getenv("MAX_PROMPT_LENGTH", "50000"))
    
    # CORS settings
    CORS_ORIGINS: List[str] = (
        [origin.strip() for origin in os.getenv("CORS_ORIGINS", "*").split(",")]
        if os.getenv("CORS_ORIGINS") else ["*"]
    )
    
    # Performance settings
    ENABLE_REQUEST_LOGGING: bool = os.getenv("ENABLE_REQUEST_LOGGING", "true").lower() == "true"
    ENABLE_PERFORMANCE_METRICS: bool = os.getenv("ENABLE_PERFORMANCE_METRICS", "true").lower() == "true"
    
    @classmethod
    def setup_logging(cls):
        """Setup VLM application logging"""
        logging.basicConfig(
            level=getattr(logging, cls.LOG_LEVEL, logging.INFO),
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )
        logger = logging.getLogger(__name__)
        logger.info(f"VLM Configuration: Model={cls.VLM_MODEL}")
        logger.info(f"Server: {cls.HOST}:{cls.PORT}")
        logger.info(f"Max tokens: {cls.VLM_MAX_TOKENS}, Temperature: {cls.VLM_TEMPERATURE}")
        logger.info(f"Timeouts: Load={cls.VLM_LOAD_TIMEOUT}s, Generation={cls.VLM_TIMEOUT}s")
        return logger

# Global VLM configuration instance
vlm_config = VLMConfig()