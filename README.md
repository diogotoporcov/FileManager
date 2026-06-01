# FileManager

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Python](https://img.shields.io/badge/Python-3.11-blue.svg)](https://www.python.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.4-2C2C2C.svg)](https://www.keycloak.org/)
[![MinIO](https://img.shields.io/badge/MinIO-RELEASE.2025--09--07T16--13--09Z-C72E49.svg)](https://min.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redpanda](https://img.shields.io/badge/Redpanda-ED1E24?logo=redpanda&logoColor=white)](https://redpanda.com/)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

FileManager is a file management system designed for organizing, storing, and preventing duplicate files in large databases. It leverages advanced techniques like visual feature extraction and semantic search to identify and manage duplicates across multiple layers.

## Core Features

- **Multi-tenant Architecture**: Comprehensive support for Organizations and Users with granular Role-Based Access Control (RBAC).
- **First-class Folders**: Folders are database-backed containers and permission scopes for files and child folders.
- **Intelligent Duplicate Detection**:
  - **Exact Match**: SHA-256 hashing for identical binary files.
  - **Visual Similarity**: pHash (Perceptual Hashing) for identifying visually similar images.
  - **Semantic Search**: Support for AI-driven vector embeddings (using `pgvector`) for deep semantic similarity.
- **Asynchronous Pipeline**: Event-driven processing architecture using Redpanda and Python workers for scalable background tasks.
- **Secure Storage**: S3-compatible storage integration (MinIO) for reliable and high-performance file management.
- **Robust Authentication**: Full integration with Keycloak for secure identity and access management.

## Tech Stack

- **Gateway**: Spring Cloud Gateway (Java 25)
- **Core API**: Spring Boot 4.x (Java 25)
- **Worker**: FastAPI (Python 3.11)
- **Database**: PostgreSQL 16+ with `pgvector` extension
- **Messaging**: Redpanda (Kafka-compatible)
- **Object Storage**: MinIO (S3-compatible)
- **Identity Provider**: Keycloak

## Getting Started

### Prerequisites

- Docker and Docker Compose
- JDK 25+ (for building/development)
- Python 3.11+ (for worker development)

### Running with Docker

To start the infrastructure services (Database, Messaging, Storage, Auth), run:

```bash
docker-compose up -d
```

## Folder API

The Java API models folders as first-class database resources. Folder membership is stored in PostgreSQL,
not inferred from object-storage paths.

Supported authenticated endpoints:

- `POST /folders` creates a root folder or child folder.
- `GET /folders?ownerUserId=...` and `GET /folders?ownerOrganizationId=...` list active root folders.
- `GET /folders/{folderId}` returns folder metadata.
- `PATCH /folders/{folderId}` renames a folder.
- `DELETE /folders/{folderId}` soft deletes an empty folder.
- `GET /folders/{folderId}/children` lists direct child folders only.
- `POST /folders/{folderId}/folders` creates a direct child folder.
- `GET /folders/{folderId}/files` lists files directly inside a folder with the existing file filters,
  sorting, and cursor pagination.
- `POST /folders/{folderId}/files` uploads a file into a folder.
- `POST /files` also accepts `folderId` when the supplied owner scope matches the folder owner scope.
- `GET /files` accepts `folderId`; without `folderId`, the existing behavior is preserved and files are
  listed across the requested owner scope.

Current permission behavior uses the existing owner and organization membership model. User-owned folders
are accessible only by their owner. Organization-owned folders require membership permissions such as
`FOLDER_VIEW`, `FOLDER_CREATE`, `FOLDER_RENAME`, `FOLDER_DELETE`, and `FOLDER_UPLOAD_FILE`. Granular
per-folder permission grants, guest/public upload links, recursive folder search, folder moves, and
delete-own-only rules are deferred.

Folder deletion is conservative: non-empty folders return `409 Conflict` and are not cascaded. File listing
by folder is direct only and does not include descendant folders.

## License

This project is licensed under the GPLv3 License — see [LICENSE](LICENSE) for details.
