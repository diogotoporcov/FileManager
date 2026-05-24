import pytest
from pydantic import ValidationError
from app.config import Settings

def test_settings_validation():
    # Valid settings should work (using defaults or explicit valid values)
    s1 = Settings(
        internal_api_token="test-token",
        metrics_enabled=True,
        worker_max_attempts=1,
        worker_retry_backoff_seconds=0,
        worker_retry_backoff_multiplier=1
    )
    assert s1.metrics_enabled is True
    
    s2 = Settings(
        internal_api_token="test-token",
        metrics_enabled=False
    )
    assert s2.metrics_enabled is False
    
    # Invalid max attempts (must be >= 1)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", worker_max_attempts=0)
        
    # Invalid backoff (must be >= 0)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", worker_retry_backoff_seconds=-1.0)
        
    # Invalid multiplier (must be >= 1)
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", worker_retry_backoff_multiplier=0.5)

def test_internal_api_token_validation(monkeypatch):
    # Blank internal_api_token
    with pytest.raises(ValidationError):
        Settings(internal_api_token="")
    
    # Whitespace internal_api_token
    with pytest.raises(ValidationError):
        Settings(internal_api_token="   ")
    
    # Missing internal_api_token
    monkeypatch.delenv("INTERNAL_API_TOKEN", raising=False)
    # We need to make sure we don't accidentally get it from any other source
    with pytest.raises(ValidationError):
        # noinspection PyArgumentList
        Settings()

def test_s3_validation():
    # Invalid S3 endpoint
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_endpoint="not-a-url")  # type: ignore

    # Blank S3 bucket
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_bucket_name="")
    
    # Whitespace S3 bucket
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_bucket_name="   ")

    # Blank/Whitespace S3 credentials
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_access_key="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_access_key="   ")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_secret_key="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", s3_secret_key="   ")

def test_kafka_validation():
    # Blank Kafka bootstrap servers
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", kafka_bootstrap_servers="")

    # Blank/Whitespace Kafka topics and groups
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", kafka_topic_file_processing="")
    
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", kafka_topic_dlq="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", kafka_topic_dlq="   ")
        
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", kafka_consumer_group_id="")
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", kafka_consumer_group_id="   ")

def test_log_level_validation():
    # Invalid log level
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", log_level="INVALID")  # type: ignore

def test_metadata_api_validation():
    # Invalid metadata API URL
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", metadata_api_base_url="not-a-url")  # type: ignore
    
    # Blank metadata API URL
    with pytest.raises(ValidationError):
        Settings(internal_api_token="test-token", metadata_api_base_url=" ")  # type: ignore
