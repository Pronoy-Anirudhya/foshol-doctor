"""Read-only SHA-256 fixture store (SIDECAR-FR-070…074)."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class FixtureStore:
    def __init__(self, root: Path) -> None:
        self.root = root

    def classify_path(self, digest: str) -> Path:
        nested = self.root / "vision" / "classify" / f"{digest}.json"
        if nested.exists():
            return nested
        return self.root / "vision" / f"{digest}.json"

    def explain_path(self, digest: str) -> Path:
        nested = self.root / "vision" / "explain" / f"{digest}.png"
        if nested.exists():
            return nested
        return self.root / "gradcam" / f"{digest}.png"

    def transcribe_path(self, digest: str) -> Path:
        return self.root / "asr" / "transcribe" / f"{digest}.json"

    def embed_path(self, digest: str) -> Path:
        return self.root / "embed" / f"{digest}.json"

    def load_json(self, path: Path) -> dict[str, Any] | None:
        if not path.is_file():
            return None
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)

    def load_bytes(self, path: Path) -> bytes | None:
        if not path.is_file():
            return None
        return path.read_bytes()
