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


ANALYSIS_GRADCAM = REPO / "modules" / "analysis" / "src" / "main" / "resources" / "fixtures" / "gradcam"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}
BOUNDARY = "----FosholFixtureBoundary"


def _sidecar_mode(base_url: str) -> str:
    with urllib.request.urlopen(base_url.rstrip("/") + "/health", timeout=5) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    return str(body.get("mode", "")).upper()


def _crop_from_name(path: Path, fallback: str) -> str:
    name = path.name.lower()
    for crop in ("rice", "tomato", "potato"):
        if crop in name:
            return crop
    return fallback


def _content_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if suffix == ".png":
        return "image/png"
    if suffix == ".webp":
        return "image/webp"
    return "application/octet-stream"


def _multipart(fields: dict[str, str], filename: str, data: bytes, content_type: str) -> tuple[bytes, str]:
    chunks: list[bytes] = []
    for name, value in fields.items():
        chunks.append(
            (
                f"--{BOUNDARY}\r\n"
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
                f"{value}\r\n"
            ).encode("utf-8")
        )
    chunks.append(
        (
            f"--{BOUNDARY}\r\n"
            f'Content-Disposition: form-data; name="image"; filename="{filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
    )
    chunks.append(data)
    chunks.append(b"\r\n")
    chunks.append(f"--{BOUNDARY}--\r\n".encode("utf-8"))
    return b"".join(chunks), f"multipart/form-data; boundary={BOUNDARY}"


def _post_multipart(url: str, body: bytes, content_type: str, timeout: int = 30) -> tuple[int, bytes, dict[str, str]]:
    request = urllib.request.Request(url, data=body, method="POST")
    request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as resp:
            headers = {k: v for k, v in resp.headers.items()}
            return int(resp.status), resp.read(), headers
    except urllib.error.HTTPError as exc:
        return int(exc.code), exc.read() if exc.fp else b"", dict(exc.headers.items()) if exc.headers else {}


def _write_explain_copies(digest: str, png: bytes, fixtures: Path, analysis_dir: Path, force: bool) -> None:
    explain_dir = fixtures / "vision" / "explain"
    explain_dir.mkdir(parents=True, exist_ok=True)
    analysis_dir.mkdir(parents=True, exist_ok=True)
    sidecar_path = explain_dir / f"{digest}.png"
    analysis_path = analysis_dir / f"{digest}.png"
    if force or not sidecar_path.exists():
        sidecar_path.write_bytes(png)
    if force or not analysis_path.exists():
        analysis_path.write_bytes(png)


def record_live_image(
    image: Path,
    *,
    base_url: str,
    fixtures: Path,
    analysis_dir: Path,
    crop_code: str,
    force: bool,
) -> int:
    digest = sha256_file(image)
    classify_dir = fixtures / "vision" / "classify"
    classify_dir.mkdir(parents=True, exist_ok=True)
    classify_path = classify_dir / f"{digest}.json"
    explain_path = fixtures / "vision" / "explain" / f"{digest}.png"
    if classify_path.exists() and explain_path.exists() and not force:
        print(f"leave unchanged {classify_path}", file=sys.stderr)
        return 0
    payload = image.read_bytes()
    fields = {"crop_code": crop_code}
    body, content_type = _multipart(fields, image.name, payload, _content_type(image))
    root = base_url.rstrip("/")
    status, raw, _headers = _post_multipart(root + "/v1/vision/classify", body, content_type)
    if status != 200:
        print(f"classify failed {image.name} status={status} body={raw[:200]!r}", file=sys.stderr)
        return 1
    classify = json.loads(raw.decode("utf-8"))
    if force or not classify_path.exists():
        classify_path.write_text(json.dumps(classify, indent=2) + "\n", encoding="utf-8")
    predictions = classify.get("predictions") or []
    target = str(predictions[0]["raw_label"]) if predictions else ""
    explain_fields = dict(fields)
    if target:
        explain_fields["target_label"] = target
    body, content_type = _multipart(explain_fields, image.name, payload, _content_type(image))
    status, png, _headers = _post_multipart(root + "/v1/vision/explain", body, content_type, timeout=20)
    if status != 200 or not png.startswith(b"\x89PNG"):
        print(f"explain failed {image.name} status={status}", file=sys.stderr)
        return 1
    _write_explain_copies(digest, png, fixtures, analysis_dir, force)
    _update_index(
        fixtures,
        digest,
        endpoint="/v1/vision/explain",
        model_id=str(classify.get("model_id") or ""),
        model_version=str(classify.get("model_version") or ""),
        source=image.name,
    )
    print(f"recorded {image.name} digest={digest}", file=sys.stderr)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="SHA an image and write a vision replay fixture.")
    parser.add_argument("--image", type=Path, help="Source image file (helper mode).")
    parser.add_argument("--source-dir", type=Path, help="Directory of images to record from a live sidecar.")
    parser.add_argument("--base-url", help="Live sidecar base URL (recording mode).")
    parser.add_argument("--fixtures", type=Path, default=DEFAULT_FIXTURES)
    parser.add_argument("--analysis-fixtures", type=Path, default=ANALYSIS_GRADCAM)
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
        images: list[Path]
        if args.source_dir:
            if not args.source_dir.is_dir():
                print(f"not a directory: {args.source_dir}", file=sys.stderr)
                return 1
            images = sorted(
                path for path in args.source_dir.iterdir() if path.suffix.lower() in IMAGE_SUFFIXES
            )
        elif args.image:
            images = [args.image]
        else:
            parser.error("--base-url requires --source-dir or --image")
            return 2
        failed = 0
        for image in images:
            if not image.is_file():
                print(f"not a file: {image}", file=sys.stderr)
                failed += 1
                continue
            crop = _crop_from_name(image, args.crop_code)
            failed += record_live_image(
                image,
                base_url=args.base_url,
                fixtures=args.fixtures,
                analysis_dir=args.analysis_fixtures,
                crop_code=crop,
                force=args.force,
            )
        return 1 if failed else 0

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
