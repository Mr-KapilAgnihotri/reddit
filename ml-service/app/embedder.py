"""
Embedding module — generates 384-dimensional text embeddings using the
all-MiniLM-L6-v2 SentenceTransformer model.

Model facts:
  • 22M parameters — lightweight, runs on CPU in ~50ms per post.
  • Output dimension: 384.
  • Pre-trained on 1B+ sentence pairs — excellent semantic similarity.
  • No GPU required. Fits comfortably inside a Docker container.

The model is loaded once at module import time (lazy singleton) so the
expensive initialisation only happens on the first call.
"""

import logging
from functools import lru_cache
from typing import List

logger = logging.getLogger(__name__)

_model = None


def _get_model():
    """Lazy-load the SentenceTransformer model (thread-safe via GIL on first load)."""
    global _model
    if _model is None:
        try:
            from sentence_transformers import SentenceTransformer
            logger.info("Loading SentenceTransformer model: all-MiniLM-L6-v2 ...")
            _model = SentenceTransformer("all-MiniLM-L6-v2")
            logger.info("✅ SentenceTransformer model loaded (dim=384)")
        except Exception as exc:
            logger.error("❌ Failed to load SentenceTransformer: %s", exc)
            raise
    return _model


def generate_embedding(text: str) -> List[float]:
    """
    Generate a 384-dimensional embedding for the given text.

    Args:
        text: The post body text to embed.

    Returns:
        A list of 384 floats representing the text embedding.
    """
    if not text or not text.strip():
        # Return a zero vector for empty text — avoids errors in the pipeline
        logger.warning("generate_embedding called with empty text — returning zero vector")
        return [0.0] * 384

    model = _get_model()
    embedding = model.encode(text, normalize_embeddings=True)
    return embedding.tolist()
