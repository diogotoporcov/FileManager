import pytest
from unittest.mock import MagicMock
from fastapi.testclient import TestClient
from app.main import app
from app import metrics
from app.config import settings
from app.worker.flow import ProcessingFlow
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink
from app.events.models import FileProcessingRequestedEvent
from app.worker.errors import RetryableProcessingError

@pytest.fixture
def client():
    return TestClient(app)

def test_metrics_endpoint_returns_prometheus_format(client):
    settings.metrics_enabled = True
    response = client.get("/metrics", headers={"Authorization": f"Bearer {settings.internal_api_token}"})
    assert response.status_code == 200
    assert "filemanager_worker_events_received_total" in response.text
    assert "TYPE filemanager_worker_events_received_total counter" in response.text

def test_metrics_endpoint_unauthorized(client):
    response = client.get("/metrics", headers={"Authorization": "Bearer invalid-token"})
    assert response.status_code == 401

def test_metrics_endpoint_no_token(client):
    settings.metrics_enabled = True
    response = client.get("/metrics")
    assert response.status_code == 401

def test_metrics_endpoint_disabled_no_token(client):
    settings.metrics_enabled = False
    response = client.get("/metrics")
    assert response.status_code == 404

def test_metrics_endpoint_respects_enabled_setting(client):
    settings.metrics_enabled = False
    response = client.get("/metrics", headers={"Authorization": f"Bearer {settings.internal_api_token}"})
    assert response.status_code == 404
    
    # Even with invalid token, should be 404 if disabled
    response = client.get("/metrics", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 404
    
    # Reset for other tests
    settings.metrics_enabled = True

def test_counters_increment():
    initial_count = metrics.EVENTS_RECEIVED._value.get()
    metrics.EVENTS_RECEIVED.inc()
    assert metrics.EVENTS_RECEIVED._value.get() == initial_count + 1

def test_labeled_counters_increment():
    initial_count = metrics.EVENTS_PROCESSED.labels(status="success")._value.get()
    metrics.EVENTS_PROCESSED.labels(status="success").inc()
    assert metrics.EVENTS_PROCESSED.labels(status="success")._value.get() == initial_count + 1

def test_histograms_observe():
    metrics.PROCESSING_DURATION.observe(0.5)
    assert metrics.PROCESSING_DURATION._sum.get() >= 0.5

@pytest.fixture
def sample_event():
    import uuid
    from datetime import datetime, timezone
    return FileProcessingRequestedEvent(
        event_id=uuid.UUID("00000000-0000-0000-0000-000000000001"),
        event_type="test.type",
        occurred_at=datetime(2024, 1, 1, tzinfo=timezone.utc),
        file_id=uuid.UUID("00000000-0000-0000-0000-000000000002"),
        processing_job_id=uuid.UUID("00000000-0000-0000-0000-000000000003"),
        job_type="CHECKSUM",
        storage_path="test/path",
        mime_type="text/plain",
        size=100,
        owner_user_id=uuid.UUID("00000000-0000-0000-0000-000000000004")
    )

@pytest.mark.asyncio
async def test_flow_metrics_recorded_on_success(sample_event):
    mock_processor = MagicMock(spec=Processor)
    mock_processor.name = "CHECKSUM"
    mock_processor.should_process.return_value = True
    mock_processor.process.return_value = {"sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
    
    mock_sink = MagicMock(spec=ProcessingResultSink)
    
    flow = ProcessingFlow([mock_processor], mock_sink)
    
    initial_runs = metrics.PROCESSOR_RUNS.labels(processor="CHECKSUM")._value.get()
    initial_duration_sum = metrics.PROCESSING_DURATION._sum.get()
    
    await flow.run(sample_event)
    
    assert metrics.PROCESSOR_RUNS.labels(processor="CHECKSUM")._value.get() == initial_runs + 1
    assert metrics.PROCESSING_DURATION._sum.get() >= initial_duration_sum

@pytest.mark.asyncio
async def test_flow_metrics_recorded_on_failure(sample_event):
    mock_processor = MagicMock(spec=Processor)
    mock_processor.name = "CHECKSUM"
    mock_processor.should_process.return_value = True
    mock_processor.process.side_effect = Exception("error")
    
    mock_sink = MagicMock(spec=ProcessingResultSink)
    
    flow = ProcessingFlow([mock_processor], mock_sink)
    
    initial_failures = metrics.PROCESSOR_FAILURES.labels(processor="CHECKSUM", failure_type="UnexpectedError")._value.get()
    initial_duration_sum = metrics.PROCESSING_DURATION._sum.get()
    initial_proc_duration_sum = metrics.PROCESSOR_DURATION.labels(processor="CHECKSUM")._sum.get()
    
    with pytest.raises(RetryableProcessingError):
        await flow.run(sample_event)
    
    assert metrics.PROCESSOR_FAILURES.labels(processor="CHECKSUM", failure_type="UnexpectedError")._value.get() == initial_failures + 1
    assert metrics.PROCESSING_DURATION._sum.get() >= initial_duration_sum
    assert metrics.PROCESSOR_DURATION.labels(processor="CHECKSUM")._sum.get() >= initial_proc_duration_sum
