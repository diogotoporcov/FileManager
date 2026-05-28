from collections.abc import Callable, Mapping, MutableSequence, Sequence
from dataclasses import dataclass
from typing import TypeAlias

import numpy as np
import pytest

from app.embeddings.base import (
    ImageEmbeddingModelOutputError,
    ImageEmbeddingServiceUnavailable,
)
from app.embeddings.triton import (
    TritonInferInput,
    TritonInferRequestedOutput,
    TritonImageEmbeddingClient,
    _normalize_triton_grpc_url,
)

FakeInferCallValue: TypeAlias = str | Sequence[TritonInferInput] | Sequence[TritonInferRequestedOutput]


class FakeInferInput:
    def __init__(self, name: str, shape: Sequence[int], datatype: str):
        self.name = name
        self.shape = shape
        self.datatype = datatype
        self.data: np.ndarray | None = None

    def set_data_from_numpy(self, data: np.ndarray):
        self.data = data


class FakeInferRequestedOutput:
    def __init__(self, name: str):
        self.name = name


class FakeResponse:
    def __init__(self, outputs: Mapping[str, np.ndarray]):
        self.outputs = outputs

    def as_numpy(self, name: str) -> np.ndarray | None:
        return self.outputs.get(name)


class FakeInferenceServerClient:
    def __init__(self, response: FakeResponse | None = None, error: Exception | None = None):
        self.response = response or FakeResponse({"image_embeds": np.ones((1, 768), dtype=np.float32)})
        self.error = error
        self.infer_calls: MutableSequence[Mapping[str, FakeInferCallValue]] = []

    def infer(
        self,
        *,
        model_name: str,
        inputs: Sequence[TritonInferInput],
        model_version: str,
        outputs: Sequence[TritonInferRequestedOutput],
    ) -> FakeResponse:
        self.infer_calls.append(
            {
                "model_name": model_name,
                "inputs": inputs,
                "model_version": model_version,
                "outputs": outputs,
            }
        )

        if self.error is not None:
            raise self.error

        return self.response


@dataclass(frozen=True)
class FakeGrpcClientModule:
    server_factory: Callable[[str], FakeInferenceServerClient]

    def InferInput(self, name: str, shape: Sequence[int], datatype: str) -> FakeInferInput:
        return FakeInferInput(name, shape, datatype)

    def InferRequestedOutput(self, name: str) -> FakeInferRequestedOutput:
        return FakeInferRequestedOutput(name)

    def InferenceServerClient(self, *, url: str) -> FakeInferenceServerClient:
        return self.server_factory(url)


@dataclass(frozen=True)
class ClientFixture:
    client: TritonImageEmbeddingClient
    created_urls: Sequence[str]


def make_client(fake_server: FakeInferenceServerClient) -> ClientFixture:
    created_urls: MutableSequence[str] = []

    def create_server(url: str) -> FakeInferenceServerClient:
        created_urls.append(url)
        return fake_server

    client = TritonImageEmbeddingClient(
        grpc_url="localhost:8001",
        model_name="image_embedding",
        model_version="1",
        input_tensor_name="pixel_values",
        output_tensor_name="image_embeds",
    )
    client._grpcclient = FakeGrpcClientModule(create_server)
    return ClientFixture(client=client, created_urls=created_urls)


def test_normalizes_triton_grpc_url_for_client_library():
    assert _normalize_triton_grpc_url("grpc://localhost:8001") == "localhost:8001"
    assert _normalize_triton_grpc_url("grpc://triton.example.test:8443/v2/") == "triton.example.test:8443/v2"
    assert _normalize_triton_grpc_url("filemanager-triton:8001/") == "filemanager-triton:8001"


@pytest.mark.asyncio
async def test_embed_image_sends_expected_triton_request():
    fake_server = FakeInferenceServerClient()
    fixture = make_client(fake_server)
    pixel_values = np.zeros((1, 3, 224, 224), dtype=np.float32)

    output = await fixture.client.embed_image(pixel_values)

    assert output.shape == (1, 768)
    assert fixture.created_urls == ["localhost:8001"]
    assert len(fake_server.infer_calls) == 1
    call = fake_server.infer_calls[0]
    assert call["model_name"] == "image_embedding"
    assert call["model_version"] == "1"

    inputs = call["inputs"]
    assert isinstance(inputs, Sequence)
    assert len(inputs) == 1
    infer_input = inputs[0]
    assert isinstance(infer_input, FakeInferInput)
    assert infer_input.name == "pixel_values"
    assert infer_input.shape == [1, 3, 224, 224]
    assert infer_input.datatype == "FP32"
    assert infer_input.data is pixel_values

    outputs = call["outputs"]
    assert isinstance(outputs, Sequence)
    assert len(outputs) == 1
    assert isinstance(outputs[0], FakeInferRequestedOutput)
    assert outputs[0].name == "image_embeds"


@pytest.mark.asyncio
async def test_embed_image_rejects_missing_triton_output():
    fake_server = FakeInferenceServerClient(response=FakeResponse({}))
    fixture = make_client(fake_server)

    with pytest.raises(ImageEmbeddingModelOutputError, match="missing image embedding output"):
        await fixture.client.embed_image(np.zeros((1, 3, 224, 224), dtype=np.float32))


@pytest.mark.asyncio
async def test_embed_image_maps_triton_failures_to_application_error():
    fake_server = FakeInferenceServerClient(error=RuntimeError("triton unavailable"))
    fixture = make_client(fake_server)

    with pytest.raises(ImageEmbeddingServiceUnavailable, match="Triton image embedding inference failed"):
        await fixture.client.embed_image(np.zeros((1, 3, 224, 224), dtype=np.float32))
