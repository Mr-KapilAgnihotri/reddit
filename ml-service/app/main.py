"""
ML Moderation Service — FastAPI entry point.

  • HTTP:  /health, /stats, POST /moderate, POST /embed (manual testing, /docs)
  • A daemon thread runs the Kafka consumer (post-created → post-moderated / post-embedding)
"""
from __future__ import annotations

import logging
import threading
from contextlib import asynccontextmanager
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app import config
from app.consumer import start_consumer, stats, stop_event
from app.embedder import generate_embedding
from app.moderation import process_text

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
)
logger = logging.getLogger(__name__)


class ModerateRequest(BaseModel):
    postId: int = Field(..., description="Unique post identifier")
    text: str = Field(..., description="Post body text to moderate")
    title: Optional[str] = Field(None, description="Optional post title")


class ModerateResponse(BaseModel):
    postId: int
    originalText: str
    maskedText: str
    maskedTitle: Optional[str] = None
    isModerated: bool
    categories: List[str] = []
    severity: float = 0.0
    reason: str = ""
    provider: str = ""


class EmbedRequest(BaseModel):
    text: str


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("ML service starting | llm_enabled=%s model=%s | kafka=%s",
                config.LLM_ENABLED, config.LLM_MODEL, config.KAFKA_BOOTSTRAP)
    if config.ENABLE_KAFKA_CONSUMER:
        stop_event.clear()
        thread = threading.Thread(target=start_consumer, daemon=True, name="kafka-consumer")
        thread.start()
    yield
    stop_event.set()
    logger.info("ML service shutting down")


app = FastAPI(
    title="Reddit ML Moderation Service",
    description="Consumes posts from Kafka, moderates them (Groq LLM + profanity filter) and publishes results.",
    version="2.0.0",
    lifespan=lifespan,
)


# Allow the frontend (served by Spring Boot on :8080) to read /health and /stats
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.get("/", tags=["meta"])
def root():
    return {"service": "ML Moderation Service", "status": "running"}


@app.get("/health", tags=["meta"])
def health():
    return {"status": "healthy"}


@app.get("/stats", tags=["meta"])
def get_stats():
    return {
        **stats,
        "llmEnabled": config.LLM_ENABLED,
        "llmModel": config.LLM_MODEL if config.LLM_ENABLED else None,
        "kafkaBootstrap": config.KAFKA_BOOTSTRAP,
    }


@app.post("/moderate", response_model=ModerateResponse, tags=["moderation"])
def moderate(request: ModerateRequest):
    """Moderate a single text synchronously (no Kafka)."""
    try:
        return process_text(request.model_dump(exclude_none=True))
    except Exception as exc:
        logger.exception("Error moderating postId=%s: %s", request.postId, exc)
        raise HTTPException(status_code=500, detail="Moderation failed")


@app.post("/embed", tags=["moderation"])
def embed(request: EmbedRequest):
    vector = generate_embedding(request.text)
    return {"dim": len(vector), "embedding": vector}
