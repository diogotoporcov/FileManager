import uuid
from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from app.events.models import FileProcessingRequestedEvent


def event_payload(owner_user_id: str | None = None) -> dict[str, object]:
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": "file.processing.requested",
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "fileId": str(uuid.uuid4()),
        "processingJobId": str(uuid.uuid4()),
        "jobType": "CHECKSUM",
        "storagePath": "uploads/file.png",
        "mimeType": "image/png",
        "size": 1024,
        "ownerUserId": owner_user_id or str(uuid.uuid4()),
    }


def test_event_parsing():
    json_data = event_payload()

    event = FileProcessingRequestedEvent.model_validate(json_data)

    assert event.file_id == uuid.UUID(str(json_data["fileId"]))
    assert event.mime_type == "image/png"
    assert event.size == 1024
    assert event.owner_user_id == uuid.UUID(str(json_data["ownerUserId"]))


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
    )

    dump = event.model_dump(by_alias=True)
    assert "eventId" in dump
    assert "fileId" in dump
    assert "ownerUserId" in dump
    assert dump["mimeType"] == "text/plain"
    assert event.storage_reference.path == "path"


def test_event_rejects_missing_owner_user():
    json_data = event_payload()
    del json_data["ownerUserId"]

    with pytest.raises(ValidationError) as excinfo:
        FileProcessingRequestedEvent.model_validate(json_data)

    assert "ownerUserId" in str(excinfo.value)
