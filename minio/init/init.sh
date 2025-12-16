#!/bin/sh
set -eu

echo "Configuring MinIO..."

mc alias set minio http://minio:9000 \
  "$MINIO_ROOT_USER" \
  "$MINIO_ROOT_PASSWORD"

mc mb --ignore-existing "minio/$MINIO_BUCKET"

echo "MinIO initialization complete."
