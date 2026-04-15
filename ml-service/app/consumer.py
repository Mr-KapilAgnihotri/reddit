from kafka import KafkaConsumer
import json
import time
import logging

from app.config import KAFKA_BOOTSTRAP, POST_TOPIC
from app.moderation import process_text
from app.producer import send_result

logger = logging.getLogger(__name__)

_MAX_RETRIES = 15        # Maximum connection attempts
_RETRY_DELAY_SEC = 4     # Seconds between attempts


def _safe_deserializer(raw: bytes):
    """Deserialize a Kafka message value; return None on any error so the
    consumer loop can skip it without crashing."""
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception as exc:
        logger.error("Failed to deserialize message (skipping): %s | raw=%r", exc, raw[:200])
        return None


def _validate_message(data: dict) -> bool:
    """Return True if the message has the required fields."""
    if not isinstance(data, dict):
        return False
    if "postId" not in data:
        logger.warning("Message missing 'postId' field, skipping: %s", data)
        return False
    if "text" not in data:
        logger.warning("Message missing 'text' field for postId=%s, using empty string", data.get("postId"))
    return True


def start_consumer():
    """
    Start the Kafka consumer loop.  This function is designed to be run
    in a daemon background thread from the FastAPI lifespan context.

    • Retries connecting to Kafka up to _MAX_RETRIES times with a backoff
      so the service survives slow Kafka broker startup inside Docker.
    • Every message is processed safely; exceptions never crash the loop.
    """
    logger.info("Kafka consumer starting — broker=%s, topic=%s", KAFKA_BOOTSTRAP, POST_TOPIC)

    consumer = None
    for attempt in range(1, _MAX_RETRIES + 1):
        try:
            consumer = KafkaConsumer(
                POST_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP,
                group_id="ml-moderation-group",
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                value_deserializer=_safe_deserializer,
                # Wait up to 5 s for a batch — keeps CPU quiet when idle
                consumer_timeout_ms=5000,
                # Raise an exception quickly on initial connect failure
                request_timeout_ms=10000,
                session_timeout_ms=8000,
            )
            logger.info("✅ Kafka consumer connected on attempt %d/%d", attempt, _MAX_RETRIES)
            break
        except Exception as exc:
            logger.warning(
                "Kafka not ready (attempt %d/%d): %s — retrying in %ds",
                attempt, _MAX_RETRIES, exc, _RETRY_DELAY_SEC,
            )
            time.sleep(_RETRY_DELAY_SEC)

    if consumer is None:
        logger.error("❌ Could not connect to Kafka after %d attempts. Consumer will not run.", _MAX_RETRIES)
        return

    logger.info("📡 Listening on topic '%s' …", POST_TOPIC)

    # ── Main consume loop ────────────────────────────────────────────────────
    while True:
        try:
            for message in consumer:
                data = message.value

                if data is None:
                    # _safe_deserializer returned None — bad JSON, already logged
                    continue

                logger.info(
                    "📩 Received message | topic=%s partition=%d offset=%d | data=%s",
                    message.topic, message.partition, message.offset, data,
                )

                if not _validate_message(data):
                    continue

                try:
                    result = process_text(data)
                    logger.info("🧠 Moderation result: %s", result)
                    send_result(result)
                except Exception as processing_exc:
                    logger.exception(
                        "Error processing message for postId=%s: %s",
                        data.get("postId"), processing_exc,
                    )
                    # Continue — do not crash the consumer loop

        except Exception as loop_exc:
            # consumer_timeout_ms causes StopIteration when idle; everything else
            # is an unexpected error we log and survive.
            if "StopIteration" not in type(loop_exc).__name__:
                logger.exception("Unexpected error in consumer loop: %s", loop_exc)
            # Re-enter the for loop to continue consuming (StopIteration is normal)
            continue