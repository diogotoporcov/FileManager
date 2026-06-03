import pytest
from pydantic import ValidationError

from app.config import Settings


def test_settings_validation():
    # Valid settings should work (using defaults or explicit valid values)
    s1 = Settings(
        internal_api_token="test-token-1234567890123456789012",
        metrics_enabled=True,
        worker_max_attempts=1,
        worker_retry_backoff_seconds=0,
        worker_retry_backoff_multiplier=1
    )
    assert s1.metrics_enabled is True
    
    s2 = Settings(
        internal_api_token="test-token-1234567890123456789012",
        metrics_enabled=False
    )
    assert s2.metrics_enabled is False
    
    # Invalid max attempts (must be >= 1)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_max_attempts=0)
        
    # Invalid backoff (must be >= 0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_retry_backoff_seconds=-1.0)
        
    # Invalid multiplier (must be >= 1)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_retry_backoff_multiplier=0.5)

def test_internal_api_token_validation(monkeypatch):
    # Blank internal_api_token
    with pytest.raises(ValidationError):
        Settings(internal_api_token="")
    
    # Whitespace internal_api_token
    with pytest.raises(ValidationError):
        Settings(internal_api_token="   ")

    # Short internal_api_token
    with pytest.raises(ValidationError):
        Settings(internal_api_token="short-token")
    
    # Missing internal_api_token
    monkeypatch.delenv("INTERNAL_API_TOKEN", raising=False)
    # We need to make sure we don't accidentally get it from any other source
    with pytest.raises(ValidationError):
        # noinspection PyArgumentList
        Settings()

def test_required_external_settings(monkeypatch):
    monkeypatch.delenv("KAFKA_BOOTSTRAP_SERVERS", raising=False)
    monkeypatch.delenv("MINIO_ENDPOINT", raising=False)
    monkeypatch.delenv("MINIO_ROOT_USER", raising=False)
    monkeypatch.delenv("MINIO_ROOT_PASSWORD", raising=False)

    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012")

def test_s3_validation():
    # Invalid S3 endpoint
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_endpoint="not-a-url")  # type: ignore

    # Blank S3 bucket
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_bucket_name="")
    
    # Whitespace S3 bucket
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_bucket_name="   ")

    # Blank/Whitespace S3 credentials
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_access_key="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_access_key="   ")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_secret_key="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", s3_secret_key="   ")

def test_kafka_validation(monkeypatch):
    # Blank Kafka bootstrap servers
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", kafka_bootstrap_servers="")

    # Blank/Whitespace Kafka topics and groups
    monkeypatch.setenv("WORKER_TOPICS", "")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012")

    monkeypatch.setenv("WORKER_TOPICS", "file.processing.image,")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012")

    monkeypatch.delenv("WORKER_TOPICS", raising=False)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_topics=())

    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", kafka_topic_dlq="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", kafka_topic_dlq="   ")
        
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", kafka_consumer_group_id="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", kafka_consumer_group_id="   ")

def test_worker_topics_default_subscribes_to_all_workload_topics():
    settings = Settings(internal_api_token="test-token-1234567890123456789012")

    assert settings.worker_topics == (
        "file.processing.checksum",
        "file.processing.image",
        "file.processing.audio",
        "file.processing.video",
    )

def test_worker_topics_accepts_one_topic(monkeypatch):
    monkeypatch.setenv("WORKER_TOPICS", "file.processing.video")

    settings = Settings(internal_api_token="test-token-1234567890123456789012")

    assert settings.worker_topics == ("file.processing.video",)

def test_worker_topics_accepts_multiple_topics(monkeypatch):
    monkeypatch.setenv("WORKER_TOPICS", "file.processing.checksum, file.processing.image")

    settings = Settings(internal_api_token="test-token-1234567890123456789012")

    assert settings.worker_topics == ("file.processing.checksum", "file.processing.image")

def test_log_level_validation():
    # Invalid log level
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", log_level="INVALID")  # type: ignore

def test_metadata_api_validation():
    # Invalid metadata API URL
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", metadata_api_base_url="not-a-url")  # type: ignore
    
    # Blank metadata API URL
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", metadata_api_base_url=" ")  # type: ignore

def test_embedding_settings_validation():
    settings = Settings(
        internal_api_token="test-token-1234567890123456789012",
        embedding_processor_enabled=True,
        embedding_dimension=768,
        embedding_image_input_size=224,
        embedding_max_image_bytes=1024,
        embedding_max_source_pixels=10_000,
        embedding_direct_decode_max_pixels=5_000,
        processable_image_mime_types="image/jpeg,image/png",
        embedding_model_name="openai/clip-vit-large-patch14",
        embedding_model_version="1",
        triton_grpc_url="localhost:8001",
        triton_model_name="image_embedding",
        triton_model_version="1",
        triton_input_tensor_name="pixel_values",
        triton_output_tensor_name="image_embeds",
    )

    assert settings.embedding_dimension == 768
    assert settings.triton_model_name == "image_embedding"

    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", embedding_dimension=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", embedding_image_input_size=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", embedding_max_image_bytes=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", embedding_max_source_pixels=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", embedding_direct_decode_max_pixels=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", processable_image_mime_types="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", embedding_model_name="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", triton_grpc_url="")

def test_video_settings_validation():
    settings = Settings(
        internal_api_token="test-token-1234567890123456789012",
        worker_video_enabled=True,
        worker_video_max_file_bytes=1024,
        worker_video_max_duration_seconds=60,
        worker_video_max_sampled_frames=8,
        worker_video_min_sampled_frames=2,
        worker_video_target_interval_seconds=5,
        worker_video_frame_timeout_seconds=2,
        worker_video_max_frame_bytes=1024,
        worker_video_supported_mime_types="video/mp4,video/webm",
    )

    assert settings.worker_video_supported_mime_types == "video/mp4,video/webm"

    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_max_file_bytes=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_max_duration_seconds=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_max_sampled_frames=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_min_sampled_frames=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_target_interval_seconds=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_frame_timeout_seconds=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_video_supported_mime_types="")

def test_audio_settings_validation():
    settings = Settings(
        internal_api_token="test-token-1234567890123456789012",
        worker_audio_enabled=True,
        worker_audio_supported_mime_types="audio/mpeg,audio/flac",
        worker_audio_max_file_bytes=1024,
        worker_audio_max_duration_seconds=60,
        worker_audio_fingerprint_length_seconds=30,
        worker_audio_subprocess_timeout_seconds=5,
        worker_audio_max_fingerprint_chars=1024,
    )

    assert settings.worker_audio_supported_mime_types == "audio/mpeg,audio/flac"

    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_audio_supported_mime_types="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_audio_max_file_bytes=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_audio_max_duration_seconds=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_audio_fingerprint_length_seconds=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_audio_subprocess_timeout_seconds=0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token-1234567890123456789012", worker_audio_max_fingerprint_chars=0)
