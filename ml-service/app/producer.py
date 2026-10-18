"""Kafka producer for moderation results and embeddings."""
from __future__ import annotations

import json
import logging
import threading
from typing import Optional

from kafka import KafkaProducer
from kafka.errors import KafkaError

from app.config import EMBEDDING_TOPIC, KAFKA_BOOTSTRAP, RESULT_TOPIC

logger = logging.getLogger(__name__)

_producer: Optional[KafkaProducer] = None
_lock = threading.Lock()


def _get_producer() -> KafkaProducer:
    """Create the producer lazily so a missing broker never crashes import."""
    global _producer
    with _lock:
        if _producer is None:
            logger.info("Creating KafkaProducer — broker=%s", KAFKA_BOOTSTRAP)
            _producer = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP,
                key_serializer=lambda k: str(k).encode("utf-8") if k is not None else None,
                value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                retries=5,
                acks="all",
            )
    return _producer


def _send(topic: str, payload: dict) -> bool:
    post_id = payload.get("postId")
    try:
        future = _get_producer().send(topic, key=post_id, value=payload)
        meta = future.get(timeout=10)
        logger.info("Sent to '%s' | partition=%d offset=%d | postId=%s",
                    topic, meta.partition, meta.offset, post_id)
        return True
    except KafkaError as exc:
        logger.error("Failed to send to '%s' for postId=%s: %s", topic, post_id, exc)
    except Exception as exc:
        logger.exception("Unexpected error sending to '%s' for postId=%s: %s", topic, post_id, exc)
    return False


def send_result(result: dict) -> bool:
    """Publish a moderation result to ``post-moderated``."""
    return _send(RESULT_TOPIC, result)


def send_embedding(payload: dict) -> bool:
    """Publish ``{"postId": int, "embedding": [384 floats]}`` to ``post-embedding``."""
    return _send(EMBEDDING_TOPIC, payload)
