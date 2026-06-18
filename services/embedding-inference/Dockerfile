ARG TRITON_BASE_IMAGE=nvcr.io/nvidia/tritonserver:25.10-py3
FROM ${TRITON_BASE_IMAGE}

COPY model_repository /models

RUN test -f /models/image_embedding/1/model.onnx || ( \
    echo "Missing Triton model artifact: services/embedding-inference/model_repository/image_embedding/1/model.onnx" >&2; \
    echo "Generate it before building the embedding profile with services/embedding-inference/scripts/export_image_embedding_model.py." >&2; \
    exit 1 \
)

EXPOSE 8000 8001 8002

ENTRYPOINT ["tritonserver", "--model-repository=/models"]
