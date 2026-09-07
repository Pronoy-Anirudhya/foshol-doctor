#!/usr/bin/env python3
"""Record replay fixtures.

Helper mode (Day 1): SHA a local image and write a vision classify JSON (and a 1×1 PNG overlay).
Live mode: call a LIVE sidecar and write the response; refuse a REPLAY sidecar (SIDECAR-FR-081).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT_FIXTURES = REPO / "sidecar" / "fixtures"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _tiny_png() -> bytes:
    import struct
    import zlib

    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    raw = b"\x00" + bytes((180, 40, 40, 180))
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def write_vision_fixture(
    image: Path,
    fixtures: Path,
    *,
    crop_code: str,
    model_id: str,
    model_version: str,
    architecture: str,
    force: bool,
) -> str:
    digest = sha256_file(image)
    classify_dir = fixtures / "vision" / "classify"
    explain_dir = fixtures / "vision" / "explain"
    classify_dir.mkdir(parents=True, exist_ok=True)
    explain_dir.mkdir(parents=True, exist_ok=True)
    classify_path = classify_dir / f"{digest}.json"
    explain_path = explain_dir / f"{digest}.png"
    if classify_path.exists() and not force:
        print(f"leave unchanged {classify_path}", file=sys.stderr)
        return digest
    body = {
        "mode": "LIVE",
        "crop_code": crop_code,
        "model_id": model_id,
        "model_version": model_version,
        "model_role": "primary",
        "fallback_used": False,
        "fallback_reason": None,
        "architecture": architecture,
        "image_sha256": digest,
        "predictions": [
            {"raw_label": "class_0", "confidence": 0.8123, "rank": 1},
            {"raw_label": "class_1", "confidence": 0.1204, "rank": 2},
            {"raw_label": "class_2", "confidence": 0.0673, "rank": 3},
        ],
        "inference_ms": 1,
        "correlation_id": "fixture",
    }
    classify_path.write_text(json.dumps(body, indent=2) + "\n", encoding="utf-8")
    if force or not explain_path.exists():
        explain_path.write_bytes(_tiny_png())
    _update_index(
        fixtures,
        digest,
        endpoint="/v1/vision/classify",
        model_id=model_id,
        model_version=model_version,
        source=image.name,
    )
    print(f"wrote {classify_path}", file=sys.stderr)
    return digest


def _update_index(
    fixtures: Path,
    digest: str,
    *,
    endpoint: str,
    model_id: str,
    model_version: str,
    source: str,
) -> None:
    index_path = fixtures / "index.json"
    index = {}
    if index_path.is_file():
        index = json.loads(index_path.read_text(encoding="utf-8"))
    index[digest] = {
        "endpoint": endpoint,
        "model_id": model_id,
        "model_version": model_version,
        "source": source,
        "recorded_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    index_path.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")


def _sidecar_mode(base_url: str) -> str:
    with urllib.request.urlopen(base_url.rstrip("/") + "/health", timeout=5) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    return str(body.get("mode", "")).upper()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="SHA an image and write a vision replay fixture.")
    parser.add_argument("--image", type=Path, help="Source image file (helper mode).")
    parser.add_argument("--source-dir", type=Path, help="Directory of images to record from a live sidecar.")
    parser.add_argument("--base-url", help="Live sidecar base URL (recording mode).")
    parser.add_argument("--fixtures", type=Path, default=DEFAULT_FIXTURES)
    parser.add_argument("--crop-code", default="rice", help="Opaque crop_code routing key.")
    parser.add_argument("--model-id", default="kssrikar4/Rice-Leaf-Disease-Classification")
    parser.add_argument("--model-version", default="02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26")
    parser.add_argument("--architecture", default="swin")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args(argv)

    if args.base_url:
        try:
            mode = _sidecar_mode(args.base_url)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            print(f"cannot reach sidecar: {exc}", file=sys.stderr)
            return 1
        if mode == "REPLAY":
            print("refusing to record fixtures from a REPLAY sidecar (SIDECAR-FR-081)", file=sys.stderr)
            return 1
        print("live recording is not implemented in this helper; run helper mode with --image", file=sys.stderr)
        return 1

    if not args.image:
        parser.error("either --image (helper) or --base-url (live recording) is required")

    if not args.image.is_file():
        print(f"not a file: {args.image}", file=sys.stderr)
        return 1

    write_vision_fixture(
        args.image,
        args.fixtures,
        crop_code=args.crop_code,
        model_id=args.model_id,
        model_version=args.model_version,
        architecture=args.architecture,
        force=args.force,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
