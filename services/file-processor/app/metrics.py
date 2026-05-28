from prometheus_client import Counter, Histogram

# Counters
EVENTS_RECEIVED = Counter(
    "filemanager_worker_events_received_total",
    "Total number of events received from Kafka",
)
EVENTS_PROCESSED = Counter(
    "filemanager_worker_events_processed_total",
    "Total number of events processed",
    ["status"],
)
EVENTS_FAILED = Counter(
    "filemanager_worker_events_failed_total",
    "Total number of events that failed processing",
    ["error_class"],
)
PROCESSOR_RUNS = Counter(
    "filemanager_worker_processor_runs_total",
    "Total number of processor runs",
    ["processor"],
)
PROCESSOR_FAILURES = Counter(
    "filemanager_worker_processor_failures_total",
    "Total number of processor failures",
    ["processor", "failure_type"],
)
DLQ_MESSAGES = Counter(
    "filemanager_worker_dlq_messages_total",
    "Total number of messages sent to DLQ",
)
RETRIES = Counter(
    "filemanager_worker_retries_total",
    "Total number of processing retries",
)

# Histograms
PROCESSING_DURATION = Histogram(
    "filemanager_worker_processing_duration_seconds",
    "Time spent processing a single event",
)
PROCESSOR_DURATION = Histogram(
    "filemanager_worker_processor_duration_seconds",
    "Time spent by a specific processor",
    ["processor"],
)
