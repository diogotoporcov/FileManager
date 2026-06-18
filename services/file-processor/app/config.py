from typing import Annotated, Any, Literal

from pydantic import AliasChoices, AnyHttpUrl, Field, StringConstraints, field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict

NonBlankString = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
InternalApiToken = Annotated[str, StringConstraints(strip_whitespace=True, min_length=32)]
WorkerTopics = Annotated[tuple[NonBlankString, ...], NoDecode]


class Settings(BaseSettings):
    # Kafka/Redpanda
    kafka_bootstrap_servers: NonBlankString = Field(default=...)
    worker_topics: WorkerTopics = Field(
        default=(
            "file.processing.checksum",
            "file.processing.image",
            "file.processing.audio",
        ),
    )
    kafka_topic_dlq: NonBlankString = Field(default="file.processing.requested.dlq")
    kafka_consumer_group_id: NonBlankString = Field(default="filemanager-worker-group")
    worker_consumer_enabled: bool = False

    # Retry policy
    worker_max_attempts: int = Field(default=3, ge=1)
    worker_retry_backoff_seconds: float = Field(default=1.0, ge=0)
    worker_retry_backoff_multiplier: float = Field(default=2.0, ge=1)
    worker_checksum_enabled: bool = True
    worker_image_phash_enabled: bool = True
    worker_image_embedding_enabled: bool = True
    worker_phash_max_image_bytes: int = Field(default=25 * 1024 * 1024, gt=0)
    worker_audio_enabled: bool = True
    worker_audio_fingerprint_enabled: bool = True
    worker_audio_supported_mime_types: NonBlankString = (
        "audio/mpeg,audio/mp3,audio/wav,audio/x-wav,audio/wave,audio/vnd.wave,audio/flac,audio/x-flac,"
        "audio/ogg,audio/aac,audio/mp4,audio/x-m4a,audio/webm,audio/opus,audio/matroska,"
        "audio/x-matroska,audio/ac3,audio/3gpp,audio/3gpp2,audio/x-aiff,audio/aiff"
    )
    worker_audio_max_file_bytes: int = Field(default=256 * 1024 * 1024, gt=0)
    worker_audio_max_duration_seconds: float = Field(default=30 * 60, gt=0)
    worker_audio_fingerprint_length_seconds: int = Field(default=120, gt=0)
    worker_audio_subprocess_timeout_seconds: float = Field(default=30.0, gt=0)
    worker_audio_max_fingerprint_chars: int = Field(default=32_768, gt=0)
    processable_image_mime_types: NonBlankString = (
        "image/apng,image/avif,image/bmp,image/gif,image/icns,image/jp2,image/jpeg,image/mpo,"
        "image/palm,image/png,image/sgi,image/tiff,image/vnd.adobe.photoshop,image/webp,"
        "image/x-icon,image/x-pcx,image/x-portable-anymap,image/x-tga,image/xbm,image/xpm"
    )
    embedding_processor_enabled: bool = True
    embedding_max_image_bytes: int = Field(default=100 * 1024 * 1024, gt=0)
    embedding_max_source_pixels: int = Field(default=400_000_000, gt=0)
    embedding_direct_decode_max_pixels: int = Field(default=89_478_485, gt=0)
    embedding_image_input_size: int = Field(default=224, gt=0)
    embedding_dimension: int = Field(default=768, gt=0)
    embedding_model_name: NonBlankString = "openai/clip-vit-large-patch14"
    embedding_model_version: NonBlankString = "1"

    # Triton
    triton_grpc_url: NonBlankString = "localhost:8001"
    triton_model_name: NonBlankString = "image_embedding"
    triton_model_version: NonBlankString = "1"
    triton_input_tensor_name: NonBlankString = "pixel_values"
    triton_output_tensor_name: NonBlankString = "image_embeds"
    triton_inference_timeout_seconds: float = Field(default=10.0, gt=0)

    # MinIO/S3
    s3_endpoint: AnyHttpUrl = Field(  # type: ignore
        default=...,
        validation_alias=AliasChoices("s3_endpoint", "MINIO_ENDPOINT"),
    )
    s3_access_key: NonBlankString = Field(
        default=...,
        validation_alias=AliasChoices("s3_access_key", "MINIO_ROOT_USER"),
    )
    s3_secret_key: NonBlankString = Field(
        default=...,
        validation_alias=AliasChoices("s3_secret_key", "MINIO_ROOT_PASSWORD"),
    )
    s3_bucket_name: NonBlankString = Field(
        default="filemanager",
        validation_alias=AliasChoices("s3_bucket_name", "MINIO_BUCKET_NAME"),
    )
    s3_connect_timeout_seconds: float = Field(default=5.0, gt=0)
    s3_read_timeout_seconds: float = Field(default=60.0, gt=0)

    # API
    metadata_api_base_url: AnyHttpUrl = Field(default="http://localhost:8081")  # type: ignore
    metadata_api_timeout_seconds: float = Field(default=10.0, gt=0)
    internal_api_token: InternalApiToken = Field(
        default=...,
        validation_alias=AliasChoices("internal_api_token", "INTERNAL_API_TOKEN"),
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

    @field_validator("worker_topics", mode="before")
    @classmethod
    def parse_worker_topics(cls, v: Any) -> Any:
        if isinstance(v, str):
            topics = [topic.strip() for topic in v.split(",")]
            if not topics or any(not topic for topic in topics):
                raise ValueError("worker_topics must contain one or more non-blank topics")

            return tuple(topics)

        return v

    @field_validator("worker_topics")
    @classmethod
    def validate_worker_topics(cls, v: tuple[str, ...]) -> tuple[str, ...]:
        if not v:
            raise ValueError("worker_topics must contain one or more topics")

        return v

    model_config = SettingsConfigDict(
        extra="ignore",
        env_file=".env",
        env_file_encoding="utf-8",
    )


settings = Settings()
