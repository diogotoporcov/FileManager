from abc import ABC, abstractmethod

import numpy as np


class ImageEmbeddingInferenceError(Exception):
    pass


class ImageEmbeddingServiceUnavailable(ImageEmbeddingInferenceError):
    pass


class ImageEmbeddingModelOutputError(ImageEmbeddingInferenceError):
    pass


class ImageEmbeddingInferenceClient(ABC):
    @abstractmethod
    async def embed_image(self, pixel_values: np.ndarray) -> np.ndarray:
        pass
