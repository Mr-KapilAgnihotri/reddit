"""
Lightweight text embeddings — no model download, no GPU, no torch.

Uses the *hashing trick*: every word unigram and bigram is hashed (stable MD5,
not Python's salted ``hash``) into one of 384 buckets with a +/- sign, the
counts are TF-weighted and the vector is L2-normalised. Posts that share
vocabulary end up close in cosine space, which is what the pgvector
recommendation feed needs. The dimension matches ``posts.embedding vector(384)``.
"""
from __future__ import annotations

import hashlib
import math
import re
from typing import List

from app.config import EMBEDDING_DIM

_TOKEN_RE = re.compile(r"[a-z0-9']+")
_STOPWORDS = {
    "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "for",
    "with", "is", "are", "was", "were", "be", "been", "it", "this", "that", "i", "you",
    "he", "she", "we", "they", "my", "your", "so", "as", "by", "from", "do", "does",
}


def _tokens(text: str) -> List[str]:
    words = [w for w in _TOKEN_RE.findall(text.lower()) if w not in _STOPWORDS]
    bigrams = [f"{a}_{b}" for a, b in zip(words, words[1:])]
    return words + bigrams


def _bucket(token: str) -> tuple[int, float]:
    digest = hashlib.md5(token.encode("utf-8")).digest()
    index = int.from_bytes(digest[:4], "little") % EMBEDDING_DIM
    sign = 1.0 if digest[4] & 1 else -1.0
    return index, sign


def generate_embedding(text: str) -> List[float]:
    """Return a 384-dim, L2-normalised embedding (all zeros for empty text)."""
    vec = [0.0] * EMBEDDING_DIM
    if not text or not text.strip():
        return vec

    counts: dict[str, int] = {}
    for tok in _tokens(text):
        counts[tok] = counts.get(tok, 0) + 1

    for tok, n in counts.items():
        idx, sign = _bucket(tok)
        vec[idx] += sign * (1.0 + math.log(n))

    norm = math.sqrt(sum(v * v for v in vec))
    if norm == 0:
        return vec
    return [v / norm for v in vec]
