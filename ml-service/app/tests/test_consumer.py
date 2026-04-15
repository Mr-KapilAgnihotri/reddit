"""
Unit tests for the Kafka consumer message-handling logic.

These tests are pure-Python and do NOT require a running Kafka broker.
All Kafka interactions are mocked.

Run with:  python -m pytest app/tests/test_consumer.py -v
"""

import json
import pytest
from unittest.mock import MagicMock, patch, call


# ── Helpers ───────────────────────────────────────────────────────────────────

def _make_kafka_message(data: dict | None, raw: bytes | None = None):
    """Create a minimal mock Kafka ConsumerRecord."""
    msg = MagicMock()
    msg.topic = "post-created"
    msg.partition = 0
    msg.offset = 0
    msg.value = data
    if raw is not None:
        msg.value = None  # simulates bad deserialization
    return msg


# ── _safe_deserializer ────────────────────────────────────────────────────────

class TestSafeDeserializer:
    """Tests for the raw-bytes → dict deserializer."""

    def _call(self, raw: bytes):
        from app.consumer import _safe_deserializer
        return _safe_deserializer(raw)

    def test_valid_json(self):
        raw = json.dumps({"postId": 1, "text": "hello"}).encode()
        assert self._call(raw) == {"postId": 1, "text": "hello"}

    def test_invalid_json_returns_none(self):
        assert self._call(b"not-json") is None

    def test_empty_bytes_returns_none(self):
        assert self._call(b"") is None

    def test_non_utf8_bytes_returns_none(self):
        assert self._call(b"\xff\xfe") is None

    def test_null_json_returns_none_value(self):
        # JSON "null" deserializes to Python None
        result = self._call(b"null")
        assert result is None


# ── _validate_message ─────────────────────────────────────────────────────────

class TestValidateMessage:
    def _call(self, data):
        from app.consumer import _validate_message
        return _validate_message(data)

    def test_valid_message(self):
        assert self._call({"postId": 1, "text": "hello"}) is True

    def test_missing_text_still_valid(self):
        # text is optional — consumer defaults it to ""
        assert self._call({"postId": 1}) is True

    def test_missing_post_id_invalid(self):
        assert self._call({"text": "hello"}) is False

    def test_not_a_dict_invalid(self):
        assert self._call("string") is False
        assert self._call(123) is False
        assert self._call(None) is False

    def test_empty_dict_invalid(self):
        assert self._call({}) is False


# ── Full consumer loop (mocked KafkaConsumer) ─────────────────────────────────

class TestConsumerLoop:
    """
    Simulate the consumer loop by mocking KafkaConsumer and asserting that
    process_text + send_result are called correctly.
    """

    def _run_with_messages(self, messages):
        """
        Patch KafkaConsumer so it yields `messages`, then calls start_consumer
        (which will exhaust the iterable and stop due to StopIteration).
        """
        mock_consumer_instance = MagicMock()
        mock_consumer_instance.__iter__ = MagicMock(return_value=iter(messages))

        with patch("app.consumer.KafkaConsumer", return_value=mock_consumer_instance), \
             patch("app.consumer.process_text") as mock_process, \
             patch("app.consumer.send_result") as mock_send:

            mock_process.side_effect = lambda d: {
                "postId": d.get("postId"),
                "originalText": d.get("text", ""),
                "maskedText": d.get("text", "").replace("fuck", "****"),
                "isModerated": "fuck" in d.get("text", ""),
            }

            from app.consumer import start_consumer
            start_consumer()

        return mock_process, mock_send

    def test_valid_message_triggers_process_and_send(self):
        msg = _make_kafka_message({"postId": 1, "text": "hello"})
        mock_process, mock_send = self._run_with_messages([msg])

        mock_process.assert_called_once_with({"postId": 1, "text": "hello"})
        mock_send.assert_called_once()

    def test_none_value_message_skipped(self):
        """Message with None value (bad JSON) must be skipped silently."""
        msg = _make_kafka_message(None)
        mock_process, mock_send = self._run_with_messages([msg])

        mock_process.assert_not_called()
        mock_send.assert_not_called()

    def test_missing_post_id_skipped(self):
        msg = _make_kafka_message({"text": "hello"})
        mock_process, mock_send = self._run_with_messages([msg])

        mock_process.assert_not_called()

    def test_processing_exception_does_not_crash_consumer(self):
        """If process_text raises, the loop continues with next message."""
        msg1 = _make_kafka_message({"postId": 1, "text": "bad"})
        msg2 = _make_kafka_message({"postId": 2, "text": "good"})

        mock_consumer_instance = MagicMock()
        mock_consumer_instance.__iter__ = MagicMock(return_value=iter([msg1, msg2]))

        with patch("app.consumer.KafkaConsumer", return_value=mock_consumer_instance), \
             patch("app.consumer.process_text", side_effect=[RuntimeError("boom"), {"postId": 2, "maskedText": "good", "isModerated": False}]) as mock_process, \
             patch("app.consumer.send_result") as mock_send:
            from app.consumer import start_consumer
            start_consumer()  # must not raise

        assert mock_process.call_count == 2
        # send_result called only for the second (successful) message
        mock_send.assert_called_once()

    def test_multiple_valid_messages(self):
        messages = [
            _make_kafka_message({"postId": i, "text": f"text {i}"})
            for i in range(5)
        ]
        mock_process, mock_send = self._run_with_messages(messages)

        assert mock_process.call_count == 5
        assert mock_send.call_count == 5

    def test_kafka_unavailable_exhausts_retries(self):
        """When Kafka is unreachable, start_consumer should give up after retries."""
        with patch("app.consumer.KafkaConsumer", side_effect=Exception("connection refused")), \
             patch("app.consumer.time.sleep"):  # skip actual sleeping
            from app.consumer import start_consumer
            start_consumer()   # must return cleanly, not raise
