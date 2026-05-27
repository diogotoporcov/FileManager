from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
MODEL_REPOSITORY = REPOSITORY_ROOT / "embedding-inference" / "model_repository" / "image_embedding"
MODEL_CONFIG = MODEL_REPOSITORY / "config.pbtxt"
MODEL_VERSION_DIRECTORY = MODEL_REPOSITORY / "1"


def test_tracked_triton_model_repository_config_matches_worker_defaults() -> None:
    config = MODEL_CONFIG.read_text(encoding="utf-8")

    assert 'name: "image_embedding"' in config
    assert 'platform: "onnxruntime_onnx"' in config
    assert 'name: "pixel_values"' in config
    assert "data_type: TYPE_FP32" in config
    assert "dims: [3, 224, 224]" in config
    assert 'name: "image_embeds"' in config
    assert "dims: [768]" in config


def test_triton_model_version_directory_is_tracked_without_large_artifacts() -> None:
    assert MODEL_VERSION_DIRECTORY.is_dir()
    assert (MODEL_VERSION_DIRECTORY / ".gitkeep").is_file()
