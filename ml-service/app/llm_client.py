"""
Thin client for an OpenAI-compatible chat-completions API (Groq by default).

The LLM is asked to classify a piece of user content and answer with strict JSON.
Every failure (no key, network error, rate limit, malformed JSON) returns ``None``
so the caller can fall back to the offline profanity filter — moderation never
blocks the pipeline.
"""
from __future__ import annotations

import json
import logging
from typing import Optional

import httpx

from app import config

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are a content-moderation classifier for a Reddit-like forum.
Classify the user's post. Flag it when it contains any of: profanity, hate speech,
harassment or threats, sexual content, self-harm encouragement, or spam/scams.
Mild everyday words with no abusive intent are fine.

Respond with ONLY a JSON object, no prose:
{
  "flagged": true | false,
  "categories": ["profanity" | "hate" | "harassment" | "sexual" | "self-harm" | "violence" | "spam"],
  "severity": number between 0 and 1,
  "offending_words": ["exact words or short phrases from the text that should be masked"],
  "reason": "one short sentence"
}"""


def is_enabled() -> bool:
    return config.LLM_ENABLED


def classify(text: str) -> Optional[dict]:
    """Return the LLM verdict dict, or ``None`` if the LLM is unavailable."""
    if not is_enabled() or not text or not text.strip():
        return None

    payload = {
        "model": config.LLM_MODEL,
        "temperature": 0,
        "max_tokens": 300,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": text[:4000]},
        ],
    }
    headers = {"Authorization": f"Bearer {config.LLM_API_KEY}"}

    try:
        resp = httpx.post(
            f"{config.LLM_BASE_URL}/chat/completions",
            json=payload,
            headers=headers,
            timeout=config.LLM_TIMEOUT_SEC,
        )
        if resp.status_code != 200:
            logger.warning("LLM moderation HTTP %s: %s", resp.status_code, resp.text[:300])
            return None
        content = resp.json()["choices"][0]["message"]["content"]
        return _parse_verdict(content)
    except Exception as exc:  # network errors, timeouts, bad JSON …
        logger.warning("LLM moderation unavailable (%s) — falling back to profanity filter", exc)
        return None


def _parse_verdict(content: str) -> Optional[dict]:
    """Parse and normalise the JSON returned by the model."""
    try:
        start, end = content.find("{"), content.rfind("}")
        data = json.loads(content[start:end + 1])
    except Exception:
        logger.warning("LLM returned non-JSON content: %r", content[:200])
        return None

    if not isinstance(data, dict):
        return None

    categories = data.get("categories") or []
    words = data.get("offending_words") or []
    try:
        severity = float(data.get("severity", 0) or 0)
    except (TypeError, ValueError):
        severity = 0.0

    return {
        "flagged": bool(data.get("flagged", False)),
        "categories": [str(c).lower() for c in categories if isinstance(c, (str, int))][:10],
        "severity": max(0.0, min(1.0, severity)),
        "offending_words": [str(w) for w in words if isinstance(w, str) and w.strip()][:50],
        "reason": str(data.get("reason", ""))[:300],
    }
