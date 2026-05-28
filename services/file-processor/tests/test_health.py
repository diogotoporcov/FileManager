from collections.abc import Mapping
from typing import Final

from fastapi.testclient import TestClient
from pydantic import JsonValue, TypeAdapter

from app.main import app

JSON_OBJECT_ADAPTER: Final[TypeAdapter[Mapping[str, JsonValue]]] = TypeAdapter(Mapping[str, JsonValue])

client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    data = JSON_OBJECT_ADAPTER.validate_python(response.json())
    assert data["status"] == "UP"
    processors = data["processors"]
    assert isinstance(processors, list)
    assert "checksum" in processors
    assert "embedding" in processors
