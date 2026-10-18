"""
Kafka consumer: post-created  →  moderation + embedding  →  post-moderated / post-embedding
"""
from __future__ import annotations

import json
import logging
import threading
import time
from typing import Optional

from kafka import KafkaConsumer

from app.config import CONSUMER_GROUP, KAFKA_BOOTSTRAP, POST_TOPIC
from app.embedder import generate_embedding
from app.moderation import process_text
from app.producer import send_embedding, send_result

logger = logging.getLogger(__name__)

_MAX_RETRIES = 30        # connection attempts (≈2 min) — survives slow Kafka start-up
_RETRY_DELAY_SEC = 4

# Set to stop the consumer loop gracefully (used on FastAPI shutdown)
stop_event = threading.Event()

# Simple runtime stats exposed via GET /stats
stats = {"consumed": 0, "moderated": 0, "errors": 0, "connected": False}


def _safe_deserializer(raw: bytes):
    """Deserialize a Kafka message value; return None on any error so the loop can skip it."""
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception as exc:
        logger.error("Failed to deserialize message (skipping): %s | raw=%r", exc, (raw or b"")[:200])
        return None


def _validate_message(data) -> bool:
    """Return True if the message has the required fields."""
    if not isinstance(data, dict):
        return False
    if "postId" not in data:
        logger.warning("Message missing 'postId' field, skipping: %s", data)
        return False
    if "text" not in data:
        logger.warning("Message missing 'text' for postId=%s, using empty string", data.get("postId"))
    return True


def handle_message(data) -> bool:
    """Process one decoded message. Returns True when a moderation result was published."""
    if data is None or not _validate_message(data):
        return False

    stats["consumed"] += 1
    try:
        result = process_text(data)
        send_result(result)
        if result.get("isModerated"):
            stats["moderated"] += 1
    except Exception as exc:
        stats["errors"] += 1
        logger.exception("Error moderating postId=%s: %s", data.get("postId"), exc)
        return False

    # Embedding is best-effort — a failure here must not affect moderation
    try:
        text = " ".join(str(p) for p in (data.get("title"), data.get("text")) if p)
        vector = generate_embedding(text)
        if any(vector):
            send_embedding({"postId": data.get("postId"), "embedding": vector})
    except Exception as exc:
        logger.exception("Error generating embedding for postId=%s: %s", data.get("postId"), exc)

    return True


def _connect() -> Optional[KafkaConsumer]:
    for attempt in range(1, _MAX_RETRIES + 1):
        if stop_event.is_set():
            return None
        try:
            consumer = KafkaConsumer(
                POST_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP,
                group_id=CONSUMER_GROUP,
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                value_deserializer=_safe_deserializer,
                consumer_timeout_ms=1000,   # iterator returns every second → loop can check stop_event
                request_timeout_ms=30000,
                session_timeout_ms=10000,
            )
            logger.info("Kafka consumer connected to %s (attempt %d)", KAFKA_BOOTSTRAP, attempt)
            return consumer
        except Exception as exc:
            logger.warning("Kafka not ready (attempt %d/%d): %s — retrying in %ds",
                           attempt, _MAX_RETRIES, exc, _RETRY_DELAY_SEC)
            time.sleep(_RETRY_DELAY_SEC)
    logger.error("Could not connect to Kafka after %d attempts. Consumer will not run.", _MAX_RETRIES)
    return None


def start_consumer(run_forever: bool = True) -> None:
    """
    Consumer loop, run in a daemon thread from the FastAPI lifespan.

    ``run_forever=False`` makes a single pass over the consumer iterator and returns
    (used by unit tests with a mocked KafkaConsumer).
    """
    logger.info("Kafka consumer starting — broker=%s topic=%s", KAFKA_BOOTSTRAP, POST_TOPIC)
    consumer = _connect()
    if consumer is None:
        return
    stats["connected"] = True

    try:
        while not stop_event.is_set():
            try:
                for message in consumer:
                    data = message.value
                    logger.info("Received | topic=%s partition=%s offset=%s",
                                message.topic, message.partition, message.offset)
                    handle_message(data)
                    if stop_event.is_set():
                        break
            except Exception as loop_exc:
                stats["errors"] += 1
                logger.exception("Unexpected error in consumer loop: %s", loop_exc)
                time.sleep(1)
            if not run_forever:
                break
    finally:
        stats["connected"] = False
        try:
            consumer.close()
        except Exception:
            pass
