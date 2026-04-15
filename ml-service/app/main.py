"""
ML Moderation Service — FastAPI entry point.

Architecture:
  • FastAPI serves HTTP requests (REST + /docs).
  • A daemon thread runs the Kafka consumer loop (start_consumer).
  • Uses the modern `lifespan` context manager instead of the deprecated
    @on_event("startup"/"shutdown") hooks.
"""

from contextlib import asynccontextmanager
import logging
import threading

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.consumer import start_consumer
from app.moderation import process_text

# ── Logging ─────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
)
logger = logging.getLogger(__name__)


# ── Pydantic schemas ─────────────────────────────────────────────────────────

class ModerateRequest(BaseModel):
    postId: int = Field(..., description="Unique post identifier")
    text: str = Field(..., description="Post body text to moderate")


class ModerateResponse(BaseModel):
    postId: int
    originalText: str
    maskedText: str
    isModerated: bool


# ── Lifespan ─────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Start the Kafka consumer in a background daemon thread at startup."""
    logger.info("🚀 ML service starting — launching Kafka consumer thread")
    thread = threading.Thread(target=start_consumer, daemon=True, name="kafka-consumer")
    thread.start()
    logger.info("Kafka consumer thread started (tid=%s)", thread.ident)
    yield
    # Shutdown: daemon thread will be killed automatically when process exits.
    logger.info("ML service shutting down")


# ── App ───────────────────────────────────────────────────────────────────────

app = FastAPI(
    title="Reddit ML Moderation Service",
    description="Consumes posts from Kafka, applies profanity filtering, and publishes results.",
    version="1.0.0",
    lifespan=lifespan,
)

# ── Routes ────────────────────────────────────────────────────────────────────

@app.get("/", tags=["meta"])
def root():
    return {"service": "ML Moderation Service", "status": "running"}


@app.get("/health", tags=["meta"])
def health():
    """Health-check endpoint for Docker / load-balancer probes."""
    return {"status": "healthy"}


@app.post("/moderate", response_model=ModerateResponse, tags=["moderation"])
def moderate(request: ModerateRequest):
    """
    Moderate a single post synchronously via HTTP.

    Useful for manual testing without going through Kafka.
    """
    try:
        result = process_text(request.model_dump())
        return result
    except Exception as exc:
        logger.exception("Error moderating postId=%s: %s", request.postId, exc)
        raise HTTPException(status_code=500, detail="Moderation failed")