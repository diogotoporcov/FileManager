from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # Kafka/Redpanda
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_topic_file_processing: str = "file.processing.requested"
    kafka_topic_dlq: str = "file.processing.requested.dlq"
    kafka_consumer_group_id: str = "filemanager-worker-group"
    worker_consumer_enabled: bool = False

    # Retry policy
    worker_max_attempts: int = Field(default=3, ge=1)
    worker_retry_backoff_seconds: float = Field(default=1.0, ge=0)
    worker_retry_backoff_multiplier: float = Field(default=2.0, ge=1)

    # MinIO/S3
    s3_endpoint: str = "http://localhost:9000"
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin"
    s3_bucket_name: str = "filemanager"

    # API
    metadata_api_base_url: str = "http://localhost:8081"
    internal_api_token: str = Field(min_length=1)

    # Metrics
    metrics_enabled: bool = True

    # Logging
    log_level: str = "INFO"

    model_config = SettingsConfigDict(extra="ignore")

settings = Settings()
