import logging
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI, Response, HTTPException, status, Request
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST
from app.config import settings
from app.processors.impl import ChecksumProcessor, PHashProcessor
from app.storage.s3 import S3ObjectStorageReader
from app.sinks.http import HttpProcessingResultSink
from app.worker.flow import ProcessingFlow
from app.worker.consumer import EventConsumer
from app.worker.dlq import KafkaDeadLetterPublisher
from app.worker.handler import WorkerMessageHandler

logging.basicConfig(level=getattr(logging, settings.log_level.upper()))
logger = logging.getLogger(__name__)

# Wire dependencies
storage_reader = S3ObjectStorageReader()
result_sink = HttpProcessingResultSink()
dlq_publisher = KafkaDeadLetterPublisher()

processors = [
    ChecksumProcessor(storage_reader),
    PHashProcessor(storage_reader)
]

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
    await dlq_publisher.stop()

app = FastAPI(title="FileManager Worker", lifespan=lifespan)

@app.get("/health")
async def health():
    return {
        "status": "UP",
        "version": "0.1.0",
        "processors": [p.name for p in processors]
    }

@app.get("/metrics")
async def metrics(request: Request):
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
    if token != settings.internal_api_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
        
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
