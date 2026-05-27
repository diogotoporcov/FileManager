import asyncio
from typing import Any
from urllib.parse import urlparse

import numpy as np

from app.embeddings.base import (
    ImageEmbeddingInferenceClient,
    ImageEmbeddingModelOutputError,
    ImageEmbeddingServiceUnavailable,
)


class TritonImageEmbeddingClient(ImageEmbeddingInferenceClient):
    def __init__(
        self,
        http_url: str,
        model_name: str,
        model_version: str,
        input_tensor_name: str,
        output_tensor_name: str,
    ):
        self.http_url = _normalize_triton_http_url(http_url)
        self.model_name = model_name
        self.model_version = model_version
        self.input_tensor_name = input_tensor_name
        self.output_tensor_name = output_tensor_name
        self._client: Any | None = None
        self._httpclient: Any | None = None

    async def embed_image(self, pixel_values: np.ndarray) -> np.ndarray:
        return await asyncio.to_thread(self._infer, pixel_values)

    def _infer(self, pixel_values: np.ndarray) -> np.ndarray:
        httpclient = self._get_httpclient()
        try:
            infer_input = httpclient.InferInput(
                self.input_tensor_name,
                list(pixel_values.shape),
                "FP32",
            )
            infer_input.set_data_from_numpy(pixel_values)
            requested_output = httpclient.InferRequestedOutput(self.output_tensor_name)
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

    def _get_httpclient(self) -> Any:
        if self._httpclient is None:
            import tritonclient.http as httpclient  # type: ignore[import-untyped]

            self._httpclient = httpclient
        return self._httpclient

    def _get_client(self) -> Any:
        if self._client is None:
            self._client = self._get_httpclient().InferenceServerClient(url=self.http_url)
        return self._client


def _normalize_triton_http_url(url: str) -> str:
    parsed = urlparse(url)
    if parsed.scheme in {"http", "https"} and parsed.netloc:
        return parsed.netloc + parsed.path.rstrip("/")
    return url.rstrip("/")
