import logging
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.config import settings
from app.processors.impl import ChecksumProcessor, PHashProcessor, EmbeddingProcessor
from app.matchers.impl import ExactHashMatcher, PHashMatcher, EmbeddingMatcher
from app.worker.flow import ProcessingFlow
from app.worker.consumer import EventConsumer

logging.basicConfig(level=getattr(logging, settings.log_level.upper()))
logger = logging.getLogger(__name__)

processors = [
    ChecksumProcessor(),
    PHashProcessor(),
    EmbeddingProcessor()
]

matchers = [
    ExactHashMatcher(),
    PHashMatcher(),
    EmbeddingMatcher()
]

flow = ProcessingFlow(processors, matchers)
consumer = EventConsumer(flow)

@asynccontextmanager
async def lifespan(app: FastAPI):
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

app = FastAPI(title="FileManager Worker", lifespan=lifespan)

@app.get("/health")
async def health():
    return {
        "status": "UP",
        "version": "0.1.0",
        "processors": [p.name for p in processors],
        "matchers": [m.name for m in matchers]
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
