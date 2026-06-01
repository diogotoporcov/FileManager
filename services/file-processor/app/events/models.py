from datetime import datetime
from typing import Self
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator
from app.storage.base import StorageObjectReference


class FileProcessingRequestedEvent(BaseModel):
    event_id: UUID = Field(validation_alias="eventId", serialization_alias="eventId")
    event_type: str = Field(validation_alias="eventType", serialization_alias="eventType")
    occurred_at: datetime = Field(validation_alias="occurredAt", serialization_alias="occurredAt")
    file_id: UUID = Field(validation_alias="fileId", serialization_alias="fileId")
    processing_job_id: UUID = Field(validation_alias="processingJobId", serialization_alias="processingJobId")
    job_type: str = Field(validation_alias="jobType", serialization_alias="jobType")
    storage_path: str = Field(validation_alias="storagePath", serialization_alias="storagePath")
    mime_type: str = Field(validation_alias="mimeType", serialization_alias="mimeType")
    size: int = Field(validation_alias="size")
    owner_user_id: UUID | None = Field(default=None, validation_alias="ownerUserId", serialization_alias="ownerUserId")
    owner_organization_id: UUID | None = Field(
        default=None,
        validation_alias="ownerOrganizationId",
        serialization_alias="ownerOrganizationId",
    )

    model_config = ConfigDict(populate_by_name=True)

    @property
    def storage_reference(self) -> StorageObjectReference:
        return StorageObjectReference(path=self.storage_path)

    @model_validator(mode="after")
    def validate_one_owner(self) -> Self:
        if self.owner_user_id and self.owner_organization_id:
            raise ValueError("Exactly one of owner_user_id or owner_organization_id must be present, not both.")

        if not self.owner_user_id and not self.owner_organization_id:
            raise ValueError("Exactly one of owner_user_id or owner_organization_id must be present, neither found.")

        return self
