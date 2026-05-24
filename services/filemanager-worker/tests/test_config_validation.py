import pytest
from pydantic import ValidationError
from app.config import Settings

def test_settings_validation():
    # Valid settings should work (using defaults or explicit valid values)
    Settings(worker_max_attempts=1, worker_retry_backoff_seconds=0, worker_retry_backoff_multiplier=1)
    
    # Invalid max attempts (must be >= 1)
    with pytest.raises(ValidationError):
        Settings(worker_max_attempts=0)
        
    # Invalid backoff (must be >= 0)
    with pytest.raises(ValidationError):
        Settings(worker_retry_backoff_seconds=-1.0)
        
    # Invalid multiplier (must be >= 1)
    with pytest.raises(ValidationError):
        Settings(worker_retry_backoff_multiplier=0.5)
