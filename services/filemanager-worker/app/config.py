from typing import Literal, Annotated, Any
from pydantic import Field, AnyHttpUrl, field_validator, StringConstraints, AliasChoices
from pydantic_settings import BaseSettings, SettingsConfigDict

NonBlankString = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]

class Settings(BaseSettings):
    # Kafka/Redpanda
    kafka_bootstrap_servers: NonBlankString = Field(default="localhost:9092")
    kafka_topic_file_processing: NonBlankString = Field(default="file.processing.requested")
    kafka_topic_dlq: NonBlankString = Field(default="file.processing.requested.dlq")
    kafka_consumer_group_id: NonBlankString = Field(default="filemanager-worker-group")
    worker_consumer_enabled: bool = False

    # Retry policy
    worker_max_attempts: int = Field(default=3, ge=1)
    worker_retry_backoff_seconds: float = Field(default=1.0, ge=0)
    worker_retry_backoff_multiplier: float = Field(default=2.0, ge=1)
    worker_phash_max_image_bytes: int = Field(default=25 * 1024 * 1024, gt=0)

    # MinIO/S3
    s3_endpoint: AnyHttpUrl = Field(  # type: ignore
        default="http://localhost:9000",
        validation_alias=AliasChoices("s3_endpoint", "MINIO_ENDPOINT")
    )
    s3_access_key: NonBlankString = Field(
        default="minioadmin",
        validation_alias=AliasChoices("s3_access_key", "MINIO_ROOT_USER")
    )
    s3_secret_key: NonBlankString = Field(
        default="minioadmin",
        validation_alias=AliasChoices("s3_secret_key", "MINIO_ROOT_PASSWORD")
    )
    s3_bucket_name: NonBlankString = Field(
        default="filemanager",
        validation_alias=AliasChoices("s3_bucket_name", "MINIO_BUCKET_NAME")
    )

    # API
    metadata_api_base_url: AnyHttpUrl = Field(default="http://localhost:8081")  # type: ignore
    internal_api_token: NonBlankString = Field(
        default=...,
        validation_alias=AliasChoices("internal_api_token", "INTERNAL_API_TOKEN")
    )

    # Metrics
    metrics_enabled: bool = True

    # Logging
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"

    @field_validator("s3_endpoint", "metadata_api_base_url", mode="before")
    @classmethod
    def validate_url(cls, v: Any) -> Any:
        if isinstance(v, str) and not v.strip():
            raise ValueError("URL cannot be blank")
        return v

    model_config = SettingsConfigDict(
        extra="ignore",
        env_file=".env",
        env_file_encoding="utf-8"
    )

settings = Settings()
