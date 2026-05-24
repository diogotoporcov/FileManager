import uuid
import pytest
from datetime import datetime, timezone
from pydantic import ValidationError
from app.events.models import FileProcessingRequestedEvent

def test_event_parsing():
    event_id = uuid.uuid4()
    file_id = uuid.uuid4()
    job_id = uuid.uuid4()
    user_id = uuid.uuid4()
    occurred_at = datetime.now(timezone.utc).isoformat()

    json_data = {
        "eventId": str(event_id),
        "eventType": "file.processing.requested",
        "occurredAt": occurred_at,
        "fileId": str(file_id),
        "processingJobId": str(job_id),
        "jobType": "CHECKSUM",
        "storagePath": "uploads/file.png",
        "mimeType": "image/png",
        "size": 1024,
        "ownerUserId": str(user_id),
        "ownerOrganizationId": None
    }

    event = FileProcessingRequestedEvent.model_validate(json_data)
    
    assert event.event_id == event_id
    assert event.file_id == file_id
    assert event.mime_type == "image/png"
    assert event.size == 1024

def test_event_parsing_optional_owners():
    event_id = uuid.uuid4()
    file_id = uuid.uuid4()
    job_id = uuid.uuid4()
    occurred_at = datetime.now(timezone.utc).isoformat()

    json_data = {
        "eventId": str(event_id),
        "eventType": "file.processing.requested",
        "occurredAt": occurred_at,
        "fileId": str(file_id),
        "processingJobId": str(job_id),
        "jobType": "CHECKSUM",
        "storagePath": "uploads/file.png",
        "mimeType": "image/png",
        "size": 1024,
        "ownerUserId": None,
        "ownerOrganizationId": str(uuid.uuid4())
    }

    event = FileProcessingRequestedEvent.model_validate(json_data)
    
    assert event.owner_user_id is None
    assert event.owner_organization_id is not None

def test_event_serialization_aliases():
    event = FileProcessingRequestedEvent(
        event_id=uuid.uuid4(),
        event_type="test",
        occurred_at=datetime.now(timezone.utc),
        file_id=uuid.uuid4(),
        processing_job_id=uuid.uuid4(),
        job_type="CHECKSUM",
        storage_path="path",
        mime_type="text/plain",
        size=10,
        owner_user_id=uuid.uuid4(),
        owner_organization_id=None
    )
    
    dump = event.model_dump(by_alias=True)
    assert "eventId" in dump
    assert "fileId" in dump
    assert dump["mimeType"] == "text/plain"

def test_event_invalid_owners_both_present():
    json_data = {
        "eventId": str(uuid.uuid4()),
        "eventType": "test",
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "fileId": str(uuid.uuid4()),
        "processingJobId": str(uuid.uuid4()),
        "jobType": "CHECKSUM",
        "storagePath": "path",
        "mimeType": "text/plain",
        "size": 10,
        "ownerUserId": str(uuid.uuid4()),
        "ownerOrganizationId": str(uuid.uuid4())
    }
    with pytest.raises(ValidationError) as excinfo:
        FileProcessingRequestedEvent.model_validate(json_data)
    assert "Exactly one of owner_user_id or owner_organization_id must be present, not both" in str(excinfo.value)

def test_event_invalid_owners_none_present():
    json_data = {
        "eventId": str(uuid.uuid4()),
        "eventType": "test",
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "fileId": str(uuid.uuid4()),
        "processingJobId": str(uuid.uuid4()),
        "jobType": "CHECKSUM",
        "storagePath": "path",
        "mimeType": "text/plain",
        "size": 10,
        "ownerUserId": None,
        "ownerOrganizationId": None
    }
    with pytest.raises(ValidationError) as excinfo:
        FileProcessingRequestedEvent.model_validate(json_data)
    assert "Exactly one of owner_user_id or owner_organization_id must be present, neither found" in str(excinfo.value)
