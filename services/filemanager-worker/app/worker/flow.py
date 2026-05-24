import logging
from typing import List, Dict, Any
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.matchers.base import Matcher
from app.sinks.base import ProcessingResultSink

logger = logging.getLogger(__name__)

class ProcessingFlow:
    def __init__(self, processors: List[Processor], matchers: List[Matcher], result_sink: ProcessingResultSink):
        self.processors = processors
        self.matchers = matchers
        self.result_sink = result_sink

    async def run(self, event: FileProcessingRequestedEvent):
        logger.info(f"Starting processing flow for file: {event.file_id}")
        
        derived_data: Dict[str, Any] = {}
        
        try:
            for processor in self.processors:
                if processor.should_process(event):
                    logger.debug(f"Running processor: {processor.name}")
                    result = await processor.process(event)
                    derived_data.update(result)

                    if processor.name == "checksum" and "sha256" in result:
                        await self.result_sink.report_checksum_success(
                            event.processing_job_id,
                            event.file_id,
                            result["sha256"]
                        )

            matches: List[Dict[str, Any]] = []
            for matcher in self.matchers:
                try:
                    logger.debug(f"Running matcher: {matcher.name}")
                    match_results = await matcher.match(event, derived_data)
                    matches.extend(match_results)
                except Exception as e:
                    logger.error(f"Matcher {matcher.name} failed: {e}")

            logger.info(f"Finished processing flow for file: {event.file_id}. Produced: {list(derived_data.keys())}, Matches found: {len(matches)}")
            
            return derived_data, matches
        except Exception as e:
            logger.error(f"Processing flow failed for file {event.file_id}: {e}")
            await self.result_sink.report_failure(
                event.processing_job_id,
                event.file_id,
                str(e)
            )
            raise
