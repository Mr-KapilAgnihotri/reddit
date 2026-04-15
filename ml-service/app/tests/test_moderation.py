"""
Unit tests for app.moderation.process_text

All tests are pure-Python and require no external services.
Run with:  python -m pytest app/tests/test_moderation.py -v
"""

import pytest
from app.moderation import process_text


# ── helpers ───────────────────────────────────────────────────────────────────

def _make(post_id=1, text="hello"):
    return {"postId": post_id, "text": text}


# ── clean text ────────────────────────────────────────────────────────────────

class TestCleanText:
    def test_clean_text_is_not_moderated(self):
        result = process_text(_make(text="This is a perfectly fine post."))
        assert result["isModerated"] is False

    def test_clean_text_masked_equals_original(self):
        txt = "Hello, world!"
        result = process_text(_make(text=txt))
        assert result["maskedText"] == txt

    def test_original_text_preserved(self):
        txt = "Good morning everyone!"
        result = process_text(_make(text=txt))
        assert result["originalText"] == txt

    def test_post_id_preserved(self):
        result = process_text({"postId": 42, "text": "nice"})
        assert result["postId"] == 42


# ── profane text ──────────────────────────────────────────────────────────────

class TestProfaneText:
    def test_profanity_detected(self):
        result = process_text(_make(text="What the fuck is this?"))
        assert result["isModerated"] is True

    def test_profanity_masked(self):
        result = process_text(_make(text="What the fuck is this?"))
        assert "fuck" not in result["maskedText"].lower()
        assert "****" in result["maskedText"]

    def test_original_text_unchanged(self):
        txt = "What the fuck!"
        result = process_text(_make(text=txt))
        assert result["originalText"] == txt

    def test_custom_word_heck(self):
        result = process_text(_make(text="What the heck is wrong?"))
        assert result["isModerated"] is True
        assert "heck" not in result["maskedText"].lower()

    def test_custom_word_darn(self):
        result = process_text(_make(text="Oh darn it!"))
        assert result["isModerated"] is True

    def test_custom_word_dicks(self):
        result = process_text(_make(text="Those dicks ruined it"))
        assert result["isModerated"] is True

    def test_mixed_clean_and_profane(self):
        result = process_text(_make(text="hello fuck world"))
        assert result["isModerated"] is True
        assert "hello" in result["maskedText"]
        assert "world" in result["maskedText"]


# ── edge cases ────────────────────────────────────────────────────────────────

class TestEdgeCases:
    def test_empty_text(self):
        result = process_text(_make(text=""))
        assert result["isModerated"] is False
        assert result["maskedText"] == ""
        assert result["originalText"] == ""

    def test_missing_text_field_defaults_empty(self):
        """Missing 'text' key in payload is handled gracefully."""
        result = process_text({"postId": 99})
        assert result["isModerated"] is False
        assert result["maskedText"] == ""

    def test_none_text_coerced(self):
        """None 'text' should not raise — coerced to empty string."""
        result = process_text({"postId": 1, "text": None})
        assert result["isModerated"] is False

    def test_non_string_text_coerced(self):
        result = process_text({"postId": 1, "text": 12345})
        # Should not raise; is_moderated will be False on numeric coercion
        assert "postId" in result

    def test_whitespace_only(self):
        result = process_text(_make(text="   "))
        assert result["isModerated"] is False

    def test_unicode_text(self):
        result = process_text(_make(text="こんにちは 世界"))
        assert result["isModerated"] is False
        assert result["originalText"] == "こんにちは 世界"

    def test_return_schema_keys(self):
        result = process_text(_make())
        assert set(result.keys()) == {"postId", "originalText", "maskedText", "isModerated"}

    def test_missing_post_id_is_none(self):
        result = process_text({"text": "hello"})
        assert result["postId"] is None


# ── REST endpoint (FastAPI TestClient) ───────────────────────────────────────

class TestRestEndpoint:
    """Tests for POST /moderate using the FastAPI test client.
    No Kafka required — the lifespan thread is a daemon so it won't block."""

    @pytest.fixture()
    def client(self):
        from fastapi.testclient import TestClient
        # Import app here to avoid starting the lifespan in module-level scope
        from app.main import app
        with TestClient(app) as c:
            yield c

    def test_moderate_clean(self, client):
        resp = client.post("/moderate", json={"postId": 1, "text": "Hello world"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["isModerated"] is False
        assert body["postId"] == 1

    def test_moderate_profane(self, client):
        resp = client.post("/moderate", json={"postId": 2, "text": "What the fuck!"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["isModerated"] is True
        assert "****" in body["maskedText"]

    def test_health_endpoint(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "healthy"

    def test_moderate_missing_text_422(self, client):
        """Missing required 'text' field must return 422 Unprocessable Entity."""
        resp = client.post("/moderate", json={"postId": 1})
        assert resp.status_code == 422

    def test_moderate_missing_post_id_422(self, client):
        resp = client.post("/moderate", json={"text": "hello"})
        assert resp.status_code == 422
