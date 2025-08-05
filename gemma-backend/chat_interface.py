#!/usr/bin/env python3

import sys
import os
from PIL import Image #type: ignore

class GemmaChat:
    def __init__(self):
        self.model = None
        self.processor = None
        self.config = None
        self.conversation_history = []
        self.max_history_length = 10  # Limit conversation history
        self.setup_model()
    
    def setup_model(self):
        """Initialize the MLX-VLM model"""
        try:
            from mlx_vlm import load #type: ignore
            from mlx_vlm.utils import load_config #type: ignore
            
            print("🚀 Loading Gemma 3n multimodal model...")
            model_name = "mlx-community/gemma-3n-E4B-it-bf16"
            self.model, self.processor = load(model_name)
            self.config = load_config(model_name)
            print("✅ Model loaded successfully!")
            print("💬 Ready for multimodal chat (text + images)")
            
        except Exception as e:
            print(f"❌ Failed to load model: {e}")
            sys.exit(1)
    
    def add_text_message(self, text: str, role: str = "user"):
        """Add text message to conversation history"""
        message = {
            "role": role,
            "content": [{"type": "text", "text": text}]
        }
        self.conversation_history.append(message)
        self._trim_history()
    
    def add_image_message(self, image_path: str, text: str, role: str = "user"):
        """Add image + text message to conversation history"""
        try:
            image = Image.open(image_path).convert('RGB')
            message = {
                "role": role,
                "content": [
                    {"type": "text", "text": text},
                    {"type": "image", "image": image}
                ]
            }
            self.conversation_history.append(message)
            self._trim_history()
            return True
        except Exception as e:
            print(f"❌ Error loading image: {e}")
            return False
    
    def generate_response(self) -> str:
        """Generate response using current conversation history"""
        try:
            from mlx_vlm import generate #type: ignore
            from mlx_vlm.prompt_utils import apply_chat_template #type: ignore
            
            # Count images in the conversation
            num_images = 0
            images = []
            
            for message in self.conversation_history:
                for content in message["content"]:
                    if content["type"] == "image":
                        num_images += 1
                        images.append(content["image"])
            
            # Apply chat template with properly formatted conversation
            conversation_string = self.format_conversation_for_gemma()
            formatted_prompt = apply_chat_template(
                self.processor, 
                self.config, 
                conversation_string,
                num_images=num_images
            )
            
            # Generate response
            response = generate(
                self.model,
                self.processor,
                formatted_prompt,
                images if images else None,
                max_tokens=512,
                verbose=False
            )
            
            # Add assistant's response to history
            self.add_text_message(response.text, role="assistant")
            
            return response.text
            
        except Exception as e:
            return f"Error generating response: {e}"
    
    def format_conversation_for_gemma(self) -> str:
        """Convert conversation history to Gemma chat format"""
        formatted_parts = []
        
        for message in self.conversation_history:
            role = message["role"]
            
            # Convert role names (user stays user, assistant becomes model)
            gemma_role = "model" if role == "assistant" else "user"
            
            # Extract text content
            text_content = ""
            for content in message["content"]:
                if content["type"] == "text":
                    text_content += content["text"]
            
            # Add Gemma format tokens
            formatted_parts.append(f"<start_of_turn>{gemma_role}\n{text_content}<end_of_turn>\n")
        
        # Add the model turn starter for generation
        formatted_parts.append("<start_of_turn>model\n")
        
        return "".join(formatted_parts)
    
    def _trim_history(self):
        """Keep conversation history within memory limits"""
        if len(self.conversation_history) > self.max_history_length:
            # Remove oldest messages, but keep pairs to maintain conversation flow
            excess = len(self.conversation_history) - self.max_history_length
            # Close any PIL images being removed to free memory
            for i in range(excess):
                message = self.conversation_history[i]
                for content in message.get("content", []):
                    if content.get("type") == "image" and hasattr(content.get("image"), "close"):
                        content["image"].close()
            self.conversation_history = self.conversation_history[excess:]
    
    def print_help(self):
        """Print help commands"""
        print("\n" + "="*50)
        print("📋 COMMANDS:")
        print("="*50)
        print("• Just type to chat normally")
        print("• /image <path> <question> - Analyze an image")
        print("• /history - Show conversation history")
        print("• /clear - Clear conversation history")
        print("• /help - Show this help")
        print("• /quit - Exit chat")
        print("="*50)
    
    def show_history(self):
        """Display conversation history"""
        print("\n" + "="*50)
        print("📝 CONVERSATION HISTORY:")
        print("="*50)
        
        for i, message in enumerate(self.conversation_history, 1):
            role = message["role"]
            emoji = "🙋‍♂️" if role == "user" else "🤖"
            print(f"{i}. {emoji} {role.upper()}:")
            
            for content in message["content"]:
                if content["type"] == "text":
                    print(f"   💬 {content['text'][:100]}{'...' if len(content['text']) > 100 else ''}")
                elif content["type"] == "image":
                    print(f"   🖼️  [Image: {content['image'].size}]")
            print()
    
    def run(self):
        """Main chat loop"""
        print("\n" + "="*60)
        print("🎯 GEMMA 3N MULTIMODAL CHAT")
        print("="*60)
        print("Ready for text and image conversations!")
        self.print_help()
        
        while True:
            try:
                user_input = input("\n💬 You: ").strip()
                
                if not user_input:
                    continue
                
                # Handle commands
                if user_input.startswith('/'):
                    command_parts = user_input.split(' ', 2)
                    command = command_parts[0].lower()
                    
                    if command == '/quit':
                        print("👋 Goodbye!")
                        break
                    
                    elif command == '/help':
                        self.print_help()
                        continue
                    
                    elif command == '/clear':
                        # Close any PIL images before clearing
                        for message in self.conversation_history:
                            for content in message.get("content", []):
                                if content.get("type") == "image" and hasattr(content.get("image"), "close"):
                                    content["image"].close()
                        self.conversation_history = []
                        print("🗑️  Conversation history cleared!")
                        continue
                    
                    elif command == '/history':
                        self.show_history()
                        continue
                    
                    elif command == '/image':
                        if len(command_parts) < 3:
                            print("❌ Usage: /image <path> <question>")
                            continue
                        
                        image_path = command_parts[1]
                        question = command_parts[2]
                        
                        if not os.path.exists(image_path):
                            print(f"❌ Image not found: {image_path}")
                            continue
                        
                        print(f"🖼️  Analyzing image: {image_path}")
                        if self.add_image_message(image_path, question):
                            print("🧠 Generating response...")
                            response = self.generate_response()
                            print(f"🤖 Gemma: {response}")
                        continue
                    
                    else:
                        print(f"❌ Unknown command: {command}")
                        continue
                
                # Regular text message
                self.add_text_message(user_input)
                print("🧠 Generating response...")
                response = self.generate_response()
                print(f"🤖 Gemma: {response}")
                
            except KeyboardInterrupt:
                print("\n👋 Goodbye!")
                break
            except Exception as e:
                print(f"❌ Error: {e}")

def main():
    chat = GemmaChat()
    chat.run()

if __name__ == "__main__":
    main()