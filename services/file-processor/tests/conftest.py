import os

# Set required environment variables for tests before any other imports
os.environ.setdefault("INTERNAL_API_TOKEN", "test-internal-token-123456789012")
os.environ.setdefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
os.environ.setdefault("MINIO_ENDPOINT", "http://localhost:9000")
os.environ.setdefault("MINIO_ROOT_USER", "minioadmin")
os.environ.setdefault("MINIO_ROOT_PASSWORD", "minioadmin-test-password")
