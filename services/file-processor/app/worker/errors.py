from enum import Enum

class FailureCategory(str, Enum):
    RETRYABLE = "RETRYABLE"
    NON_RETRYABLE = "NON_RETRYABLE"
    POISON = "POISON"

class ProcessingError(Exception):
    def __init__(self, message: str, category: FailureCategory = FailureCategory.NON_RETRYABLE):
        super().__init__(message)
        self.category = category

class RetryableProcessingError(ProcessingError):
    def __init__(self, message: str):
        super().__init__(message, category=FailureCategory.RETRYABLE)

class NonRetryableProcessingError(ProcessingError):
    def __init__(self, message: str):
        super().__init__(message, category=FailureCategory.NON_RETRYABLE)
