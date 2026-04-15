from better_profanity import profanity
import logging

logger = logging.getLogger(__name__)

profanity.load_censor_words()
profanity.add_censor_words(["heck", "darn", "nigga", "dicks"])


def process_text(data: dict) -> dict:
    """
    Perform content moderation on a post.

    Expected input schema:
        { "postId": int, "text": str }

    Returns:
        {
            "postId": int,
            "originalText": str,
            "maskedText": str,
            "isModerated": bool
        }
    """
    post_id = data.get("postId")
    text = data.get("text", "")

    if not isinstance(text, str):
        logger.warning("postId=%s — 'text' field is not a string; coercing to empty string", post_id)
        text = ""

    masked_text = profanity.censor(text)
    is_moderated = profanity.contains_profanity(text)

    logger.info(
        "postId=%s | is_moderated=%s | original=%r | masked=%r",
        post_id, is_moderated, text[:100], masked_text[:100],
    )

    return {
        "postId": post_id,
        "originalText": text,
        "maskedText": masked_text,
        "isModerated": is_moderated,
    }