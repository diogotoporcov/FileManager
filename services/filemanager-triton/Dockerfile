ARG TRITON_BASE_IMAGE=nvcr.io/nvidia/tritonserver:25.10-py3
FROM ${TRITON_BASE_IMAGE}

COPY model_repository /models

RUN test -f /models/image_embedding/1/model.onnx

EXPOSE 8000 8001 8002

ENTRYPOINT ["tritonserver", "--model-repository=/models"]
