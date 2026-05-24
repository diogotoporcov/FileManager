import pytest
from pydantic import ValidationError
from app.config import Settings

def test_settings_validation():
    # Valid settings should work (using defaults or explicit valid values)
    Settings(
        internal_api_token="test-token",
        worker_max_attempts=1,
        worker_retry_backoff_seconds=0,
        worker_retry_backoff_multiplier=1
    )
    
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
    
    # Missing internal_api_token
    monkeypatch.delenv("INTERNAL_API_TOKEN", raising=False)
    # We need to make sure we don't accidentally get it from any other source
    with pytest.raises(ValidationError):
        Settings()
