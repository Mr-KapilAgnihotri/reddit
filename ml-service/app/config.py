"""Runtime configuration for the ML moderation service (all values come from env vars)."""
import os


def _env_bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}


# ── Kafka ────────────────────────────────────────────────────────────────────
# Docker: kafka:29092 (internal listener) · Local dev on the host: localhost:9093
KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "kafka:29092")
CONSUMER_GROUP = os.getenv("KAFKA_CONSUMER_GROUP", "ml-moderation-group")

# Topic names — must match Spring Boot exactly
POST_TOPIC = os.getenv("POST_TOPIC", "post-created")
RESULT_TOPIC = os.getenv("RESULT_TOPIC", "post-moderated")
EMBEDDING_TOPIC = os.getenv("EMBEDDING_TOPIC", "post-embedding")

# Start the background Kafka consumer thread with the FastAPI app
ENABLE_KAFKA_CONSUMER = _env_bool("ENABLE_KAFKA_CONSUMER", True)

# ── LLM moderation (Groq / any OpenAI-compatible endpoint) ───────────────────
# Groq offers a free tier: create a key at https://console.groq.com/keys
# To use xAI Grok instead: LLM_BASE_URL=https://api.x.ai/v1, LLM_MODEL=grok-3-mini
LLM_API_KEY = os.getenv("LLM_API_KEY") or os.getenv("GROQ_API_KEY") or ""
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.groq.com/openai/v1").rstrip("/")
LLM_MODEL = os.getenv("LLM_MODEL", "llama-3.1-8b-instant")
LLM_TIMEOUT_SEC = float(os.getenv("LLM_TIMEOUT_SEC", "10"))
# Set LLM_ENABLED=false to force the offline profanity filter even if a key is present
LLM_ENABLED = _env_bool("LLM_ENABLED", True) and bool(LLM_API_KEY)

# ── Embeddings ───────────────────────────────────────────────────────────────
# Must match the posts.embedding vector(384) column in PostgreSQL
EMBEDDING_DIM = 384
