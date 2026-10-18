"""Tests for the Groq LLM layer (HTTP mocked) and the hashing embedder."""
import json
import math
from unittest.mock import MagicMock, patch

import pytest

from app import config, llm_client
from app.embedder import generate_embedding
from app.moderation import process_text


def _llm_response(verdict: dict, status: int = 200):
    resp = MagicMock()
    resp.status_code = status
    resp.text = json.dumps(verdict)
    resp.json.return_value = {"choices": [{"message": {"content": json.dumps(verdict)}}]}
    return resp


@pytest.fixture()
def llm_on(monkeypatch):
    monkeypatch.setattr(config, "LLM_ENABLED", True)
    monkeypatch.setattr(config, "LLM_API_KEY", "test-key")


class TestLlmClient:
    def test_disabled_returns_none(self, monkeypatch):
        monkeypatch.setattr(config, "LLM_ENABLED", False)
        assert llm_client.classify("anything") is None

    def test_parses_verdict(self, llm_on):
        verdict = {"flagged": True, "categories": ["Harassment"], "severity": 0.9,
                   "offending_words": ["idiot"], "reason": "insult"}
        with patch("app.llm_client.httpx.post", return_value=_llm_response(verdict)) as post:
            out = llm_client.classify("you are an idiot")
        assert out["flagged"] is True
        assert out["categories"] == ["harassment"]
        assert out["offending_words"] == ["idiot"]
        # Sent to Groq's OpenAI-compatible endpoint with the bearer key
        url = post.call_args[0][0]
        assert url.endswith("/chat/completions")
        assert post.call_args.kwargs["headers"]["Authorization"] == "Bearer test-key"

    def test_http_error_returns_none(self, llm_on):
        with patch("app.llm_client.httpx.post", return_value=_llm_response({}, status=429)):
            assert llm_client.classify("hello") is None

    def test_network_error_returns_none(self, llm_on):
        with patch("app.llm_client.httpx.post", side_effect=Exception("timeout")):
            assert llm_client.classify("hello") is None

    def test_garbage_content_returns_none(self):
        assert llm_client._parse_verdict("not json at all") is None


class TestModerationWithLlm:
    def test_llm_flags_and_masks_words_profanity_filter_misses(self, llm_on):
        verdict = {"flagged": True, "categories": ["harassment"], "severity": 0.8,
                   "offending_words": ["moron"], "reason": "insult"}
        with patch("app.llm_client.httpx.post", return_value=_llm_response(verdict)):
            out = process_text({"postId": 5, "text": "Only a moron would say that"})
        assert out["isModerated"] is True
        assert "moron" not in out["maskedText"]
        assert "harassment" in out["categories"]
        assert out["provider"].startswith("llm:")

    def test_llm_failure_falls_back_to_profanity(self, llm_on):
        with patch("app.llm_client.httpx.post", side_effect=Exception("down")):
            out = process_text({"postId": 6, "text": "what the fuck"})
        assert out["isModerated"] is True
        assert out["provider"] == "profanity-filter"


class TestEmbedder:
    def test_dimension_and_norm(self):
        vec = generate_embedding("Spring Boot with Kafka and PostgreSQL")
        assert len(vec) == 384
        assert math.isclose(math.sqrt(sum(v * v for v in vec)), 1.0, rel_tol=1e-6)

    def test_empty_text_is_zero_vector(self):
        assert generate_embedding("   ") == [0.0] * 384

    def test_deterministic(self):
        assert generate_embedding("hello kafka") == generate_embedding("hello kafka")

    def test_similar_texts_closer_than_unrelated(self):
        a = generate_embedding("kafka streaming pipeline with spring boot")
        b = generate_embedding("building a kafka pipeline in spring boot")
        c = generate_embedding("my favourite pasta recipe with tomatoes")
        dot = lambda x, y: sum(i * j for i, j in zip(x, y))
        assert dot(a, b) > dot(a, c)
