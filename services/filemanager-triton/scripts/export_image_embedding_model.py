import os
from pathlib import Path

import torch
import torch.nn.functional as functional
from transformers import CLIPModel

MODEL_ID = os.getenv("TRITON_EXPORT_MODEL_ID", "openai/clip-vit-large-patch14")
OUTPUT_PATH = Path(os.getenv("TRITON_EXPORT_OUTPUT", "model_repository/image_embedding/1/model.onnx"))
INPUT_SIZE = int(os.getenv("TRITON_EXPORT_INPUT_SIZE", "224"))
DIMENSION = int(os.getenv("TRITON_EXPORT_DIMENSION", "768"))
OPSET = int(os.getenv("TRITON_EXPORT_OPSET", "17"))


class NormalizedClipImageEncoder(torch.nn.Module):
    def __init__(self, model_id: str):
        super().__init__()
        self.clip = CLIPModel.from_pretrained(model_id)
        self.clip.eval()

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        features = self.clip.get_image_features(pixel_values=pixel_values)
        return functional.normalize(features, p=2, dim=-1)


def main() -> None:
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    model = NormalizedClipImageEncoder(MODEL_ID)
    dummy_input = torch.zeros(1, 3, INPUT_SIZE, INPUT_SIZE, dtype=torch.float32)

    with torch.no_grad():
        output = model(dummy_input)
    if output.shape != (1, DIMENSION):
        raise ValueError(f"Expected output shape (1, {DIMENSION}), got {tuple(output.shape)}")

    torch.onnx.export(
        model,
        (dummy_input,),
        str(OUTPUT_PATH),
        input_names=["pixel_values"],
        output_names=["image_embeds"],
        dynamic_axes={
            "pixel_values": {0: "batch"},
            "image_embeds": {0: "batch"},
        },
        opset_version=OPSET,
    )


if __name__ == "__main__":
    main()
