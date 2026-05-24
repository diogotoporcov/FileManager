import logging
from typing import List, Dict, Any
from app.events.models import FileProcessingRequestedEvent
from app.processors.base import Processor
from app.matchers.base import Matcher

logger = logging.getLogger(__name__)

class ProcessingFlow:
    def __init__(self, processors: List[Processor], matchers: List[Matcher]):
        self.processors = processors
        self.matchers = matchers

    async def run(self, event: FileProcessingRequestedEvent):
        logger.info(f"Starting processing flow for file: {event.file_id}")
        
        derived_data: Dict[str, Any] = {}
        
        for processor in self.processors:
            if processor.should_process(event):
                try:
                    logger.debug(f"Running processor: {processor.name}")
                    result = await processor.process(event)
                    derived_data.update(result)
                except Exception as e:
                    logger.error(f"Processor {processor.name} failed: {e}")

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
