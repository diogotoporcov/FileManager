import asyncio
import logging
import secrets
from collections.abc import Mapping, MutableSequence, Sequence
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, Response, status
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app.config import settings
from app.embeddings.triton import TritonImageEmbeddingClient
from app.processors.base import Processor
from app.processors.audio import AudioFingerprintProcessor
from app.processors.embedding import ImageEmbeddingProcessor
from app.processors.impl import ChecksumProcessor, PHashProcessor
from app.sinks.http import HttpProcessingResultSink
from app.storage.s3 import S3ObjectStorageReader
from app.worker.consumer import EventConsumer
from app.worker.dlq import KafkaDeadLetterPublisher
from app.worker.flow import ProcessingFlow
from app.worker.handler import WorkerMessageHandler

logging.basicConfig(level=getattr(logging, settings.log_level.upper()))
logger = logging.getLogger(__name__)

# Wire dependencies
storage_reader = S3ObjectStorageReader()
result_sink = HttpProcessingResultSink()
dlq_publisher = KafkaDeadLetterPublisher()

processors: MutableSequence[Processor] = [
    ChecksumProcessor(storage_reader),
    PHashProcessor(storage_reader),
]

needs_embedding_client = settings.embedding_processor_enabled and settings.worker_image_embedding_enabled
embedding_client = None

if needs_embedding_client:
    embedding_client = TritonImageEmbeddingClient(
        grpc_url=settings.triton_grpc_url,
        model_name=settings.triton_model_name,
        model_version=settings.triton_model_version,
        input_tensor_name=settings.triton_input_tensor_name,
        output_tensor_name=settings.triton_output_tensor_name,
        inference_timeout_seconds=settings.triton_inference_timeout_seconds,
    )

if settings.worker_image_embedding_enabled and embedding_client is not None:
    processors.append(ImageEmbeddingProcessor(storage_reader, embedding_client))

if settings.worker_audio_enabled and settings.worker_audio_fingerprint_enabled:
    processors.append(AudioFingerprintProcessor(storage_reader))

flow = ProcessingFlow(processors, result_sink)
handler = WorkerMessageHandler(flow, dlq_publisher)
consumer = EventConsumer(handler)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    logger.info("Starting worker lifespan")
    consumer_task = None

    if settings.worker_consumer_enabled:
        logger.info("Kafka consumer is enabled, starting background task")
        consumer_task = asyncio.create_task(consumer.start())

    else:
        logger.info("Kafka consumer is disabled")

    yield

    logger.info("Shutting down worker lifespan")

    if consumer_task:
        await consumer.stop()
        consumer_task.cancel()

        try:
            await consumer_task

        except asyncio.CancelledError:
            pass

    await result_sink.close()
    await dlq_publisher.stop()


app = FastAPI(title="FileManager Worker", lifespan=lifespan)


@app.get("/health")
async def health() -> Mapping[str, str | Sequence[str]]:
    return {
        "status": "UP",
        "version": "0.1.0",
        "processors": [p.name for p in processors],
    }


@app.get("/metrics")
async def metrics(request: Request) -> Response:
    if not settings.metrics_enabled:
        return Response(status_code=404)

    auth_header = request.headers.get("Authorization")

    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid authentication credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = auth_header[7:]

    if not secrets.compare_digest(token, settings.internal_api_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
