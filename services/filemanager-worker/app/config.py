from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # Kafka/Redpanda
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_topic_file_processing: str = "file.processing.requested"
    kafka_consumer_group_id: str = "filemanager-worker-group"
    worker_consumer_enabled: bool = False

    # MinIO/S3
    s3_endpoint: str = "http://localhost:9000"
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin"
    s3_bucket_name: str = "filemanager"

    # Logging
    log_level: str = "INFO"

    model_config = SettingsConfigDict(extra="ignore")

settings = Settings()
