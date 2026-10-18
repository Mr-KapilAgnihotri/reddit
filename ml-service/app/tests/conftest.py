"""Test configuration: never talk to real Kafka or a real LLM during unit tests."""
import os

os.environ["ENABLE_KAFKA_CONSUMER"] = "false"
os.environ["LLM_ENABLED"] = "false"
os.environ.pop("GROQ_API_KEY", None)
os.environ.pop("LLM_API_KEY", None)
