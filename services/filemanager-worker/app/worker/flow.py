import logging
from typing import List, Dict, Any
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.sinks.base import ProcessingResultSink

logger = logging.getLogger(__name__)

class ProcessingFlow:
    def __init__(self, processors: List[Processor], result_sink: ProcessingResultSink):
        self.processors = {p.name.upper(): p for p in processors}
        self.result_sink = result_sink

    async def run(self, event: FileProcessingRequestedEvent):
        logger.info(f"Starting processing flow for job: {event.processing_job_id} ({event.job_type})")
        
        job_type = event.job_type.upper()
        processor = self.processors.get(job_type)
        
        try:
            if not processor:
                raise ValueError(f"No processor found for job type: {job_type}")

            if not processor.should_process(event):
                raise ValueError(f"Processor {processor.name} cannot handle file with mime type {event.mime_type}")

            result = await processor.process(event)
            
            if job_type == "CHECKSUM":
                if "sha256" not in result:
                    raise ValueError(f"Processor {processor.name} did not produce required 'sha256' output")
                await self.result_sink.report_checksum_success(
                    event.processing_job_id,
                    event.file_id,
                    result["sha256"]
                )
            elif job_type == "PHASH":
                if "phash" not in result:
                    raise ValueError(f"Processor {processor.name} did not produce required 'phash' output")
                await self.result_sink.report_phash_success(
                    event.processing_job_id,
                    event.file_id,
                    result["phash"]
                )

            logger.info(f"Finished processing flow for job: {event.processing_job_id}. Produced: {list(result.keys())}")
            
            return result
        except Exception as e:
            logger.error(f"Processing flow failed for job {event.processing_job_id}: {e}")
            try:
                await self.result_sink.report_failure(
                    event.processing_job_id,
                    event.file_id,
                    str(e)
                )
                return {}
            except Exception as report_error:
                logger.error(f"Failed to report failure to API for file {event.file_id}: {report_error}")
                raise
