"""Generate committed synthetic replay fixtures (no field photographs, no EXIF)."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from tests.conftest import png_bytes, wav_bytes

from app.replay import sha256_hex

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "fixtures"


def write_png(path: Path, **kwargs) -> bytes:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = png_bytes(**kwargs)
    path.write_bytes(data)
    return data


def main() -> None:
    rice = write_png(FIXTURES / "sources" / "synthetic-rice.png", rgba=(200, 16, 16, 255))
    tomato = write_png(FIXTURES / "sources" / "synthetic-tomato.png", width=1, height=2, rgba=(16, 160, 16, 255))
    rice_sha = sha256_hex(rice)
    tomato_sha = sha256_hex(tomato)

    rice_body = {
        "mode": "LIVE",
        "crop_code": "rice",
        "model_id": "kssrikar4/Rice-Leaf-Disease-Classification",
        "model_version": "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26",
        "model_role": "primary",
        "fallback_used": False,
        "fallback_reason": None,
        "architecture": "swin",
        "image_sha256": rice_sha,
        "predictions": [
            {"raw_label": "class_0", "confidence": 0.8123, "rank": 1},
            {"raw_label": "class_1", "confidence": 0.1204, "rank": 2},
            {"raw_label": "class_2", "confidence": 0.0673, "rank": 3},
        ],
        "inference_ms": 1,
        "correlation_id": "fixture",
    }
    tomato_body = {
        "mode": "LIVE",
        "crop_code": "tomato",
        "model_id": "Daksh159/plant-disease-mobilenetv2",
        "model_version": "d3fb2afc90da83086eff06e9088a889b6c43d4a6",
        "model_role": "primary",
        "fallback_used": False,
        "fallback_reason": None,
        "architecture": "mobilenetv2",
        "image_sha256": tomato_sha,
        "predictions": [
            {"raw_label": "class_0", "confidence": 0.6401, "rank": 1},
            {"raw_label": "class_1", "confidence": 0.2100, "rank": 2},
        ],
        "inference_ms": 1,
        "correlation_id": "fixture",
    }

    classify_dir = FIXTURES / "vision" / "classify"
    explain_dir = FIXTURES / "vision" / "explain"
    asr_dir = FIXTURES / "asr" / "transcribe"
    classify_dir.mkdir(parents=True, exist_ok=True)
    explain_dir.mkdir(parents=True, exist_ok=True)
    asr_dir.mkdir(parents=True, exist_ok=True)
    (FIXTURES / "embed").mkdir(parents=True, exist_ok=True)
    (FIXTURES / "gradcam").mkdir(parents=True, exist_ok=True)

    (classify_dir / f"{rice_sha}.json").write_text(json.dumps(rice_body, indent=2) + "\n", encoding="utf-8")
    (classify_dir / f"{tomato_sha}.json").write_text(json.dumps(tomato_body, indent=2) + "\n", encoding="utf-8")
    (explain_dir / f"{rice_sha}.png").write_bytes(png_bytes(rgba=(40, 40, 200, 200)))
    (explain_dir / f"{tomato_sha}.png").write_bytes(png_bytes(rgba=(40, 200, 40, 200)))
    (FIXTURES / "gradcam" / f"{rice_sha}.png").write_bytes(png_bytes(rgba=(40, 40, 200, 200)))

    silent = wav_bytes(duration_s=0.05)
    silent_sha = sha256_hex(silent)
    (FIXTURES / "sources" / "synthetic-silent.wav").write_bytes(silent)
    asr_body = {
        "mode": "LIVE",
        "model_id": "ashrafulparan/whisper-small-bangla",
        "model_version": "25c88973563146654493b97882fb2806d2fdeaaa",
        "audio_sha256": silent_sha,
        "language": "bn",
        "duration_ms": 50,
        "sample_rate_hz": 16000,
        "speech_detected": False,
        "transcript": "",
        "confidence": 0.0,
        "segments": [],
        "inference_ms": 1,
        "correlation_id": "fixture",
    }
    (asr_dir / f"{silent_sha}.json").write_text(json.dumps(asr_body, indent=2) + "\n", encoding="utf-8")

    recorded = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    index = {
        rice_sha: {
            "endpoint": "/v1/vision/classify",
            "model_id": rice_body["model_id"],
            "model_version": rice_body["model_version"],
            "source": "synthetic-rice.png",
            "recorded_at": recorded,
            "note": "synthetic 1x1 PNG; not a field photograph; EXIF-free",
        },
        tomato_sha: {
            "endpoint": "/v1/vision/classify",
            "model_id": tomato_body["model_id"],
            "model_version": tomato_body["model_version"],
            "source": "synthetic-tomato.png",
            "recorded_at": recorded,
            "note": "synthetic 1x2 PNG; not a field photograph; EXIF-free",
        },
        silent_sha: {
            "endpoint": "/v1/asr/transcribe",
            "model_id": asr_body["model_id"],
            "model_version": asr_body["model_version"],
            "source": "synthetic-silent.wav",
            "recorded_at": recorded,
            "note": "synthetic silent PCM WAV; not a person's voice",
        },
    }
    (FIXTURES / "index.json").write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    print(f"rice={rice_sha}")
    print(f"tomato={tomato_sha}")
    print(f"silent={silent_sha}")


if __name__ == "__main__":
    main()
