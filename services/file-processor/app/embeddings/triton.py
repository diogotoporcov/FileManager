import asyncio
from collections.abc import Sequence
from typing import Any, Protocol, cast
from urllib.parse import urlparse

import numpy as np

from app.embeddings.base import (
    ImageEmbeddingInferenceClient,
    ImageEmbeddingModelOutputError,
    ImageEmbeddingServiceUnavailable,
)


class TritonInferInput(Protocol):
    def set_data_from_numpy(self, data: np.ndarray) -> None:
        pass


class TritonInferResponse(Protocol):
    def as_numpy(self, name: str) -> np.ndarray | None:
        pass


class TritonInferRequestedOutput(Protocol):
    pass


class TritonInferenceServerClient(Protocol):
    def infer(
        self,
        *,
        model_name: str,
        inputs: Sequence[TritonInferInput],
        model_version: str,
        outputs: Sequence[TritonInferRequestedOutput],
    ) -> TritonInferResponse:
        pass


class TritonGrpcClientModule(Protocol):
    def InferInput(self, name: str, shape: Sequence[int], datatype: str) -> TritonInferInput:
        pass

    def InferRequestedOutput(self, name: str) -> TritonInferRequestedOutput:
        pass

    def InferenceServerClient(self, *, url: str) -> TritonInferenceServerClient:
        pass


class TritonImageEmbeddingClient(ImageEmbeddingInferenceClient):
    def __init__(
        self,
        grpc_url: str,
        model_name: str,
        model_version: str,
        input_tensor_name: str,
        output_tensor_name: str,
    ):
        self.grpc_url = _normalize_triton_grpc_url(grpc_url)
        self.model_name = model_name
        self.model_version = model_version
        self.input_tensor_name = input_tensor_name
        self.output_tensor_name = output_tensor_name
        self._client: TritonInferenceServerClient | None = None
        self._grpcclient: TritonGrpcClientModule | None = None

    async def embed_image(self, pixel_values: np.ndarray) -> np.ndarray:
        return await asyncio.to_thread(self._infer, pixel_values)

    def _infer(self, pixel_values: np.ndarray) -> np.ndarray:
        grpcclient = self._get_grpcclient()
        try:
            infer_input = grpcclient.InferInput(
                self.input_tensor_name,
                list(pixel_values.shape),
                "FP32",
            )
            infer_input.set_data_from_numpy(pixel_values)

            requested_output = grpcclient.InferRequestedOutput(self.output_tensor_name)
            response = self._get_client().infer(
                model_name=self.model_name,
                inputs=[infer_input],
                model_version=self.model_version,
                outputs=[requested_output],
            )
            output = response.as_numpy(self.output_tensor_name)

            if output is None:
                raise ImageEmbeddingModelOutputError("Triton response missing image embedding output")

            return output

        except ImageEmbeddingModelOutputError:
            raise

        except Exception as exc:
            raise ImageEmbeddingServiceUnavailable("Triton image embedding inference failed") from exc

    def _get_grpcclient(self) -> TritonGrpcClientModule:
        grpcclient_module = self._grpcclient

        if grpcclient_module is None:
            import tritonclient.grpc as grpcclient

            imported_grpcclient: Any = grpcclient
            grpcclient_module = cast(TritonGrpcClientModule, imported_grpcclient)
            self._grpcclient = grpcclient_module

        return grpcclient_module

    def _get_client(self) -> TritonInferenceServerClient:
        client = self._client

        if client is None:
            client = self._get_grpcclient().InferenceServerClient(url=self.grpc_url)
            self._client = client

        return client


def _normalize_triton_grpc_url(url: str) -> str:
    parsed = urlparse(url)

    if parsed.scheme == "grpc" and parsed.netloc:
        return parsed.netloc + parsed.path.rstrip("/")

    return url.rstrip("/")
