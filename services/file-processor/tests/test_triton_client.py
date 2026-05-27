from types import SimpleNamespace
from typing import Any, Dict, List, Tuple

import numpy as np
import pytest

from app.embeddings.base import (
    ImageEmbeddingModelOutputError,
    ImageEmbeddingServiceUnavailable,
)
from app.embeddings.triton import TritonImageEmbeddingClient, _normalize_triton_http_url


class FakeInferInput:
    def __init__(self, name: str, shape: List[int], datatype: str) -> None:
        self.name = name
        self.shape = shape
        self.datatype = datatype
        self.data: np.ndarray | None = None

    def set_data_from_numpy(self, data: np.ndarray) -> None:
        self.data = data


class FakeInferRequestedOutput:
    def __init__(self, name: str) -> None:
        self.name = name


class FakeResponse:
    def __init__(self, outputs: Dict[str, np.ndarray]) -> None:
        self.outputs = outputs

    def as_numpy(self, name: str) -> np.ndarray | None:
        return self.outputs.get(name)


class FakeInferenceServerClient:
    def __init__(self, response: FakeResponse | None = None, error: Exception | None = None) -> None:
        self.response = response or FakeResponse({"image_embeds": np.ones((1, 768), dtype=np.float32)})
        self.error = error
        self.infer_calls: List[Dict[str, Any]] = []

    def infer(self, **kwargs: Any) -> FakeResponse:
        self.infer_calls.append(kwargs)
        if self.error is not None:
            raise self.error
        return self.response


def make_client(fake_server: FakeInferenceServerClient) -> Tuple[TritonImageEmbeddingClient, List[str]]:
    created_urls: List[str] = []

    def create_server(url: str) -> FakeInferenceServerClient:
        created_urls.append(url)
        return fake_server

    client = TritonImageEmbeddingClient(
        http_url="http://localhost:8000",
        model_name="image_embedding",
        model_version="1",
        input_tensor_name="pixel_values",
        output_tensor_name="image_embeds",
    )
    client._httpclient = SimpleNamespace(
        InferInput=FakeInferInput,
        InferRequestedOutput=FakeInferRequestedOutput,
        InferenceServerClient=create_server,
    )
    return client, created_urls


def test_normalizes_triton_http_url_for_client_library() -> None:
    assert _normalize_triton_http_url("http://localhost:8000") == "localhost:8000"
    assert _normalize_triton_http_url("https://triton.example.test:8443/v2/") == "triton.example.test:8443/v2"
    assert _normalize_triton_http_url("filemanager-triton:8000/") == "filemanager-triton:8000"


@pytest.mark.asyncio
async def test_embed_image_sends_expected_triton_request() -> None:
    fake_server = FakeInferenceServerClient()
    client, created_urls = make_client(fake_server)
    pixel_values = np.zeros((1, 3, 224, 224), dtype=np.float32)

    output = await client.embed_image(pixel_values)

    assert output.shape == (1, 768)
    assert created_urls == ["localhost:8000"]
    assert len(fake_server.infer_calls) == 1
    call = fake_server.infer_calls[0]
    assert call["model_name"] == "image_embedding"
    assert call["model_version"] == "1"

    inputs = call["inputs"]
    assert len(inputs) == 1
    infer_input = inputs[0]
    assert infer_input.name == "pixel_values"
    assert infer_input.shape == [1, 3, 224, 224]
    assert infer_input.datatype == "FP32"
    assert infer_input.data is pixel_values

    outputs = call["outputs"]
    assert len(outputs) == 1
    assert outputs[0].name == "image_embeds"


@pytest.mark.asyncio
async def test_embed_image_rejects_missing_triton_output() -> None:
    fake_server = FakeInferenceServerClient(response=FakeResponse({}))
    client, _ = make_client(fake_server)

    with pytest.raises(ImageEmbeddingModelOutputError, match="missing image embedding output"):
        await client.embed_image(np.zeros((1, 3, 224, 224), dtype=np.float32))


@pytest.mark.asyncio
async def test_embed_image_maps_triton_failures_to_application_error() -> None:
    fake_server = FakeInferenceServerClient(error=RuntimeError("triton unavailable"))
    client, _ = make_client(fake_server)

    with pytest.raises(ImageEmbeddingServiceUnavailable, match="Triton image embedding inference failed"):
        await client.embed_image(np.zeros((1, 3, 224, 224), dtype=np.float32))
