from datetime import datetime
from uuid import UUID
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field, model_validator

class FileProcessingRequestedEvent(BaseModel):
    event_id: UUID = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    occurred_at: datetime = Field(alias="occurredAt")
    file_id: UUID = Field(alias="fileId")
    processing_job_id: UUID = Field(alias="processingJobId")
    storage_path: str = Field(alias="storagePath")
    mime_type: str = Field(alias="mimeType")
    size: int = Field(alias="size")
    owner_user_id: Optional[UUID] = Field(default=None, alias="ownerUserId")
    owner_organization_id: Optional[UUID] = Field(default=None, alias="ownerOrganizationId")

    model_config = ConfigDict(populate_by_name=True)

    @model_validator(mode="after")
    def validate_one_owner(self) -> "FileProcessingRequestedEvent":
        if self.owner_user_id and self.owner_organization_id:
            raise ValueError("Exactly one of owner_user_id or owner_organization_id must be present, not both.")
        if not self.owner_user_id and not self.owner_organization_id:
            raise ValueError("Exactly one of owner_user_id or owner_organization_id must be present, neither found.")
        return self
