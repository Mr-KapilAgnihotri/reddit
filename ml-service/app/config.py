import os

# Kafka broker address — read from env var so it works in Docker (kafka:29092)
# and in local dev (localhost:9092) without code changes.
KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "kafka:29092")

# Topic names — must match Spring Boot exactly
POST_TOPIC      = "post-created"
RESULT_TOPIC    = "post-moderated"
EMBEDDING_TOPIC = "post-embedding"   # Spring consumes this to store vector(384) in posts