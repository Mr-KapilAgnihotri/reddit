from kafka import KafkaProducer
from kafka.errors import KafkaError
import json
import logging

from app.config import KAFKA_BOOTSTRAP, RESULT_TOPIC

logger = logging.getLogger(__name__)

# Module-level producer, created lazily on first use so that import-time
# errors (Kafka not yet available) never crash the process.
_producer: KafkaProducer | None = None


def _get_producer() -> KafkaProducer:
    global _producer
    if _producer is None:
        logger.info("Creating KafkaProducer — broker=%s", KAFKA_BOOTSTRAP)
        _producer = KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            retries=5,
            acks="all",
        )
    return _producer


def send_result(result: dict) -> None:
    """
    Publish a moderation result to the ``post-moderated`` Kafka topic.

    Expected schema:
        {
            "postId": int,
            "originalText": str,
            "maskedText": str,
            "isModerated": bool
        }

    Errors are logged but never propagated so the consumer loop keeps running.
    """
    try:
        producer = _get_producer()
        future = producer.send(RESULT_TOPIC, result)
        # Block for up to 10 s to confirm delivery; raises on timeout/error
        record_metadata = future.get(timeout=10)
        logger.info(
            "📤 Sent to '%s' | partition=%d offset=%d | postId=%s",
            RESULT_TOPIC,
            record_metadata.partition,
            record_metadata.offset,
            result.get("postId"),
        )
    except KafkaError as exc:
        logger.error("Failed to send result to Kafka for postId=%s: %s", result.get("postId"), exc)
    except Exception as exc:
        logger.exception("Unexpected error sending result for postId=%s: %s", result.get("postId"), exc)

def send_embedding(result: dict) -> None:
    """
    Publish a generated text embedding to the ``post-embedding`` Kafka topic.

    Expected schema:
        {
            "postId": int,
            "embedding": list[float]
        }
    """
    from app.config import EMBEDDING_TOPIC
    try:
        producer = _get_producer()
        future = producer.send(EMBEDDING_TOPIC, result)
        record_metadata = future.get(timeout=10)
        logger.info(
            "📤 Sent to '%s' | partition=%d offset=%d | postId=%s",
            EMBEDDING_TOPIC,
            record_metadata.partition,
            record_metadata.offset,
            result.get("postId"),
        )
    except KafkaError as exc:
        logger.error("Failed to send embedding to Kafka for postId=%s: %s", result.get("postId"), exc)
    except Exception as exc:
        logger.exception("Unexpected error sending embedding for postId=%s: %s", result.get("postId"), exc)