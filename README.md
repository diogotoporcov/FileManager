# FileManager

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![Python](https://img.shields.io/badge/Python-3.11-blue.svg)](https://www.python.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-6DB33F?logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.4-2C2C2C.svg)](https://www.keycloak.org/)
[![MinIO](https://img.shields.io/badge/MinIO-RELEASE.2025--09--07T16--13--09Z-C72E49.svg)](https://min.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql\&logoColor=white)](https://www.postgresql.org/)
[![Redpanda](https://img.shields.io/badge/Redpanda-ED1E24?logo=redpanda\&logoColor=white)](https://redpanda.com/)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

FileManager is a **file management backend system** designed to **store, organize, search, and share files**.

It features a highly optimized **multi-method duplicate detection engine** capable of finding both **exact duplicates** and **similar files** using checksums, perceptual hashes, image embeddings, and audio fingerprints.

---

## Summary

1. [Features](#features)
2. [Technology Stack](#technology-stack)
3. [Installation Guide](#installation-guide)
4. [Configuration](#configuration)
5. [Running the Project](#running-the-project)
6. [Using the Application](#using-the-application)
7. [API Documentation](#api-documentation)
8. [Duplicate Detection](#duplicate-detection)
9. [License](#license)

---

## Features

### File Management

* **Organize files into folders** and nested directory structures.
* **Search files and folders** using names, metadata, filters, and sorting options.
* **Use tags** to organize files and folders and locate related content more easily.

### File and Folder Sharing

* **Share files and folders** with other users.
* **Assign permissions** that control how shared content can be accessed.
* **Share folders directly or recursively** with their nested content.
* **Revoke access** when a file or folder should no longer be shared.

### Duplicate Detection

* **Find byte-identical files** using exact duplicate detection.
* **Find visually similar images** even when they have been resized, recompressed, or converted.
* **Find semantically similar images** based on their visual content.
* **Find matching audio files** independently from their container or metadata.

---

## Technology Stack

| Component           | Technology                 |
| ------------------- | -------------------------- |
| API Gateway         | Java, Spring Cloud Gateway |
| API                 | Java 25, Spring Boot       |
| File processor      | Python 3.11, FastAPI       |
| Database            | PostgreSQL, pgvector       |
| Object storage      | MinIO                      |
| Authentication      | Keycloak                   |
| Messaging           | Redpanda                   |
| Embedding inference | NVIDIA Triton              |

---

## Installation Guide

> **TODO**

---

## Configuration

> **TODO**

---

## Running the Project

> **TODO**

---

## Using the Application

> **TODO**

---

## API Documentation

> **TODO**

---

## Duplicate Detection

FileManager applies different duplicate-detection strategies according to the media type and the kind of similarity being measured.

### All Files

Every file receives a content hash calculated from its binary data.

Files with the same hash are treated as exact duplicates because their contents are byte-for-byte identical. This method is deterministic and works for every supported file type.

### Images

Images receive two additional representations for similarity analysis.

#### 1. Perceptual Hash (pHash)

A perceptual hash converts the visual structure of an image into a compact binary signature.

Similarity is determined by comparing the Hamming distance between signatures. This allows FileManager to detect images that remain visually similar after operations such as resizing, recompression, format conversion, or minor visual modification.

The search first narrows the candidate set through indexed hash bands and then calculates the exact Hamming distance for the remaining candidates.

#### 2. Image Embeddings

An image embedding encodes the semantic and visual characteristics of an image as a high-dimensional vector.

Similarity is measured using cosine distance between vectors. FileManager performs approximate nearest-neighbor searches through an HNSW index.

This method can identify images with related subjects, objects, scenes, composition, or visual meaning even when their binary contents and perceptual hashes differ significantly.

### Audio

Audio files receive a **Chromaprint acoustic fingerprint** derived from their decoded audio signal.

The fingerprint represents characteristic patterns in the recording rather than container metadata or file encoding. This allows FileManager to identify equivalent audio content across different containers, bitrates, codecs, or metadata values.

---

## License

This project is licensed under the GPLv3 License — see [LICENSE](LICENSE) for details.
