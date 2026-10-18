"""
Content moderation.

Two layers:
  1. better_profanity  — fast, offline, always on. Guarantees a masked version.
  2. LLM (Groq, optional) — semantic check for hate/harassment/sexual/spam etc.
     Enabled only when LLM_API_KEY / GROQ_API_KEY is set. Words the LLM marks as
     offending are masked too.

If the LLM is not configured or fails, the result comes from layer 1 alone.
"""
from __future__ import annotations

import logging
import re

from better_profanity import profanity

from app import llm_client

logger = logging.getLogger(__name__)

profanity.load_censor_words()
profanity.add_censor_words(["heck", "darn", "nigga", "dicks"])

CENSOR_CHAR = "*"


def _mask_phrases(text: str, phrases: list[str]) -> str:
    """Mask each phrase (case-insensitive, whole-word where possible) with ****."""
    for phrase in sorted(set(phrases), key=len, reverse=True):
        phrase = phrase.strip()
        if len(phrase) < 2:
            continue
        pattern = re.compile(r"(?<!\w)" + re.escape(phrase) + r"(?!\w)", re.IGNORECASE)
        text = pattern.sub(CENSOR_CHAR * 4, text)
    return text


def process_text(data: dict) -> dict:
    """
    Moderate one post.

    Input:  { "postId": int, "text": str, "title": str (optional) }
    Output: {
        "postId": int, "originalText": str, "maskedText": str, "isModerated": bool,
        "maskedTitle": str (only when a title was supplied),
        "categories": list[str], "severity": float, "reason": str, "provider": str
    }
    """
    post_id = data.get("postId")
    text = data.get("text", "")

    if not isinstance(text, str):
        logger.warning("postId=%s — 'text' is not a string; coercing to empty string", post_id)
        text = ""

    title = data.get("title")
    has_title = isinstance(title, str)

    # ── Layer 1: offline profanity filter ────────────────────────────────────
    masked_text = profanity.censor(text)
    masked_title = profanity.censor(title) if has_title else None
    is_moderated = profanity.contains_profanity(text) or (
        has_title and profanity.contains_profanity(title))
    categories: list[str] = ["profanity"] if is_moderated else []
    severity = 0.5 if is_moderated else 0.0
    reason = "Profanity detected" if is_moderated else ""
    provider = "profanity-filter"

    # ── Layer 2: LLM (optional) ──────────────────────────────────────────────
    verdict = llm_client.classify(f"{title}\n\n{text}" if has_title else text)
    if verdict is not None:
        provider = f"llm:{llm_client.config.LLM_MODEL}+profanity-filter"
        if verdict["flagged"]:
            is_moderated = True
            masked_text = _mask_phrases(masked_text, verdict["offending_words"])
            if has_title:
                masked_title = _mask_phrases(masked_title, verdict["offending_words"])
            categories = sorted(set(categories) | set(verdict["categories"] or ["flagged"]))
            severity = max(severity, verdict["severity"])
            reason = verdict["reason"] or reason

    logger.info(
        "postId=%s | moderated=%s | provider=%s | categories=%s",
        post_id, is_moderated, provider, categories,
    )

    result = {
        "postId": post_id,
        "originalText": text,
        "maskedText": masked_text,
        "isModerated": is_moderated,
        "categories": categories,
        "severity": round(float(severity), 3),
        "reason": reason,
        "provider": provider,
    }
    if has_title:
        result["maskedTitle"] = masked_title
    return result
